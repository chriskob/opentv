/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.recording

import android.content.Context
import app.opentv.core.AppSettings
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Where a recording's bytes go — and how to read the size back and delete it — hidden behind one
 * small surface so the engine, library and playback don't care whether a recording lives on the
 * box's internal storage or on a NAS over SMB. Adding USB later is one more branch here.
 */
object RecordingStorage {

    /** An opened destination the capture writes to, plus how to close it. */
    class Sink(val output: OutputStream, private val closer: () -> Unit) {
        fun close() = closer()
    }

    fun smbConfig(settings: AppSettings): SmbConfig = SmbConfig(
        host = settings.smbHost.value,
        share = settings.smbShare.value,
        folder = settings.smbFolder.value,
        username = settings.smbUser.value,
        password = settings.smbPassword.value,
    )

    /** Internal recordings dir on the device's own (app-private) external storage — no permission needed. */
    fun internalDir(context: Context): File =
        File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }

    /**
     * The locator a new recording named [filename] would be stored at, given the current target.
     * Computed up front so the DB row knows where its file lives before capture starts.
     */
    fun plannedLocator(context: Context, settings: AppSettings, filename: String): String =
        when (settings.recordingTarget.value) {
            AppSettings.RecordingTarget.SMB ->
                SmbClient.locator(smbConfig(settings), filename)
            AppSettings.RecordingTarget.INTERNAL ->
                File(internalDir(context), filename).absolutePath
        }

    /** Open the destination named by [locator] for writing. */
    fun openSink(settings: AppSettings, locator: String): Sink =
        if (SmbClient.isSmb(locator)) {
            val handle = SmbClient.openForWrite(smbConfig(settings), locator)
            Sink(handle.output) { handle.close() }
        } else {
            val file = File(locator)
            file.parentFile?.mkdirs()
            val out = FileOutputStream(file)
            Sink(out) { runCatching { out.close() } }
        }

    fun delete(settings: AppSettings, locator: String) {
        if (SmbClient.isSmb(locator)) {
            runCatching { SmbClient.delete(smbConfig(settings), locator) }
        } else {
            runCatching { File(locator).delete() }
        }
    }

    /** Current on-disk size, or -1 when it can't be cheaply known (SMB — the DB tracks it instead). */
    fun sizeOf(locator: String): Long =
        if (SmbClient.isSmb(locator)) -1L else File(locator).length()

    /** A filesystem-safe recording filename: `Channel - 2026-08-02 1830.ts`. */
    fun fileNameFor(channelName: String, title: String, startMillis: Long): String {
        val stamp = android.text.format.DateFormat.format("yyyy-MM-dd HHmm", startMillis)
        val base = (if (title.isBlank() || title == channelName) channelName else "$channelName - $title")
        val safe = base.replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(80).trim()
        return "$safe - $stamp.ts"
    }
}
