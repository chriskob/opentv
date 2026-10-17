package app.opentv

import app.opentv.ui.components.KeyRepeatGate
import app.opentv.ui.channels.awaitRowFocus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Guards for the Compose key-dispatch race that killed the app while the guide was held-scrolled
 * (2026-10-12 dropbox: two FATAL "Dispatching key event while focus system is invalidated" and
 * one "intercepted soft keyboard event" variant, all from repeat keys landing while the previous
 * step's focus change was still unresolved).
 */
class GuideInputGuardsTest {

    @Test fun focusSystemInvalidatedIsSwallowed() {
        assertThat(
            isComposeKeyDispatchRace(
                IllegalStateException("Dispatching key event while focus system is invalidated."),
            ),
        ).isTrue()
    }

    @Test fun interceptedSoftKeyboardInvalidationIsSwallowed() {
        assertThat(
            isComposeKeyDispatchRace(
                IllegalStateException(
                    "Dispatching intercepted soft keyboard event while focus system is invalidated.",
                ),
            ),
        ).isTrue()
    }

    @Test fun theEarlierFocusedChildRaceStillMatches() {
        assertThat(
            isComposeKeyDispatchRace(IllegalStateException("ActiveParent must have a focusedChild")),
        ).isTrue()
    }

    @Test fun unrelatedStateProblemsStillPropagate() {
        assertThat(isComposeKeyDispatchRace(IllegalStateException("boom"))).isFalse()
    }

    @Test fun firstKeyAlwaysPassesAndRepeatsInsideTheWindowCoalesce() {
        var now = 1_000L
        val gate = KeyRepeatGate(intervalMillis = 60, clock = { now })
        assertThat(gate.accept()).isTrue()
        now += 25
        assertThat(gate.accept()).isFalse()
        now += 25 // 50 ms since the accepted step — still inside the window
        assertThat(gate.accept()).isFalse()
        now += 20 // 70 ms — the next step is due
        assertThat(gate.accept()).isTrue()
    }

    @Test fun humanTapsFarApartAlwaysPass() {
        var now = 0L
        val gate = KeyRepeatGate(intervalMillis = 60, clock = { now })
        repeat(10) {
            assertThat(gate.accept()).isTrue()
            now += 300
        }
    }

    // Wrap-around focus follow: after the list jumps to the far end, the target row is not
    // composed yet, so the first request always misses. The waiter retries until the focus
    // adoption lands (or the bound hits), instead of stranding focus on the disposed edge row
    // — which is how the cursor vanished and every later press re-fired the wrap.

    @Test fun focusAlreadyThereReturnsAtOnce() = runTest {
        var requests = 0
        var delays = 0
        val arrived = awaitRowFocus(
            requestFocus = { requests++ },
            isFocused = { true },
            maxAttempts = 10,
            delayMillis = 30L,
            delay = { delays++ },
        )
        assertThat(arrived).isTrue()
        assertThat(requests).isEqualTo(1)
        assertThat(delays).isEqualTo(0)
    }

    @Test fun focusArrivingLateIsRetriedUntilItLands() = runTest {
        var requests = 0
        var checks = 0
        val arrived = awaitRowFocus(
            requestFocus = { requests++ },
            isFocused = { ++checks >= 3 },
            maxAttempts = 10,
            delayMillis = 30L,
            delay = {},
        )
        assertThat(arrived).isTrue()
        assertThat(requests).isEqualTo(3)
    }

    @Test fun focusThatNeverArrivesGivesUpBounded() = runTest {
        var requests = 0
        val arrived = awaitRowFocus(
            requestFocus = { requests++ },
            isFocused = { false },
            maxAttempts = 4,
            delayMillis = 30L,
            delay = {},
        )
        assertThat(arrived).isFalse()
        assertThat(requests).isEqualTo(4)
    }

    @Test fun aNewerNavigationStepAbortsTheWait() = runTest {
        var requests = 0
        var checks = 0
        val arrived = awaitRowFocus(
            requestFocus = { requests++ },
            isFocused = { false },
            maxAttempts = 10,
            delayMillis = 30L,
            delay = {},
            // The generation guard trips on the second attempt: one request went out, then a
            // newer step cancelled the wait.
            isCancelled = { ++checks >= 2 },
        )
        assertThat(arrived).isFalse()
        assertThat(requests).isEqualTo(1)
    }

    @Test fun throwingRequestsAreSwallowedAndRetried() = runTest {
        var requests = 0
        val arrived = awaitRowFocus(
            requestFocus = {
                if (++requests < 3) throw IllegalStateException("focus system is invalidated")
            },
            isFocused = { requests >= 3 },
            maxAttempts = 10,
            delayMillis = 30L,
            delay = {},
        )
        assertThat(arrived).isTrue()
        assertThat(requests).isEqualTo(3)
    }
}
