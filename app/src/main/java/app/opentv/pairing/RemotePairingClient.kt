/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.pairing

import android.util.Log
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Filter and curation options for an Xtream provider provisioned remotely.
 */
data class XtreamFilterOptions(
    val includeLive: Boolean = true,
    val includeVod: Boolean = false,
    val includeSeries: Boolean = false,
    val excludeKeywords: List<String> = emptyList(),
    val includeKeywords: List<String> = emptyList(),
    val excludeCategories: List<String> = emptyList(),
    val includeCategories: List<String> = emptyList(),
)

/**
 * Represents a provisioned playlist or provider item received from the remote admin portal.
 */
data class ProvisionedSource(
    val id: Long? = null,
    val name: String,
    val kind: SourceKind,
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val epgUrl: String? = null,
    val filterOptions: XtreamFilterOptions? = null,
) {
    fun toSource(): Source = Source(
        id = id ?: 0L,
        name = name,
        kind = kind,
        url = url,
        username = username,
        password = password,
        epgUrl = epgUrl,
    )
}

/**
 * Client for OpenTV Self-Hosted Remote Pairing & Provisioning Service.
 *
 * Connects to a self-hosted instance (e.g. running on Synology NAS or behind a Cloudflare Tunnel),
 * obtains an ephemeral 6-character code, renders a QR code pointing to the admin portal,
 * and waits on a WebSocket for real-time multi-playlist provisioning with Xtream channel filtering.
 */
class RemotePairingClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build(),
) {

    data class Session(
        val code: String,
        val expiresInSeconds: Int,
        val webPortalUrl: String,
        val webSocketUrl: String,
    )

    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data class Listening(val session: Session) : State
        data class Received(
            val sources: List<ProvisionedSource>,
            val deletedSourceIds: List<Long> = emptyList(),
        ) : State {
            val draft: Source get() = sources.firstOrNull()?.toSource() ?: Source(name = "Provider", kind = SourceKind.M3U, url = "")
        }
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var activeCall: Call? = null
    private var activeWebSocket: WebSocket? = null
    @Volatile private var running = false
    private var currentSources: List<Source> = emptyList()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null

    /**
     * Updates the current playlist sources and sends device_info if connected.
     */
    fun updateSources(sources: List<Source>) {
        this.currentSources = sources
        sendDeviceInfoIfOpen()
    }

    private fun sendDeviceInfoIfOpen() {
        val ws = activeWebSocket ?: return
        if (currentSources.isEmpty()) return
        try {
            val devInfo = JSONObject()
            devInfo.put("type", "device_info")
            val arr = org.json.JSONArray()
            for (s in currentSources) {
                val sObj = JSONObject()
                sObj.put("id", s.id)
                sObj.put("name", s.name)
                sObj.put("kind", s.kind.name.lowercase())
                sObj.put("url", s.url)
                sObj.put("username", s.username ?: "")
                sObj.put("password", s.password ?: "")
                sObj.put("epgUrl", s.epgUrl ?: "")
                arr.put(sObj)
            }
            devInfo.put("sources", arr)
            ws.send(devInfo.toString())
            Log.d(TAG, "Sent device_info with ${currentSources.size} source(s)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send device_info", e)
        }
    }

    /**
     * Start the remote pairing handshake with the given server base URL.
     * @param serverBaseUrl Base URL, e.g. "http://192.168.1.100:3000" or "https://pair.yourdomain.com"
     * @param initialSources Current sources on the TV to report to the admin portal
     */
    fun start(serverBaseUrl: String, initialSources: List<Source> = emptyList()) {
        if (running) return
        val cleanUrl = serverBaseUrl.trim().trimEnd('/')
        if (cleanUrl.isEmpty()) {
            _state.value = State.Failed("Please specify your pairing server URL.")
            return
        }

        this.currentSources = initialSources
        running = true
        _state.value = State.Connecting

        val initUrl = "$cleanUrl/api/pair/init"
        val request = Request.Builder()
            .url(initUrl)
            .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()

        val call = okHttpClient.newCall(request)
        activeCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!running) return
                Log.w(TAG, "Failed to reach remote pairing server", e)
                _state.value = State.Failed(
                    "Cannot connect to pairing server at $cleanUrl.\nCheck that your container is running on your NAS."
                )
            }

            override fun onResponse(call: Call, response: Response) {
                if (!running) return
                response.use { res ->
                    if (!res.isSuccessful) {
                        _state.value = State.Failed("Pairing server returned error HTTP ${res.code}")
                        return
                    }

                    val bodyStr = res.body?.string().orEmpty()
                    try {
                        val json = JSONObject(bodyStr)
                        val code = json.getString("code")
                        val expiresIn = json.optInt("expiresIn", 600)

                        val portalUrl = "$cleanUrl/?code=$code"

                        val isHttps = cleanUrl.startsWith("https://", ignoreCase = true)
                        val hostAndPort = cleanUrl.substringAfter("://")
                        val wsProtocol = if (isHttps) "wss" else "ws"
                        val wsUrl = "$wsProtocol://$hostAndPort/?code=$code"

                        val session = Session(
                            code = code,
                            expiresInSeconds = expiresIn,
                            webPortalUrl = portalUrl,
                            webSocketUrl = wsUrl,
                        )

                        _state.value = State.Listening(session)

                        connectWebSocket(wsUrl)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing pairing init response", e)
                        _state.value = State.Failed("Failed to parse server response.")
                    }
                }
            }
        })
    }

    private fun connectWebSocket(wsUrl: String) {
        if (!running) return

        val request = Request.Builder().url(wsUrl).build()
        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket open to $wsUrl")
                if (currentSources.isNotEmpty()) {
                    try {
                        val devInfo = JSONObject()
                        devInfo.put("type", "device_info")
                        val arr = org.json.JSONArray()
                        for (s in currentSources) {
                            val sObj = JSONObject()
                            sObj.put("id", s.id)
                            sObj.put("name", s.name)
                            sObj.put("kind", s.kind.name.lowercase())
                            sObj.put("url", s.url)
                            sObj.put("username", s.username ?: "")
                            sObj.put("password", s.password ?: "")
                            sObj.put("epgUrl", s.epgUrl ?: "")
                            arr.put(sObj)
                        }
                        devInfo.put("sources", arr)
                        webSocket.send(devInfo.toString())
                        Log.d(TAG, "Sent device_info with ${currentSources.size} source(s)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send device_info", e)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WS Message: $text")
                try {
                    val json = JSONObject(text)
                    if (json.optString("type") == "provision") {
                        val parsedSources = mutableListOf<ProvisionedSource>()

                        val playlistsArr = json.optJSONArray("playlists")
                        if (playlistsArr != null && playlistsArr.length() > 0) {
                            for (i in 0 until playlistsArr.length()) {
                                val item = playlistsArr.getJSONObject(i)
                                val isXtream = item.optString("kind").equals("xtream", ignoreCase = true)
                                val name = item.optString("name").ifBlank {
                                    if (isXtream) "Xtream Provider" else "M3U Playlist"
                                }
                                val epgUrl = item.optString("epgUrl").takeIf { it.isNotBlank() && it != "null" }
                                val id = item.optLong("id", 0L).takeIf { it != 0L }

                                if (isXtream) {
                                    val optObj = item.optJSONObject("options")
                                    val filterOptions = if (optObj != null) {
                                        XtreamFilterOptions(
                                            includeLive = optObj.optBoolean("includeLive", true),
                                            includeVod = optObj.optBoolean("includeVod", false),
                                            includeSeries = optObj.optBoolean("includeSeries", false),
                                            excludeKeywords = optObj.optString("excludeKeywords")
                                                .split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                            includeKeywords = optObj.optString("includeKeywords")
                                                .split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                            excludeCategories = optObj.optJSONArray("excludeCategories")?.let { arr ->
                                                (0 until arr.length()).map { arr.getString(it) }
                                            } ?: emptyList(),
                                            includeCategories = optObj.optJSONArray("includeCategories")?.let { arr ->
                                                (0 until arr.length()).map { arr.getString(it) }
                                            } ?: emptyList()
                                        )
                                    } else null

                                    parsedSources.add(
                                        ProvisionedSource(
                                            id = id,
                                            name = name,
                                            kind = SourceKind.XTREAM,
                                            url = item.getString("serverUrl"),
                                            username = item.getString("username"),
                                            password = item.getString("password"),
                                            epgUrl = epgUrl,
                                            filterOptions = filterOptions,
                                        )
                                    )
                                } else {
                                    parsedSources.add(
                                        ProvisionedSource(
                                            id = id,
                                            name = name,
                                            kind = SourceKind.M3U,
                                            url = item.getString("playlistUrl"),
                                            epgUrl = epgUrl,
                                        )
                                    )
                                }
                            }
                        } else {
                            // Fallback to legacy single provision format
                            val isXtream = json.optString("playlistType") == "xtream" ||
                                (json.has("xtreamData") && !json.isNull("xtreamData"))

                            val source = if (isXtream) {
                                val xObj = json.getJSONObject("xtreamData")
                                val optObj = xObj.optJSONObject("options")
                                val filterOptions = if (optObj != null) {
                                    XtreamFilterOptions(
                                        includeLive = optObj.optBoolean("includeLive", true),
                                        includeVod = optObj.optBoolean("includeVod", false),
                                        includeSeries = optObj.optBoolean("includeSeries", false),
                                        excludeKeywords = optObj.optString("excludeKeywords")
                                            .split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                        includeKeywords = optObj.optString("includeKeywords")
                                            .split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                        excludeCategories = optObj.optJSONArray("excludeCategories")?.let { arr ->
                                            (0 until arr.length()).map { arr.getString(it) }
                                        } ?: emptyList(),
                                        includeCategories = optObj.optJSONArray("includeCategories")?.let { arr ->
                                            (0 until arr.length()).map { arr.getString(it) }
                                        } ?: emptyList()
                                    )
                                } else null

                                ProvisionedSource(
                                    name = "Xtream Provider",
                                    kind = SourceKind.XTREAM,
                                    url = xObj.getString("serverUrl"),
                                    username = xObj.getString("username"),
                                    password = xObj.getString("password"),
                                    epgUrl = json.optString("epgUrl").takeIf { it.isNotBlank() && it != "null" },
                                    filterOptions = filterOptions,
                                )
                            } else {
                                ProvisionedSource(
                                    name = "M3U Playlist",
                                    kind = SourceKind.M3U,
                                    url = json.getString("playlistUrl"),
                                    epgUrl = json.optString("epgUrl").takeIf { it.isNotBlank() && it != "null" },
                                )
                            }
                            parsedSources.add(source)
                        }

                        val delArr = json.optJSONArray("deletedSourceIds")
                        val deletedIds = if (delArr != null) {
                            (0 until delArr.length()).map { delArr.getLong(it) }
                        } else emptyList()

                        _state.value = State.Received(parsedSources, deletedIds)

                        webSocket.close(1000, "Received configuration")
                        stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode provision payload", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                if (running && _state.value is State.Listening && code != 1000) {
                    reconnectDelay((_state.value as State.Listening).session)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!running) return
                Log.w(TAG, "WebSocket error: ${t.localizedMessage}. Attempting auto-reconnect...")
                if (_state.value is State.Listening) {
                    reconnectDelay((_state.value as State.Listening).session)
                }
            }
        })
    }

    private fun reconnectDelay(session: Session) {
        if (!running) return
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = Runnable {
            if (running && _state.value is State.Listening) {
                Log.i(TAG, "Auto-reconnecting WebSocket for pairing code ${session.code}...")
                connectWebSocket(session.webSocketUrl)
            }
        }.also {
            mainHandler.postDelayed(it, 2000)
        }
    }

    fun stop() {
        running = false
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
        try {
            activeCall?.cancel()
        } catch (_: Exception) {}
        activeCall = null
        try {
            activeWebSocket?.close(1000, "Closing client")
        } catch (_: Exception) {}
        activeWebSocket = null
        if (_state.value is State.Listening || _state.value is State.Connecting) {
            _state.value = State.Idle
        }
    }

    private companion object {
        const val TAG = "RemotePairingClient"
    }
}
