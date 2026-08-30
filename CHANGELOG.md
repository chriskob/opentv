# Changelog

## 0.12.31

- **Instant Zero-Lag Guide Display & EPG Memory Caching.**
  - **In-Memory EPG Window Cache**: Added persistent in-memory EPG window caching with eager background pre-warming in `EpgRepository`. Guide rows now populate instantly on Frame 1 without showing temporary "No information" placeholders.
  - **Prevent Startup Full-Catalogue Scan**: Fixed race condition where cold launch previously fell back to querying all 40,000 channels across the entire database while waiting for category groups to initialize.
  - **Optimized Programme Queries**: Streamlined SQLite programme window queries by removing redundant temporary table sorting, leveraging existing compound indices for instant retrieval.

## 0.12.30

- **Silent Full-Screen Previous Channel Switch.**
  - **Menu-Free Channel Toggle**: Pressing **Right** on the remote in full-screen mode now switches instantly to the previous watched channel without opening the sub-menu or OSD overlay.

## 0.12.29

- **TiviMate-Accurate Playback Transport Bar Layout & Styling.**
  - **Centered 5-Button Cluster**: Styled the 5 transport buttons exactly as shown in the reference photo — Skip Previous (|◀), Fast Rewind (◀◀), solid white circular center Play/Pause toggle, Fast Forward (▶▶), and Skip Next (▶|).
  - **Right-Aligned [LIVE] & Record**: Positioned the pill-shaped `[LIVE]` badge button alongside the circular `Record` button on the far right.
  - **Left-Aligned Progress Timer**: Displayed elapsed and total program time (e.g. `24:29 / 1:00:00`) on the far left beneath the timeline.

## 0.12.28

- **Full-Screen D-Pad Right Channel Toggle & Enhanced 5-Button Transport Bar.**
  - **Quick Previous Channel Toggle**: In full-screen playback (OSD hidden), pressing **Right** on the remote immediately jumps to the previously watched channel (quick A/B channel toggling).
  - **Compact Transport Controls**: Streamlined button dimensions for a cleaner, modern look.
  - **Integrated Record Button**: Added a direct **Record** button with live recording status indicators to the transport control row.
  - **Integrated Live Button**: Added a **Live** button to the transport control row to immediately jump to the real-time live playback edge.

## 0.12.27

- **Fixed Program Reminders & Reliable Auto-Tune Channel Switching.**
  - **Rock-Solid Alarm Scheduling**: Reminders now use high-precision `AlarmClockInfo` to guarantee exact-second wakeups across all Android versions, even in deep sleep or Doze mode.
  - **Seamless Auto-Tune Execution**: When an auto-tune reminder triggers, the app immediately switches to the target channel directly in `PlayerScreen` or navigates to the player without screen tearing or duplicate instances.
  - **In-App Reminder Alerts**: When a standard reminder fires while the app is active, a TV-friendly dialog prompts with "Tune to Channel" or "Dismiss" options.
  - **Real-Time Signal Bus**: Added `ReminderSignals` to coordinate reminder firings seamlessly across UI layers.

## 0.12.26

- **Sleek Playback Transport Controls (Play, Pause, Rewind, Fast Forward).**
  - **Modern Transport Control Bar**: Added beautiful, circular transport controls (Rewind 10s, prominent Play/Pause center toggle, Fast Forward 10s) positioned prominently between the timeline bar and the history carousel upon pressing OK.
  - **Dynamic State & Focus**: The Play/Pause button dynamically toggles between Play and Pause icons based on stream state and receives initial focus on menu reveal.
  - **Intuitive D-pad Flow**: Seamless vertical navigation across all 3 tiers (Transport Controls ↕ History Carousel ↕ 14 Action Buttons).

## 0.12.25

- **Two-Tier Player Sub-Menu Hierarchy (OK for History, Down for Action Buttons).**
  - **Press OK**: Directly brings up the info banner and watched channels history carousel (TV Guide, History, recent channels) with immediate focus, keeping the action row neatly tucked away.
  - **Press Down**: Smoothly reveals the 14 player sub-menu action buttons underneath and shifts focus directly into the action row.
  - **Press Up / Back**: Gracefully hides the action buttons row and returns focus back up to the history row.

## 0.12.24

- **Full Suite of 14 Player Sub-Menu Quick Action Buttons & Settings Customization.**
  - **14 Player Quick Action Buttons**: Added the complete suite of sub-menu action buttons matching TiviMate (Search, Movies, Shows, Recordings, Multiview, Video Resolution/Quality, Audio Track, Audio Delay, Subtitles/CC, Aspect Ratio, Channels List, Add to Favorites, Channel Options, Settings).
  - **Live State & Telemetry Display**: Action buttons show dynamic stream properties such as current resolution (e.g. `1280 × 720`), audio format (`Stereo`, `5.1`), audio delay (`0 ms`), subtitles (`Off`, language), aspect ratio (`Normal`, `Fill`, `Stretch`), and favorite status.
  - **Audio Delay & Sync Adjuster**: Added audio sync delay adjustment panel with quick step offsets (-200ms to +500ms).
  - **Player Sub Menu Buttons Settings Section**: Added dedicated customization settings in Display Settings with "Enable all" / "Disable all" and individual toggle switches for every button (all enabled by default).

## 0.12.23

- **Fixed Uninterrupted Audio & Playback on Continuous Guide Looping.**
  - **Preserved Active Stream during Guide Wrap**: D-pad navigation wrapping from top-to-bottom or bottom-to-top now exclusively moves the guide cursor highlight without touching the actively playing channel, ensuring continuous uninterrupted picture and sound.
  - **Independent Playing & Highlight Indicators**: Separated the currently tuned playing channel marker (cyan play triangle & border) from the cursor focus highlight so browsing and looping never trigger stream reload or audio cutouts.

## 0.12.22

- **Complete Modern TV Redesign for Settings Hub & All Settings Subscreens.**
  - **Categorized 2-Column Settings Grid**: Grouped all preferences into 3 intuitive visual sections ("Playlists & Content", "Playback & Interface", "System & Management") with colorful tinted icon badges, crisp subtitle guidance, and clean chevron affordances.
  - **Premium Dark Slate TV Styling**: Uniform dark background (`#10171E`), dark slate card containers (`#18222C`), subtle slate borders (`#263442`), and glowing high-contrast focus outlines (`#F0F4F8`, `#26C6DA`).
  - **Redesigned App, EPG, Recording, Add-ons, Profiles, Sync, Parental & Web Manager Pages**: Consistent action headers, modern toggle rows, radio options, and focusable back/done buttons across every screen.

## 0.12.21

- **Continuous Guide Looping with Seamless Cursor Focus & Playlist Settings Editing in Providers.**
  - **Smooth & Continuous Guide Looping**: D-pad UP at row 0 wraps seamlessly to the bottom row, and D-pad DOWN at the bottom row wraps to row 0 with immediate focus attachment so the cursor never disappears.
  - **Edit Playlist / Provider Settings**: Added an "Edit" button to every provider card allowing instant customization of playlist name, server/playlist URL, credentials, MAC address, custom EPG XMLTV URL, User-Agent header, live stream format, and testing connections with optional re-sync on save.

## 0.12.20

- **Fixed Guide & Sub Menu Focus Stability, Cursor Visibility, and Player Sub Menu Shortcut Channels.**
  - **Fixed Disappearing Cursor at Guide Boundaries**: Clamped D-pad navigation at the top and bottom of the TV Guide and channel list so focus remains firmly locked on screen without losing focus or disappearing into offscreen space.
  - **Visible Cursor in Category Sub Menu**: Added high-contrast white card highlighting and bold text state to category rail items so the cursor is clearly visible when browsing categories.
  - **Fixed Shortcut Channels in Player Sub Menu**: Removed the click lockout delay and ensured selecting any channel card or quick action immediately tunes to the channel and dismisses the overlay.

## 0.12.19

- **Live Current Time Line Indicator, Continuous D-Pad Looping, Timeframe Alignment & Zero-Black-Screen Transitions.**
  - **Live Current Time Indicator Line**: Added a vibrant vertical line indicator across the EPG header and timeline rows that marks and tracks the current minute in real time.
  - **Continuous D-Pad Looping**: D-pad navigation continuously wraps between the top channel (row 0) and bottom channel without escaping out of the guide.
  - **Live Timeframe Alignment**: Vertical navigation across rows is anchored to the currently airing programme ("Now" column), preventing focus drift into past or future shows.
  - **Active Channel Focus on Return**: Returning to the guide always scrolls to and highlights the currently playing channel with zero jump back to Channel 1.
  - **Zero-Black-Screen Transitions**: Shared persistent video surface between Guide Preview and PlayerScreen eliminates video decoder teardown and black flashes.

## 0.12.18

- **Instant Full-Screen Play on Live Programmes & Popup Only for Future Airings (TiviMate Standard).**
  - **Instant Live Playback**: Pressing OK on a currently airing programme or on a channel card instantly launches full-screen playback with zero popup dialogs.
  - **Contextual Future Airing Dialog**: Selecting a future programme in the EPG timeline opens the schedule dialog (Record now, Remind me, Auto-switch when it starts, Record series).
  - **Quick Channel Menu**: Long-pressing a channel card retains access to provider options and channel series rules.

## 0.12.17

- **Restored Clean Navigation & Complete TV Guide Rendering.**
  - **Fixed Blank TV Guide Overlay**: Removed the SurfaceView occlusion conflict so the TV Guide renders with 100% full opacity, restoring category rails, channel numbers, logos, and EPG timelines.
  - **Fluid Navigation**: Seamless full-screen playback with zero re-tuning delays, instant guide return, and no full-screen loading banners.

## 0.12.16

- **Eliminated 'Loading your channels' on Guide Return & Fixed Black Screen / Loading Circle on 'Watch'.**
  - **Zero 'Loading your channels' Spinner**: Kept the Guide layout continuously mounted in memory behind full-screen playback, eliminating all Guide unmounting and re-evaluations on back navigation.
  - **Fixed Black Screen on 'Watch' Selection**: Selecting "Watch" in the channel action popup checks if the channel stream is already running on the active player and skips redundant re-tuning, avoiding video decoder resets.
  - **Filtered Buffering Overlay**: Prevented the dark buffering overlay from rendering if playback is already active and streaming audio/video.

## 0.12.15

- **Seamless Uninterrupted Live TV Playback & In-Place Guide Transition (TiviMate Architecture).**
  - **Zero Audio/Video Interruption on Guide Transition**: Full screen and TV Guide now share the same persistent in-place player surface inside `HomeScreen`. Transitioning between the Guide preview and Full Screen never stops, resets, or re-buffers the media stream.
  - **No Intra-App Surface Detach**: Preserved the active `ExoPlayer` surface across view transitions without clearing `it.player`, ensuring video decoding remains 100% fluid at 60fps without black flashes or codec re-initializations.
  - **Clean App Backgrounding**: `MainActivity.onStop` exclusively handles app-level backgrounding to pause playback when switching apps or pressing the TV Home button, while intra-app navigation preserves uninterrupted audio and video.

## 0.12.14

- **Instant Startup Playback & Zero-Lag Guide Return (TiviMate Speed Tuning).**
  - **Instant Boot-to-Playback (1-2s cold start)**: When "Resume last channel" is active, NavHost boots directly into `PlayerScreen` on frame 0, bypassing initial HomeScreen rendering, preview player conflicts, and startup EPG queries.
  - **Activity-Scoped ChannelsViewModel (0s guide return)**: Promoted `ChannelsViewModel` to Activity scope so channel rows and EPG groupings remain warm in memory across all navigation transitions (Home <-> Player), eliminating the 5-6s loading spinner when pressing Back to the guide.
  - **Fast-Start Live Buffering**: Optimized ExoPlayer live playback thresholds (`BUFFER_FOR_PLAYBACK_MILLIS` reduced from 2.5s to 1.0s, rebuffer to 2.0s) for rapid stream startup matching TiviMate tuning.
  - **Instant Audio Stop on Exit**: Added `onStop` lifecycle handlers across `MainActivity`, `PlayerScreen`, and `HomeScreen` to instantly pause ExoPlayer when exiting the app, eliminating background audio bleeding after exit.
  - **Back Navigation Handling**: Backing out from player when booted as root cleanly transitions straight to the TV Guide.

## 0.12.13

- **Configurable Playlist & Guide Refresh Intervals & Persistent Startup Cache.**
  - **Persistent Guide Caching on Startup**: Cold starts now load directly from the local database cache and only trigger network EPG sync when the data has exceeded the user's configured refresh interval.
  - **Custom Playlist Refresh Interval**: Added setting in Display Settings under "Data Refresh" with options for 2h, 4h, 6h, 8h, 12h, 24h, or Manual only (cancels background WorkManager sync).
  - **Custom Guide Refresh Interval**: Added independent EPG refresh interval configuration with matching hourly intervals or manual-only mode.
  - **Update Guide with Playlist Toggle**: Added toggle to synchronize guide refreshes whenever the playlist updates or decouple them for independent refreshing.
  - **Manual Refresh Now Action**: Added a direct "Refresh now" button inside the Data Refresh settings.

## 0.12.12

- **Tier 1 Performance & Responsiveness Enhancements (TiviMate UX Polish).**
  - **Optimized Guide Timeline Layout**: Memoized programme block layout calculations and debt-reconciliation math in `GuideRow` using `remember(programmes, windowStartMillis, nowMillis)` to eliminate heavy frame math during scrolling.
  - **Smooth Channel Navigation**: Added smooth `animateScrollToItem` with easing for D-pad channel navigation in the TV guide.
  - **Synchronized Time Updates**: Centralized `nowMillis` tick across `HomeScreen`, `GuideGrid`, and `ChannelList` for seamless progress bar updates without re-instantiating date formatters.
  - **Eliminated Sub-Composition in Player**: Refactored `LiveTimelineBar` in `PlayerScreen` from `BoxWithConstraints` to `Modifier.onSizeChanged`, avoiding forced sub-composition passes during playback.
  - **Database Index Optimization**: Added standalone index on `channels.sourceId` (Room schema v13 + `MIGRATION_12_13`) to eliminate full table scans on single-source queries.
  - **Startup Memory Optimization**: Refactored `renormalizeAll()` in `CatalogRepository` to process channels source-by-source, preventing large allocations on 1GB TV devices.

## 0.12.11

- **Focus Current Playing Channel & Programme on Return from Player.**
  - When returning from full-screen playback (Back button), focus lands directly on the currently playing channel and its active program in the TV guide timeline.

## 0.12.10

- **Guide Timeline Navigation Fix & Immersive Playback Entry.**
  - Removed focus hijacking on the channel column in `GuideGrid` so you can scroll freely through timeline programme blocks without being forced back to the channel name.
  - Going full-screen (via long press or channel selection) now starts cleanly with full-screen video without popping up the sub menu.
  - Pre-seeded recent watched channels synchronously so opening the sub menu reliably focuses on the first channel card on frame 0.

## 0.12.9

- **Sub Menu Initial Focus, Clear History Button & Options Highlight.**
  - Opening the player sub menu (OK button) now places focus directly on the first watched channel card instead of the TV guide button.
  - Added a "Clear history" button at the end of the sub menu carousel to reset watched channel history.
  - Fixed focus highlight on the "More options" toggle button so it highlights distinctly in solid high-contrast white with dark text.

## 0.12.8

- **TV Overscan Margins & 2-Line Text Wrapping in Guide.**
  - Added TV overscan safe padding to `HomeScreen` so the top preview pane, left channel column, and descriptions are never clipped by the TV bezel.
  - Enabled 2-line title rendering (`maxLines = 2`) in `ProgrammeBlock` with dedicated line height so titles like "Wheel of Fortune" wrap and fit fully without ellipsis truncation.
  - Expanded Guide Preview header to allow 2-line show titles and 3-line synopses so long titles and descriptions are fully readable.
  - Widened channel column and timeline slots for clean text formatting.

## 0.12.7

- **Cursor Retention, Guide Typography & About Clean-up.**
  - Fixed cursor dropping in Sub Menu and Guide by adding trailing timeline filler blocks to guarantee 100% continuous focus coverage across the entire time window, and preventing root Box from capturing focus while controls are up.
  - Added dedicated focusable "More options" pill in player sub menu for smooth vertical navigation.
  - Increased typography size in Guide (channels, show titles, timeline slots) and reduced horizontal padding to eliminate premature `...` truncation.
  - Removed donation links and QR code from the About screen.

## 0.12.6

- **Sub Menu Navigation & History Population Fix.**
  - Fixed D-Pad key event handling so navigating left/right/up/down while the sub menu is visible passes all events directly to UI focus without intercepting and zapping through background channels.
  - Sub menu watch history strictly contains only channels deliberately tuned and watched.

## 0.12.5

- **Watched Channel History in Sub Menu Carousel.**
  - The bottom quick bar carousel now exclusively displays previously and recently watched channels (watch history), with newly watched channels appearing first at the front of the list.
  - Channels only tune/switch when explicitly clicked (OK / Enter pressed) — navigating and highlighting cards with the cursor no longer triggers auto-tuning.

## 0.12.4

- **Full-Screen Player OSD / Sub Menu Redesign (TiviMate Style).**
  - **Top Header Bar**: Added top overlay displaying playlist & category info on the left, and current date & time on the right.
  - **Programme Details & Telemetry**: Shows large bold current show title, start/end time, remaining duration, channel number + name, and stream quality/audio telemetry badges (`HD`, `60 FPS`, `Stereo`, `HEVC`/`H.264`). Also previews the upcoming next programme.
  - **Cyan Timeline Scrubber**: Full-width live progress bar with glowing white scrubber indicator.
  - **Quick Action & Channel Cards Carousel**: Bottom quick bar with TV Guide card, History (Last channel) card, and horizontally scrollable channel cards with logos and current playing shows.
  - **Expandable Secondary Controls**: Down arrow toggle reveals Audio, Subtitles, Quality, Aspect Ratio, Record, and PIP controls.

## 0.12.3

- **TV Guide Cursor & Focus Improvements.**
  - Fixed cursor trapping: Disabled focusable/clickable behavior on the 16:9 preview video player card so D-Pad UP from the top row never escapes into the preview pane.
  - Automatic focus request on playing channel: Returning to the TV Guide from full-screen live playback immediately places and visibly renders the active cursor on the currently playing channel row.
  - Empty EPG focus handling: Channels with no schedule information now have focusable empty blocks with high-contrast borders, preventing the cursor from disappearing when scrolling past channels without EPG data.

## 0.12.2

- **Enhanced TV Guide Grid Dimensions.** Adjusted grid layout to display exactly 6 channels vertically with increased row height (56dp) and widened timeline blocks (5.2dp/min, ~2.5 hours visible horizontally) so show titles and details are significantly more legible.

## 0.12.1

- **Interactive TV Guide Focus & Metadata Header.** Scrolling and navigating the TV Guide timeline now dynamically updates the top header with the full title, start/end time, duration, and synopsis of whatever specific programme is currently highlighted by the remote cursor (while keeping the live channel stream playing in the preview box).

## 0.12.0

- **Seamless TV Guide & Full Screen Playback Transition (TiviMate Architecture).** Switching between full-screen playback and the TV Guide is now instantaneous with continuous uninterrupted audio and zero buffering gap.
- **Background TV Guide Browsing.** Pressing Back from live stream highlights and focuses the active playing channel in the guide. Scrolling up and down allows browsing programme information while the active channel plays continuously without re-tuning until a new channel is selected.
- **TV Remote Navigation & Long-Press Controls.**
  - Holding the Back button (450ms) anywhere in the app jumps directly back to full-screen live playback.
  - Double-pressing Back prompts to confirm exit, preventing accidental app closure.
  - Pressing OK in full screen reveals the bottom menu without pausing the stream.
  - Back button inside menus dismisses the menu overlay without leaving full screen.
- **Live Stream Telemetry Stats Badges.** Real-time badges in the bottom playback menu displaying stream resolution (e.g. `1920x1080`, `4K`), framerate (`60 fps`), video codec (`H.264`, `HEVC`), and audio format (`AAC Stereo`, `AC3 5.1`).
- **7-Day EPG Persistence & Offline Caching.** Complete multi-day programme schedule persistence with room database caching for instant EPG display on app startup.

## 0.11.7

- **Stalker portals: closer to a real box.** Building on v0.11.6, OpenTV now sends the rest of the
  identity a real MAG set-top box presents at login — the auth token in the session cookie (not just
  the header), a hardware-version hash, a timestamp and the box's API signature — and it probes a
  couple more portal paths. Some Ministra/Stalker panels only return the channel list when they see
  all of this, so lines that connected but showed no channels have a better chance of loading. If a
  portal still comes up empty, please open an issue and say which panel software it runs.

## 0.11.6

- **Stalker portals: full set-top-box identity.** OpenTV now authenticates to Stalker/Ministra
  portals the way a real MAG box does — sending the device serial, `device_id`, a signature and a
  metrics blob derived from your MAC, instead of the MAC on its own. Many portals won't hand over
  the channel list or stream links to a box that only presents a MAC, which is why some lines that
  worked in other apps came up empty in OpenTV. This lets those portals authorise OpenTV. If a
  portal still fails, please open an issue and say which one — it helps a lot.

## 0.11.5

- **Add a provider from your phone or laptop.** The "Manage on phone or laptop" web page now has an
  **Add a provider** section — pick Xtream, M3U or Stalker portal, fill it in with a real keyboard,
  then Test and Add. Channels load straight onto the TV, so you never have to type a server address
  or a MAC on the remote again.
- **Fixed the "camera won't scan it?" web address.** The manage screen was printing that link without
  its access token, so typing it into a browser hit a dead "Not found". It now shows the full working
  URL, and the token is short enough to type.


## 0.11.4

The big DVR release. Recordings behave like Sky Q, you can record straight to a NAS, watch a
recording while it's still taping, and the whole interface now speaks 30 languages.

### Recording & DVR

- **Watch a recording while it's still recording.** Start playing something the moment it begins
  taping — OpenTV reads the growing file straight off disk, so it costs **zero extra connections**
  to your provider. On a single-stream account that's the difference between "wait until it's
  finished" and "watch now". Fast-forward and rewind work within whatever's been taped so far.
- **Record straight to a NAS.** Point recordings at a network share over SMB (Settings →
  Recordings → NAS, or set it up from the phone/laptop web manager) so a cheap box isn't boxed in
  by its own storage — and every OpenTV in the house can reach the same recordings.
- **Single-connection auto-switch.** On a one-stream provider, if a recording is due to start on
  another channel, OpenTV moves the screen onto that recording as it begins — with a **30-second
  warning** first and a *Keep watching* button if you'd rather not. No more "why did my live stream
  just cut out?" (Recordings → Recording behaviour → *Auto-switch when recording starts*.)
- **Clash handling with multi-provider fallback.** Book two overlapping recordings and, if more
  than one of your providers carries that channel, OpenTV records each from a **different provider**
  so neither is cut. On a single provider it flags the clash instead.
- **Recording padding.** Start each recording a minute early and run a few minutes past the listed
  end, so a late kick-off or an overrun isn't clipped. Adjustable per side.
- **Series links show the next episode.** A series link now tells you exactly when it next records.
- **Storage readout.** See how much space recordings use and how much is left.

### Live TV

- **Pause & rewind live TV** *(experimental, opt-in).* Holds the last couple of minutes so you can
  pause and jump back. Off by default — it uses more memory.

### Languages

- **30 languages.** The whole interface is translated into Spanish, French, German, Italian,
  Portuguese, Dutch, Polish, Russian, Turkish, Arabic, Simplified Chinese, Japanese, Korean, Hindi,
  Swedish, Danish, Finnish, Norwegian, Czech, Greek, Romanian, Hungarian, Ukrainian, Indonesian,
  Thai, Vietnamese, Bulgarian, Slovak, Croatian and Persian. Settings → Language (each listed in
  its own name), or leave it on *System* to follow your device.

### Fixes & polish

- Smoother watch-while-recording, especially from a NAS: playback now keeps a buffer behind the
  live edge so a network share doesn't stutter at the write head.
- Back never lands you on a stray live channel or drops you out by accident — coming back from a
  recording returns to your recordings, and Back on the home screen asks before it exits.
- Password fields are masked, with a show/hide toggle.
- Recording stability: NAS write timeouts and reconnect handling, so a quiet network doesn't
  silently freeze a capture.

### Notes

- Everything stays on your device — provider logins never leave the box.
- Free and open source (GPL-3.0): https://github.com/opentvproject/opentv


## 0.10.0

- **Movies & Shows, redesigned.** A Plex-style layout with big artwork, cast & director, "more with
  this cast" rows, and cleaned-up titles (no more "NF -" / "(KR)" junk). Optional: add your own free
  TMDB key in Settings → Metadata and OpenTV fills in any posters, backdrops, cast or synopses your
  provider left blank — the key stays on your device.
- **No more stuck "Loading movies & shows".** The movies/series catalogue is cached, so it loads
  instantly on later launches instead of re-downloading every time — and the live preview no longer
  stutters while it loads.
- **Record to a USB / external drive.** Settings → Recordings → USB, pick a folder on a plugged-in
  drive, and recordings write straight there (no storage permission needed) and play back in-app.
- **Recordings screen overhaul.** Scheduled recordings show *when* they'll record and sit in their
  own "Scheduled" section; failed ones have a **Retry**; each is tagged to a profile; and bookings
  re-arm on every launch so a force-stop or app update can't quietly drop them.

## 0.9.0

- **Manage your channels from a phone or laptop.** A new "Manage on phone or laptop" screen in
  Settings shows a QR code and a link — open it on any device on your wifi and you get a proper web
  page to browse, **rename**, hide, favourite and **reorder** channels with a real keyboard and
  mouse. Changes apply to the TV instantly. Local-only: the TV is the server, nothing touches a
  cloud.
- **Recording keeps going when you leave the app or the box sleeps.** OpenTV can now ask Android to
  exempt it from battery optimisation (Settings → Recording → "Recording in the background"), which
  is what keeps recordings running in standby and when you switch away — swiping OpenTV out of
  recents no longer stops a recording either.
- **Rename channels.** Give a channel your own name; it sticks and survives a guide refresh (from
  the web manager for now).
- **Tidier update notes.** The "update available" prompt shows a clean summary of what's new instead
  of build metadata.

## 0.8.0

- **No more endless "Loading channels".** If a provider fails to load or comes back empty (a wrong
  login, a dead server), the guide now shows a clear error with **Retry** and **Provider setup**
  buttons instead of spinning forever.
- **Set up on a phone without the QR dance.** Installing on a phone now shows the login form
  directly so you can type your details in; the QR code is only offered on a TV, where typing is the
  painful part.
- **Paste into the setup fields.** The URL, username and password fields — and the on-screen
  keyboard — now have a **Paste** button, so a copied Xtream line drops straight in.
- **MPEG-TS or HLS, your choice.** Each Xtream provider now has a **Stream format** toggle. If your
  panel only serves `.ts` and channels wouldn't play, switch to MPEG-TS and they will. Defaults to
  HLS, so nothing changes unless you need it.
- **Parental controls scrolls.** The hidden-categories list is reachable to the bottom now, however
  many categories your provider has.
- **A real Channel Manager.** Settings → Channels is now a browsable, two-pane manager: pick a
  category on the left, see its channels on the right, hide or favourite each one — including
  hidden channels so you can bring them back. With more than one provider, a **source filter** keeps
  them separate instead of merged into one list.

## 0.3.0

- **Profiles.** Add local profiles (just a name — no accounts) from the person icon in the top
  menu, and switch between them. Each profile keeps its own resume points and watched state, so
  your half-watched film doesn't show as watched on someone else's profile. Existing resume points
  become the default "Me" profile's — nothing is lost.
- **Continue Watching.** Movies and Shows now open with a "Continue watching" shelf for the active
  profile — pick up your last film or episode where you left off, with a progress bar on each.
- **Local sync (no servers).** Settings → Sync copies your continue-watching between two OpenTV
  devices over your own wifi: one device shares behind a six-digit code, the other receives. It's
  local-only — nothing leaves the house — the household answer to the cloud sync that died with
  Viewella.

- **Proper player controls.** A single control bar slides up from the bottom with play/pause,
  rewind/forward (where the stream allows it), and pickers for **subtitles, audio track, quality
  and aspect ratio**. It hides after a few seconds and any remote button brings it back —
  nothing is ever left painted permanently over the picture.
- **Captions that actually show.** Subtitles are now chosen from the real tracks in the stream
  (and can be turned off), instead of a blind on/off that often left the renderer enabled but
  nothing on screen. Audio-track switching works the same way for multi-language streams.
- **Page through the guide.** Earlier / Now / Later buttons above the guide move the timeline
  forward and back, so you can see what's on later this evening with a remote.
- **Live preview in the guide.** The highlighted channel now plays, muted, inside the preview
  pane. It uses a single player that stops the instant you go full-screen or leave the guide —
  no second decoder — and it can be turned off in Display & playback for older boxes.
- **Channel manager.** Settings → Channels: search for a channel and hide it from the guide (or
  favourite it). Hidden channels drop out of Live TV and search but stay here, greyed, so you can
  bring them back. Hiding covers every quality variant of a channel at once.
- **Parental controls.** Set a 4-digit PIN and mark categories as hidden — they drop out of the
  guide, All channels and search until you unlock them (and with a PIN set, the parental screen
  itself is locked). In Settings → Parental controls.
- **Unified search with an on-screen keyboard.** Search now has its own screen with a d-pad
  keyboard (no more "type on your phone") and covers **channels, movies and shows** together, with
  live results as you type. Reachable from the top menu.
- **Loading indicators for Movies and Shows.** Both now show a spinner while the catalogue is
  syncing instead of a premature "nothing here".
- **Settings and Search in the top menu.** Both now live top-right, next to Live TV / Movies /
  Shows, instead of being buried behind the guide's icons.
- **A single Settings screen.** One Settings hub — Providers (add / remove / re-test sources), TV
  guide, Display & playback, Parental controls, and About (version, a manual update check,
  licence and links).
- **Dark / light / follow-system.** A new appearance setting. TV still defaults to dark; pick
  Dark or Light to force it on any device.
- **Quality picker only when there's a choice.** No more "Quality: Standard" on single-quality
  channels; the picker appears only when a channel actually has multiple qualities.
- New **Display & playback** settings screen (the sliders icon in the guide).

## 0.2.0

- **Guide detail pane** — highlighting a channel now shows its logo, what's on now with
  start/end times and a live progress bar, a synopsis when the guide carries one, and what's
  on next. Press to watch full-screen.
- **Live progress fill** on the current programme in the guide grid, and a focus highlight on
  the highlighted channel.
- **Fix: never asks for your provider again.** A cold launch could briefly decide "first run"
  before your saved sources had loaded and drop you on the setup screen — it now waits for the
  sources to load before deciding what to show.
- **Fix: no second video decoder on the guide**, which was locking up low-end boxes (a
  Chromecast could freeze). The detail pane is logo + guide info; the one decoder lives
  full-screen.

## 0.1.0

First public release.

- Xtream Codes and M3U/M3U8 sources
- Streaming XMLTV guide parsing with incremental, non-destructive sync
- Live TV playback on Media3/ExoPlayer with IPTV-tuned retry behaviour
- Movies and series catalogue
- Favourites, categories, search
- In-app self-update for sideloaded installs
- Single APK for Android TV, Fire TV, phones and tablets
