/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.torrent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Magnet links on their way from a phone to the app.
 *
 * The phone-add server lives on a plain socket thread and knows nothing about the app's screens,
 * while the Torrents view model is the only part of the app that knows how to add and start a
 * torrent. This is the single step between them: the server leaves a request and waits for the real
 * outcome, and whoever can answer picks it up — wherever the viewer happens to be.
 */
object PhoneAddRequests {

    /**
     * How long the phone waits for the app to answer.
     *
     * Longer than a cold engine start on a slow box, short enough that a phone is told something
     * rather than left spinning. The case it exists for: the app is on the setup screen, so there is
     * no tab shell at all to add and start anything.
     */
    const val ANSWER_TIMEOUT_MS = 30_000L

    data class Pending(
        val link: String,
        val response: CompletableDeferred<PhoneAddPage.Result>,
    )

    private val _pending = MutableStateFlow<List<Pending>>(emptyList())

    /**
     * Arrivals, drained by the Torrents view model to add the link. A `StateFlow` because a request
     * that is queued while the app is on the setup screen must still be there when a tab shell
     * finally appears; nothing else may consume it by observation.
     */
    val pending: StateFlow<List<Pending>> = _pending.asStateFlow()

    private val _arrivals = MutableStateFlow(0L)

    /**
     * Bumped for every link offered, and never reset.
     *
     * The shell follows this to bring the Torrents tab forward. It is deliberately not the queue
     * itself: the view model drains the queue the moment it appears, and a Compose state that only
     * ever sees the queue *after* the drain would never notice the arrival at all. A count that
     * only goes up cannot be missed that way — an observed change means at least one link arrived.
     */
    val arrivals: StateFlow<Long> = _arrivals.asStateFlow()

    /**
     * Queues [link] and waits for the app's answer. Timed-out and cancelled requests are dropped on
     * the next arrival so a phone that has already been told "no" cannot make the TV play something
     * minutes later.
     */
    suspend fun request(link: String, timeoutMs: Long = ANSWER_TIMEOUT_MS): PhoneAddPage.Result {
        val response = CompletableDeferred<PhoneAddPage.Result>()
        _pending.update { list ->
            list.filterNot { it.response.isCancelled } + Pending(link.trim(), response)
        }
        _arrivals.update { it + 1 }
        return withTimeoutOrNull(timeoutMs) { response.await() } ?: run {
            response.cancel()
            _pending.update { list -> list.filterNot { it.response === response } }
            PhoneAddPage.Result.EngineUnavailable
        }
    }

    /** Takes the oldest unanswered request, or null when there is none. */
    fun takeNext(): Pending? {
        while (true) {
            val current = _pending.value
            if (current.isEmpty()) return null
            if (_pending.compareAndSet(current, current.drop(1))) return current.first()
        }
    }
}
