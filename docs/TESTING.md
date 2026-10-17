# Testing OpenTV

There is no QA department; there is you, your provider and your telly. This page makes that
count. The app logs everything it does, so a bug report with a log attached is usually a fix
within the day — and a "works for me" without one is usually nothing.

## The quick loop (with a Mac/PC on the same network)

```bash
.\gradlew.bat :app:installDebug   # reinstalls the debug build on the connected device (adb)
```

## What to test after any change

| # | Check | Pass looks like |
|---|-------|-----------------|
| A | Quality grouping | One row per logical channel; "N qualities" badge on grouped ones |
| B | Guide feeds | Gear → enable a guide for your country → "matched N of M" reported |
| C | Now/Next | Matched channels show program + progress bar, not "No guide information" |
| D | Playback | Channel plays; quality switch top-right; switching mid-stream keeps playing |
| E | **Wrong matches** | No channel shows another channel's programs — the worst bug this app can have. Report the exact channel name |
| F | State survives | Favorites/hidden survive a refresh and an app restart |
| G | Fast switching | Holding channel-change never wedges playback (one tune when you stop) |

## Reading the log

Interesting lines and what they mean:

- `Matcher: N of M channels have a working guide` — the headline EPG number.
- `Feed 'X' failed: <reason>` — a guide source failed; the same text shows in Guide settings.
- `Source N: ... channels` — catalog sync result.
- `AndroidRuntime: FATAL` — a crash; everything above it in the log is the story.

## Performance baseline (speed-first plan, 2026-10-16)

Measured on the Onn 4K box (`192.168.1.128:5555`) with the release-like `perf` build
(`:app:installPerf`, package `app.opentv.debug`), resuming into the last channel:

- `am start -W` TotalTime: **714ms** (WaitTime 732ms)
- `OpenTV-Boot` marks: `activityCreated=+201ms`, `firstFrame=+632ms`,
  `playResolved=+758ms`, `sourcesLoaded:1=+859ms`, `playbackStarted=+2896ms`
- Guide surf / VOD open: no visible stall noted on this pass; re-measure after Task 2.

After Task 2 (same device, same `perf` build with the fix):

- `am start -W` TotalTime: **588ms** (WaitTime 607ms)
- `OpenTV-Boot` marks: `activityCreated=+117ms`, `firstFrame=+509ms`,
  `playResolved=+532ms`, `sourcesLoaded:1=+688ms`, `playbackStarted=+2303ms`
- Caveat: run-to-run variance (warm caches, network) explains part of the delta; no
  still-recording item was active, so the recording-open path itself was not timed. What the
  fix guarantees structurally — `GrowingRecordingDataSource.open()` never touches Room, and
  the locator is pre-registered from the already-loaded row — is pinned by
  `GrowingRecordingLocatorTest`, not by these timings.

Repeat with: force-stop, `logcat -c`, `am start -W`, wait ~18s, `logcat -d -s OpenTV-Boot`.

## Reporting

Open a GitHub issue with: device + Android version, what you did (checklist letter is
enough), what happened, and the relevant chunk of `testrun.log`. **Strip your provider's
URL/username/password first** — logs deliberately never print credentials, but check what
you paste anyway.
