package app.opentv.pairing

import android.os.Handler
import android.os.Looper
import android.util.Log
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
 * Production-ready client for OpenTV Remote Pairing & Provisioning Service.
 *
 * Workflow:
 * 1. Calls `POST /api/pair/init` to obtain an ephemeral 6-character code.
 * 2. Formats a QR code URL: `https://<YOUR_DOMAIN>/?code=<CODE>`.
 * 3. Opens a WebSocket to `wss://<YOUR_DOMAIN>/?code=<CODE>`.
 * 4. Listens for real-time provisioning payload (`m3u` or `xtream`).
 * 5. Passes extracted credentials to callback and closes the session.
 */
class RemotePairingClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for WebSocket
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
) {

    data class PairingSession(
        val code: String,
        val expiresInSeconds: Int,
        val qrUrl: String,
        val webSocketUrl: String
    )

    data class XtreamCredentials(
        val serverUrl: String,
        val username: String,
        val password: String
    )

    data class ProvisionPayload(
        val playlistType: String, // "m3u" or "xtream"
        val playlistUrl: String?,
        val epgUrl: String?,
        val xtreamData: XtreamCredentials?
    )

    interface PairingCallback {
        fun onSessionReady(session: PairingSession)
        fun onConnected()
        fun onProvisioned(payload: ProvisionPayload)
        fun onError(errorMessage: String)
        fun onClosed()
    }

    private var currentWebSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var isCancelled = false

    /**
     * Start the remote pairing process.
     *
     * @param serverBaseUrl Base URL of your self-hosted service, e.g. "https://pair.opentv.app" or "http://192.168.1.50:3000"
     * @param callback Status and result callbacks invoked on the Android Main Thread.
     */
    fun startPairing(serverBaseUrl: String, callback: PairingCallback) {
        isCancelled = false
        val cleanBaseUrl = serverBaseUrl.trimEnd('/')

        val initUrl = "$cleanBaseUrl/api/pair/init"
        val request = Request.Builder()
            .url(initUrl)
            .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isCancelled) return
                postError(callback, "Failed to connect to pairing service: ${e.localizedMessage}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (isCancelled) return
                response.use { res ->
                    if (!res.isSuccessful) {
                        postError(callback, "Server returned error: HTTP ${res.code}")
                        return
                    }

                    val bodyStr = res.body?.string().orEmpty()
                    try {
                        val json = JSONObject(bodyStr)
                        val code = json.getString("code")
                        val expiresIn = json.optInt("expiresIn", 600)

                        val qrUrl = "$cleanBaseUrl/?code=$code"

                        // Convert http(s) to ws(s)
                        val wsProtocol = if (cleanBaseUrl.startsWith("https://", ignoreCase = true)) "wss" else "ws"
                        val hostAndPort = cleanBaseUrl.substringAfter("://")
                        val wsUrl = "$wsProtocol://$hostAndPort/?code=$code"

                        val session = PairingSession(
                            code = code,
                            expiresInSeconds = expiresIn,
                            qrUrl = qrUrl,
                            webSocketUrl = wsUrl
                        )

                        mainHandler.post { callback.onSessionReady(session) }

                        // Step 3: Open the WebSocket connection
                        connectWebSocket(wsUrl, callback)

                    } catch (e: Exception) {
                        postError(callback, "Failed to parse session response: ${e.localizedMessage}")
                    }
                }
            }
        })
    }

    private fun connectWebSocket(wsUrl: String, callback: PairingCallback) {
        if (isCancelled) return

        val wsRequest = Request.Builder()
            .url(wsUrl)
            .build()

        currentWebSocket = client.newWebSocket(wsRequest, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened to pairing server.")
                mainHandler.post { callback.onConnected() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: $text")
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")

                    if (type == "provision") {
                        val playlistType = json.optString("playlistType", "m3u")
                        val playlistUrl = json.optString("playlistUrl").takeIf { it.isNotEmpty() && it != "null" }
                        val epgUrl = json.optString("epgUrl").takeIf { it.isNotEmpty() && it != "null" }

                        var xtreamCreds: XtreamCredentials? = null
                        if (json.has("xtreamData") && !json.isNull("xtreamData")) {
                            val xObj = json.getJSONObject("xtreamData")
                            xtreamCreds = XtreamCredentials(
                                serverUrl = xObj.getString("serverUrl"),
                                username = xObj.getString("username"),
                                password = xObj.getString("password")
                            )
                        }

                        val payload = ProvisionPayload(
                            playlistType = playlistType,
                            playlistUrl = playlistUrl,
                            epgUrl = epgUrl,
                            xtreamData = xtreamCreds
                        )

                        mainHandler.post {
                            callback.onProvisioned(payload)
                        }

                        // Close socket after successful delivery
                        webSocket.close(1000, "Provisioning data received by client")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling incoming payload", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed ($code: $reason)")
                mainHandler.post { callback.onClosed() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (isCancelled) return
                Log.w(TAG, "WebSocket failure", t)
                postError(callback, "Connection dropped: ${t.localizedMessage}")
            }
        })
    }

    /**
     * Stop and cancel any active pairing session.
     */
    fun cancel() {
        isCancelled = true
        try {
            currentWebSocket?.close(1000, "User cancelled pairing")
        } catch (_: Exception) {}
        currentWebSocket = null
    }

    private fun postError(callback: PairingCallback, msg: String) {
        if (!isCancelled) {
            mainHandler.post { callback.onError(msg) }
        }
    }

    companion object {
        private const val TAG = "RemotePairingClient"
    }
}
