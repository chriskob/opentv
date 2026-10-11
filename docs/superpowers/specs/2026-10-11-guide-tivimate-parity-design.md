# Design: TiviMate-parity guide for OpenTV

- Date: 2026-10-11
- Status: approved for planning
- Scope: the guide screen only — the programme grid, the channel column, the time ruler, the
  detail/preview panel, and the guide-specific behaviours listed below.
- Author: reverse-engineering session (REA) + brainstorming with the maintainer.

## 1. Goal

Make OpenTV's guide look and behave like TiviMate's — same grid geometry, same type scale, same
focus/cursor language, same detail panel, and the same archive (catch-up) affordances in the
grid — without taking a single byte of TiviMate's artwork, and without changing how the Slate
Guide theme (or the other nine themes) look.

## 2. Decisions taken

| # | Decision |
|---|----------|
| 1 | A new palette (working id `TVIMATE_PARITY`) becomes the guide's default. Slate Guide and the other themes keep their current guide appearance. |
| 2 | Scope is the guide screen only: grid + detail/preview panel. EPG source management and guide settings are a later pass. |
| 3 | Behaviour parity: visual + focus parity, dim past programmes, "go to live", "show previous programmes without catch-up", and launching an archived programme from a past cell. |
| 4 | Catch-up launch supports **Xtream timeshift only** in this pass. M3U catch-up templates stay as they are today (already resolved by `CatchupResolver`). |
| 5 | Structure: one `GuideMetrics` object consumed by the guide, plus the new palette. No palette-owned geometry. |
| 6 | The guide always renders in the parity palette, independent of the app-wide theme (an optional dedicated "guide theme" setting is a later pass). |

## 3. Reference data (measured, not guessed)

Everything below was read out of the shipped APK with REA (apktool 2.11.1 + jadx-headless-mcp
0.7.1); the APK's SHA-256 is `beaebb25…0909ee6c`, package `ar.tvplayer.tv` 5.3.3 (1000005332).

**Typeface and text sizes.** Their styles reference `android:fontFamily="sans-serif-medium"`, and the
one embedded font is a 3,316-byte subset of **Roboto Medium** ("Copyright 2011 Google Inc.",
Apache-2.0). So their typography *is* the system face — no font file needs to ship in OpenTV.

Text sizes in use: `12, 14, 16, 18, 20, 22, 24, 28, 34, 44 sp`.

**Dimens (dip) in use** — the candidate set the guide's geometry comes from:

```
0, 1, 2, 3, 4, 5, 6, 6.5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 18, 20, 21, 22, 24, 27, 28,
32, 36, 40, 48, 50, 52, 56, 60, 64, 70, 72, 80, 87, 90, 96, 98, 108, 118, 132, 150, 160, 167,
180, 190, 220, 238, 252, 270, 296, 300, 320, 340, 360, 380, 400, 408, 560, 584, 600, 720
```

plus negatives used for optical nudging (`-1, -3, -100`).

**Colours.** 196 entries; the guide palette is the dark navy family in that set (near-black navy
surfaces, slate programme cells, cyan navigation accent, light selected cell with dark ink).

**Locality caveat (important).** The APK's resource *names* are obfuscated — apktool could only
emit `APKTOOL_DUPLICATE_*` placeholders, so `dimen_0x7f070034 = 296dip` cannot be tied to "row
height" by name. The mapping from dimension *value* to *element* is therefore established by a
measurement pass (below), not by reading names. Every number carried into `GuideMetrics` is
labelled either **measured** (value comes straight from the APK) or **derived** (mapped to an
element by inspection of the decoded layouts/styles plus the reference screenshot).

**Reference screenshot.** The maintainer supplies a TiviMate guide screenshot for side-by-side
comparison. It is used for measurement only and is never committed or shipped.

## 4. Architecture

Presentation only. No schema, sync, provider, or networking changes; the guide's data path
(`ChannelsViewModel` → `EpgRepository`/`EpgMatcher` → `CatchupResolver`) is untouched.

```
MainScreen
 └─ GuideScreen (existing)
     ├─ GuideTheme(palette = TVIMATE_PARITY, metrics = TIVIMATE_PARITY)   ← new wrapper
     │   ├─ OpenTvTheme(...)            (existing)
     │   └─ LocalGuideMetrics provides GuideMetrics
     ├─ GuideGrid      (existing; reads metrics instead of local constants)
     ├─ GuidePreview   (existing; detail panel parity)
     └─ ChannelList    (existing; channel column parity)
```

### 4.1 `ui/theme/GuideMetrics.kt` (new)

A single immutable `data class GuideMetrics` holding the guide's geometry and type scale, with a
`TIVIMATE_PARITY` default instance and a `LocalGuideMetrics` composition local. Fields (names to
be settled in the measurement pass; seeded from the values in §3):

- row and column: `rowHeight`, `channelColumnWidth`, `channelNumberWidth`, `logoSize`,
  `logoPaddingEnd`
- time axis: `timeHeaderHeight`, `pixelsPerMinute` (the 30-minute column width / 30),
  `nowLineWidth`, `firstMinuteSnap` (grid alignment)
- cells: `cellCornerRadius`, `cellPaddingHorizontal`, `cellPaddingVertical`, `cellGap`,
  `focusRingWidth`, `chromeAlpha` (today's `LocalGuideChromeAlpha`)
- badges: `badgeHeight`, `badgeCornerRadius`, `liveBadgeSize`, `replayMarkerSize`,
  `favouriteStarSize`
- type: `channelNameSp`, `channelCallsignSp`, `programmeTitleSp`, `programmeMetaSp`,
  `timeRulerSp`, `detailTitleSp`, `detailMetaSp`, `detailBodySp`, `detailCategorySp`

Rules:

- `GuideGrid.kt` drops `CHANNEL_COLUMN`, `ROW_HEIGHT`, `HALF_HOUR_WIDTH` and reads these instead.
- Minute→pixel conversion lives in one function in this file (`widthFor(from, to)` moves here),
  unit-tested in isolation.
- Nothing outside the guide reads `GuideMetrics`, so a future per-theme override is opt-in.

### 4.2 Palette (existing file, one addition)

`OpenTvPalette.kt` gains `TVIMATE_PARITY(<display name>)` — display name neutral (no suggestion of
affiliation; e.g. "Parity Guide"). It reuses the existing guide roles rather than new ones:
`background`, `surface`, `subtle`, `onSurface`, `onSurfaceVariant`, `outline`, `focusBorder`,
`primary`, `cellFocus`/`onCellFocus`, `cursorFill`/`cursorBorder`, `chrome`, `chromeCell`,
`chromeCellBorder`, `live`, `recording`, `favourite`, `textMuted`. Values come from the measured
navy family in §3.

Two existing mechanisms must not be touched:

- `AppSettings.DEFAULT_PALETTE` is `SLATE_GUIDE`, and a one-shot `THEME_FORCE_VERSION` bump force-moves
  every user onto it. Changing either would alter Slate Guide for existing users, which decision 1
  rules out. The guide takes the parity palette from the §4.3 wrapper, not from the default.
- The settings picker iterates `AppPalette.entries`, so the new palette appears there automatically
  and a user may select it app-wide; that is fine, but it is not what drives the guide's look.

Slate Guide is not modified. The theme list order puts the parity palette first so it is the
default when a user has not chosen one.

### 4.3 `ui/theme/GuideTheme.kt` (new)

```kotlin
@Composable
fun GuideTheme(content: @Composable () -> Unit) {
    OpenTvTheme(palette = AppPalette.TVIMATE_PARITY) {
        CompositionLocalProvider(LocalGuideMetrics provides GuideMetrics.TIVIMATE_PARITY) {
            content()
        }
    }
}
```

Wrapping happens at the guide screen root, so the rest of the app keeps the user's theme.

### 4.4 `ui/channels/GuideGrid.kt` (existing, reworked)

- Replace the local constants with metric reads (see §4.1).
- Time ruler: fixed 30-minute columns, half-hour labels, first-line minute snapping, and the
  vertical now-line at the exact now-minute.
- Programme cells: rounded corners, internal padding, two-line title with ellipsis, right-aligned
  meta (time or category), and the existing `NEW` / `Live` / replay / favourite / reminder badges
  repositioned to the parity metrics.
- Focus/cursor: the current cell uses `cellFocus`/`onCellFocus` with a focus ring of
  `focusRingWidth`; dimming applies to fills only, never to the ring or text (the transparency
  rule already documented in `Theme.kt`).
- Past programmes: dimmed via `chromeCell.copy(alpha = … * 0.55f)` (already present) — kept, but
  the alpha comes from the metrics object.
- New behaviours:
  - **Go to live** — a control in the guide header row (beside the "EPG updated …" line) that
    returns focus and scroll to the now-position; selecting a programme that is already live
    plays it. The guide already tracks `onTimeShifted`, so this reuses that state rather than
    adding a parallel mode.
  - **Show previous programmes without catch-up** — a toggle; when off, past cells on channels
    without archive support stay dimmed and inert; when on, they remain dimmed but focusable so
    they can be inspected (matching TiviMate's behaviour).
  - **Catch-up launch from a past cell** — a past cell on an archive-capable channel opens the
    archive stream through the existing `CatchupResolver.resolve()` → player path. Channels are
    detected with the existing `catchUpChannelIds` / `isSourceCapable` logic (Xtream timeshift
    only per decision 4).
- Channel column: number (fixed width), logo, and the two-line name split (the existing
  `CHANNEL_PAREN_PATTERN` / `CHANNEL_CALLSIGN_PATTERN` logic stays; widths move to metrics).

### 4.5 `ui/channels/GuidePreview.kt` (existing, reworked)

Detail panel parity: title, time range with progress bar and duration, season/episode line,
description, favourite star (top-right), and right-aligned category — all spaced with the metrics
object. Behaviour (what it shows for now vs past vs archive programmes) is unchanged.

## 5. Data flow

Unchanged. The guide's EPG data continues to arrive through `EpgRepository` and be matched by
`EpgMatcher`; archive URLs come from `CatchupResolver.resolve(source, channel, programme)`.
`tvArchive` per channel already flows from the Xtream `get_live_streams` fields through the
models, so no new fetching or storage is required.

## 6. Error handling and edge cases

- A past cell on a channel with no archive support is **inert**: dimmed, no error toast (matches
  TiviMate). It may still be focused for inspection when decision 3's toggle is on.
- EPG refresh keeps the existing guarantee: nothing is deleted before the replacement arrives.
- Errors shown in the guide keep the existing rule — they say what to try next.
- Clock changes / DST: the now-line and the 30-minute grid derive from the same UTC-normalised
  programme times the repository already stores, so no new timezone handling is introduced.
- Low-end boxes: metrics are static Dp values, so the grid cost is unchanged; no recomposition of
  the whole grid on scroll.

## 7. Testing

- **Unit** (JVM): `widthFor` minute→pixel mapping; cell partitioning (`firstBlockEndingAfter` and
  friends) with no overlap and no clipping at window edges; catch-up toggle matrix (past/present ×
  archive-capable) resolving to the right affordance.
- **Palette invariants**: contrast of `onCellFocus` on `cellFocus` and of `onSurface` on `surface`
  in the parity palette, asserted in a test so a future palette edit cannot make the cursor
  unreadable.
- **Compose UI**: the guide renders at the metrics' cell widths and row heights (assert against
  `GuideMetrics.TIVIMATE_PARITY`), and the guide wrapper does not leak the palette to sibling
  screens.
- **Manual / visual**: side-by-side against the maintainer's reference screenshot, with a
  checklist of the measured elements (row height, channel column width, 30-minute column, badge
  placement, focus ring, detail panel spacing). Deltas are logged as measured-vs-target numbers.

## 8. Risks and unknowns

1. **Fidelity ceiling.** TiviMate's resource names are obfuscated, so element→dimension mapping
   needs the measurement pass in step 1 of the plan. Expect within-a-pixel-or-two parity on
   geometry; the type scale and palette are exact. Anything not measurable stays labelled derived.
2. **Provider variance in catch-up.** Only Xtream timeshift is in scope; a provider that reports
   `tv_archive` but rejects timeshift URLs will surface as a playback failure, not a guide
   failure — acceptable, but it must be an error message the user can act on.
3. **Two sources of truth during the transition.** Until `GuideGrid` stops using its local
   constants, edits must land on the metrics object; the plan makes the swap an early, separate
   step so the two never both apply.
4. **Theme naming.** A display name implying affiliation with TiviMate is avoided (see §4.2).

## 9. Out of scope

- TiviMate's artwork, icons, logos, channel art, or fonts (never copied).
- EPG source management screens and guide-related settings screens.
- M3U catch-up tag support (already in `CatchupResolver`; unchanged).
- Any change to the player, recordings, sync, or VOD screens.
- Changing Slate Guide or any of the other nine themes' appearance.

## 10. Suggested implementation phases

1. **Measurement pass** — resolve the guide's element↔dimension mapping from the decoded layouts
   and styles plus the reference screenshot; produce the final `GuideMetrics.TIVIMATE_PARITY`
   numbers and the parity palette values, each labelled measured or derived.
2. **Metrics plumbing** — add `GuideMetrics`, `LocalGuideMetrics`, `GuideTheme`; switch
   `GuideGrid`/`GuidePreview`/`ChannelList` to read them; delete the local constants.
3. **Parity palette** — add `TVIMATE_PARITY`, set it as the guide default, add the palette
   invariant tests.
4. **Grid parity** — ruler, cells, badges, focus/cursor, now-line, channel column.
5. **Detail panel parity.**
6. **Behaviour additions** — go-to-live, previous-programmes-without-catchup toggle,
   catch-up launch from a past cell.
7. **Verification** — unit/Compose tests, visual side-by-side, delta log.
