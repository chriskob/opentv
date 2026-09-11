plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    /*
     * Declared here, not just in the modules that apply them, because :app and :baselineprofile both
     * need them: the baseline-profile plugin brings its own copy of the Android Gradle plugin onto the
     * classpath, so a module that then asks for `com.android.test` *with* a version is told the plugin
     * "is already on the classpath with an unknown version". Pinning both here gives Gradle one known
     * version for the whole build, which is also how the Android Studio template lays it out.
     */
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}
