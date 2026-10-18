/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.torrent

import android.util.Log
import app.opentv.pairing.PairingHttp
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/**
 * Lets a magnet link be pasted on a phone instead of typed with a TV remote.
 *
 * The page is served on the local network for as long as the app is running: the QR on the Torrents
 * screen opens it, and a shortcut saved on a phone reaches it directly with
 * `GET /add?t=<key>&link=<magnet>` without any screen on the TV having been opened first.
 *
 * ## A stable address
 *
 * The socket prefers the fixed port [PREFERRED_PORT] (anything else already holding it falls back to
 * a random one) and the key is generated once and kept, so the address a phone has saved does not
 * change between visits. That is what makes a Shortcuts action a set-up-once affair rather than a
 * copy-the-QR-again chore.
 *
 * ## What stops the neighbours using it
 *
 * - The socket binds to the device's **Wi-Fi/Ethernet address**, never to `0.0.0.0`, so a VPN or
 *   other interface cannot reach it, and it only listens while the app is running.
 * - Every route needs the key. Requests without it get a 404, so knowing the address and port is
 *   not enough.
 * - Requests from non-private addresses are refused outright.
 * - Nothing is retained: the page is one self-contained document with no external requests, and
 *   the link goes straight to the engine on the TV.
 *
 * There is deliberately **no code to type**: the QR already carries the key, which is the whole
 * point of scanning it.
 */
class PhoneAddServer(
    private val key: String,
    private val onLink: suspend (String) -> PhoneAddPage.Result,
) {

    data class Session(
        /** What the QR encodes: the page with the paste-it-in form. */
        val url: String,
        val host: String,
        val port: Int,
        /** The one-shot address a shortcut or bookmark can call; the link is appended to `link=`. */
        val addUrl: String,
    )

    private val _session = MutableStateFlow<Session?>(null)

    /** The QR's contents and the shortcut address, or null while the server is not listening. */
    val session: StateFlow<Session?> = _session.asStateFlow()

    private var serverSocket: ServerSocket? = null

    /**
     * Opens the socket and starts serving. Returns null when the device has no LAN address to
     * bind to (no Wi-Fi or Ethernet), which is a normal state, not an error.
     */
    fun start(): Session? {
        _session.value?.let { return it }
        if (key.isBlank()) return null
        val host = lanAddress()
        if (host == null) {
            Log.i(TAG, "No LAN address; phone add is unavailable")
            return null
        }
        val socket = openSocket(host) ?: return null

        serverSocket = socket
        val opened = Session(
            url = "http://$host:${socket.localPort}/?t=$key",
            host = host,
            port = socket.localPort,
            addUrl = "http://$host:${socket.localPort}/add?t=$key&link=",
        )
        _session.value = opened
        Log.i(TAG, "Phone add listening on $host:${socket.localPort}")

        // A plain thread rather than a coroutine: ServerSocket.accept() blocks uninterruptibly,
        // and closing the socket is the reliable way to stop it.
        thread(name = "opentv-phone-add", isDaemon = true) {
            while (true) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break // socket closed, which is how we stop
                }
                runCatching { handle(client) }
                    .onFailure { Log.w(TAG, "Phone add request failed", it) }
                runCatching { client.close() }
            }
        }
        return opened
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        _session.value = null
    }

    // ---- Request handling ------------------------------------------------------------------

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

        val contentLength = (headers["content-length"]?.toIntOrNull() ?: 0)
            .coerceAtMost(MAX_BODY_BYTES)
        val body = if (contentLength > 0) {
            val buffer = CharArray(contentLength)
            val read = reader.read(buffer, 0, contentLength)
            if (read > 0) String(buffer, 0, read) else ""
        } else {
            ""
        }

        val output = client.getOutputStream()

        // Every route checks the key. Without it the page does not exist.
        val supplied = query["t"] ?: PairingHttp.parseFormEncoded(body)["t"] ?: ""
        if (key.isBlank() || !PairingHttp.secretsMatch(supplied, key)) {
            output.write(PairingHttp.httpResponse("404 Not Found", "text/plain", "Not found."))
            return
        }

        when {
            // A shortcut (or a bookmark) sends the link as the query's last value, percent-encoded
            // or not: everything from `link=` to the end is the magnet.
            method == "GET" && path == "/add" -> handleLink(
                output,
                PairingHttp.trailingParam(PairingHttp.rawQuery(requestLine), "link")
                    .orEmpty()
                    .trim(),
            )

            method == "GET" -> output.write(
                PairingHttp.httpResponse(
                    "200 OK",
                    "text/html",
                    PhoneAddPage.form(key, null, addUrl()),
                ),
            )

            method == "POST" && path.startsWith("/submit") -> handleLink(
                output,
                PairingHttp.parseFormEncoded(body)["link"].orEmpty().trim(),
            )

            else -> output.write(
                PairingHttp.httpResponse("405 Method Not Allowed", "text/plain", "No."),
            )
        }
    }

    /** The address a shortcut calls, for the page's own recipe. Null once the server has stopped. */
    private fun addUrl(): String? = _session.value?.addUrl

    /**
     * The shared tail of both ways in — the form's POST and a shortcut's GET: an empty link asks for
     * a link, anything else goes to the app and waits for the real outcome.
     */
    private fun handleLink(output: java.io.OutputStream, link: String) {
        if (link.isEmpty()) {
            output.write(
                PairingHttp.httpResponse(
                    "200 OK",
                    "text/html",
                    PhoneAddPage.form(key, PhoneAddPage.missingLink(), addUrl()),
                ),
            )
            return
        }

        // The engine client is blocking, and this already runs off the main thread. The phone
        // waits for the real outcome, so a refused link is reported on the phone rather than
        // silently disappearing on the TV.
        val result = runBlocking { onLink(link) }
        val html = if (result is PhoneAddPage.Result.Added) {
            PhoneAddPage.done(key, result.title)
        } else {
            PhoneAddPage.form(key, PhoneAddPage.errorFor(result), addUrl())
        }
        output.write(PairingHttp.httpResponse("200 OK", "text/html", html))
    }

    /**
     * The fixed port keeps a saved address working; the fallback keeps the page alive when something
     * else on the box already holds it. The QR and the page always show the port actually in use.
     */
    private fun openSocket(host: String): ServerSocket? {
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
        val preferred = runCatching { ServerSocket(PREFERRED_PORT, BACKLOG, address) }.getOrNull()
        if (preferred != null) return preferred
        Log.i(TAG, "Phone-add port $PREFERRED_PORT is taken; using a random one")
        return runCatching { ServerSocket(0, BACKLOG, address) }
            .onFailure { Log.w(TAG, "Could not open the phone-add socket", it) }
            .getOrNull()
    }

    // ---- Finding our own address -----------------------------------------------------------

    /**
     * The device's address on the home network.
     *
     * Wi-Fi and Ethernet only, and never a tunnel: a box with a VPN has a `tun0` address that is
     * private too, and a QR pointing at it would send the phone's request into the VPN instead of
     * to the television in the room.
     */
    private fun lanAddress(): String? {
        val candidates = mutableListOf<Pair<String, InetAddress>>()
        runCatching {
            for (nic in NetworkInterface.getNetworkInterfaces()) {
                if (!nic.isUp || nic.isLoopback) continue
                val name = nic.name.lowercase()
                if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("rmnet") ||
                    name.startsWith("dummy") || name.startsWith("ipsec") || name.startsWith("clat")
                ) {
                    continue
                }
                for (address in nic.inetAddresses) {
                    if (address.isLoopbackAddress) continue
                    val host = address.hostAddress ?: continue
                    if (host.contains(':')) continue // IPv4 only
                    if (host.startsWith("169.254")) continue // no DHCP, nothing can reach us
                    if (!PairingHttp.isPrivateAddress(host)) continue
                    candidates += name to address
                }
            }
        }.onFailure { Log.w(TAG, "Could not enumerate interfaces", it) }

        return candidates
            .sortedBy { (name, _) ->
                when {
                    name.startsWith("wlan") -> 0
                    name.startsWith("eth") -> 1
                    else -> 2
                }
            }
            .firstOrNull()
            ?.second
            ?.hostAddress
    }

    private companion object {
        const val TAG = "PhoneAddServer"

        /**
         * Tried first so the address survives between visits: a shortcut or bookmark saved on a
         * phone keeps pointing at the page. It is unrelated to the engine's own 8090.
         */
        const val PREFERRED_PORT = 8767

        const val BACKLOG = 32
        const val SOCKET_TIMEOUT_MILLIS = 15_000
        const val MAX_BODY_BYTES = 16 * 1024
    }
}
