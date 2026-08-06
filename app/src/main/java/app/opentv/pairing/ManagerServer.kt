/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.pairing

import android.util.Log
import app.opentv.data.model.Channel
import app.opentv.data.model.shownName
import app.opentv.data.repo.CatalogRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Serves a "manage your channels from your phone or laptop" web app off this television.
 *
 * ## Same ethos as [PairingServer]
 *
 * There is no cloud. The TV runs a tiny HTTP server on the local network for as long as the manage
 * screen is open; a phone or laptop on the same wifi opens a real page — with a keyboard and mouse
 * instead of a D-pad — and browses, hides, favourites, renames and reorders channels. Every change
 * writes straight to this device's database. It works with the internet unplugged.
 *
 * ## What stops the neighbours using it
 *
 * - Binds to the LAN on a **random high port**, and only while the manage screen is open.
 * - The URL carries a **192-bit random token**; every route checks it. Knowing the IP and port is
 *   not enough. The token is only ever shown on the TV (QR + link), so only someone who can see the
 *   telly can connect — which is the whole security model. Unlike pairing there is no six-digit
 *   code, because this handles no credentials and the point is to be frictionless.
 * - Requests from non-private addresses are refused outright.
 * - Request bodies are capped ([MAX_BODY_BYTES]).
 * - Unlike pairing (which closes after the first submit) this keeps serving while the screen is
 *   open, and only closes itself after [IDLE_TIMEOUT_MINUTES] with no requests, or on [stop].
 */
class ManagerServer(
    private val scope: CoroutineScope,
    private val catalog: CatalogRepository,
) {

    data class Session(
        /** What the QR code encodes, e.g. `http://192.168.0.14:41234/?t=…` */
        val url: String,
        val host: String,
        val port: Int,
    ) {
        /** The address to type if the QR will not scan. */
        val shortUrl: String get() = "$host:$port"
    }

    sealed interface State {
        data object Idle : State
        data class Listening(val session: Session) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var token: String = ""
    @Volatile private var running = false
    @Volatile private var lastActivityMillis = 0L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun start() {
        if (running) return
        val host = lanAddress()
        if (host == null) {
            _state.value = State.Failed(
                "This device doesn't appear to be on a network, so a phone or laptop can't reach it.",
            )
            return
        }

        val random = SecureRandom()
        token = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }

        val socket = try {
            // Port 0 asks the OS for a free one. A fixed port would be predictable and would
            // clash with whatever else the user runs at home.
            ServerSocket(0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open a socket", e)
            _state.value = State.Failed("Could not start the channel manager on this device.")
            return
        }

        serverSocket = socket
        running = true
        lastActivityMillis = System.currentTimeMillis()

        _state.value = State.Listening(
            Session(
                url = "http://$host:${socket.localPort}/?t=$token",
                host = host,
                port = socket.localPort,
            ),
        )

        // A plain thread rather than a coroutine: ServerSocket.accept() blocks uninterruptibly,
        // and the only reliable way to stop it is to close the socket.
        thread(name = "opentv-manager", isDaemon = true) {
            while (running) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break // socket closed, which is how we stop
                }
                runCatching { handle(client) }
                    .onFailure { Log.w(TAG, "Manager request failed", it) }
                runCatching { client.close() }
            }
        }

        // Idle watchdog: keep serving while the screen is open, but do not leave a socket on the
        // user's network for hours if they wander off. Closes after a long unused stretch.
        scope.launch(Dispatchers.Default) {
            while (running) {
                delay(TimeUnit.MINUTES.toMillis(1))
                val idleFor = System.currentTimeMillis() - lastActivityMillis
                if (running && idleFor > TimeUnit.MINUTES.toMillis(IDLE_TIMEOUT_MINUTES)) {
                    stop()
                    _state.value = State.Failed(
                        "The channel manager closed after a while unused. Reopen it when you need it.",
                    )
                    break
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        token = ""
        if (_state.value is State.Listening) _state.value = State.Idle
    }

    // ---- Request handling ----------------------------------------------------------------------

    private fun handle(client: Socket) {
        client.soTimeout = SOCKET_TIMEOUT_MILLIS

        if (!PairingHttp.isPrivateAddress(client.inetAddress?.hostAddress)) {
            client.getOutputStream().write(
                PairingHttp.httpResponse("403 Forbidden", "text/plain", "Local network only."),
            )
            return
        }

        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val (method, path, query) = PairingHttp.parseRequestLine(requestLine) ?: return

        val headers = HashMap<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            PairingHttp.parseHeader(line)?.let { (name, value) -> headers[name] = value }
        }

        val output = client.getOutputStream()

        // Every route checks the token. Without it, none of this exists.
        val supplied = query["t"] ?: ""
        if (token.isEmpty() || !PairingHttp.secretsMatch(supplied, token)) {
            output.write(PairingHttp.httpResponse("404 Not Found", "text/plain", "Not found."))
            return
        }

        // A valid request counts as activity; the idle watchdog measures from here.
        lastActivityMillis = System.currentTimeMillis()

        val body = if (method == "POST") readBody(reader, headers) else ""

        when {
            method == "GET" && path == "/" ->
                output.write(PairingHttp.httpResponse("200 OK", "text/html", ManagerPage.page(token)))

            method == "GET" && path == "/meta" ->
                output.write(PairingHttp.httpResponse("200 OK", "application/json", metaJson()))

            method == "GET" && path == "/channels" ->
                output.write(PairingHttp.httpResponse("200 OK", "application/json", channelsJson(query)))

            method == "POST" && path == "/channel" -> handleChannelPatch(output, body)

            method == "POST" && path == "/reorder" -> handleReorder(output, body)

            else -> output.write(PairingHttp.httpResponse("404 Not Found", "text/plain", "Not found."))
        }
    }

    private fun readBody(reader: BufferedReader, headers: Map<String, String>): String {
        val contentLength = (headers["content-length"]?.toIntOrNull() ?: 0)
            .coerceAtMost(MAX_BODY_BYTES)
        if (contentLength <= 0) return ""
        val buffer = CharArray(contentLength)
        val read = reader.read(buffer, 0, contentLength)
        return if (read > 0) String(buffer, 0, read) else ""
    }

    // ---- Routes --------------------------------------------------------------------------------

    /** `{ sources:[{id,name}], categories:[{id,label,sourceId,count}] }` */
    private fun metaJson(): String = runBlocking {
        val sources = catalog.enabledSources()
        val sourceIds = sources.map { it.id }.toSet()
        val counts = catalog.categoryChannelCounts()
            .associateBy({ it.sourceId to it.categoryId }, { it.count })
        // Only categories that belong to an enabled source AND actually hold channels — an empty
        // category is noise on a phone, and a category count includes hidden rows so it stays > 0
        // for a category whose channels are all hidden (which is exactly what you'd browse to fix).
        val categories = catalog.liveCategories()
            .filter { it.sourceId in sourceIds }
            .mapNotNull { cat ->
                val count = counts[cat.sourceId to cat.id] ?: 0
                if (count <= 0) null
                else CategoryDto(id = cat.id, label = cat.name, sourceId = cat.sourceId, count = count)
            }
        json.encodeToString(
            MetaDto.serializer(),
            MetaDto(sources = sources.map { SourceDto(it.id, it.name) }, categories = categories),
        )
    }

    /** `GET /channels?cat=<id>[&source=<id>]` → the category's channels, hidden included. */
    private fun channelsJson(query: Map<String, String>): String = runBlocking {
        val serializer = ListSerializer(ChannelDto.serializer())
        val cat = query["cat"]
        if (cat.isNullOrBlank()) return@runBlocking json.encodeToString(serializer, emptyList())
        val source = query["source"]?.toLongOrNull()
        val rows = catalog.channelsInCategoryForManager(source, cat).map { it.toDto() }
        json.encodeToString(serializer, rows)
    }

    /** `POST /channel {id, hidden?|favourite?|name?}` → apply the given field(s). */
    private fun handleChannelPatch(output: OutputStream, body: String) {
        val patch = runCatching { json.decodeFromString(ChannelPatch.serializer(), body) }.getOrNull()
        if (patch == null || patch.id <= 0L) {
            output.write(PairingHttp.httpResponse("400 Bad Request", "text/plain", "Bad request."))
            return
        }
        // Unknown ids simply update zero rows — harmless, so no existence check.
        runBlocking {
            patch.hidden?.let { catalog.setChannelHidden(patch.id, it) }
            patch.favourite?.let { catalog.setChannelFavourite(patch.id, it) }
            // A blank name clears the rename back to the provider's own (see setChannelCustomName).
            patch.name?.let { catalog.setChannelCustomName(patch.id, it) }
        }
        writeOk(output)
    }

    /** `POST /reorder {ids:[orderedChannelIds]}` → assign sortIndex by position. */
    private fun handleReorder(output: OutputStream, body: String) {
        val req = runCatching { json.decodeFromString(ReorderReq.serializer(), body) }.getOrNull()
        if (req == null) {
            output.write(PairingHttp.httpResponse("400 Bad Request", "text/plain", "Bad request."))
            return
        }
        runBlocking {
            req.ids.forEachIndexed { index, id -> if (id > 0L) catalog.setChannelSortIndex(id, index) }
        }
        writeOk(output)
    }

    private fun writeOk(output: OutputStream) {
        output.write(
            PairingHttp.httpResponse("200 OK", "application/json", json.encodeToString(OkDto.serializer(), OkDto())),
        )
    }

    private fun Channel.toDto() = ChannelDto(
        id = id,
        name = shownName,
        original = displayName,
        hidden = hidden,
        favourite = favourite,
        sortIndex = sortIndex,
        logo = logoUrl,
        number = number,
    )

    // ---- Finding our own address ---------------------------------------------------------------

    /**
     * The device's address on the LAN.
     *
     * Deliberately the same enumeration as [PairingServer.lanAddress] and [app.opentv.sync.SyncServer]
     * (duplicated, not shared, to keep the servers independent): asks the interfaces rather than
     * ConnectivityManager, because TV boxes are frequently on ethernet and the wifi-shaped API
     * returns nothing useful when the box is wired.
     */
    private fun lanAddress(): String? {
        val candidates = mutableListOf<InetAddress>()
        runCatching {
            for (nic in NetworkInterface.getNetworkInterfaces()) {
                if (!nic.isUp || nic.isLoopback) continue
                for (address in nic.inetAddresses) {
                    if (address.isLoopbackAddress) continue
                    if (address.hostAddress?.contains(':') == true) continue // IPv4 only
                    if (PairingHttp.isPrivateAddress(address.hostAddress)) candidates += address
                }
            }
        }.onFailure { Log.w(TAG, "Could not enumerate interfaces", it) }

        // Prefer a real private range over a 169.254 self-assigned address, which means DHCP
        // failed and nothing will be able to reach us anyway.
        return candidates
            .sortedBy { it.hostAddress?.startsWith("169.254") == true }
            .firstOrNull()
            ?.hostAddress
    }

    private companion object {
        const val TAG = "ManagerServer"
        const val SOCKET_TIMEOUT_MILLIS = 15_000
        const val IDLE_TIMEOUT_MINUTES = 30L

        // Generous next to pairing's 16 KB: a /reorder body carries every channel id in a
        // category, which can be a few thousand on a big provider.
        const val MAX_BODY_BYTES = 256 * 1024
    }
}

// ---- JSON DTOs ---------------------------------------------------------------------------------
// kotlinx.serialization handles the escaping that matters here: channel names carry quotes, `|`
// and unicode, and hand-rolled string concatenation is exactly where that goes wrong.

@Serializable private data class MetaDto(val sources: List<SourceDto>, val categories: List<CategoryDto>)

@Serializable private data class SourceDto(val id: Long, val name: String)

@Serializable private data class CategoryDto(val id: String, val label: String, val sourceId: Long, val count: Int)

@Serializable private data class ChannelDto(
    val id: Long,
    val name: String,
    val original: String,
    val hidden: Boolean,
    val favourite: Boolean,
    val sortIndex: Int,
    val logo: String?,
    val number: Int?,
)

@Serializable private data class OkDto(val ok: Boolean = true)

@Serializable private data class ChannelPatch(
    val id: Long,
    val hidden: Boolean? = null,
    val favourite: Boolean? = null,
    val name: String? = null,
)

@Serializable private data class ReorderReq(val ids: List<Long>)
