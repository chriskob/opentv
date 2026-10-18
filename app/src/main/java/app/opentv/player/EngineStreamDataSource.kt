/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * The data source for the torrent engine's stream endpoint.
 *
 * It wraps Media3's own [androidx.media3.datasource.DefaultHttpDataSource] rather than the OkHttp one
 * used for providers, and the reason is a single response code. A player reading a file to its end
 * legitimately asks for a range that starts *at* the end; the engine answers 416, which is correct
 * HTTP. Media3's HTTP source reads that as "no more input" — which is what it means for a file —
 * while the OkHttp source raises a hard source error. That difference is why a torrent could load its
 * codecs (so the screen showed a picture-less player with a decoder running) and then sit on
 * "Response code: 416" indefinitely.
 *
 * Media3's source also takes its own connect/read timeouts, which the engine needs to be generous:
 * it holds a response open while it waits for the swarm to deliver the piece behind it.
 */
@OptIn(UnstableApi::class)
class EngineStreamDataSourceFactory(
    private val delegate: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = EngineStreamDataSource(delegate.createDataSource())
}

@OptIn(UnstableApi::class)
private class EngineStreamDataSource(
    private val delegate: DataSource,
) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) =
        delegate.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long = try {
        delegate.open(dataSpec)
    } catch (e: HttpDataSource.InvalidResponseCodeException) {
        // The offset is the whole diagnosis: 416 at the file's end is normal, anywhere else is not.
        Log.w(TAG, "engine answered ${e.responseCode} for byte ${dataSpec.position}")
        throw e
    } catch (e: HttpDataSource.HttpDataSourceException) {
        Log.w(TAG, "engine read failed at byte ${dataSpec.position}", e)
        throw e
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate.read(buffer, offset, length)

    override fun getUri(): Uri? = delegate.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun close() = delegate.close()

    private companion object {
        const val TAG = "TorrentStream"
    }
}
