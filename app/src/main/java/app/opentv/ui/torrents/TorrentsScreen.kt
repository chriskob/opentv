/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.torrents

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.data.model.TorrentFile
import app.opentv.data.model.TorrentStatus
import app.opentv.data.remote.TorrServerClient
import app.opentv.pairing.QrCodes
import app.opentv.torrent.PhoneAddPage
import app.opentv.torrent.PhoneAddRequests
import app.opentv.torrent.PhoneAddServer
import app.opentv.torrent.TorrentEngine
import app.opentv.torrent.TorrentMediaKey
import app.opentv.ui.components.TvOutlinedTextField
import app.opentv.ui.settings.components.SettingsButton
import app.opentv.ui.settings.components.SettingsButtonStyle
import app.opentv.ui.settings.components.SettingsEmptyState
import app.opentv.ui.settings.components.SettingsShape
import app.opentv.ui.settings.components.SettingsSpacing
import app.opentv.ui.settings.components.settingsFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "watch now" list: paste a magnet, the engine finds it, the player opens.
 *
 * ## What the list actually is
 * The items are held here, in the ViewModel, and not read back from the engine's own list. Two
 * reasons, both learned from the engine's API rather than guessed: pasted links are added *without*
 * being saved, so a torrent the engine has finished with simply vanishes from its list — a list
 * built on it would empty itself mid-session; and re-reading a status with `get` is what would load
 * a finished torrent back up, so the engine's list cannot be a passive read the way a catalogue
 * query is. The engine stays the source of truth for *progress*, which is what it is good at.
 *
 * ## Why polling stops when the tab isn't on screen
 * The engine drops a torrent with no reader after `torrentDisconnectTimeout` (30 s by default), so
 * a status is only shown while someone is looking at it and leaving the tab lets the engine forget
 * the torrent on its own. The player no longer drops the torrent when it closes: leaving it lets
 * the same timer clean up, so coming straight back resumes instead of starting the download over.
 * Reading status through `list` - never `get` - matters: `get` reloads a dropped torrent and starts
 * it downloading again, which is what made the poll resurrect a finished one.
 */
class TorrentsViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = ServiceLocator.get(app)
    private val engine = graph.torrentEngine
    private val client = graph.torrServerClient

    /** One row on screen: what was pasted, what the engine says about it now, and its file list. */
    data class Item(
        val hash: String,
        val magnet: String,
        val title: String = "",
        /** Live status, or null when the engine is no longer holding this torrent. */
        val status: TorrentStatus? = null,
        /**
         * The file list as last seen. Kept separately from [status] so that a torrent the engine has
         * dropped — which is the normal end of a session — still shows its files and can be played
         * again without waiting for metadata a second time.
         */
        val files: List<TorrentFile> = emptyList(),
        val selectedFileId: Int? = null,
        /** Set while a play is waiting for this torrent's metadata. */
        val autoPlay: Boolean = false,
        /** Set while a play has its metadata and is waiting for the engine to hold real data. */
        val preparing: Boolean = false,
        val addedAtMs: Long = System.currentTimeMillis(),
        val peerHintShown: Boolean = false,
    ) {
        val videoFiles: List<TorrentFile> get() = TorrServerClient.videoFiles(files)

        /** The file to play: the one the user picked, else the torrent's biggest video. */
        val chosenFile: TorrentFile?
            get() = selectedFileId?.let { id -> files.firstOrNull { it.id == id } }
                ?: TorrServerClient.biggestVideo(files)

        val displayName: String
            get() = title.ifBlank { videoFiles.firstOrNull()?.path?.substringAfterLast('/').orEmpty() }
    }

    data class Message(val resId: Int, val arg: String? = null)

    data class PlayRequest(val mediaKey: String, val url: String, val title: String)

    val engineState: StateFlow<TorrentEngine.State> = engine.state

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private val _message = MutableStateFlow<Message?>(null)
    val message: StateFlow<Message?> = _message.asStateFlow()

    private val _playRequest = MutableStateFlow<PlayRequest?>(null)
    val playRequest: StateFlow<PlayRequest?> = _playRequest.asStateFlow()

    /**
     * Promises the engine is not keeping, straight from its own settings: upload, on-disk caching,
     * local-network discovery. Normally empty; non-empty means the app should say so rather than
     * quietly claim the engine behaves.
     */
    private val _assuranceWarnings = MutableStateFlow<List<String>>(emptyList())
    val assuranceWarnings: StateFlow<List<String>> = _assuranceWarnings.asStateFlow()

    /**
     * The phone-add page's address, straight from the app-wide server: it is up for as long as the
     * app is, and its address never changes, so the QR here is the same one every visit.
     */
    val phoneSession: StateFlow<PhoneAddServer.Session?> = graph.phoneAddServer.session

    private var screenVisible = false
    private var startingEngine = false

    /** The play that is waiting for the engine to have data, so a poll cannot ask twice. */
    private var prepareJob: Job? = null

    init {
        viewModelScope.launch {
            while (true) {
                delay(POLL_MS)
                if (!screenVisible || engine.baseUrl == null || _items.value.isEmpty()) continue
                refresh()
            }
        }

        // The phone's links arrive whenever the app is open, not only while this screen is, so this
        // is app-scoped rather than tied to the screen's visibility. Adding here — rather than
        // somewhere app-wide — keeps a single owner of the list and a single place that starts
        // playback.
        viewModelScope.launch {
            PhoneAddRequests.pending.collect { queue ->
                if (queue.isEmpty()) return@collect
                while (true) {
                    val request = PhoneAddRequests.takeNext() ?: break
                    // A request the phone already gave up on must not start a film minutes later.
                    if (request.response.isCancelled) continue
                    request.response.complete(addFromPhone(request.link))
                }
            }
        }
    }

    /**
     * The screen is on (or off) display. Opening it starts the engine, so a paste doesn't wait for a
     * cold process start; leaving it clears any play still waiting for metadata, so a film can't
     * start itself later from somewhere else in the app.
     */
    fun onScreenVisible(visible: Boolean) {
        screenVisible = visible
        if (visible) {
            startEngine()
        } else {
            prepareJob?.cancel()
            prepareJob = null
            _items.update { list ->
                list.map { it.copy(autoPlay = false, preparing = false) }
            }
        }
    }

    private fun startEngine() {
        if (startingEngine) return
        startingEngine = true
        viewModelScope.launch {
            val state = engine.ensureStarted()
            startingEngine = false
            if (state is TorrentEngine.State.Running) {
                _assuranceWarnings.value = withContext(Dispatchers.IO) { client.violatedAssurances() }
                refresh()
            }
        }
    }

    /** Try the engine again after a failed start. */
    fun restartEngine() {
        engine.stop()
        startEngine()
    }

    /**
     * Add what the user pasted and start playing it as soon as the torrent's files are known.
     *
     * The whole point of the screen is that the player opens by itself, so this sets [Item.autoPlay]
     * and lets the poll loop do the waiting: metadata for a magnet comes from peers, which is the one
     * part of this that takes an unpredictable amount of time.
     */
    fun add(raw: String) {
        val link = raw.trim()
        if (!TorrServerClient.isTorrentLink(link)) {
            _message.value = Message(R.string.tor_invalid)
            return
        }
        viewModelScope.launch {
            val state = engine.ensureStarted()
            if (state !is TorrentEngine.State.Running) {
                _message.value = Message(R.string.tor_engine_not_running)
                return@launch
            }
            when (val result = withContext(Dispatchers.IO) { client.add(link) }) {
                is TorrServerClient.AddResult.Added -> {
                    upsertAdded(link, result.status)
                    _message.value = null
                }
                is TorrServerClient.AddResult.Refused -> {
                    _message.value = Message(R.string.tor_add_failed, result.reason)
                }
                TorrServerClient.AddResult.EngineDown -> {
                    _message.value = Message(R.string.tor_engine_not_running)
                }
            }
        }
    }

    /**
     * The phone's way into the same flow: add the link, mark it for auto-play, and let the poll's
     * readiness gate open the player. Returns what the phone page should say, including the
     * engine's own refusal so it can be shown on the phone instead of silently vanishing.
     */
    suspend fun addFromPhone(raw: String): PhoneAddPage.Result {
        val link = raw.trim()
        if (!TorrServerClient.isTorrentLink(link)) return PhoneAddPage.Result.InvalidLink
        val state = engine.ensureStarted()
        if (state !is TorrentEngine.State.Running) return PhoneAddPage.Result.EngineUnavailable
        return when (val result = withContext(Dispatchers.IO) { client.add(link) }) {
            is TorrServerClient.AddResult.Added -> {
                upsertAdded(link, result.status)
                PhoneAddPage.Result.Added(
                    result.status.title.ifBlank { result.status.name }.takeIf { it.isNotBlank() },
                )
            }
            is TorrServerClient.AddResult.Refused -> PhoneAddPage.Result.Refused(result.reason)
            TorrServerClient.AddResult.EngineDown -> PhoneAddPage.Result.EngineUnavailable
        }
    }

    /** Add or refresh the row for a freshly added torrent, marked to play as soon as it can. */
    private fun upsertAdded(link: String, status: TorrentStatus) {
        _items.update { list ->
            list.filterNot { it.hash == status.hash } + Item(
                hash = status.hash,
                magnet = link,
                title = status.title.ifBlank { status.name },
                status = status,
                files = status.fileStats,
                autoPlay = true,
            )
        }
    }

    /** Play a row, loading it again first if the engine has already let it go. */
    fun play(item: Item, fileId: Int? = null) {
        val chosen = fileId?.let { id -> item.files.firstOrNull { it.id == id } } ?: item.chosenFile
        // Both paths end in the same queue: the readiness gate below must not be something only the
        // automatic play goes through, or pressing Play would hand the player an empty URL again.
        update(item.hash) {
            it.copy(autoPlay = true, selectedFileId = chosen?.id ?: it.selectedFileId)
        }
        if (item.status != null) {
            maybeAutoPlay(_items.value)
            return
        }
        viewModelScope.launch {
            val state = engine.ensureStarted()
            if (state !is TorrentEngine.State.Running) {
                _message.value = Message(R.string.tor_engine_not_running)
                return@launch
            }
            when (val result = withContext(Dispatchers.IO) { client.add(item.magnet) }) {
                is TorrServerClient.AddResult.Added -> {
                    val status = result.status
                    update(item.hash) {
                        it.copy(
                            status = status,
                            files = status.fileStats.ifEmpty { it.files },
                            addedAtMs = System.currentTimeMillis(),
                            peerHintShown = false,
                        )
                    }
                }
                is TorrServerClient.AddResult.Refused -> {
                    update(item.hash) { it.copy(autoPlay = false) }
                    _message.value = Message(R.string.tor_add_failed, result.reason)
                }
                TorrServerClient.AddResult.EngineDown -> {
                    update(item.hash) { it.copy(autoPlay = false) }
                    _message.value = Message(R.string.tor_engine_not_running)
                }
            }
        }
    }

    /** Forget a torrent: the engine stops it and releases everything it held for it. */
    fun remove(item: Item) {
        _items.update { list -> list.filterNot { it.hash == item.hash } }
        viewModelScope.launch { withContext(Dispatchers.IO) { client.remove(item.hash) } }
    }

    /** Forget every torrent, and the list with it. */
    fun removeAll() {
        _items.value = emptyList()
        _message.value = null
        viewModelScope.launch { withContext(Dispatchers.IO) { client.wipe() } }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** The screen has navigated to the player; the request must not fire twice. */
    fun consumePlay() {
        _playRequest.value = null
    }

    private suspend fun refresh() {
        val current = _items.value
        if (current.isEmpty()) return
        // One `list` for the whole screen, and never `get`: `get` reloads a torrent the engine has
        // dropped and starts downloading it again, so the one-second poll would restart a finished
        // download from zero. `list` only reports what is actually held right now.
        val held = withContext(Dispatchers.IO) { client.list() }
            .associateBy { it.hash.lowercase() }
        val refreshed = current.map { item ->
            // Absent from `list` means the engine no longer holds it — normally because it was
            // dropped after playback ended, or because it was never in this session. The row stays,
            // marked stopped, so it can be played again from its magnet.
            val status = held[item.hash.lowercase()]
            if (status == null) {
                item.copy(status = null, autoPlay = false)
            } else {
                item.copy(
                    status = status,
                    files = status.fileStats.ifEmpty { item.files },
                    title = status.title.ifBlank { status.name }.ifBlank { item.title },
                )
            }
        }
        _items.value = refreshed
        maybeAutoPlay(refreshed)
        maybeHintNoPeers(refreshed)
    }

    private fun maybeAutoPlay(items: List<Item>) {
        if (!screenVisible) return
        if (prepareJob?.isActive == true) return
        val pending = items.firstOrNull { it.autoPlay && it.files.isNotEmpty() } ?: return
        val file = pending.chosenFile ?: return
        prepareJob = viewModelScope.launch { prepareAndPlay(pending, file) }
    }

    /**
     * Wait until the engine can hand the player a real byte of [file], then open it.
     *
     * The engine accepts a stream URL as soon as the file list is known, but a swarm takes an
     * unpredictable time to deliver the first piece. Handing that URL over early is what makes a
     * perfectly alive magnet look broken: the player asks for the file, gets nothing usable back
     * and gives up, while the torrent is still warming up. A one-byte probe on the same URL costs
     * one request, so the player opens only when there is something to play — and if the swarm
     * never answers, the screen says so instead of leaving a spinner on a dead end.
     */
    private suspend fun prepareAndPlay(item: Item, file: TorrentFile) {
        val base = engine.baseUrl ?: return
        val url = TorrServerClient.streamUrl(base, item.magnet, file) ?: return
        update(item.hash) { it.copy(preparing = true) }
        try {
            val ready = withContext(Dispatchers.IO) {
                val deadline = System.currentTimeMillis() + READY_WAIT_MS
                while (System.currentTimeMillis() < deadline) {
                    if (client.canServe(url)) return@withContext true
                    delay(PROBE_MS)
                }
                false
            }
            if (!screenVisible) return
            if (ready) {
                startPlayback(item, file)
            } else {
                update(item.hash) { it.copy(autoPlay = false) }
                _message.value = Message(R.string.tor_no_data)
            }
        } finally {
            update(item.hash) { it.copy(preparing = false) }
        }
    }

    /**
     * A magnet nobody answers is the single most common failure, and it looks identical to one that
     * is merely slow. After [NO_PEERS_MS] of waiting for metadata the screen says so — once, and
     * while leaving the torrent in place, because peers do sometimes turn up much later.
     */
    private fun maybeHintNoPeers(items: List<Item>) {
        val now = System.currentTimeMillis()
        items.forEach { item ->
            val stat = item.status?.stat
            // Only while there is still no file list: once metadata is here, peers *have* answered
            // and this message would be wrong — a slow swarm is what [tor_no_data] is for.
            val waiting = item.files.isEmpty() && (
                item.autoPlay || stat == TorrentStatus.STAT_ADDED || stat == TorrentStatus.STAT_GETTING_INFO
                )
            if (waiting && !item.peerHintShown && now - item.addedAtMs > NO_PEERS_MS) {
                update(item.hash) { it.copy(peerHintShown = true) }
                _message.value = Message(R.string.tor_no_peers)
            }
        }
    }

    private fun startPlayback(item: Item, file: TorrentFile) {
        val base = engine.baseUrl ?: return
        val url = TorrServerClient.streamUrl(base, item.magnet, file) ?: return
        val title = item.displayName.ifBlank { file.path.substringAfterLast('/') }
        _items.update { list ->
            list.map {
                if (it.hash == item.hash) {
                    it.copy(autoPlay = false, preparing = false, selectedFileId = file.id)
                } else {
                    it
                }
            }
        }
        _playRequest.value = PlayRequest(
            mediaKey = TorrentMediaKey.of(item.hash, file.id),
            url = url,
            title = title,
        )
    }

    private fun update(hash: String, transform: (Item) -> Item) {
        _items.update { list -> list.map { if (it.hash == hash) transform(it) else it } }
    }

    private companion object {
        const val POLL_MS = 1_000L

        /**
         * How often the one-byte probe is repeated while the swarm catches up. Half a second is
         * faster than the poll loop, so a torrent that is already warm opens without a visible
         * wait.
         */
        const val PROBE_MS = 500L

        /**
         * How long a play waits for the first byte before giving up on it. Long enough for a thin
         * swarm to deliver a piece, short enough that nobody stares at a spinner for a minute
         * wondering whether the app is broken.
         */
        const val READY_WAIT_MS = 90_000L

        /** Long enough to cover a slow metadata fetch on a thin swarm, short enough to be useful. */
        const val NO_PEERS_MS = 45_000L
    }
}

/**
 * The Torrents tab: paste a magnet, watch it arrive, play it.
 */
@Composable
fun TorrentsScreen(
    onPlay: (mediaKey: String, url: String, title: String) -> Unit,
    viewModel: TorrentsViewModel = viewModel(),
) {
    val engineState by viewModel.engineState.collectAsState()
    val items by viewModel.items.collectAsState()
    val message by viewModel.message.collectAsState()
    val playRequest by viewModel.playRequest.collectAsState()
    val warnings by viewModel.assuranceWarnings.collectAsState()
    val phoneSession by viewModel.phoneSession.collectAsState()
    val magnetFocus = remember { FocusRequester() }
    var magnetFocused by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    // Compose restores whatever had focus when this screen was left, and that is almost always the
    // rail item that opened it. Coming back from the player - or opening the tab - should put the
    // cursor in the magnet field, ready to paste, rather than on the side menu. The request is
    // repeated because the window is still settling after the player closes, and a request made
    // mid-transition can be accepted and then overwritten by focus restoration.
    LaunchedEffect(Unit) {
        repeat(10) {
            if (magnetFocused) return@LaunchedEffect
            runCatching { magnetFocus.requestFocus() }
            delay(200)
        }
    }

    // The engine runs only while someone is looking at this screen, and leaving it cancels a pending
    // auto-play (see onScreenVisible).
    DisposableEffect(Unit) {
        viewModel.onScreenVisible(true)
        onDispose { viewModel.onScreenVisible(false) }
    }

    LaunchedEffect(playRequest) {
        val request = playRequest ?: return@LaunchedEffect
        viewModel.consumePlay()
        onPlay(request.mediaKey, request.url, request.title)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = SettingsSpacing.PageHorizontal, vertical = SettingsSpacing.PageVertical),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tor_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
                SettingsButton(
                    text = stringResource(R.string.tor_remove_all),
                    onClick = viewModel::removeAll,
                    style = SettingsButtonStyle.Danger,
                    icon = Icons.Filled.Delete,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.tor_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1f)) {
                TvOutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        if (message != null) viewModel.clearMessage()
                    },
                    label = { Text(stringResource(R.string.tor_paste_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { magnetFocused = it.isFocused }
                        .focusRequester(magnetFocus),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsButton(
                        text = stringResource(R.string.tor_add_and_play),
                        onClick = {
                            viewModel.add(input)
                            input = ""
                        },
                        style = SettingsButtonStyle.Primary,
                        icon = Icons.Filled.PlayArrow,
                        enabled = input.isNotBlank(),
                    )
                    message?.let { current ->
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = current.arg?.let { stringResource(current.resId, it) }
                                ?: stringResource(current.resId),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                EngineLine(state = engineState, onRetry = viewModel::restartEngine)
                if (warnings.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.tor_assurance_warning, warnings.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            phoneSession?.let { session ->
                PhoneAddCard(session = session, modifier = Modifier.width(196.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        if (items.isEmpty()) {
            SettingsEmptyState(
                title = stringResource(R.string.tor_empty),
                message = stringResource(R.string.tor_empty_hint),
                icon = Icons.Filled.Download,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.hash }) { item ->
                    TorrentRow(
                        item = item,
                        onPlayAll = { viewModel.play(item) },
                        onPlayFile = { file -> viewModel.play(item, file.id) },
                        onRemove = { viewModel.remove(item) },
                    )
                }
            }
        }
    }
}

/**
 * The "add from your phone" plate: a QR code pointing at this device's local page, and a line
 * telling the user what to do with it. Shown only while the LAN server is listening.
 */
@Composable
private fun PhoneAddCard(session: PhoneAddServer.Session, modifier: Modifier = Modifier) {
    val size = 172.dp
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = remember(session.url, px) { QrCodes.render(session.url, px) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.tor_phone_qr_description),
                modifier = Modifier
                    .size(size)
                    .clip(SettingsShape.Card)
                    .background(Color.White)
                    .padding(8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.tor_phone_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.tor_phone_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The engine's own state, as one line: getting ready, ready, or why not. */
@Composable
private fun EngineLine(state: TorrentEngine.State, onRetry: () -> Unit) {
    when (state) {
        TorrentEngine.State.Stopped, TorrentEngine.State.Starting -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.tor_engine_starting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is TorrentEngine.State.Running -> {
            Text(
                text = stringResource(R.string.tor_engine_running, state.version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is TorrentEngine.State.Failed -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.tor_engine_failed, state.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(12.dp))
                SettingsButton(
                    text = stringResource(R.string.tor_engine_retry),
                    onClick = onRetry,
                    icon = Icons.Filled.Refresh,
                )
            }
        }
        TorrentEngine.State.Unsupported -> {
            Text(
                text = stringResource(R.string.tor_engine_unsupported),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TorrentRow(
    item: TorrentsViewModel.Item,
    onPlayAll: () -> Unit,
    onPlayFile: (TorrentFile) -> Unit,
    onRemove: () -> Unit,
) {
    val status = item.status
    val percent = status?.let { TorrServerClient.percentLoaded(it) } ?: -1

    Column(
        Modifier
            .fillMaxWidth()
            .clip(SettingsShape.Card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SettingsShape.Card)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.displayName.ifBlank { item.hash.take(12) },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = statusLine(item, status, percent),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            SettingsButton(
                text = stringResource(R.string.tor_play),
                onClick = onPlayAll,
                style = SettingsButtonStyle.Primary,
                icon = Icons.Filled.PlayArrow,
            )
            Spacer(Modifier.width(10.dp))
            SettingsButton(
                text = stringResource(R.string.common_remove),
                onClick = onRemove,
                style = SettingsButtonStyle.Danger,
                icon = Icons.Filled.Delete,
            )
        }

        if (percent >= 0 && status != null && status.stat <= TorrentStatus.STAT_WORKING) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // A torrent with more than one video is a pack, and the biggest file is a guess. Listing them
        // costs nothing and is the difference between "it played the wrong episode" and a choice.
        val videos = item.videoFiles
        if (videos.size > 1) {
            Spacer(Modifier.height(10.dp))
            videos.forEach { file ->
                FileRow(
                    file = file,
                    selected = item.selectedFileId == file.id || (item.selectedFileId == null && file.id == videos.first().id),
                    onPlay = { onPlayFile(file) },
                )
            }
        }
        val chosen = item.chosenFile
        if (chosen != null && TorrServerClient.isUnplayableContainer(chosen.path)) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.tor_unplayable_container,
                    TorrServerClient.extension(chosen.path).uppercase(),
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun FileRow(file: TorrentFile, selected: Boolean, onPlay: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .settingsFocus(
                shape = SettingsShape.Row,
                selected = selected,
                onFocusChange = { focused = it },
            )
            .focusable()
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = file.path.substringAfterLast('/'),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused || selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = TorrServerClient.formatBytes(file.length),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What the row says under its title: state, then however much of it is worth knowing. */
@Composable
private fun statusLine(item: TorrentsViewModel.Item, status: TorrentStatus?, percent: Int): String {
    if (status == null) return stringResource(R.string.tor_state_stopped)
    val state = stringResource(
        when (status.stat) {
            TorrentStatus.STAT_ADDED -> R.string.tor_state_waiting
            TorrentStatus.STAT_GETTING_INFO -> R.string.tor_state_info
            TorrentStatus.STAT_PRELOAD -> R.string.tor_state_buffering
            TorrentStatus.STAT_WORKING -> R.string.tor_state_ready
            else -> R.string.tor_state_stopped
        },
    )
    val parts = buildList {
        add(state)
        if (percent >= 0 && status.torrentSize > 0) add("$percent% ${TorrServerClient.formatBytes(status.loadedSize)}/${TorrServerClient.formatBytes(status.torrentSize)}")
        TorrServerClient.formatSpeed(status.downloadSpeed).takeIf { it.isNotEmpty() }?.let { add(it) }
        if (status.activePeers > 0) add(stringResource(R.string.tor_peers, status.activePeers))
        else if (item.autoPlay) add(stringResource(R.string.tor_finding_peers))
    }
    return parts.joinToString(" · ")
}
