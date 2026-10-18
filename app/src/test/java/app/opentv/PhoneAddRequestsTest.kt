/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.torrent.PhoneAddPage
import app.opentv.torrent.PhoneAddRequests
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test

/**
 * The hand-off between the phone-add server, which lives on a plain socket thread, and the Torrents
 * view model, which is the only part of the app that can add and start a torrent.
 *
 * The queue is an object, so these tests run one after another and each drains what it queues.
 */
class PhoneAddRequestsTest {

    @Test
    fun `whoever drains the queue answers the phone`() = runBlocking {
        val answer = async { PhoneAddRequests.request("magnet:?xt=urn:btih:AAAA", timeoutMs = 5_000) }
        yield() // let the request reach the queue before it starts waiting

        val pending = PhoneAddRequests.takeNext()
        assertThat(pending?.link).isEqualTo("magnet:?xt=urn:btih:AAAA")
        pending!!.response.complete(PhoneAddPage.Result.Added("Big Buck Bunny"))

        assertThat(answer.await()).isEqualTo(PhoneAddPage.Result.Added("Big Buck Bunny"))
        assertThat(PhoneAddRequests.takeNext()).isNull()
    }

    @Test
    fun `requests are answered oldest first`() = runBlocking {
        val first = async { PhoneAddRequests.request("magnet:one", timeoutMs = 5_000) }
        yield()
        val second = async { PhoneAddRequests.request("magnet:two", timeoutMs = 5_000) }
        yield()

        val one = PhoneAddRequests.takeNext()!!
        val two = PhoneAddRequests.takeNext()!!
        assertThat(one.link).isEqualTo("magnet:one")
        assertThat(two.link).isEqualTo("magnet:two")

        one.response.complete(PhoneAddPage.Result.Added("one"))
        two.response.complete(PhoneAddPage.Result.Added("two"))
        assertThat(first.await()).isEqualTo(PhoneAddPage.Result.Added("one"))
        assertThat(second.await()).isEqualTo(PhoneAddPage.Result.Added("two"))
        assertThat(PhoneAddRequests.takeNext()).isNull()
    }

    @Test
    fun `every arrival bumps the counter the shell follows`() = runBlocking<Unit> {
        // The shell cannot watch the queue itself: the view model drains it the moment it appears,
        // so a Compose state built on the queue would only ever see it empty. The count only goes up.
        val before = PhoneAddRequests.arrivals.value

        val answer = async { PhoneAddRequests.request("magnet:?xt=urn:btih:AAAA", timeoutMs = 5_000) }
        yield()

        assertThat(PhoneAddRequests.arrivals.value).isEqualTo(before + 1)

        PhoneAddRequests.takeNext()!!.response.complete(PhoneAddPage.Result.Added("x"))
        answer.await()
    }

    @Test
    fun `a request nobody answers times out and leaves the queue`() = runBlocking {
        // The app can be sitting on the setup screen with no tab shell to answer; the phone must
        // hear that rather than wait forever.
        val answer = PhoneAddRequests.request("magnet:?xt=urn:btih:AAAA", timeoutMs = 50)

        assertThat(answer).isEqualTo(PhoneAddPage.Result.EngineUnavailable)
        assertThat(PhoneAddRequests.takeNext()).isNull()
    }
}
