/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.opentv.core.AppSettings
import app.opentv.core.ServiceLocator
import app.opentv.ui.MainScreen
import app.opentv.ui.ProfilesViewModel
import app.opentv.ui.SourcesViewModel
import app.opentv.ui.channels.ChannelManagerScreen
import app.opentv.ui.channels.SearchScreen
import app.opentv.ui.VodViewModel
import app.opentv.ui.onboarding.AddSourceScreen
import app.opentv.ui.player.PlayerScreen
import app.opentv.ui.settings.AboutScreen
import app.opentv.ui.settings.AppSettingsScreen
import app.opentv.ui.settings.EpgSettingsScreen
import app.opentv.ui.settings.ParentalControlsScreen
import app.opentv.ui.settings.ProfilesScreen
import app.opentv.ui.settings.ProvidersScreen
import app.opentv.ui.settings.RecordingSettingsScreen
import app.opentv.ui.settings.SyncScreen
import app.opentv.ui.settings.SettingsHubScreen
import app.opentv.ui.settings.WebManagerScreen
import app.opentv.ui.theme.OpenTvTheme
import app.opentv.ui.vod.SeriesDetailScreen
import app.opentv.ui.vod.VodPlayerScreen
import app.opentv.update.UpdateGate

class MainActivity : ComponentActivity() {

    // Apply the chosen UI language before any view or resource is resolved. A language change in
    // settings calls recreate(), which re-runs this with the new tag.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(app.opentv.core.LocaleUtils.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handlePlayIntent(intent)
        val isTelevision = isRunningOnTelevision(this)

        setContent {
            val settings = remember { ServiceLocator.get(this).settings }
            val themeMode by settings.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                AppSettings.ThemeMode.DARK -> true
                AppSettings.ThemeMode.LIGHT -> false
                // A living-room screen defaults to dark; a phone/tablet follows the system.
                AppSettings.ThemeMode.SYSTEM -> isTelevision || isSystemInDarkTheme()
            }
            OpenTvTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    OpenTvApp(isTelevision = isTelevision)
                }
            }
        }
    }

    // singleTask: a reminder tapped while the app is already running arrives here, not a fresh
    // onCreate. Either way the requested channel is handed to the nav graph via [PlayRequests].
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayIntent(intent)
    }

    private fun handlePlayIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_PLAY_CHANNEL, 0L) ?: 0L
        if (id != 0L) app.opentv.core.PlayRequests.request(id)
    }

    // ---- Picture-in-picture ------------------------------------------------------------------

    /** Home pressed while a programme is playing → shrink to a floating window instead of stopping. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        maybeEnterPip()
    }

    private fun supportsPip(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun maybeEnterPip() {
        if (!app.opentv.core.PipState.eligible || !app.opentv.core.PipState.isPlaying) return
        enterPipNow()
    }

    /** Explicit request from the player's PiP button — no eligibility guard, the user asked. */
    fun enterPipNow() {
        if (!supportsPip()) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build(),
            )
        }
    }

    fun pipSupported(): Boolean = supportsPip()

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        app.opentv.core.PipState.setInPip(isInPictureInPictureMode)
    }

    companion object {
        /** A reminder notification carries the channel to tune to in this extra. */
        const val EXTRA_PLAY_CHANNEL = "opentv.play_channel"
    }
}

object Routes {
    const val HOME = "home"
    const val ADD_SOURCE = "add-source"
    const val PLAYER = "player/{channelId}"
    const val SEARCH = "search"
    const val EPG_SETTINGS = "epg-settings"
    const val APP_SETTINGS = "app-settings"
    const val SETTINGS_HUB = "settings"
    const val PROVIDERS = "providers"
    const val CHANNELS = "channels"
    const val WEB_MANAGER = "web-manager"
    const val PROFILES = "profiles"
    const val PARENTAL = "parental"
    const val SYNC = "sync"
    const val REC_SETTINGS = "recording-settings"
    const val ABOUT = "about"
    const val SERIES_DETAIL = "series/{seriesId}"

    // VOD plays carry the stream inline; a movie/episode is a one-off URL, not a stored id
    // the player can look up the way a channel is.
    const val VOD_PLAYER = "vod?key={key}&url={url}&title={title}&ua={ua}"

    fun player(channelId: Long) = "player/$channelId"
    fun seriesDetail(seriesId: Long) = "series/$seriesId"
    fun vodPlayer(key: String, url: String, title: String, ua: String): String {
        fun e(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
        return "vod?key=${e(key)}&url=${e(url)}&title=${e(title)}&ua=${e(ua)}"
    }
}

@Composable
private fun OpenTvApp(isTelevision: Boolean) {
    val navController = rememberNavController()
    val sourcesViewModel: SourcesViewModel = viewModel()
    val vodViewModel: VodViewModel = viewModel()
    val profilesViewModel: ProfilesViewModel = viewModel()
    val sourcesUi by sourcesViewModel.ui.collectAsState()
    val profiles by profilesViewModel.profiles.collectAsState()
    val activeProfileId by profilesViewModel.activeProfileId.collectAsState()
    val activeProfileName = profiles.firstOrNull { it.id == activeProfileId }?.name ?: "Me"

    // Until the saved sources have loaded from the database, we cannot tell a first run from a
    // returning user — and guessing "first run" drops a returning user on the setup screen and
    // asks for their provider again. NavHost locks in its start destination on first
    // composition, so wait for that first load before building it.
    if (!sourcesUi.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // First run goes straight to setup — an empty channel list with no explanation is the
    // worst possible first impression.
    val start = if (sourcesUi.sources.isEmpty()) Routes.ADD_SOURCE else Routes.HOME

    // Boot to last channel: if enabled and we have one, jump straight into the player on launch.
    // Runs once; backing out returns to the guide and doesn't re-trigger.
    val bootContext = androidx.compose.ui.platform.LocalContext.current
    val bootSettings = remember { ServiceLocator.get(bootContext).settings }
    LaunchedEffect(start) {
        if (start == Routes.HOME && bootSettings.resumeLastChannel.value && bootSettings.lastChannelId != 0L) {
            navController.navigate(Routes.player(bootSettings.lastChannelId))
        }
    }

    // A tapped reminder notification asks for a specific channel. Consume it so it fires once and
    // never re-triggers on a later launch.
    val playRequest by app.opentv.core.PlayRequests.channelId.collectAsState()
    LaunchedEffect(playRequest) {
        val id = playRequest
        if (id != null && id != 0L) {
            app.opentv.core.PlayRequests.consume()
            navController.navigate(Routes.player(id))
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = start) {
            composable(Routes.ADD_SOURCE) {
                AddSourceScreen(
                    viewModel = sourcesViewModel,
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ADD_SOURCE) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.HOME) {
                MainScreen(
                    isTelevision = isTelevision,
                    hasSources = sourcesUi.sources.isNotEmpty(),
                    isSyncing = sourcesUi.syncing,
                    onPlayChannel = { channel -> navController.navigate(Routes.player(channel.id)) },
                    onPlayMovie = { movie ->
                        navController.navigate(
                            Routes.vodPlayer(
                                key = "movie:${movie.id}",
                                url = movie.streamUrl,
                                title = movie.name,
                                ua = "OpenTV/0.1 (Android)",
                            ),
                        )
                    },
                    onOpenSeries = { series ->
                        navController.navigate(Routes.seriesDetail(series.id))
                    },
                    onResume = { key, url, title ->
                        navController.navigate(
                            Routes.vodPlayer(key, url, title, "OpenTV/0.1 (Android)"),
                        )
                    },
                    onAddSource = { navController.navigate(Routes.ADD_SOURCE) },
                    onRefresh = sourcesViewModel::refreshAll,
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS_HUB) },
                    onOpenProfiles = { navController.navigate(Routes.PROFILES) },
                    onPlayRecording = { rec ->
                        // A NAS recording plays straight off its smb:// locator; an internal one
                        // through a file:// uri. Both go through the VOD player, which seeks.
                        val url =
                            if (app.opentv.recording.SmbClient.isSmb(rec.filePath)) rec.filePath
                            else android.net.Uri.fromFile(java.io.File(rec.filePath)).toString()
                        navController.navigate(
                            Routes.vodPlayer(
                                key = "rec:${rec.id}",
                                url = url,
                                title = rec.title,
                                ua = rec.userAgent,
                            ),
                        )
                    },
                    onPlayCatchup = { key, url, title, ua ->
                        // Catch-up is a seekable archive stream — plays through the VOD player.
                        navController.navigate(Routes.vodPlayer(key, url, title, ua))
                    },
                    activeProfileName = activeProfileName,
                )
            }

            composable(Routes.PROFILES) {
                ProfilesScreen(
                    viewModel = profilesViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onPlayChannel = { channel -> navController.navigate(Routes.player(channel.id)) },
                    onPlayMovie = { movie ->
                        navController.navigate(
                            Routes.vodPlayer(
                                key = "movie:${movie.id}",
                                url = movie.streamUrl,
                                title = movie.name,
                                ua = "OpenTV/0.1 (Android)",
                            ),
                        )
                    },
                    onOpenSeries = { series -> navController.navigate(Routes.seriesDetail(series.id)) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTINGS_HUB) {
                SettingsHubScreen(
                    onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                    onOpenGuide = { navController.navigate(Routes.EPG_SETTINGS) },
                    onOpenChannels = { navController.navigate(Routes.CHANNELS) },
                    onOpenWebManager = { navController.navigate(Routes.WEB_MANAGER) },
                    onOpenDisplay = { navController.navigate(Routes.APP_SETTINGS) },
                    onOpenParental = { navController.navigate(Routes.PARENTAL) },
                    onOpenSync = { navController.navigate(Routes.SYNC) },
                    onOpenRecordings = { navController.navigate(Routes.REC_SETTINGS) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SYNC) {
                SyncScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.REC_SETTINGS) {
                RecordingSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.CHANNELS) {
                ChannelManagerScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.WEB_MANAGER) {
                WebManagerScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PARENTAL) {
                ParentalControlsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PROVIDERS) {
                ProvidersScreen(
                    viewModel = sourcesViewModel,
                    onAddSource = { navController.navigate(Routes.ADD_SOURCE) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.EPG_SETTINGS) {
                EpgSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.APP_SETTINGS) {
                AppSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PLAYER) { entry ->
                val channelId = entry.arguments?.getString("channelId")?.toLongOrNull()
                PlayerScreen(
                    channelId = channelId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SERIES_DETAIL) { entry ->
                val seriesId = entry.arguments?.getString("seriesId")?.toLongOrNull() ?: return@composable
                SeriesDetailScreen(
                    seriesId = seriesId,
                    viewModel = vodViewModel,
                    onPlayEpisode = { key, url, title ->
                        navController.navigate(
                            Routes.vodPlayer(key, url, title, "OpenTV/0.1 (Android)"),
                        )
                    },
                )
            }

            composable(Routes.VOD_PLAYER) { entry ->
                fun arg(name: String) = entry.arguments?.getString(name)
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }.orEmpty()
                VodPlayerScreen(
                    mediaKey = arg("key"),
                    streamUrl = arg("url"),
                    title = arg("title"),
                    userAgent = arg("ua").ifEmpty { "OpenTV/0.1 (Android)" },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // Sits above the whole nav graph so a found update can prompt from any screen.
        UpdateGate()
    }
}

/**
 * Detects a ten-foot device.
 *
 * Checked at runtime rather than by shipping a separate leanback build: one APK for phone,
 * tablet, Android TV and Fire TV means one thing to release and one thing to test.
 */
fun isRunningOnTelevision(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val packageManager = context.packageManager
    return packageManager.hasSystemFeature("android.software.leanback") ||
        packageManager.hasSystemFeature("android.hardware.type.television")
}
