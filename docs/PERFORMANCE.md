# Performance

What OpenTV does about the things a viewer actually notices: how fast the app opens, how smoothly the
guide scrolls, how quickly a channel tunes, and whether the picture moves evenly.

## Baseline profiles

A baseline profile is a list of the methods the app runs during startup and ordinary use. On device,
ART pre-compiles exactly those, so the first frame after launch is not spent interpreting Compose's and
the guide's own code.

Library profiles — Compose, AndroidX, Media3, Coil — are merged into the APK by the Android Gradle
plugin automatically; that is why `assets/dexopt/baseline.prof` exists in builds from before this was
set up. What the `:baselineprofile` module adds is a profile for **OpenTV's own** classes, which is
where the guide's opening and scrolling live.

### Recording it

    ./gradlew :app:generateBaselineProfile

Run it against a device or emulator (CI does this in the [Baseline profile](/.github/workflows/baselineprofile.yml)
workflow, which starts one for the occasion). The result is written to
`app/src/release/generated/baselineProfiles/baseline-prof.txt` — **commit it**, because every release
build then carries it.

What the recorded profile *covers* depends on what the app has. On a bare emulator the app opens its
onboarding screen, so the profile covers startup and the source-setup path. Run it on a box that
already has a playlist and the D-pad presses in `BaselineProfileGenerator` walk the real guide, which
is the coverage worth having.

### Judging it on a sideloaded install

The Play Store compiles an app's profile at install time; a sideloaded APK does not get that. OpenTV
users get theirs from `ProfileInstaller` on first run, and ART then compiles during an idle
maintenance window — so a new profile can land **a day later, or after a reboot**, rather than the
moment the update installs. That is expected, not a failure:

    adb shell cmd package compile -m speed-profile -f app.opentv

forces it immediately, which is the honest way to judge a profile rather than waiting overnight.

## Frame rate

`Settings → Playback → Match display refresh rate` asks the TV for a mode the stream's frame rate
divides into evenly — 50 Hz for a 50 fps channel, 24 Hz for a film — because a 25 fps stream on a 60 Hz
panel is shown on a 2, 2, 3 pattern of refreshes, and the third is a visible hitch once a second. See
`RefreshRateMatcher` for the rule and `RefreshRateMatcherTest` for the cases it must refuse.

Off by default: switching mode re-syncs the HDMI link and blanks the screen for a beat, and a TV that
handles that badly looks worse than one that judders.
