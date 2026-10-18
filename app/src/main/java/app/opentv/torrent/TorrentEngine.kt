/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.torrent

import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The torrent engine OpenTV ships inside its own APK: [TorrServer](https://github.com/YouROK/TorrServer),
 * a Go BitTorrent engine with an HTTP front end, run as a **separate process** from the app.
 *
 * ## Why a separate process
 * Bundling the engine into the APK (rather than pointing at the user's home server, or asking them to
 * install a second app) is what makes "paste a magnet, it plays" a single-app story. Keeping it as a
 * child process rather than linking it in is what makes it *safe*: a torrent engine that wedges,
 * leaks memory, or crashes takes itself down and the UI can restart it, instead of taking OpenTV's
 * process with it. It also means the engine can be killed outright when the app exits — there is no
 * native state inside the app's own process to unwind.
 *
 * ## How it runs on Android
 * Android 10+ refuses `exec` on a file written into the app's own data directory (W^X). The one
 * directory an app may execute from is its native library directory, which the installer populates
 * as root with mode 755 — so the engine ships as `jniLibs/armeabi-v7a/libtorrserver.so` and the
 * manifest sets `android:extractNativeLibs="true"` to make sure the installer actually puts it on
 * disk rather than leaving it mapped inside the APK. It is a `.so` by naming convention only; it is
 * an ARM executable that happens to have a library's name.
 *
 * ## What OpenTV promises about it, and how those promises are kept
 * The engine is configured by a `settings.json` written *before its first launch* (see
 * [defaultSettingsJson]) rather than by poking it at runtime — because applying settings through
 * TorrServer's API replaces the entire settings object, which is how a two-field update silently
 * wipes its tracker list. [app.opentv.data.remote.TorrServerClient.violatedAssurances] then reads
 * the live configuration back and reports any promise the engine is not keeping, rather than the app
 * claiming it on the engine's behalf.
 *
 * ## Licence
 * TorrServer is GPL-3.0 and OpenTV is GPL-3.0-or-later, so bundling is compatible. The version, its
 * source and the licence ride along in docs/TORRENTS.md.
 */
class TorrentEngine(
    private val context: Context,
    private val http: OkHttpClient,
) {

    sealed interface State {
        /** Never started, or stopped again. */
        data object Stopped : State

        /** The process is up (or being started) and is answering, or not yet answering, on its port. */
        data object Starting : State

        data class Running(val version: String, val port: Int) : State

        /** The engine was there but would not run. [reason] is for the screen, not a log. */
        data class Failed(val reason: String) : State

        /**
         * This device's CPU has no engine in the APK — the binary is per-architecture, and the one
         * shipped is ARM 32-bit.
         */
        data object Unsupported : State
    }

    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: StateFlow<State> = _state.asStateFlow()

    private val startMutex = Mutex()

    @Volatile
    private var process: Process? = null

    /** The engine's HTTP address while it is running, or null whenever it isn't. */
    val baseUrl: String?
        get() = (_state.value as? State.Running)?.let { "http://127.0.0.1:${it.port}" }

    /**
     * Start the engine if it isn't already up, and return what happened. Serialised on a mutex so two
     * screens asking at once start one process, not two.
     */
    suspend fun ensureStarted(): State = startMutex.withLock {
        val existing = _state.value
        if (existing is State.Running && process?.isAlive == true) return existing

        _state.value = State.Starting
        val binary = engineBinary()
        if (binary == null) {
            Log.w(TAG, "no ${BINARY_NAME} for ${Build.SUPPORTED_ABIS.joinToString()} in ${context.applicationInfo.nativeLibraryDir}")
            return@withLock State.Unsupported.also { _state.value = it }
        }

        val dir = File(context.filesDir, ENGINE_DIR).apply { mkdirs() }
        seedSettingsIfMissing(dir)
        // A previous run of OpenTV that was killed (rather than exited) leaves the engine running,
        // still holding peer connections for a film nobody is watching. It is the same UID, so it
        // can be stopped — but only after checking the process really is ours.
        stopStaleEngines(dir)

        val port = pickPort()
        val logFile = File(dir, "engine.log")
        val started = withContext(Dispatchers.IO) {
            runCatching {
                ProcessBuilder(
                    binary.absolutePath,
                    "-p", port.toString(),
                    // Loopback only. The engine is OpenTV's own child, so nothing outside this device
                    // has any business reaching it — including the user's own LAN.
                    "-i", "127.0.0.1",
                    "-d", dir.absolutePath,
                )
                    .directory(dir)
                    .redirectErrorStream(true)
                    // Log to a file the app can show, and one truncated each start so a long-lived
                    // box can't fill its storage with engine chatter.
                    .redirectOutput(ProcessBuilder.Redirect.to(logFile))
                    .start()
            }
        }
        val proc = started.getOrElse { error ->
            val reason = error.message ?: error::class.java.simpleName
            Log.w(TAG, "engine did not start", error)
            return@withLock State.Failed(reason).also { _state.value = it }
        }
        process = proc

        val ready = awaitReady(proc, port) { logTail(logFile) }
        _state.value = ready
        ready
    }

    /**
     * Stop the engine. Non-blocking: it signals the process and returns, leaving the reaping to a
     * background thread, because this is called from teardown paths (Activity destroy, the exit
     * dialog) where waiting seconds for a process to die would be felt.
     */
    fun stop() {
        val proc = process ?: return
        process = null
        _state.value = State.Stopped
        runCatching { proc.destroy() } // SIGTERM: the engine stops its torrents and exits cleanly
        Thread {
            runCatching {
                if (!proc.waitFor(STOP_GRACE_SECONDS, TimeUnit.SECONDS)) proc.destroyForcibly()
            }
        }.apply { isDaemon = true }.start()
    }

    private suspend fun awaitReady(proc: Process, port: Int, logTail: () -> String): State {
        val client = http.newBuilder()
            .callTimeout(READY_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        val url = "http://127.0.0.1:$port/echo"
        val deadline = System.currentTimeMillis() + START_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                return State.Failed("exited with code ${runCatching { proc.exitValue() }.getOrDefault(-1)}. $logTail()")
            }
            val version = withContext(Dispatchers.IO) {
                runCatching {
                    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        response.body?.string()?.trim()?.takeIf { it.isNotEmpty() }
                    }
                }.getOrNull()
            }
            if (version != null) return State.Running(version = version, port = port)
            delay(READY_POLL_MS)
        }
        return State.Failed("no answer within ${START_TIMEOUT_MS / 1000}s. $logTail()")
    }

    /**
     * The engine executable, if this build carries one for this device.
     *
     * `nativeLibraryDir` is the installer-managed directory: files there are the only ones Android
     * lets an app execute.
     */
    private fun engineBinary(): File? {
        val dir = runCatching { File(context.applicationInfo.nativeLibraryDir) }.getOrNull() ?: return null
        val file = File(dir, BINARY_NAME)
        return file.takeIf { it.isFile && it.canExecute() }
    }

    /**
     * Write the engine's configuration on first run, and never again: from then on the file belongs
     * to the engine (it rewrites it when its own web UI changes something), and quietly overwriting a
     * user's settings on every launch would be a bug, not a feature.
     */
    private fun seedSettingsIfMissing(dir: File) {
        val file = File(dir, SETTINGS_FILE)
        if (file.exists()) return
        runCatching { file.writeText(defaultSettingsJson()) }
            .onFailure { Log.w(TAG, "could not write $SETTINGS_FILE", it) }
    }

    /**
     * The engine's port: 8090, its own default, when it is free — so the address is predictable when
     * reading logs or testing by hand — and otherwise any free port, so a user already running
     * TorrServer on this device can't break the bundled copy.
     */
    private fun pickPort(): Int = if (isFree(DEFAULT_PORT)) DEFAULT_PORT else freePort()

    private fun isFree(port: Int): Boolean = runCatching {
        ServerSocket(port).use { true }
    }.getOrDefault(false)

    private fun freePort(): Int = runCatching { ServerSocket(0).use { it.localPort } }.getOrDefault(DEFAULT_PORT)

    /**
     * Stop any engine left behind by an OpenTV process that was killed rather than exited.
     *
     * Java's [Process] does not expose the child's pid on the Android versions OpenTV supports, so
     * there is no recorded pid to check. Instead the processes are found the same way the OS would:
     * scan `/proc` for a command line holding this app's own engine path, which no other process on
     * the device can have. The UID check is the second lock on the same door — `/proc` is readable
     * for other apps in some Android versions, and killing someone else's torrent engine because it
     * matched a filename would be a spectacular bug.
     */
    private fun stopStaleEngines(dataDir: File) {
        val myUid = runCatching { Os.getuid() }.getOrDefault(-1)
        val marker = dataDir.absolutePath
        val myPid = android.os.Process.myPid()
        File("/proc").listFiles()?.forEach { entry ->
            val pid = entry.name.toIntOrNull() ?: return@forEach
            if (pid == myPid) return@forEach
            val cmdline = runCatching { File(entry, "cmdline").readText().replace('\u0000', ' ') }
                .getOrNull() ?: return@forEach
            if (!cmdline.contains(BINARY_NAME) || !cmdline.contains(marker)) return@forEach
            val owner = runCatching { Os.stat(entry.absolutePath).st_uid }.getOrNull() ?: return@forEach
            if (owner != myUid) return@forEach
            Log.i(TAG, "stopping engine pid $pid left over from a previous run")
            runCatching { Os.kill(pid, OsConstants.SIGTERM) }
                .onFailure { Log.w(TAG, "could not stop stale engine pid $pid", it) }
        }
    }

    private fun logTail(file: File, lines: Int = LOG_TAIL_LINES): String {
        val text = runCatching { file.readLines().takeLast(lines).joinToString(" ") }.getOrNull().orEmpty()
        return text.take(LOG_TAIL_CHARS).trim()
    }

    companion object {
        private const val TAG = "TorrentEngine"
        private const val LOG_TAIL_LINES = 6
        private const val LOG_TAIL_CHARS = 300

        /** How the engine is named inside the APK. Must match the file under `jniLibs/<abi>/`. */
        const val BINARY_NAME = "libtorrserver.so"

        /** Where the engine keeps its database, its log and [SETTINGS_FILE], inside the app's data. */
        const val ENGINE_DIR = "torrents"

        const val SETTINGS_FILE = "settings.json"

        /** TorrServer's own default port. */
        const val DEFAULT_PORT = 8090

        private const val START_TIMEOUT_MS = 20_000L
        private const val READY_POLL_MS = 250L
        private const val READY_PROBE_TIMEOUT_SECONDS = 3L
        private const val STOP_GRACE_SECONDS = 5L

        /**
         * The engine's configuration, taken verbatim from the file the engine itself writes on its
         * first run (TorrServer MatriX.147) and changed only where OpenTV needs it to differ.
         *
         * Shipped whole rather than as a few keys because the engine's settings loader is not a
         * merge: a key that isn't in this file is a zero value, and the tracker list it merges into
         * every magnet is one of them. Starting from the engine's own defaults and editing the five
         * keys that matter is the only version of this that can't quietly weaken peer discovery.
         *
         * ## The edits, and why
         * - `DisableUpload: true` — **never upload**. With no other change the engine seeds every
         *   torrent it has, from the user's own connection, for as long as the torrent is loaded.
         *   This makes it a pure downloader: the peer protocol still handshakes and announces, but
         *   no piece of any file is ever sent to anyone. Verified against the live engine at startup
         *   by [app.opentv.data.remote.TorrServerClient.violatedAssurances].
         * - `DisableUPNP: true` — without it the engine asks the home router to open a port for
         *   incoming peers. A player that pokes a hole in someone's firewall to be *more useful to
         *   strangers* is the opposite of what this feature is for.
         * - `EnableLPD: false`, `EnableBonjour: false` — local peer discovery and mDNS advertisement.
         *   Both are right for a server you run on purpose and announce; a bundled engine has no
         *   business advertising itself to the devices on the user's network.
         * - `UseDisk: false` (the engine's default, kept deliberately) — the cache stays a fixed pool
         *   in RAM, so watching a 40 GB season does not write 40 GB to the box. This is the honest
         *   answer to "won't the box fill up": with no disk cache there is nothing to fill it with.
         * - `RemoveCacheOnDrop: true` — should anyone ever enable the disk cache, dropping a torrent
         *   takes its files with it, because the engine's own delete is `rm -rf` of a per-hash
         *   directory and leaving those behind is how a box does eventually fill up.
         *
         * Left at the engine's defaults on purpose: `CacheSize` 64 MiB (a bounded pool shared by all
         * torrents — generous on a 2 GB box without competing with the decoder), `TorrentDisconnectTimeout`
         * 30 s (a torrent with no reader is dropped by the engine itself half a minute after playback
         * stops, which is what stops the swarm when the user simply backs out), DHT and PEX on (a
         * magnet with no trackers finds peers through DHT and nothing else), and
         * `StoreSettingsInJson: true` so this file stays the live configuration rather than being
         * shadowed by the engine's database.
         */
        fun defaultSettingsJson(): String = SETTINGS_TEMPLATE

        private val SETTINGS_TEMPLATE = """
{
  "BitTorr": {
    "CacheSize": 67108864,
    "ConnectionsLimit": 25,
    "DefaultTrackers": "http://retracker.local/announce\nhttp://bt4.t-ru.org/ann?magnet\nhttp://retracker.mgts.by:80/announce\nhttp://tracker.city9x.com:2710/announce\nhttp://tracker.electro-torrent.pl:80/announce\nhttp://tracker.internetwarriors.net:1337/announce\nhttp://tracker2.itzmx.com:6961/announce\nudp://opentor.org:2710\nudp://public.popcorn-tracker.org:6969/announce\nudp://tracker.opentrackr.org:1337/announce\nhttp://bt.svao-ix.ru/announce\nudp://explodie.org:6969/announce\nwss://tracker.btorrent.xyz\nwss://tracker.openwebtorrent.com",
    "DisableDHT": false,
    "DisablePEX": false,
    "DisableTCP": false,
    "DisableUPNP": true,
    "DisableUTP": false,
    "DisableUpload": true,
    "DownloadRateLimit": 0,
    "EnableBonjour": false,
    "EnableDLNA": false,
    "EnableDebug": false,
    "EnableIPv6": false,
    "EnableLPD": false,
    "EnableRutorSearch": false,
    "EnableTorznabSearch": false,
    "ForceEncrypt": false,
    "FriendlyName": "",
    "LPDIPv6": false,
    "MergeAllM3U": false,
    "PeersListenPort": 0,
    "PreloadCache": 50,
    "ReaderReadAHead": 95,
    "RemoveCacheOnDrop": true,
    "ResponsiveMode": true,
    "RetrackersMode": 1,
    "ShowFSActiveTorr": true,
    "SslCert": "",
    "SslKey": "",
    "SslPort": 0,
    "StoreSettingsInJson": true,
    "StoreViewedInJson": false,
    "TMDBSettings": {
      "APIKey": "",
      "APIURL": "https://api.themoviedb.org",
      "ImageURL": "https://image.tmdb.org",
      "ImageURLRu": "https://imagetmdb.com"
    },
    "TorrentDisconnectTimeout": 30,
    "TorrentsSavePath": "",
    "TorznabUrls": null,
    "TrackTimecode": false,
    "TrackersListURL": "",
    "UploadRateLimit": 0,
    "UseDisk": false
  }
}
""".trimIndent() + "\n"
    }
}
