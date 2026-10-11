# Guide parity verification and delta log

- Plan: `docs/superpowers/plans/2026-10-11-guide-tivimate-parity.md`
- Spec: `docs/superpowers/specs/2026-10-11-guide-tivimate-parity-design.md`
- Measured table: `docs/superpowers/plans/2026-10-11-guide-metrics-table.md`
- Range reviewed: `ee95105..HEAD` (9 commits: 2 docs + 7 code).

## Automated results

| Check | Command | Result |
|---|---|---|
| JVM suite | `.\gradlew.bat :app:testDebugUnitTest` | **306 tests, 0 failures** (baseline before this work: 286) |
| Guide palette invariants | `GuidePaletteContrastTest` | 5/5 — cursor-on-cell, text-on-cell, text-on-background, secondary text, palette ≠ Slate |
| Guide geometry | `GuideMetricsTest` | 7/7 — minute→width mapping, grid-boundary ending, pinned defaults |
| Metrics plumbing | `GuideRenderParityTest` | 3/3 — old constants gone, `LocalGuideMetrics` read, local `widthFor` deleted |
| Cell affordances | `GuideAffordanceMatrixTest` | 5/5 — full past/archive/toggle matrix |
| Debug APK | `.\gradlew.bat :app:assembleDebug` | **BUILD SUCCESSFUL** — `app/build/outputs/apk/debug/app-debug.apk`, 33,760,182 bytes |

New tests since baseline: 20 (7 metrics + 5 palette + 3 plumbing + 5 affordances). No pre-existing test
was modified or deleted.

### Environment note (not caused by this work)

`dexBuilderDebug` emitted `WARNING: D8: Unexpected error during rewriting of Kotlin metadata` for
`app.opentv.ui.vod.VodScreensKt$sortedSeries...`, and one incremental dex run failed with it. The
project's own `build_apk_run.log` (2026-10-05, before this work) shows the same Kotlin/R8 metadata
skew — Kotlin 2.2.20 against AGP 8.7.3's R8 — as a warning. The failing class is VOD code this work
never touches. A re-run of `assembleDebug` completed successfully and produced the APK above. If
dexing starts failing hard, the fix is a Kotlin/AGP version bump, not a guide change.

## Visual delta — what still needs a device and a reference

The parity numbers that could be measured from the APK are wired and pinned (see the measured table).
What is **not** yet verified is pixel agreement against TiviMate, because that needs a TiviMate guide
screenshot (1920×1080) supplied by the maintainer. The reference is measurement-only: it is not
committed and none of TiviMate's artwork or fonts were copied.

On-device checklist, in the order a viewer would hit it:

1. Guide opens in the Parity Guide palette (navy surfaces, amber cursor) regardless of the app theme.
2. Row height, channel column width, logo size and number width match the metrics (48/296/32/40dp).
3. 30-minute columns are even across the full timeline; the now-line sits exactly on the current minute.
4. Programme cells: 14sp title, 12sp meta, 6dp corners, NEW/LIVE badges at the metric size.
5. Cursor on a past (dimmed) cell: fill dimmed, ring and text still fully opaque.
6. Scrub back in time → the header offers **Jump to Live**; selecting it returns focus and viewport to now.
7. A past programme on an archive-capable channel opens the archive stream; on a channel without
   archive it does nothing (no error), unless *show previous programmes without catch-up* is on, in
   which case it is shown but still inert.
8. `docs/TESTING.md` manual checks: B (guide feeds), C (now/next), E (no wrong channel matches),
   F (state survives), G (fast switching).

## Known gaps at this commit

1. **Element→dimension mapping is derived, not measured** (see the Ruling in the ledger for Task 1):
   `rowHeight` 48, `channelColumnWidth` 296, `halfHourWidth` 240 and the badge/icon sizes are the
   best mapping of a measured value set whose resource names are obfuscated. Expect a few dp of
   correction once the reference screenshot is used; each is a one-line metrics change plus a test
   value, and `GuideRenderParityTest` prevents a metric edit from leaving a second source of truth.
2. **`chromeAlpha` is 0.7f** (TiviMate's stated default) — a one-line change if the maintainer prefers
   the previous opaque panels.
3. The **"show previous programmes without catch-up"** setting has no UI row yet; the value is
   stored and plumbed (default off). Adding the toggle is a settings-screen row, out of this scope.
