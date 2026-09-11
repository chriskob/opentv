/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

/**
 * Records which methods the app runs while starting up and moving around, and writes a baseline
 * profile from them.
 *
 * The profile is a list of what a cold start touched, and ART pre-compiles exactly that, so the
 * first frame after launch is not spent interpreting the guide's own code. Library profiles — Compose,
 * AndroidX, Media3 — are merged into the APK automatically and were already being carried; what this
 * adds is OpenTV's own classes, which is where the guide's opening and scrolling actually live.
 *
 * **What it covers depends on what the app has.** On a bare emulator the app opens its onboarding
 * screen, so the recorded profile covers startup, Compose, and the source-setup path. Point this at a
 * box with a playlist (or run it after adding one) and the D-pad presses below walk the real guide,
 * which is the coverage worth having. Either way the same file is written; the difference is how much
 * of the UI it has seen.
 *
 * Run it with:
 *
 *     ./gradlew :app:generateBaselineProfile
 *
 * ...against a connected device or a running emulator. The result lands in
 * `app/src/release/generated/baselineProfiles/baseline-prof.txt` and belongs in version control.
 *
 * No `@RunWith(AndroidJUnit4::class)` here on purpose: `AndroidJUnitRunner` — the runner this module
 * declares — already discovers plain JUnit4 test classes, and the annotation only tied this class to
 * an extra artifact for no benefit.
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndGuide() = rule.collect(
        packageName = PACKAGE_NAME,
        /*
         * Startup profile: the classes reached while the first frame is being produced are compiled
         * even more eagerly on device than the rest of the profile. That is the expensive part of a
         * cold start, and the one a viewer notices.
         */
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        /*
         * Move around with the remote keys the app is built for. Rows and rails are lazy, so the
         * presses matter: code that is never reached here is never recorded, and stays unoptimised on
         * a real box. Six rows and a few columns is enough to cross the first screens of the guide
         * without turning this into a fixture that breaks every time the UI is reordered.
         */
        repeat(6) {
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
            device.waitForIdle()
        }
        repeat(4) {
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_UP)
            device.waitForIdle()
        }

        // Into whatever the focused item opens, then back out — the detail screens cost layout code
        // too, and this is the cheap way to reach them.
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        device.waitForIdle()
        repeat(3) {
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
            device.waitForIdle()
        }
        device.pressKeyCode(KeyEvent.KEYCODE_BACK)
        device.waitForIdle()
    }

    private companion object {
        /** The application id, which this module is deliberately not given (it is a separate APK). */
        const val PACKAGE_NAME = "app.opentv"
    }
}
