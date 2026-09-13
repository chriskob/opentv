/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads an update APK and hands it to the system installer.
 *
 * The download lands in the app's own cache (no storage permission, cleaned up by the OS
 * under pressure) and is handed to the platform through a [FileProvider] uri — a raw
 * `file://` uri throws `FileUriExposedException` on modern Android. `ACTION_VIEW` with the
 * package-archive mime is what gets the system install screen on phones, Android TV and
 * Fire OS; `ACTION_INSTALL_PACKAGE` is tried after it for older phone builds. If the user has
 * not yet allowed OpenTV to install unknown apps we send them to that setting and say so,
 * rather than firing an install the platform will silently refuse. We never install silently.
 */
class ApkInstaller(private val http: OkHttpClient) {

    /** Progress as a 0f..1f fraction, or -1f when the total size is unknown. */
    fun interface Progress {
        fun onProgress(fraction: Float)
    }

    /** How far the hand-off to the platform got. */
    enum class Outcome {
        /** The system installer is on screen; our own UI should step aside. */
        InstallerShown,

        /**
         * Android has not been told that OpenTV may install packages. The setting screen
         * has been opened; the user has to allow it and then start the install again.
         */
        NeedsUnknownSourcesPermission,
    }

    /**
     * Downloads [url] and launches the installer, reporting how far it got. Throws on a
     * download failure so the caller can show a retry.
     */
    suspend fun downloadAndInstall(
        context: Context,
        url: String,
        expectedBytes: Long,
        progress: Progress,
    ): Outcome {
        val apk = download(context, url, expectedBytes, progress)
        return launchInstaller(context, apk)
    }

    private suspend fun download(
        context: Context,
        url: String,
        expectedBytes: Long,
        progress: Progress,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // A fixed name means each download overwrites the last rather than piling up copies.
        val out = File(dir, "opentv-update.apk")

        val request = Request.Builder().url(url).header("User-Agent", "OpenTV").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
            val bodyStream = response.body?.byteStream() ?: error("Empty download")
            val total = if (expectedBytes > 0) expectedBytes else (response.body?.contentLength() ?: -1L)

            out.outputStream().use { sink ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                var written = 0L
                while (bodyStream.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    written += read
                    progress.onProgress(if (total > 0) (written.toFloat() / total) else -1f)
                }
            }
        }

        // A release asset arrives whole or not at all, so a short file means the connection
        // dropped mid-flight. Handing a truncated APK to the installer earns the user a
        // "problem parsing the package" screen at best, so fail here, where we can say why.
        if (out.length() <= 0L) error("Download produced an empty file")
        if (expectedBytes > 0L && out.length() != expectedBytes) {
            error("Download incomplete: ${out.length()} of $expectedBytes bytes")
        }
        if (!looksLikeApk(out)) error("Downloaded file is not an APK")

        out
    }

    /**
     * True when [file] begins with the zip magic `PK`. An APK is a zip, so this catches the
     * case where a redirect or a captive portal handed us an HTML error page instead.
     */
    private fun looksLikeApk(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val magic = ByteArray(2)
            input.read(magic) == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        }
    }.getOrDefault(false)

    private fun launchInstaller(context: Context, apk: File): Outcome {
        // Since Android 8, REQUEST_INSTALL_PACKAGES in the manifest is only half the story:
        // the user has to allow installs from *this app*. Without that the platform refuses
        // the install without showing anything at all, which is indistinguishable from the
        // app doing nothing, so ask for the permission up front instead of failing mutely.
        if (!canInstallPackages(context)) {
            openUnknownSourcesSettings(context)
            return Outcome.NeedsUnknownSourcesPermission
        }

        // Commit through the platform's PackageInstaller first. An install session is an
        // *update* by definition: it replaces the installed package in place and leaves its
        // data — settings, playlists, profile — untouched. The launcher-intent path below hands
        // the APK to whatever package screen the device has, and several of those (Fire OS's
        // in particular) treat the download as a brand-new install and refuse with
        // "app already installed" unless the caller asked to replace. Committing through a
        // session is what makes About → Update actually update in place.
        val session = runCatching { installThroughSession(context, apk) }
        if (session.isSuccess) return Outcome.InstallerShown
        Log.w(TAG, "Package session install unavailable, falling back to the installer intent", session.exceptionOrNull())

        // ACTION_VIEW is the action that actually has a handler on Android TV and Fire OS;
        // the older ACTION_INSTALL_PACKAGE is only honoured on some phone builds. Try the
        // one that works first and keep the other as a fallback rather than betting on it.
        val intents = listOf(Intent.ACTION_VIEW, Intent.ACTION_INSTALL_PACKAGE).map { action ->
            Intent(action).apply {
                setDataAndType(
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk),
                    APK_MIME,
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                // Ask for a replace. Without this the same APK that a session updates cleanly
                // is treated as a fresh install by the package screen and answers
                // "app already exists" — the original report. Deliberately *no*
                // EXTRA_RETURN_RESULT. That extra asks the installer to report back to a
                // startActivityForResult caller; set on an intent that was never started for
                // result, the platform cancels the install and finishes the activity with no
                // UI — the original "install does nothing" bug.
                // "android.intent.extra.REPLACE" — the platform never gave this extra a public
                // constant, which is why it is spelled out: it is what a package screen reads to
                // install over the existing app instead of refusing with "app already exists".
                putExtra("android.intent.extra.REPLACE", true)
            }
        }

        // Looped with runCatching rather than resolveActivity(): on Android 11+ package
        // visibility can hide the installer from queries even though startActivity works,
        // so ask the platform to try each in turn and only give up when all of them refuse.
        var lastError: Exception? = null
        for (intent in intents) {
            val started = runCatching { context.startActivity(intent) }
            if (started.isSuccess) return Outcome.InstallerShown
            lastError = started.exceptionOrNull() as? Exception
        }
        throw IllegalStateException("No app on this device can install an APK", lastError)
    }

    /**
     * Streams [apk] into an install session and commits it.
     *
     * The device shows its own confirm screen once the session is committed; nothing is installed
     * silently. The result arrives as a broadcast on [STATUS_ACTION] — registered just for this
     * install — which cleans the downloaded APK up after either outcome, since the cache copy is
     * worthless once the installer has streamed its own copy in.
     */
    private fun installThroughSession(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        val session = installer.openSession(sessionId)
        try {
            apk.inputStream().use { source ->
                session.openWrite("opentv-update.apk", 0, apk.length()).use { sink ->
                    source.copyTo(sink, STREAM_BUFFER_BYTES)
                    sink.flush()
                    session.fsync(sink)
                }
            }

            // The status broadcast needs a PendingIntent, and FLAG_MUTABLE to have the status
            // extras attached to it at all on Android 12+; the platform sets the failure/success
            // fields in place rather than sending a new intent.
            val mutableFlag =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            context.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context, intent: Intent) {
                        runCatching { receiverContext.unregisterReceiver(this) }
                        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                        if (status == PackageInstaller.STATUS_SUCCESS) {
                            apk.delete()
                        } else {
                            Log.w(
                                TAG,
                                "Install session finished with status $status: " +
                                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                            )
                        }
                    }
                },
                IntentFilter(STATUS_ACTION),
            )

            session.commit(
                PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(STATUS_ACTION).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
                ).intentSender,
            )
        } finally {
            // abandon() is harmless after a commit: it discards a session that was never
            // committed, and is rejected (caught here) for one that was.
            runCatching { session.abandon() }
            session.close()
        }
    }

    /**
     * Whether the user has allowed OpenTV to install packages. Always true below Android 8,
     * where a single device-wide "Unknown sources" switch governed this instead.
     */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /**
     * Opens the per-app "install unknown apps" switch for OpenTV. Returns false when the
     * device has no such screen — some TV builds hide it — so the caller can point the user
     * at the settings manually rather than leaving them at a dialog that does nothing.
     */
    fun openUnknownSourcesSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
        )
        for (intent in candidates) {
            val started = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            if (started.isSuccess) return true
        }
        return false
    }

    private companion object {
        /** The MIME type the platform's package installer registers itself against. */
        const val APK_MIME = "application/vnd.android.package-archive"

        /** One-shot status broadcast action for a single committed install session. */
        const val STATUS_ACTION = "app.opentv.update.SESSION_STATUS"

        /** Chunk size for streaming the APK into the install session. */
        const val STREAM_BUFFER_BYTES = 64 * 1024

        const val TAG = "ApkInstaller"
    }
}
