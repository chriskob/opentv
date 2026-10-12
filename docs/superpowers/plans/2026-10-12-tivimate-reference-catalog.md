# TiviMate reference catalog — fonts, colours, layout, icons, scroll mechanics

- Working notes for the 2026-10-12 exploration pass requested by the maintainer.
- Source artifact: `tivimate.apk` SHA-256 `beaebb25bf818450bf496d4cefb16885b190f156815dea280c31eaae0909ee6c`
  (ar.tvplayer.tv 5.3.3), decoded at `C:\Users\buick\Desktop\data\_work\apktool_full\`.
- Builds on `2026-10-11-guide-metrics-table.md` (geometry + palette) and
  `_work\ghidra_analysis\EPG_BEHAVIOR.md` (motion model). This catalog extends both with
  the items still unmeasured: per-style font usage, the full colour table, layout structure,
  icon identification, and scroll/animation mechanics.

## Status

- [x] 1. Fonts — families, the type scale, and which styles use which size
- [x] 2. Colours — full resource table mapped to roles
- [x] 3. Guide layout — decoded layout structure (and its hard limit)
- [x] 4. Icons — drawable identification pass
- [x] 5. Scroll/cursor mechanics — Tivimate evidence + OpenTV audit
- [ ] 6. Catch-up audit — OpenTV's current implementation vs the protocol reference

## 1. Fonts and the type scale (complete)

Families, from `values/styles.xml` (names obfuscated; families and sizes legible):

| Family | Size(s) | Where the evidence points |
|---|---|---|
| `sans-serif-medium` (Roboto Medium) | **14 sp** — app-wide default (parent `TextAppearance.Material` overridden) | the base text style; every guide cell/title inherits from here |
| `sans-serif` (regular) | 14, 16, 22, 18 | a secondary text-appearance suite (large screens: lists, detail/prose) |
| `sans-serif-light` | 34, 44, 36 | display-size text in the same suite (big numerals/owners of 0x7f14051c 36sp) |
| `sans-serif-condensed` | 12, 14 (TextAppearance items), 18, 36(condensed+light pair) | the compressed set TiviMate uses for chip/badge and condensed labels |
| `sans-serif-regular` | 18 | four sibling styles (0x7f1402a7-aa) — a repeated fixed config |

Full measured sp scale (dimens with sp units, superset of the earlier table):
**9, 12, 14, 16, 18, 20, 22, 24, 28, 34, 36, 44** — the 9 sp (micro captions) and
36 sp (light display) entries are the two the metrics table did not carry.

The only embedded font file in the APK is `res/font/h.ttf` = a 3,316-byte
**Roboto Medium** subset, "Copyright 2011 Google Inc." — Apache-2.0. Every other
family resolves to the system Roboto.

**Implication for OpenTV:** the parity guide's choice (system `sans-serif-medium`,
scale 12–44) is confirmed and complete. No font files need to be shipped or changed.
The 9 sp micro-caption and 36 sp light display styles are optionally available via the
same system families if a later screen needs them.

## 2. Colours (complete — supersedes role guesses in the metrics table)

Full `values/colors.xml` decoded (199 entries; most names obfuscated). The guide-dark
set identified against the native 1920×1080 Onn 4K capture (which measured the page as
`#1C232C`) now anchors the neutral family (not the earlier navy guess, which was the
"Dark Blue" *theme* family TiviMate ships as an alternate):

| Resource | Value | Read |
|---|---|---|
| 0x7f060038 | `#1c232c` | guide page background — exact match to the capture |
| 0x7f060037 | `#2c333c` | programme-cell tone (airing/emphasis family; capture saw `#2C323A`, Δ within JPEG tolerance) |
| 0x7f060039 | `#3c434c` | lightened cell / border tone |
| 0x7f060034/35/36 | `#b0bec5` / `#607d8b` / 60% of it | cool-grey secondary text + its 60% alpha |
| `divider` (survived by name!) | `#10ffffff` | 6%-white hairline — the row divider the capture shows |
| 0x7f06000e | `#7fa87f` | muted green (category/category-coding family) |
| 0x7f06001c/1d/1e | `#ffe082` / `#ffc107` / `#60ffc107` (60%) | the amber accent family (+ exact 60% alpha entry) |
| 0x7f06001f/20/21 | `#58491d` / `#3a3113` / `#756226` | dark amber ink family (amber-on-light surfaces) |
| 0x7f060019/1a/1b | `#2196f3` (+ two aliases) | the blue accent family |
| 0x7f060068–6d | `#80deea`, `#00bcd4`, `#6000bcd4`, `#174247`, `#0f2c2f`, `#1f585f` | cyan family (dark theme's accent + backgrounds) |
| 0x7f06006e–73 | `#232629`, `#131619`, `#333639`, `#262626`, `#161616`, `#363636` | the neutral dark-anthracite set (OLED-ish alternate theme) |
| 0x7f0600b0–b3 | `#a5d6a7`, `#4caf50`, 60%, `#194c1b` | green family |
| 0x7f0600a5/a6 | `#ff7043`, `#ff5722` | orange/red-orange family (recording?) |
| 0x7f0600aa | `#f4f3f0` | the light parchment tone (light theme surface) |
| 0x7f0600a8/07 | `#b0000000` | 69%-black — the menu/list scrim backdrop (see layout 0x7f0e003f) |
| 0x7f060055 | `#e0e0e0` | light-grey (light theme cell tone) |

**Correction vs the metrics table §3:** the navy set (`#142b3d/#1f415c/#29577b`) is a
legitimate palette family in the APK, but the *native capture* matches the neutral set
above (`#1c232c` page, `#2c333c` cell, `#3c434c` emphasis, `#dfe1e4` focused). If the
maintainer wants the guide to match the capture exactly, `TVIMATE_PARITY` should use
the neutral set; the navy set is what TiviMate's "Dark Blue" user theme would give.
`gammaHex`/categories: category coding uses the saturated families (green/cyan/blue/brown).

## 3. Guide layout — the hard limit (complete)

- All 184 layout files have obfuscated names. Of these, only **two** contain a
  `RecyclerView` — and both are simple vertical lists:
  - `0x7f0e003f`: full-screen RecyclerView with `scrollbars="vertical"` over a
    69%-black background (a menu/search list scrim).
  - `0x7f0e0133`: RecyclerView with `clipToPadding="false"` + zero top/bottom padding
    (content scrolls under edge padding — a scrolling list with edge fade).
- **No layout XML exists for the guide grid**: the grid, the channel column and the
  time ruler are a custom view constructed in code, and app code is
  DexProtector-protected (classes renamed to unicode gibberish, strings encrypted —
  see `_work\ghidra_analysis\FINDINGS.md`).
- **The static ceiling is therefore reached.** Deeper guide-internal extraction (exact
  adapter/prefetch config, paint pipeline) needs runtime instrumentation on a device
  (Frida/objection against the unpacked DexProtector payload), which is the operator's
  device decision and explicitly out of static scope.
- Everything behaviourally important was already extracted the legitimate way:
  screenshot measurement (metrics-table §4b/§4c) and the motion model (EPG_BEHAVIOR §8).

## 4. Icons (complete — and a strong result)

Every vector drawable was rendered to a contact sheet (`_work\contact_page1.html` …
`page3.html`; 121 renderable vectors) and identified visually.

**Finding: TiviMate's UI icon set is essentially the stock Google Material Icon set —
Google's project, Apache-2.0.** Identified glyphs include: arrows, chevrons, close,
copy, scissors, paste, QR/api, share, search, mic, star, send, TV, `live_tv`,
restore/replay, fullscreen, the D-pad/nearby glyphs, pause, play, refresh, settings
gear, skip-next/prev, slow-motion, fast-forward/rewind, repeat, shuffle (+ "on"
variants), error, warning, `picture_in_picture_alt`, notifications bell,
`visibility`/`visibility_off`, bookmarks (plain/edit/add), checkbox/radio states,
**CC** and **DVR** text glyphs (both are stock `closed_caption`/`dvr` Material icons),
delete, download/logout, description, film, `playlist_add`/`playlist_remove`, filter,
image, folder, grid, history, `vpn_key`, device hub, `link`/`link_off`, lock / lock
open, login, `page_search`, `movie`, cast/target, an aspect-ratio box,
`playlist_add_check`, radio/target circles, **record** (grey dot + **red** dot ×2),
sort, edit, star outline/star, `tv` variants, alarm/snooze variants, timer,
`settings_suggest`-style gear box, `videocam`, list rows, volume.

PNG raster assets are only 96 files, every one **< 5 KB** — tiny shutter-bitmaps/
close-ups, no large art. Channel logos are fetched from providers at runtime; the
launcher icon is the adaptive vector (`0x7f0800b8`, 312.5 viewport, 7 paths + alias
`ic_launcher-background` structure).

**Implication for OpenTV (the artwork task, resolved):** picking the matching
**Material icon names in Compose** gets ~100% visual parity with zero TiviMate assets
and zero licensing exposure (each glyph is Apache-2.0 licensed Google art). The only
TiviMate-specific shapes worth porting verbatim, if ever desired, are the two "text
gylines" (DVR / CC pills) and the red REC dots — all five are themselves Material-stock
shapes; nothing proprietary remains.

## 5. Scroll and cursor mechanics (complete)

**TiviMate side (evidence-bounded):**
- The guide grid is a code-built custom view; its adapter internals are
  DexProtector-blocked (see §3). The behavioural model stands on the capture work in
  `EPG_BEHAVIOR.md` §8 + metrics-table §4c:
  - One fixed geometry for every tile; cursor move = recolour exactly two cells
    (previous + new) — no re-measure, no relayout, no elevation change.
  - The now-line and time ruler persist through cursor motion; side panes decouple
    (detail hydrates async after settle; preview re-tunes only when a channel settles).
  - Menus/lists over the guide render through a scrimmed vertical RecyclerView
    (`#b0000000` background) and a `clipToPadding=false` list — i.e. standard
    RecyclerView lists, nothing exotic.
- What *cannot* be extracted statically: adapter recycle/prefetch knobs, the exact
  scroll interpolation of deliberate jumps. Runtime instrumentation would answer that
  and is a separate operator decision.

**OpenTV side (audited today against `GuideGrid.kt` + `HomeScreen.kt`):** the
architecture already implements the motion model:
- `UNIFIED_ARCHIVE`-style single horizontal `ScrollState` shared by every row and the
  time header (GuideGrid.kt 164, 1557, 1764) — one scroll, no per-row relayout.
- Stable row keys + fixed row height in the `LazyColumn` (`key = { row.key }`).
- D-pad vertical steps land **instantly** via `listState.scrollToItem` — animation is
  reserved for deliberate jumps (jump-to-live/restore/preview snap; comment lines
  809-811). Same for horizontal: one tap = one 30-min column step;
  **hold Left = 30-minute scrub**, single tap falls through on key-up
  (962-989); RIGHT hold is swallowed so scrubbing can't walk forward.
- Scroll-into-view only fires when the target row/column is at or beyond the viewport
  edge (790-797) — no gratuitous scrolling per step.
- Detail pane hydration is decoupled (`snapshotFlow { highlightedRow.key }` +
  `collectLatest` on the EPG store) and preview only tunes the OK-selection
  (HomeScreen.kt 265-297) — matching EPG_BEHAVIOR's "cursor moves are draw-only",
  plus the earlier commits (7ed5812/f86058e) already dropped focus-zoom and took the
  cursor to a dark cell + teal ring from the native capture.

**Gaps remaining on OpenTV (from the addendum's own pin list, not yet written):**
1. No test pins "detail panel content lands on a later frame than the focus move"
   (hydration is async today but un-pinned — a refactor could re-tie them).
2. No test pins "focus move does zero layout work on non-adjacent cells".
3. Horizontal cursor travel currently snaps the shared timeline via `scroll.scrollTo`
   after each step (16 ms retry loop, lines 823-827) — works but is an implementation
   smell worth replacing with a single `bringIntoView`-style computation if profile
   ever shows churn.
   All three are small Compose/Robolectric tests; none block the catch-up work.

## 6. Catch-up audit (complete — the feature is already built)

**State: implemented end-to-end, test suite green** (`Catchup*` + `Archive*`
filtered run, 2026-10-12: BUILD SUCCESSFUL). Inventory of what exists:

| Area | Implementation | Pinned by |
|---|---|---|
| Capability detection | `CatchupResolver.isSupported`/`isSourceCapable`: per-channel flag, template, Xtream portal, embedded credentials, URL shape (`Watcher攀` evidence in HomeScreen 1580-1600) | `GuideAffordanceMatrixTest`, HomeScreen badge wiring |
| Resolution — Xtream | `/timeshift/u/p/minutes/STAMP/id.ts`, UTC stamp, `tv_archive_duration` carried as `tvArchiveDays` (`XtreamApi`, `XtreamPanelDiscovery` + `applyArchive`) | `CatchupResolverTest`, `ArchiveRequestTest` |
| Resolution — templates | full token set: `{utc}` `{start}` `{timestamp}` `{lutc}` `{end}` `{duration}` `{offset}` `{(b)yyyyMMddHHmmss}` `{(b)yyyy-MM-dd:HH-mm}` `{catchup-id}`, relative/query-form templates | `CatchupResolverTest` |
| Resolution — modes | `shift`/`append` (SIPTV `?utc=&lutc=`), `flussonic` (`archive-{utc}-{secs}.m3u8`), `xc` auto-detect, `default` fallback | `CatchupResolverTest` |
| Corrections | per-channel `catchupCorrectionMin` + global Settings correction (spans hours; timezone skews) | resolver tests + AppSettings 1092-1141 |
| Retention window | `effectiveArchiveDays` clamps (never extends) the provider's `tv_archive_duration`/`catchup-days`; player refuses beyond it locally instead of 404ing (PlayerScreen 978-981) | — |
| Seek | minute-resolution stamp handled via `ArchiveRequest(streamStart, offsetInStream)` floor + confirm-on-open offset; `catchupSkipSec` setting (5–60 s, TiviMate "Playback > Skip Steps") drives timeline scrub and channel-key skips; RW/FF/Pause work on archive | `ArchiveRequestTest`, `ArchiveSkipTargetTest` |
| Security | `redactCredentials` for log lines (path, query, userinfo shapes) | `resolver` tests |
| Guide integration | past cell → `LAUNCH_CATCHUP`; inert without archive unless `guideShowPreviousWithoutCatchup`; jump-to-live | `GuideAffordanceMatrixTest` |
| Player integration | session swap `isLive=false`, keep-parked-time on Back, start-over,OnError surface | `CatchupPlaybackTest` |

**Key bindings today (deliberate, commented in PlayerScreen 1601-1640):**
while full-screen, Left/Down-as-seek applies at the OSD tier; hidden bar: Down = channel
zap, Left = transparent channel list; RW/FF and timeline-left always seek. TiviMate's
"Use Left/Right / Down for seeking" playback option is therefore *structurally* covered —
if a setting row is wanted to swap Down from zap to seek, it is one enum + one handler swap.

**What "nail down" can still mean from here:**
1. **On-device verification against the maintainer's real provider** — the road to real
   confidence. Checklist: past-cell opens archive · seek ±step · FF/RW · pause ·
   leave-and-return keeps position · jump-to-live returns · `tv_archive` but broken
   timeshift surfaces an actionable error (not silence) · program started 3 minutes ago
   opens at the correct offset (stamp minute-flooring).
2. **The two unpinned motion-model tests** from §5 (detail-panel frame decoupling;
   no-layout-on-focus-move) — small Robolectric pins, unrelated to catch-up correctness.
3. Optional: a "Down seeks" playback option row, per the reference above.

