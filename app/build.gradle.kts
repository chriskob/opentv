plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

import java.util.Properties

android {
    namespace = "app.opentv"
    compileSdk = 35

    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) {
            f.inputStream().use { load(it) }
        }
    }
    // The remote-pairing service behind "Remote NAS Setup" / phone provisioning.
    //
    // Resolution order:
    //   1. local.properties        — a developer's own server (this file is gitignored)
    //   2. OPENTV_PAIRING_URL      — a CI repository variable, to point a build somewhere else
    //   3. -Popentv.remote.pairing.url
    //   4. [pairingDefault]        — the project's own pairing service
    //
    // Step 4 matters: this used to fall back to an unconfigured placeholder, and because
    // local.properties is gitignored, every CI-built release baked that placeholder in and
    // "Remote NAS Setup" could never connect — the only symptom being a connection error naming a
    // host nobody recognised. A build that cannot pair is worse than one that reaches the wrong
    // server loudly, so the default is now the real service.
    val pairingPlaceholder = "https://pair.example.com"
    val pairingDefault = "https://pair.haloautohaus.com"
    val defaultPairingUrl = localProps.getProperty("opentv.remote.pairing.url")
        ?: providers.environmentVariable("OPENTV_PAIRING_URL").orNull?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("opentv.remote.pairing.url").orNull?.takeIf { it.isNotBlank() }
        ?: pairingDefault
    if (defaultPairingUrl == pairingPlaceholder) {
        logger.warn(
            "OPENTV: the remote-pairing URL is set to the placeholder $pairingPlaceholder, so " +
                "Remote NAS Setup will not connect. Set 'opentv.remote.pairing.url' in " +
                "local.properties or the OPENTV_PAIRING_URL repository variable.",
        )
    }

    defaultConfig {
        applicationId = "app.opentv"
        minSdk = project.findProperty("devMinSdk")?.toString()?.toIntOrNull() ?: 23
        targetSdk = 35
        versionCode = 109
        versionName = "0.12.88"

        /*
         * Only the languages OpenTV itself ships: English (the default resources) and Polish.
         *
         * Deleting the other values-xx/ directories only removes *our* translations. The APK still
         * carried every locale the AndroidX, Compose, Media3 and Coil libraries are translated into
         * — eighty-odd languages of library strings, so a device set to German showed a German
         * "Cancel" next to English app text. This strips them at build time, which makes the app
         * speak two languages consistently and takes the rest of the weight out of the download.
         */
        resourceConfigurations += listOf("en", "pl")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEFAULT_REMOTE_PAIRING_URL", "\"$defaultPairingUrl\"")
    }

    signingConfigs {
        create("upload") {
            // Populated from environment variables in CI. Absent locally, which is why the
            // release build type falls back to the debug key below.
            val storePath = providers.environmentVariable("KEYSTORE_PATH").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
            }
        }
    }

    val isCi = providers.environmentVariable("CI").orNull == "true" || providers.environmentVariable("KEYSTORE_PATH").orNull != null
    val enableMinify = project.hasProperty("minify") || (isCi && !project.hasProperty("fast"))

    buildTypes {
        release {
            isMinifyEnabled = enableMinify
            isShrinkResources = enableMinify
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            /*
             * Falls back to the debug key when no keystore is configured.
             *
             * An *unsigned* APK cannot be installed on Android at all — it is rejected before
             * the user sees anything, with an error that explains nothing. For an early
             * project that means every tester is blocked on the maintainer setting up signing
             * infrastructure first.
             *
             * The trade-off is real and is written up in docs/RELEASING.md: moving to a
             * proper keystore later changes the signature, and Android will refuse to upgrade
             * over a debug-signed install. Set up real signing before announcing publicly.
             */
            signingConfig =
                if (providers.environmentVariable("KEYSTORE_PATH").orNull != null) {
                    signingConfigs.getByName("upload")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
        debug {
            // Keep package name as app.opentv so debug builds update existing TV app without wiping data
            isMinifyEnabled = false
        }

        /*
         * The two release-like types the baseline profile is recorded against.
         *
         * A profile records the *names* of the methods the app actually ran, so it has to be recorded
         * from a build whose names resemble what ships: a debug build is debuggable, and a minified
         * one has R8's obfuscated names. These are therefore neither debuggable nor minified — ART
         * must compile them the way it compiles a real install, and R8 rewrites the recorded profile
         * into the obfuscated names when the real release build is assembled.
         *
         * They are signed with the debug key because they are only ever installed on a test device.
         */
        create("benchmarkRelease") {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        create("nonMinifiedRelease") {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        ignoreTestSources = true
    }
}

/*
 * Baseline-profile recording.
 *
 * `./gradlew :app:generateBaselineProfile` records against a device or emulator that is already
 * connected — that is what CI provides — and writes the result into app/src/release/generated.
 */

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    // Storage Access Framework helpers - write recordings to a plugged-in USB / external
    // drive via a user-granted tree URI, with no storage permission.
    implementation(libs.androidx.documentfile)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    // SMB/CIFS client for recording to a NAS (Synology etc.) and playing those recordings
    // back in-app over the network. Pure-Java SMB2/3, no native bits.
    implementation("com.hierynomus:smbj:0.12.2")

    /*
     * Software audio decoders (Media3's FFmpeg extension).
     *
     * Android ships no free AC-3 / E-AC-3 / DTS decoder, so channels carrying them fail on devices
     * that can only pass the bitstream through — the "plays in VLC but not here" case. The extension
     * is not on Maven and must be cross-compiled: run .github/workflows/codecs.yml and put the
     * resulting AAR in app/libs/. Nothing changes until that file exists, so this is safe to leave
     * in place. See docs/CODECS.md.
     */
    val ffmpegDecoderAar = fileTree("libs") { include("*.aar") }
    if (ffmpegDecoderAar.files.isNotEmpty()) {
        implementation(ffmpegDecoderAar)
    }

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.ext.junit)
}
