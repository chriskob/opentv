/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

/*
 * Records a baseline profile for the app.
 *
 * This module ships in nothing. It exists to be run — on an emulator in CI, or on a box over adb —
 * so that the app's own startup and UI code is AOT-compiled on a user's device instead of being
 * interpreted the first time they open the guide. The profile it writes is committed, and every
 * release build then carries it.
 */
android {
    namespace = "app.opentv.baselineprofile"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        // A benchmark module needs a device the benchmark library supports; 28 also matches the
        // emulator used to record it, so a miss there fails loudly rather than silently recording
        // nothing.
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /** The app being recorded. */
    targetProjectPath = ":app"

    /** A benchmark module instruments itself rather than needing a separate test APK. */
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.junit)
}

/*
 * Device-dependent tasks stay out of generic builds.
 *
 * Recording a profile means running instrumented tests on a real device or emulator, and the profile
 * plugin wires those `connected*AndroidTest` tasks into this module. That is right when a recording is
 * what was asked for — `:app:generateBaselineProfile` — and wrong for everything else: `assemble`,
 * `build`, and CodeQL's autobuild all build every module, on machines with no device attached, and
 * they fail on exactly these two tasks. That is what broke the code-scanning run the first time this
 * module was pushed.
 *
 * So they are enabled only when the recording was actually requested. Anything that merely builds the
 * project skips them instead of failing.
 */
val recordingProfile = gradle.startParameter.taskNames.any {
    it.contains("generateBaselineProfile", ignoreCase = true)
}
tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }
    .configureEach { enabled = recordingProfile }
