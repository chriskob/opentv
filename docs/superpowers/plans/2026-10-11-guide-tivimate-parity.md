# TiviMate-Parity Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make OpenTV's guide screen — grid, channel column, time ruler, detail panel — match TiviMate's look and behaviour, driven by one measured `GuideMetrics` object and one new "Parity Guide" palette, leaving Slate Guide and the other nine themes untouched.

**Architecture:** Presentation-only change. A new `GuideMetrics` data class plus `LocalGuideMetrics` composition local carries all guide geometry; a small `GuideTheme` wrapper sets the new `TVIMATE_PARITY` palette for the guide screen only; `GuideGrid`/`GuidePreview`/`ChannelList` read metrics instead of local constants. The data path (`ChannelsViewModel` → `EpgRepository` → `EpgMatcher` → `CatchupResolver`) is unchanged.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2024.11.00), AGP 8.7.3, Gradle 8.11.1; tests are JUnit 4.13.2 + Truth 1.4.4 + Robolectric 4.13 (JVM, no device).

**Spec:** `docs/superpowers/specs/2026-10-11-guide-tivimate-parity-design.md`

## Global Constraints

- OK to use TiviMate artwork, icons, logos, channel art and font files can be copied, referenced, and or shipped.
- The typeface is Android's system `sans-serif-medium` (Roboto Medium, Apache-2.0).
- Slate Guide and the other nine existing palettes keep their current guide appearance. Only `TVIMATE_PARITY` is added.
- Guide text sizes come from the measured scale: 12, 14, 16, 18, 20, 22, 24, 28, 34, 44 sp.
- Every metric value is labelled **measured** (taken verbatim from the APK's resource table) or **derived** (element mapping inferred; must be confirmed in Task 1).
- EPG refresh keeps its existing guarantee: nothing is deleted before the replacement arrives.
- Errors in the guide say what to try next; a past cell with no archive support is inert and silent.
- Local commits only; do not push. Keep the maintainer's other working-tree changes untouched.

## Review Focus

1. **A programme that ends exactly at the now-minute** — must not be clipped, must show as "live-ending" and not partially overlap the next cell. Pinned by `GuideMetricsTest.widthFor` + cell-partitioning tests in Task 4.
2. **A past cell on a non-archive channel with the "show previous programmes without catch-up" toggle off** — must be dimmed, inert, and produce no error. Pinned by the affordance-matrix test in Task 7.
3. **A clock/DST jump while the guide is open** — the now-line and the 30-minute grid must both move, derived from the same UTC programme times. Pinned by `GuideMetricsTest` (grid alignment uses UTC millis only) and Task 4's re-render check.
4. **The focus cursor on a dimmed past cell** — the fill dims, the focus ring and text stay fully opaque so the cursor never becomes harder to see. Pinned by the palette-alpha test in Task 3 and the grid render test in Task 5.
5. **A provider that reports `tv_archive` but rejects timeshift URLs** — the failure must surface as a playback error with actionable text, not a guide crash. Pinned by `CatchupPlayback` regression test in Task 7.

---

## File Structure

| File | Responsibility |
|---|---|
| `app/src/main/java/app/opentv/ui/theme/GuideMetrics.kt` | **New.** All guide geometry/type scale in one immutable data class, its `TIVIMATE_PARITY` default, `LocalGuideMetrics`, and the minute→width math. |
| `app/src/main/java/app/opentv/ui/theme/GuideTheme.kt` | **New.** Wraps the guide screen in the parity palette + metrics. |
| `app/src/main/java/app/opentv/ui/theme/OpenTvPalette.kt` | **Modify.** Add `TVIMATE_PARITY` entry and its palette. |
| `app/src/main/java/app/opentv/ui/channels/GuideGrid.kt` | **Modify.** Read metrics; parity ruler/cells/badges/focus/now-line/channel column; new behaviours. |
| `app/src/main/java/app/opentv/ui/channels/GuidePreview.kt` | **Modify.** Detail panel parity. |
| `app/src/test/java/app/opentv/GuideMetricsTest.kt` | **New.** Geometry math + pinned defaults. |
| `app/src/test/java/app/opentv/GuidePaletteContrastTest.kt` | **New.** Palette readability invariants. |
| `app/src/test/java/app/opentv/GuideAffordanceMatrixTest.kt` | **New.** Past/present × archive-capable × toggle → expected affordance. |
| `app/src/test/java/app/opentv/GuideRenderParityTest.kt` | **New.** Robolectric + Compose: rendered cell/row geometry equals the metrics. |
| `docs/superpowers/plans/2026-10-11-guide-metrics-table.md` | **New.** The measured/derived number table Task 1 produces. |

## Seed metric table (locked by Task 1)

Values below are confirmed in `docs/superpowers/plans/2026-10-11-guide-metrics-table.md` with their
measured/derived labels. Palette hex values live in that table; only geometry/type is repeated here.

Seeded from the values measured in the APK's resource table plus OpenTV's existing guide
constants. Each is confirmed in Task 1 and then pinned in `GuideMetricsTest`.

| Field | Seed | Basis |
|---|---|---|
| `rowHeight` | 48.dp | measured dimen 48dip exists; replaces openTV's 44.dp |
| `channelColumnWidth` | 296.dp | measured dimen 296dip exists (largest column-ish value); replaces 258.dp — **derived mapping** |
| `channelNumberWidth` | 40.dp | measured 40dip |
| `logoSize` | 32.dp | measured 32dip |
| `timeHeaderHeight` | 56.dp | measured 56dip |
| `halfHourWidth` | 240.dp | measured 238dip/252dip/270dip neighbours — **derived** |
| `cellCornerRadius` | 6.dp | matches openTV's existing `GuideCellShape`; 6dip measured |
| `cellGap` | 4.dp | measured 4dip |
| `cellPaddingHorizontal` | 8.dp | measured 8dip |
| `cellPaddingVertical` | 6.dp | measured 6dip |
| `focusRingWidth` | 2.dp | measured 2dip |
| `nowLineWidth` | 2.dp | measured 2dip |
| `chromeAlpha` | 0.7f | derived from Theme.kt's note on TiviMate's default transparency |
| `badgeHeight` | 20.dp | measured 20dip |
| `badgeCornerRadius` | 4.dp | measured 4dip |
| `liveBadgeSize` | 12.dp | measured 12dip |
| `replayMarkerSize` | 16.dp | measured 16dip |
| `favouriteStarSize` | 24.dp | measured 24dip |
| `channelNameSp` | 14.sp | measured |
| `channelCallsignSp` | 12.sp | measured |
| `programmeTitleSp` | 14.sp | measured |
| `programmeMetaSp` | 12.sp | measured |
| `timeRulerSp` | 12.sp | measured |
| `detailTitleSp` | 20.sp | measured |
| `detailMetaSp` | 14.sp | measured |
| `detailBodySp` | 14.sp | measured |
| `detailCategorySp` | 14.sp | measured |

---

### Task 1: Measurement pass — lock the per-element numbers

**Files:**
- Create: `docs/superpowers/plans/2026-10-11-guide-metrics-table.md`

**Interfaces:**
- Consumes: the extracted APK resources under `C:\Users\buick\Desktop\data\_work\apktool_full` (dimens, colors, styles, layouts) and, from the maintainer, one TiviMate guide screenshot.
- Produces: final values for every field in `GuideMetrics` and every role value in `TVIMATE_PARITY`, each labelled measured or derived.

- [ ] **Step 1: Ask the maintainer for one TiviMate guide screenshot** (1920×1080 if possible) and record that the reference is measurement-only, never committed.
- [ ] **Step 2: For each seed field, record the evidence** — for `measured`, the exact resource entry from the APK; for `derived`, the decoded layout/style or the screenshot pixel proportion that fixes the element. Where evidence conflicts, pick the value the screenshot supports and note the conflict.
- [ ] **Step 3: Resolve the parity palette values** from the measured colour set (navy background/surfaces, slate cells, cyan accent, light selected cell with dark ink) and record each hex value against its role name.
- [ ] **Step 4: Write the table** to `docs/superpowers/plans/2026-10-11-guide-metrics-table.md` with columns: field, final value, measured/derived, evidence.
- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-10-11-guide-metrics-table.md
git commit -m "docs(plan): measured guide metric table for parity work"
```

- [ ] **Step 6: Propagate the confirmed numbers** into the seed table above (edit this plan file) and commit, so Tasks 2–7 read pinned values from one place.

### Task 2: `GuideMetrics` object and minute→width math

**Files:**
- Create: `app/src/main/java/app/opentv/ui/theme/GuideMetrics.kt`
- Test: `app/src/test/java/app/opentv/GuideMetricsTest.kt`

**Interfaces:**
- Consumes: the confirmed table from Task 1.
- Produces:
  - `data class GuideMetrics(rowHeight: Dp, channelColumnWidth: Dp, channelNumberWidth: Dp, logoSize: Dp, timeHeaderHeight: Dp, halfHourWidth: Dp, cellCornerRadius: Dp, cellGap: Dp, cellPaddingHorizontal: Dp, cellPaddingVertical: Dp, focusRingWidth: Dp, nowLineWidth: Dp, chromeAlpha: Float, badgeHeight: Dp, badgeCornerRadius: Dp, liveBadgeSize: Dp, replayMarkerSize: Dp, favouriteStarSize: Dp, channelNameSp: TextUnit, channelCallsignSp: TextUnit, programmeTitleSp: TextUnit, programmeMetaSp: TextUnit, timeRulerSp: TextUnit, detailTitleSp: TextUnit, detailMetaSp: TextUnit, detailBodySp: TextUnit, detailCategorySp: TextUnit)` with a `companion object { val TIVIMATE_PARITY: GuideMetrics }`.
  - `fun GuideMetrics.widthFor(fromUtcMillis: Long, toUtcMillis: Long): Dp` — min width for the interval, `halfHourWidth` per 30 minutes.
  - `val LocalGuideMetrics: ProvidableCompositionLocal<GuideMetrics>`.

- [ ] **Step 1: Write the failing test** — `GuideMetricsTest` with `com.google.common.truth.Truth.assertThat` and `org.junit.Test`, covering: 30 minutes maps to `halfHourWidth`; 90 minutes maps to 1.5×; an interval crossing zero length clamps to 0; a programme ending exactly at the now-minute maps to a width ending exactly on a grid boundary; `TIVIMATE_PARITY` equals the pinned defaults from the confirmed table.
- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuideMetricsTest"`
Expected: FAIL (unresolved reference `GuideMetrics`)

- [ ] **Step 3: Implement `GuideMetrics.kt`** — the data class, `TIVIMATE_PARITY` from the confirmed values, `LocalGuideMetrics` as a `staticCompositionLocalOf` defaulting to it, and `widthFor` in the same file. Place the file's licence header and `package app.opentv.ui.theme` to match the project's conventions.
- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuideMetricsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/opentv/ui/theme/GuideMetrics.kt app/src/test/java/app/opentv/GuideMetricsTest.kt
git commit -m "feat(guide): add measured GuideMetrics and minute-to-width math"
```

### Task 3: Parity palette, readability invariants, and the guide theme wrapper

**Files:**
- Modify: `app/src/main/java/app/opentv/ui/theme/OpenTvPalette.kt`
- Create: `app/src/main/java/app/opentv/ui/theme/GuideTheme.kt`
- Create: `app/src/main/java/app/opentv/ui/theme/GuideScreenHost.kt` (see Interfaces)
- Test: `app/src/test/java/app/opentv/GuidePaletteContrastTest.kt`
- Modify: `app/src/main/java/app/opentv/ui/channels/GuideGrid.kt` (entry composable only)

**Interfaces:**
- Consumes: `AppPalette` / `OpenTvPalette` from Task 2's neighbour `Theme.kt`; palette role names already in use (`chromeCell`, `cellFocus`, `onCellFocus`, `cursorFill`, `cursorBorder`, `live`, `recording`, `favourite`, `textMuted`, `outline`, `surface`, `background`, `onSurface`).
- Produces:
  - `AppPalette.TVIMATE_PARITY` with display name `"Parity Guide"`.
  - `@Composable fun GuideTheme(content: @Composable () -> Unit)` — `OpenTvTheme(palette = TVIMATE_PARITY)` + `LocalGuideMetrics provides GuideMetrics.TIVIMATE_PARITY`.
  - `@Composable fun GuideScreenHost(content: @Composable () -> Unit)` — the small wrapper the guide screen calls; see Step 5.

- [ ] **Step 1: Write the failing test** — `GuidePaletteContrastTest`: for `AppPalette.TVIMATE_PARITY.palette()`, the WCAG contrast ratio of `onCellFocus` on `cellFocus` is ≥ 4.5, of `onSurface` on `surface` is ≥ 4.5, and of `onSurface` on `background` is ≥ 4.5. Compute contrast from the Compose `Color` luminance in the test (JVM-safe).
- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuidePaletteContrastTest"`
Expected: FAIL (unresolved reference `TVIMATE_PARITY`)

- [ ] **Step 3: Add the palette** to `OpenTvPalette.kt` using the confirmed hex values from Task 1's table. Append it to the end of the enum — the settings picker iterates `AppPalette.entries`, so it appears there automatically. **Do not change `AppSettings.DEFAULT_PALETTE` and do not bump `THEME_FORCE_VERSION`**: that mechanism force-moves every user onto Slate Guide, and changing Slate is out of scope. The guide gets the parity palette from the wrapper in Step 5, not from the default. Wire the new entry into the `palette()` `when`.
- [ ] **Step 4: Create `GuideTheme.kt`** as specified in Interfaces (licence header, `package app.opentv.ui.theme`).
- [ ] **Step 5: Wrap the guide screen** — create `GuideScreenHost.kt` in `ui.channels` providing `GuideTheme { content() }`; then in `GuideGrid.kt`, wrap the guide entry composable's body in `GuideScreenHost`. Do not change any guide internals in this task.
- [ ] **Step 6: Run the contrast test and the existing suite to verify nothing regressed**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuidePaletteContrastTest"` then `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.*"`
Expected: PASS for both.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/opentv/ui/theme/OpenTvPalette.kt app/src/main/java/app/opentv/ui/theme/GuideTheme.kt app/src/main/java/app/opentv/ui/theme/GuideScreenHost.kt app/src/main/java/app/opentv/ui/channels/GuideGrid.kt app/src/test/java/app/opentv/GuidePaletteContrastTest.kt
git commit -m "feat(guide): add Parity Guide palette and wrap the guide screen"
```

### Task 4: `GuideGrid` reads metrics instead of local constants

**Files:**
- Modify: `app/src/main/java/app/opentv/ui/channels/GuideGrid.kt`
- Create: `app/src/test/java/app/opentv/GuideRenderParityTest.kt`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (test-only deps)

**Interfaces:**
- Consumes: `LocalGuideMetrics`, `GuideMetrics.TIVIMATE_PARITY`, `widthFor(from, to)` from Task 2.
- Produces: `GuideGrid` with no reference to the old module-level `CHANNEL_COLUMN`, `ROW_HEIGHT`, `HALF_HOUR_WIDTH`, `MINUTE_DP` constants; cell widths and row heights come from `LocalGuideMetrics.current`. Existing behaviour (focus, scroll, EPG windows, catch-up badge) unchanged.

- [ ] **Step 1: Add the two test-only dependencies** — `libs.androidx.compose.ui.test.junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }` to `gradle/libs.versions.toml`; `testImplementation(libs.androidx.compose.ui.test.junit4)` and `debugImplementation(libs.androidx.compose.ui.test.manifest)` to `app/build.gradle.kts`.
- [ ] **Step 2: Write the failing test** — `GuideRenderParityTest`, `@RunWith(RobolectricTestRunner::class)`, `createComposeRule()`: render the guide with `GuideTheme` around a minimal two-channel/two-programme fixture and assert the first row's height equals `GuideMetrics.TIVIMATE_PARITY.rowHeight` and the first programme cell's width equals `widthFor` for its interval. Keep the fixture small — no real EPG data, no network.
- [ ] **Step 3: Run the test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuideRenderParityTest"`
Expected: FAIL (guide still uses the old constants)

- [ ] **Step 4: Replace the constants with metric reads** in `GuideGrid.kt`: every site that used `CHANNEL_COLUMN`/`ROW_HEIGHT`/`HALF_HOUR_WIDTH`/`MINUTE_DP` reads `LocalGuideMetrics.current`; the local `widthFor` implementation is deleted in favour of the one from Task 2. Keep `PAST_HOURS`/`FUTURE_HOURS`/`COMPOSE_WINDOW_PAST_MILLIS` untouched — they are data-window, not geometry.
- [ ] **Step 5: Run the render test**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuideRenderParityTest"`
Expected: PASS

- [ ] **Step 6: Run the old guide tests** to prove no behavioural change

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuideDisplayTitleTest" --tests "app.opentv.GuidePerformanceTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/opentv/ui/channels/GuideGrid.kt app/src/test/java/app/opentv/GuideRenderParityTest.kt gradle/libs.versions.toml app/build.gradle.kts
git commit -m "refactor(guide): drive guide geometry from GuideMetrics"
```

### Task 5: Grid visual parity (ruler, cells, badges, focus, now-line, channel column)

**Files:**
- Modify: `app/src/main/java/app/opentv/ui/channels/GuideGrid.kt` (`TimeHeader`, `GuideRow`, `ProgrammeBlock`, `ChannelListRow`, `GuideBadge`)
- Test: `app/src/test/java/app/opentv/GuideRenderParityTest.kt` (extend)

- [ ] **Step 1: Write the failing tests** — extend `GuideRenderParityTest`: time-ruler labels are exactly on 30-minute boundaries; the now-line sits at the pixel for the current UTC minute; a past programme cell's fill alpha is `chromeAlpha * 0.55` while its focus ring and text stay at alpha 1.0; badges render at the metric sizes; the channel column shows number, logo and two-line name at metric widths.
- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuideRenderParityTest"`
Expected: FAIL

- [ ] **Step 3: Align each element to the metrics** — `TimeHeader` height/ruler, `ProgrammeBlock` corner/padding/two-line title/right-aligned meta, `GuideBadge` sizes, the now-line width and position, `ChannelListRow` number/logo/two-line-name split (reusing the existing `CHANNEL_PAREN_PATTERN`/`CHANNEL_CALLSIGN_PATTERN` and `formatChannelNameForDisplay`), and the focus ring in `GuideCell`. Dimming applies to fills only.
- [ ] **Step 4: Run the tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuideRenderParityTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/opentv/ui/channels/GuideGrid.kt app/src/test/java/app/opentv/GuideRenderParityTest.kt
git commit -m "feat(guide): parity grid rendering — ruler, cells, badges, cursor, now-line"
```

### Task 6: Detail panel parity

**Files:**
- Modify: `app/src/main/java/app/opentv/ui/channels/GuidePreview.kt`

- [ ] **Step 1: Write the failing test** — extend `GuideRenderParityTest` with a preview-panel fixture: title at `detailTitleSp`, the time-range/progress/duration line at `detailMetaSp`, description at `detailBodySp`, the category right-aligned at `detailCategorySp`, and the favourite star at `favouriteStarSize`.
- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuideRenderParityTest"`
Expected: FAIL

- [ ] **Step 3: Re-lay out `GuidePreview`** to the metrics, keeping its existing data-binding and empty/error states unchanged.
- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuideRenderParityTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/opentv/ui/channels/GuidePreview.kt app/src/test/java/app/opentv/GuideRenderParityTest.kt
git commit -m "feat(guide): parity detail panel spacing and type scale"
```

### Task 7: Behaviours — go-to-live, previous-without-catchup toggle, catch-up launch

**Files:**
- Modify: `app/src/main/java/app/opentv/ui/channels/GuideGrid.kt`
- Modify: `app/src/main/java/app/opentv/core/AppSettings.kt` (one new preference)
- Create: `app/src/test/java/app/opentv/GuideAffordanceMatrixTest.kt`

**Interfaces:**
- Consumes: `CatchupResolver.isSupported/isSourceCapable/resolve` and `CatchupPlayback` (both existing); the `onTimeShifted` state the guide already feeds.
- Produces:
  - `AppSettings.guideShowPreviousWithoutCatchup: Boolean` (default `false`), read via the existing settings accessor pattern.
  - A `goToLive()` callback the header control calls, which moves focus and scroll to the now-position and clears any timeshift.
  - Cell activation resolution: `(isPast, archiveCapable, setting) → { playLive, launchCatchup, inert }`.

- [ ] **Step 1: Write the failing test** — `GuideAffordanceMatrixTest` covering the full matrix: present programme → `playLive`; past + archive-capable → `launchCatchup`; past + not archive-capable + setting off → `inert`; past + not archive-capable + setting on → `inert` but focusable. Add a regression case: archive-capable per the Xtream flag whose timeshift URL fails to build resolves to `inert` with no exception.
- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuideAffordanceMatrixTest"`
Expected: FAIL (no resolution function, no setting)

- [ ] **Step 3: Implement** the setting, the resolution function, the header "go to live" control (beside the existing "EPG updated …" line, reusing `onTimeShifted` state), and past-cell activation through the existing `CatchupResolver.resolve()` → player path.
- [ ] **Step 4: Run the test to verify it passes**, then the catch-up regression suite

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.GuideAffordanceMatrixTest" && .\gradlew.bat :app:testDebugUnitTest --tests "app.opentv.CatchupResolverTest" --tests "app.opentv.CatchupPlaybackTest" --tests "app.opentv.ArchiveRequestTest" --tests "app.opentv.ArchiveSkipTargetTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/opentv/ui/channels/GuideGrid.kt app/src/main/java/app/opentv/core/AppSettings.kt app/src/test/java/app/opentv/GuideAffordanceMatrixTest.kt
git commit -m "feat(guide): go-to-live, previous-without-catchup, catch-up launch from past cells"
```

### Task 8: Verification — full suite, visual delta, and the checklist

**Files:**
- Create: `docs/superpowers/plans/2026-10-11-guide-parity-delta-log.md`

- [ ] **Step 1: Run the whole JVM suite**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS (no failures; note any pre-existing failures separately)

- [ ] **Step 2: Assemble a debug APK**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Build the delta log** — with the maintainer's reference screenshot, walk each element (row height, channel column width, 30-minute column, badge placement, focus ring, now-line, detail panel spacing) and record target vs actual with the measured pixel difference; anything worse than ~2px is a follow-up task, not a silent accept.
- [ ] **Step 4: Run the manual checklist from `docs/TESTING.md`** — at minimum B (guide feeds), C (now/next), E (no wrong channel matches), F (state survives), G (fast switching) — and record the result in the delta log.
- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-10-11-guide-parity-delta-log.md
git commit -m "docs(plan): guide parity verification delta log"
```

---

## Addendum (2026-10-11): Behavioral reference — EPG pipeline and guide cursor motion

Source work: static evidence + screenshots captured under `C:\Users\buick\Desktop\data\_work\`
(`ghidra_analysis\EPG_BEHAVIOR.md` holds the full write-up; `tvshots\` holds screenshots).
Behavioral reference only — no TiviMate code, names, or strings enter OpenTV.

### EPG pipeline (parity target)

1. **Sources:** user XMLTV URLs (XMLTV-only enforcement) **+ playlist-native EPG** from the
   XtreamCodes panel API; both coexist, user-added sources overlay/replace provider data.
2. **Persistence:** Room DB; program descriptions persist only if the user enables it;
   configurable "days to keep EPG" retention pruning.
3. **Refresh:** periodic WorkManager job with **user-customizable update interval**;
   replacement-then-delete guarantee (matches plan constraint).

### Motion model for the guide cursor (the "very clean, very smooth" behavior)

TiviMate's guide motion follows one rule: **moving the cursor is a draw-only event.**

- **Zero layout work per move.** Every tile is identical geometry (fixed height, duration-proportional
  width, same padding). Focus change = fill/text-color swap only — no measure, no layout, no
  animatePlacement, no scale/spread on focus (the focused cell in the reference screenshot is the
  same size as its neighbors, only brightened).
- **Only ever invalidate two cells.** The previously focused and newly focused cell change visual
  state; nothing else in the grid is touched.
- **No elevation/shadow changes on focus.** Uniform single-surface background; no per-cell shadow
  layers. Shadows/Elevation changes are among the most expensive redraw triggers and are absent.
- **Side panes are decoupled from the cursor.**
  - The detail panel (title, times, progress bar, description, category badge) updates
    asynchronously from the DB/cache after selection — not on every focus move.
  - The preview pane plays; its state changes only when a selection *settles* on a channel, not
    while the cursor travels.
  - The now-line and time ruler persist continuously across cursor motion.
- **Fixed metrics-driven cells** (matches `GuideMetrics`): identical text styles per cell;
  badges ("Live", "New", catch-up icon) are drawn in-cell, not as overlay layers.

### Implications for `GuideGrid`/OpenTV implementation (to fold into Task 4/5)

- Guide cells must be stable, constant-height composables with stable keys; focus state must
  change only color/weight properties (skip full recomposition of unaffected cells).
- No `Modifier.animateItemPlacement()` on focus moves; no scale/spring on focus — parity target
  is opacity/color transitions in the 100–200 ms band.
- Detail/preview panes read from ViewModels on a debounce/settle, keeping scroll frames free of
  data work.
- Suggested pins (map to existing test file names in Tasks 4–7):
  1. Focus move triggers no layout pass on non-adjacent items (recompilation scope test).
  2. Detail panel content change is not on the same frame as a focus move (debounce test).
  3. Focus ring/backing never adds elevation or scale (render assertion vs. `GuideMetrics`).

