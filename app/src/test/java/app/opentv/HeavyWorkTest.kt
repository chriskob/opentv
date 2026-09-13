/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.HeavyWork
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The one-heavy-job-at-a-time gate.
 *
 * Written because "adding a playlist on a Fire Stick completely bogs the stick down" was not one
 * slow import — it was three unrelated callers (the periodic worker, the launch-time guide refresh
 * and the phone-portal provisioning run) importing a catalogue and scanning the whole channel list
 * simultaneously on one SQLite writer. Two properties fix that, and these tests are what stops them
 * being quietly dropped later: no two jobs are ever inside at once, and the opportunistic variant
 * declines rather than queueing.
 */
class HeavyWorkTest {

    @Test
    fun `never lets two jobs in at once`() = runTest {
        val inside = AtomicInteger(0)
        val mostInside = AtomicInteger(0)

        // Eight jobs that suspend in the middle, which is what a real import does for seconds at a
        // time. Without the gate a second job walks straight into that suspension.
        val jobs = (1..8).map {
            launch {
                HeavyWork.run {
                    val now = inside.incrementAndGet()
                    mostInside.updateAndGet { seen -> maxOf(seen, now) }
                    delay(10)
                    inside.decrementAndGet()
                }
            }
        }
        jobs.forEach { it.join() }

        assertThat(mostInside.get()).isEqualTo(1)
        assertThat(inside.get()).isEqualTo(0)
    }

    @Test
    fun `a second job waits for the one inside instead of skipping it`() = runTest {
        val order = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()

        val first = launch {
            HeavyWork.run {
                order += "first-in"
                release.await()
                order += "first-out"
            }
        }
        testScheduler.advanceUntilIdle()

        val second = launch { HeavyWork.run { order += "second" } }
        testScheduler.advanceUntilIdle()
        // The point of waiting rather than yielding: a job the user asked for must still happen.
        assertThat(order).containsExactly("first-in")

        release.complete(Unit)
        first.join()
        second.join()

        assertThat(order).containsExactly("first-in", "first-out", "second").inOrder()
    }

    @Test
    fun `the opportunistic variant stands down while the gate is busy`() = runTest {
        val ran = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()
        val holder = launch { HeavyWork.run { release.await() } }
        testScheduler.advanceUntilIdle()

        assertThat(HeavyWork.isBusy).isTrue()
        assertThat(HeavyWork.runIfIdle { ran.incrementAndGet() }).isNull()
        assertThat(ran.get()).isEqualTo(0)

        release.complete(Unit)
        holder.join()
        assertThat(HeavyWork.isBusy).isFalse()
    }

    @Test
    fun `the opportunistic variant runs when the gate is free`() = runTest {
        assertThat(HeavyWork.isBusy).isFalse()
        assertThat(HeavyWork.runIfIdle { 7 }).isEqualTo(7)
        assertThat(HeavyWork.isBusy).isFalse()
    }
}
