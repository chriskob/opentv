package app.opentv

import app.opentv.ui.components.KeyRepeatGate
import com.google.common.truth.Truth.assertThat
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
}
