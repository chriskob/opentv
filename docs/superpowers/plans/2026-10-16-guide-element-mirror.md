# Guide element mirror - Plan 1: grid and chrome

**Status:** implemented 2026-10-16 and verified on the Onn box; the capture is
`docs/screenshots/parity-10.png`. Two values were corrected against the device *render* after the
first pass - see "What the device render changed" at the end.

**Spec:** `docs/superpowers/specs/2026-10-11-guide-tivimate-parity-design.md`, extended by the
element map below. That spec set the method (the reference's own resources govern geometry, the
Onn capture governs rendered colour). This plan applies the method element by element to the guide
grid and its chrome, which is what "mirror it element-for-element" asks for.

## Why

The first parity pass ported the values it had measured. Reading the reference's decoded layouts
end to end (`PLAYLIST/work/decoded_res/res/layout/`) shows a handful of elements that pass never
touched, and one row structure it got wrong. This plan closes the grid-and-chrome half of that gap.

## The mirror rule

1. **Geometry, sizes, margins, type sizes come from the decoded layout XML** - the exact `dip`/`sp`
   values, quoted with the file and element id in the map below.
2. **Rendered colour comes from the capture** - the XML states colours only when it inlines them
   (`#20ffffff` below), and the theme attributes it uses instead (`?0x7f040131`, `?0x7f040132`)
   do not resolve in a decoded tree.
3. **Mirror the rendered result, not the container arithmetic.** The reference reaches x=270dip by
   a `190dip` margin plus an `80dip` padding on two separate recyclers; its guide rows and its ruler
   both simply begin at the channel column's right edge. OpenTV achieves the same edge by laying the
   column and the timeline side by side. That difference is invisible and stays.
4. **Where the reference ships an element `gone`, that is a design-time default, not a decision.**
   Whether its code shows the element is unknowable from the decode. Elements in that state are
   called out in the map with the state this plan gives them.

## Element map

Provenance is `res/layout/APKTOOL_RENAMED_<file>.xml` unless the row says otherwise. "Shipped"
means the first parity pass already renders it, and this plan does not change it.

| # | Element | Reference value | Provenance | State |
|---|---|---|---|---|
| G1 | Page background | `#1c232c` | `res/values/colors.xml` `0x7f060038` | shipped |
| G2 | Row band | `41dip` | `0x7f0e008e` `ChannelFrameLayout` | shipped |
| G3 | Row container | `270dip x 144dip`, horizontal guideline at `41dip` from the top | `0x7f0e008e` `0x7f0b01e1`/`0x7f0b01e2`; `res/values/dimens.xml` `0x7f070059` | Plan 4 |
| G4 | Channel column | `270dip` | `dimens` `0x7f070059` | shipped |
| G5 | Channel number | `24sp`, min width `28dip`, marginStart `16dip`, centred, maxLines 1 | `0x7f0e008e` `0x7f0b0404` | shipped |
| G6 | Logo box | `47 x 31dip`, marginStart `6dip` | `0x7f0e008e` `0x7f0b0224` | shipped |
| G7 | Channel name | `14sp` `sans-serif-medium`, `singleLine`, `ellipsize=marquee`, `marqueeRepeatLimit=1`, marginStart `10dip`, marginEnd `6dip` | `0x7f0e008e` `0x7f0b03db` | **Task 2** |
| G8 | Status icon, primary | `20 x 20dip`, marginEnd `6dip` | `0x7f0e008e` `0x7f0b022a` | **Task 3** |
| G9 | Status icon, secondary | `16 x 16dip`, `alpha=0.6`, marginEnd `8dip` | `0x7f0e008e` `0x7f0b0219` | **Task 3** |
| G10 | Status icon, tertiary | `18 x 18dip`, marginEnd `8dip` | `0x7f0e008e` `0x7f0b0230` | **Task 3** |
| G11 | Programme cell | padding `12dip` horizontal, `1dip` bottom, one line, `sans-serif-condensed`, `#ffffffff` | `0x7f0e0092` | shipped |
| G12 | Cell text size | `12sp` (set in code, not in the layout) | maintainer's screencap | shipped |
| G13 | Header clock | `16sp` `sans-serif-medium`, height `29dip`, marginTop `12dip`, marginStart `16dip`, marginEnd `8dip` | `0x7f0e0058` `0x7f0b03a6` | **Task 4** |
| G14 | Header divider | `1dip`, `#20ffffff`, full width, at the clock's bottom edge | `0x7f0e0058` `0x7f0b0140`; `dimens` `0x7f0700a2` | **Task 4** |
| G15 | Grid block | marginTop `2dip`, paddingBottom `8dip` | `0x7f0e0058` `0x7f0b0430` | **Task 4** |
| G16 | Time ruler | `12sp` labels; `fadingEdgeLength=24dip`, horizontal | `0x7f0e0058` `0x7f0b033c` | shipped, fade not mirrored (see below) |
| G17 | Detail panel | height `180dip`, marginTop `12dip`, marginStart `346dip`, marginEnd `12dip` | `0x7f0e0058` `0x7f0b0320`; `dimens` `0x7f0705a9` | Plan 2 |
| G18 | Guide menu overlay | width `248dip`, padding `8dip`, margin `16dip`, elevation `8dip` | `0x7f0e0058` `0x7f0b0436` | Plan 3 |
| G19 | Expanded row panel | full size below the band, marginTop `41dip`, marginStart `270dip` | `0x7f0e008e` `0x7f0b0322` | Plan 4 |
| G20 | Secondary text | `#a0ffffff` | `colors.xml` `0x7f06047c` | shipped |
| G21 | Primary text | `#ffffffff` | `colors.xml` `0x7f060374` | shipped |

G13 is confirmed against the capture as well as the XML: the clock sits at `12 + 29 = 41dip` below
the detail panel's bottom edge - exactly one row band - and the ruler labels share that band. In
`parity-09.png` the date/time and the `2:00 PM` label are on the same line, and both are larger
than the label run beneath them.

### Deliberately not mirrored

| Element | Reference | Why not |
|---|---|---|
| Ruler fading edge | `24dip` horizontal scroll-edge fade | A `RecyclerView` scroll-edge effect with no Compose equivalent worth building. It is a scroll affordance, not a look. |
| `408dip` fragment container | `0x7f0e0058` `0x7f0b00ee`, padding `24dip` | Its owning screen could not be identified from the decode, and it is not the guide's rail (408dip = 816px, where the capture shows a 240dp rail). Left alone rather than guessed at. |
| Rail width | - | OpenTV's rail is its own; the reference's rail layout is not in this file. Not part of this plan. |
| Cell width axis (`halfHourWidth`, `minuteDp`) | code-driven | Capture-governed, already measured. Untouched. |

## Folding phases into plans

The mirror spans four semi-independent subsystems, so it is four plans rather than one. Each
produces a guide that builds, tests green, and looks closer than the last.

| Plan | Scope | Slices |
|---|---|---|
| **1 (this)** | Guide grid and chrome | G7, G8-G10, G13-G15; retire the metrics nothing reads |
| 2 | Detail panel | G17, its internal arrangement |
| 3 | Guide context menu | G18, its internal arrangement |
| 4 | Expandable channel row | G3, G19 - a behaviour addition, not just geometry |
| 5 | Assets | the reference's own row/status/cell icons in place of OpenTV's Material ones |

Plan 1 is deliberately the one that needs no new assets and no behaviour change, so it can land and
be looked at on the box before the structural work starts.

## Global constraints

- Repository `C:\Users\buick\Desktop\data\opentv`, package `app.opentv`. No worktree.
- Build needs `JAVA_HOME=C:\Users\buick\Desktop\data\tools\jdk-21\jdk-21.0.12.1+1`; the default
  `java` on this machine is 1.8 and Gradle will not run under it.
- Tests: `.\gradlew.bat :app:testDebugUnitTest`. Build: `.\gradlew.bat :app:assembleDebug --console=plain`.
- The D8 Kotlin-metadata rewrite warnings for `app.opentv.ui.vod.VodScreensKt$...` are pre-existing
  and harmless. Do not treat them as regressions.
- The debug APK's applicationId is `app.opentv` (no `.debug` suffix). `app.opentv.debug` on the box
  is a stale build from an older config; never measure that one.
- On-device: `C:\Users\buick\adb-fastboot\platform-tools\adb.exe connect 192.168.1.128:5555`,
  `install --no-incremental -r -d -t`, `screencap`. OpenTV resumes into playback on launch; send
  `input keyevent 4` to reach the guide. Never pipe adb or gradle through `Select-Object -First` -
  early termination kills the process and fakes an exit code.
- TDD throughout, in this project's idiom: the values are pinned in
  `app/src/test/java/app/opentv/GuideMetricsTest.kt`, and the wiring (the render reading the metric
  rather than a literal) is pinned in `app/src/test/java/app/opentv/GuideRenderParityTest.kt`, which
  reads `GuideGrid.kt` as text. Write the test first, watch it fail, then implement.
- Every metric added here carries the reference's provenance in its KDoc, the way the existing
  fields in `GuideMetrics.kt` do. A value without provenance is a value that will drift.

---

## Task 1: the reference's grid values into `GuideMetrics`, and retire the ones nothing reads

**Files:** `app/src/main/java/app/opentv/ui/theme/GuideMetrics.kt`,
`app/src/test/java/app/opentv/GuideMetricsTest.kt`

**Step 1 - tests first.** Add, to `GuideMetricsTest`, assertions pinning each new value on
`GuideMetrics.TIVIMATE_PARITY`, and one assertion pinning the relationship instead of the two
numbers:

- `channelNameMarginEnd` == `6.dp`
- `channelNameMaxLines` == `1`
- `statusIconPrimarySize` == `20.dp`, `statusIconPrimaryMarginEnd` == `6.dp`
- `statusIconSecondarySize` == `16.dp`, `statusIconSecondaryMarginEnd` == `8.dp`,
  `statusIconSecondaryAlpha` == `0.6f`
- `statusIconTertiarySize` == `18.dp`, `statusIconTertiaryMarginEnd` == `8.dp`
- `headerClockHeight` == `29.dp`
- `headerClockMarginStart` == `16.dp`, `headerClockMarginEnd` == `8.dp`,
  `headerClockMarginTop` == `12.dp`
- `headerDividerHeight` == `1.dp`
- `gridMarginTop` == `2.dp`, `gridPaddingBottom` == `8.dp`
- the band relationship: `headerClockMarginTop + headerClockHeight == rowHeight`, with a comment
  saying the reference's clock band is exactly one channel-row band (12dip + 29dip = 41dip), which
  is why the clock lines up with the first row in the capture.

Run the suite; every new assertion fails to compile. That is the red state.

**Step 2 - add the fields.** Add each field to `GuideMetrics` with a KDoc line naming the reference
source, in the reference's own words where they are terse (e.g. `marginStart=16.0dip`,
`alpha=0.6`), and set the values in `TIVIMATE_PARITY`. Keep the existing grouping: geometry first,
then type.

**Step 3 - sweep the metrics nothing reads.** Run
`grep -rn "timeHeaderHeight\|badgeHeight\|focusRingWidth\|nowLineWidth\|replayMarkerSize\|reminderBadgeSize\|favouriteStarSize\|logoSize" app/src`.
For each name whose only hits are its declaration in `GuideMetrics.kt` and `GuideMetricsTest.kt`,
delete the field and its test. Known expectation from the first pass: `timeHeaderHeight` (`25.dp`)
is read by nothing and corresponds to no value in the reference at all - G13 supersedes it.
`logoSize` has one consumer (`ChannelListRow`) and stays.

If any of them turns out to have a consumer, leave it and note it in the delta log instead. Do not
delete a metric that something reads.

**Step 4 - green.** `.\gradlew.bat :app:testDebugUnitTest` passes with the new assertions.

**Done when:** the new values are pinned, the band relationship holds, and no field in the metrics
object is unread.

---

## Task 2: the channel name - one line, in the reference's face, with its own marquee

**Files:** `app/src/main/java/app/opentv/ui/channels/GuideGrid.kt` (`GuideRow`, the name at ~line
1739), tests in `GuideRenderParityTest.kt`

**Reference:** `0x7f0e008e` `0x7f0b03db` - `textSize=14.0sp`, `fontFamily=sans-serif-medium`,
`singleLine=true`, `ellipsize=marquee`, `marqueeRepeatLimit=1`, `layout_marginStart=10.0dip`,
`layout_marginEnd=6.0dip`. The base appearance it inherits (`0x7f14030e`) is `sans-serif` with the
text colour `#ffffffff`; `sans-serif-medium` is the layout's own override.

**Step 1 - tests first,** in `GuideRenderParityTest`:

- the name reads `metrics.channelNameMaxLines` and `metrics.channelNameMarginEnd`
- `GuideGrid.kt` contains `basicMarquee`
- `GuideGrid.kt` no longer contains a two-line name: assert the render has no
  `maxLines = 2` anywhere (the name was the last one), with a comment naming what was removed

Run; fail.

**Step 2 - implement.** In the name block:

- wrap the name in a `Box(Modifier.weight(1f).padding(end = metrics.channelNameMarginEnd))` so the
  text has a bounded width for the marquee to run against, and the column's trailing icons keep
  their place
- `maxLines = metrics.channelNameMaxLines`, `softWrap = false`
- `Modifier.basicMarquee(iterations = 1)` on the `Text` - `iterations = 1` is the reference's
  `marqueeRepeatLimit="1"`
- drop `overflow = TextOverflow.Ellipsis`; `basicMarquee` measures unconstrained and translates, so
  an ellipsis would defeat it
- `fontWeight = FontWeight.Medium` **always**. The reference sets `sans-serif-medium` and shows
  focus by the row plate's colour, not by weight, so the `Bold` on highlight goes

**Step 3 - green,** then on-device: a long name (`FOX BUSINESS NETWORK`) must run once and stop; a
short one must sit still. That is the difference from the old two-line wrap, and it is visible
without measuring anything.

**Done when:** the name is one line in `sans-serif-medium`, scrolls once when it overflows, and
stops at the reference's 6dip end margin.

---

## Task 3: the channel column's three status icons

**Files:** `GuideGrid.kt` (`GuideRow`, the icons at ~lines 1754-1787), tests in
`GuideMetricsTest.kt` and `GuideRenderParityTest.kt`

**Reference:** `0x7f0e008e` - `0x7f0b022a` `20 x 20dip` marginEnd `6dip`; `0x7f0b0219`
`16 x 16dip` `alpha=0.6` marginEnd `8dip`; `0x7f0b0230` `18 x 18dip` marginEnd `8dip`. All three are
trailing elements of the channel column, after the name.

OpenTV already renders three status icons there (playing, catch-up, favourite) at hard-coded
`15dp`, `14dp`, `14dp`. The mirror is the geometry and the one alpha, not the vocabulary: the
reference's slots take OpenTV's icons in the order both already use.

| Slot | Reference | OpenTV icon |
|---|---|---|
| primary | `20dp`, marginEnd `6dp` | `Icons.Default.PlayArrow`, shown while the row is the playing channel |
| secondary | `16dp`, `alpha 0.6`, marginEnd `8dp` | `Icons.Filled.History`, shown for channels with catch-up |
| tertiary | `18dp`, marginEnd `8dp` | `Icons.Filled.Star`, shown for favourites |

Which semantic belongs in which slot is a look decision that cannot be read out of the decode - the
icons' own drawables are anonymous in it. Sizes largest-to-smallest following the reference's slot
order, with OpenTV's render order unchanged, is the mapping to confirm on the device in Step 4. If
the catch-up mark looks wrong at `16dp @0.6`, swap the semantics between the secondary and tertiary
slots rather than changing the sizes: those are the reference's numbers.

**Step 1 - tests first.** `GuideMetricsTest`: the six new values plus the alpha (they are Task 1's
fields; assert them here if Task 1 did not). `GuideRenderParityTest`: each `metrics.statusIcon*` is
referenced by `GuideGrid.kt`, and no `Modifier.size(15.dp)` / `Modifier.size(14.dp)` remains in the
channel column.

**Step 2 - implement.** Replace the three icon blocks' literals with the metrics, and put
`Modifier.alpha(metrics.statusIconSecondaryAlpha)` on the secondary. The `Spacer(Modifier.width(4.dp))`
between them goes: the reference spaces its trailing icons with their own end margins
(`6dip`/`8dip`), not with inserted spacers.

**Step 3 - green.**

**Step 4 - on-device look.** Confirm against `parity-10.png` that the catch-up mark reads as
secondary and that nothing collides with the name.

**Done when:** the three icons are the reference's sizes, margins and alpha, driven by metrics.

---

## Task 4: the header band, the header divider, and the grid's own spacing

**Files:** `GuideGrid.kt` (`TimeHeader` lines 1489-1601, the guide `LazyColumn`),
`app/src/main/java/app/opentv/ui/theme/OpenTvPalette.kt`, tests in `GuideMetricsTest.kt` and
`GuideRenderParityTest.kt`

**Reference:** `0x7f0e0058` - the clock `0x7f0b03a6` is `29dip` tall with marginTop `12dip`,
marginStart `16dip`; the divider `0x7f0b0140` is `1dip` of `#20ffffff` spanning the full width; the
grid `0x7f0b0430` has marginTop `2dip` and paddingBottom `8dip`.

The clock is already `16sp` (shipped). What is not mirrored: the header is a `38dp` band with the
clock and OpenTV's EPG stamp stacked inside it, padded `12dp` at both ends.

**Step 1 - tests first.** `GuideRenderParityTest`: `TimeHeader` reads `metrics.headerClockHeight`,
`metrics.headerClockMarginStart`, `metrics.headerClockMarginEnd`, and the header renders
`metrics.headerDividerHeight`; assert no literal `.height(38.dp)` in the header.
`GuideMetricsTest` already pins the values (Task 1).

**Step 2 - restructure the header** into a `Column`:

1. a `Row` of exactly `metrics.headerClockHeight` (`29dp`), vertically centred, holding:
   - the clock, in a box `metrics.channelColumnWidth` wide with padding
     `start = metrics.headerClockMarginStart`, `end = metrics.headerClockMarginEnd`, at
     `metrics.headerClockSp` in `sans-serif-medium`
   - the timeline box, `horizontalScroll(scroll)`, whose label cells keep
     `.height(metrics.headerClockHeight)`
2. OpenTV's EPG stamp (and the jump-to-live control) on its own line below, `timeRulerSp`, only when
   the stamp is present. TiviMate has no such line; ours stays because it is the only place the
   guide reports EPG freshness, and it now sits outside the band instead of inside it.
3. the `1dip` divider at the header's bottom edge, `#20ffffff`

The divider's colour goes on the palette as `headerDivider`, and `TivimateParityPalette` sets it to
`Color(0x20FFFFFF)`. Give the property a default on the constructor so the other palettes compile
untouched, and note in its KDoc that it is the reference's `0x7f0b0140`.

Note on its state: the reference ships this divider `gone`, which only means its preview render
hides it. The mirror this plan takes is the geometry and the colour, rendered as the header's bottom
edge. If it reads wrong on the box, hide it - but then hide it to a metric, not to a deleted element.

**Step 3 - the grid's own spacing.** The guide's rows `LazyColumn` takes
`contentPadding = PaddingValues(bottom = metrics.gridPaddingBottom)` and `Modifier.padding(top = metrics.gridMarginTop)`
between the header and the first row. `clipToPadding` is already false in Compose's default for
`LazyColumn` content padding, which is the reference's `clipToPadding="false"`.

**Step 4 - green,** then on-device: the clock and the `2:00 PM` label must share a line, both above
the first row, with the first row's top edge exactly one band below the detail panel.

**Done when:** the clock band is 29dp at the reference's margins, the ruler shares it, the divider is
the header's bottom edge, and the grid's 2dip/8dip spacing is in place.

---

## Task 5: verification

**Step 1.** `.\gradlew.bat :app:testDebugUnitTest` - the whole suite, not just the new tests. The
first pass ended at 323 tests / 0 failures; this plan adds to that count.

**Step 2.** `.\gradlew.bat :app:assembleDebug --console=plain` and confirm
`app/build/outputs/apk/debug/app-debug.apk` is newer than the build.

**Step 3.** Install the debug APK on `192.168.1.128:5555`, capture the guide to
`docs/screenshots/parity-10.png`, and confirm the five things this plan changed: one-line marquee
name, the three icon slots, the 29dp header band with the ruler on the same line, the divider,
the grid's spacing. Keep `parity-07.png` to `parity-09.png` as the before set.

**Step 4.** Update the two documents that record what is left:
- `docs/superpowers/plans/2026-10-11-guide-metrics-table.md` - the new values with their provenance
- `docs/superpowers/plans/2026-10-11-guide-parity-delta-log.md` - the results, and the deltas this
  plan closes (channel-name lines and marquee; dead metrics) versus what is still open (G3/G17/G18/G19,
  the ruler fade, the assets)

**Done when:** the suite is green, the APK builds, the capture shows the five changes, and both
documents name the remaining deltas.

---

## What the device render changed (2026-10-16)

The three calls this plan left open were settled by measuring the reference on the box itself, and
one of them went against the plan.

1. **The header divider** - geometry pinned, `headerDividerVisible = false`, palette entry
   `headerDivider = 0x20FFFFFF`. The reference ships it `gone` and no line shows in the capture, so
   drawing it would have been the less faithful choice. One flag to flip.
2. **The icon semantics - reversed.** The plan mapped catch-up to the 20dip slot by how often it
   shows. The capture says otherwise: the reference draws the *playing* mark in the 20dip slot
   (accent tinted, leftmost, 6dip before the clock - 18x22px of ink against the clock's 27x24px),
   the catch-up clock in the quiet 16dip @0.6 slot, and the chain runs play -> catch-up, not
   catch-up -> play. The render is the authority on which mark lives where, so the render won.
3. **Deleting the dead metrics** - done. `timeHeaderHeight`, `badgeHeight`, `focusRingWidth`,
   `nowLineWidth`, `liveBadgeSize` and `channelCallsignSp` had no consumer and are gone, pinned by a
   test so bringing one back is deliberate. `channelColumnPaddingEnd` went too: it double-counted the
   name's own `marginEnd 6dip`.

## Still open (needs the maintainer)

1. **The channel number's size.** The layout declares 24sp; the reference's own code draws it at
   ~16sp (digits 22px beside the 14sp name's 21px, not the 34px 24sp gives). This pass mirrors the
   drawn size because that is what the screen shows. If the *declaration* is what is wanted instead,
   it is one metric line back to 24sp.
2. **The row pitch.** The same render puts the reference's rows 78px (39dp) apart, where this plan
   and the decoder both say 41dip band + 3dp gutter = 44dp - which is what OpenTV builds. Moving the
   pitch means re-deriving the row's internals against the render, so it is left alone until asked
   for.
3. **The clock's colour.** The render's header clock is a soft `#90CAF9`; OpenTV paints it with its
   own accent. That is a theme decision rather than a geometry one.
