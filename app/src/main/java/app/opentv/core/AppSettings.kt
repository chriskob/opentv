/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The handful of user preferences that are not "data" (sources, guides) but "how the app
 * behaves". Backed by [android.content.SharedPreferences] rather than DataStore on purpose:
 * three flags read at composition time do not need an async, Flow-based store and the
 * ceremony that comes with it. Each setting is also mirrored into a [StateFlow] so Compose
 * recomposes the moment one changes.
 */
class AppSettings private constructor(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("opentv_settings", Context.MODE_PRIVATE)

    /** How the app chooses light vs dark. TV defaults to dark under [ThemeMode.SYSTEM]. */
    enum class ThemeMode { SYSTEM, DARK, LIGHT }

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Whether embedded subtitles/closed captions are shown when a stream carries them. */
    private val _subtitlesEnabled = MutableStateFlow(prefs.getBoolean(KEY_SUBTITLES, true))
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()

    /** Whether the selected channel plays live inside the guide's preview pane. */
    private val _guidePreviewVideo = MutableStateFlow(prefs.getBoolean(KEY_PREVIEW_VIDEO, true))
    val guidePreviewVideo: StateFlow<Boolean> = _guidePreviewVideo.asStateFlow()

    /** Whether the guide preview plays sound (off by default — quieter while browsing). */
    private val _guidePreviewSound = MutableStateFlow(prefs.getBoolean(KEY_PREVIEW_SOUND, false))
    val guidePreviewSound: StateFlow<Boolean> = _guidePreviewSound.asStateFlow()

    /** The profile whose watch history is active. Defaults to the built-in profile (id 1). */
    private val _activeProfileId = MutableStateFlow(prefs.getLong(KEY_ACTIVE_PROFILE, 1L))
    val activeProfileId: StateFlow<Long> = _activeProfileId.asStateFlow()

    fun setActiveProfile(id: Long) {
        prefs.edit().putLong(KEY_ACTIVE_PROFILE, id).apply()
        _activeProfileId.value = id
    }

    // ---- Parental controls -------------------------------------------------------------------

    /** Whether a parental PIN is set. The PIN itself is only ever stored as a salted hash. */
    private val _pinIsSet = MutableStateFlow(prefs.getString(KEY_PIN_HASH, null) != null)
    val pinIsSet: StateFlow<Boolean> = _pinIsSet.asStateFlow()

    /** Category group keys the user has marked adult/hidden. */
    private val _hiddenCategories =
        MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_CATS, emptySet())!!.toSet())
    val hiddenCategories: StateFlow<Set<String>> = _hiddenCategories.asStateFlow()

    /**
     * Session unlock. Deliberately *not* persisted: revealing hidden categories lasts until the
     * app is next launched, so a child restarting the app is back behind the lock.
     */
    private val _hiddenUnlocked = MutableStateFlow(false)
    val hiddenUnlocked: StateFlow<Boolean> = _hiddenUnlocked.asStateFlow()

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
        _pinIsSet.value = true
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).apply()
        _pinIsSet.value = false
        _hiddenUnlocked.value = true
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return true
        return stored == hashPin(pin)
    }

    fun setHiddenCategories(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN_CATS, keys).apply()
        _hiddenCategories.value = keys.toSet()
    }

    fun setHiddenUnlocked(unlocked: Boolean) {
        _hiddenUnlocked.value = unlocked
    }

    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("opentv-pin::$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUBTITLES, enabled).apply()
        _subtitlesEnabled.value = enabled
    }

    fun setGuidePreviewVideo(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREVIEW_VIDEO, enabled).apply()
        _guidePreviewVideo.value = enabled
    }

    fun setGuidePreviewSound(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREVIEW_SOUND, enabled).apply()
        _guidePreviewSound.value = enabled
    }

    private fun readThemeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SUBTITLES = "subtitles_enabled"
        private const val KEY_PREVIEW_VIDEO = "guide_preview_video"
        private const val KEY_PREVIEW_SOUND = "guide_preview_sound"
        private const val KEY_PIN_HASH = "parental_pin_hash"
        private const val KEY_HIDDEN_CATS = "hidden_categories"
        private const val KEY_ACTIVE_PROFILE = "active_profile_id"

        @Volatile private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }
}
