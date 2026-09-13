# Releasing

Releases are cut by pushing a tag. GitHub Actions does the rest.

```bash
git tag v0.1.0
git push origin v0.1.0
```

`.github/workflows/release.yml` runs the tests, assembles the release APK and attaches it to a
GitHub release.

## Signing

**Right now, release builds are signed with the Android debug key.** This is deliberate, and
it is temporary.

An *unsigned* APK cannot be installed on Android at all — the installer rejects it before the
user sees anything, with an error that explains nothing. That would mean no one can test the
app until signing infrastructure exists. So `app/build.gradle.kts` falls back to the debug key
when no keystore is configured, and the result installs fine.

The cost, stated plainly: **when the project moves to a real keystore, Android will refuse to
upgrade over a debug-signed install.** Everyone testing today will have to uninstall and
reinstall, losing their settings. That is fine for a handful of early testers and completely
unacceptable once there are hundreds. **Set up real signing before announcing the project
publicly.**

### Setting up real signing

1. Generate a keystore:

   ```bash
   keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 4096 \
     -validity 10000 -alias opentv
   ```

2. Add these repository secrets under **Settings → Secrets and variables → Actions**:
   `KEYSTORE_BASE64` (the output of `base64 -i upload.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
   `KEY_PASSWORD`.

3. That is all — `release.yml` and `build.gradle.kts` already pick them up. The release notes
   will report `signed: yes` instead of `debug-key`.

**The keystore must belong to the project, not to one person.** A single maintainer holding
the only copy is the same single point of failure this project exists to avoid — if they go
quiet, nobody can ship an upgrade, and every user is stranded on whatever version they have.
Share it among at least two maintainers, and back it up somewhere that is not one laptop.

Losing the keystore is unrecoverable. There is no reset.

### Signing is what makes updates possible, not just installs

Android installs an update only when the new APK is signed with **the same key** as the install
it replaces. If it is not, the platform refuses with:

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match previously installed version
```

This is worth stating because it hid a real bug for a long time. A release workflow that falls
back to the debug key is signed with *the runner's* debug key, and every runner generates its
own — so every release was signed differently, and no release could ever be installed over the
one before it. No amount of fixing the in-app updater could have helped: the installer at the
bottom was refusing on signature alone.

Two consequences worth remembering:

- **A missing keystore is not a cosmetic CI wart.** It silently disables in-place updates for
  everyone.
- **`.github/workflows/release.yml` prints `signed: debug-key` in the release notes** when no
  keystore was configured. Read that line before assuming an update bug is in the app.

### When the key changes, ask before wiping

Switching keys forces a reinstall, and a reinstall normally means starting over. Before wiping a
device, check whether the data can be lifted off it first:

```bash
# 1. Which signature is installed? Compare with the key you are about to sign with.
adb shell dumpsys package <app.id> | grep -i signatures
# or, from the APK itself:
apksigner verify --print-certs <apk>

# 2. Debug/flavoured builds are `run-as`-able, so their data can be copied verbatim.
adb exec-out "run-as <app.id> tar -cf - -C /data/data <app.id>" > device-data.tar

# 3. Reinstall signed with the new key, then put the data back.
adb uninstall <app.id>
adb install <new-signed.apk>
adb push device-data.tar /data/local/tmp/ && \
  adb shell "run-as <app.id> tar -xf /data/local/tmp/device-data.tar -C /data/data"
```

A release build is not debuggable, so `run-as` will refuse — `adb backup` is the only
device-level option left, and it needs the on-screen confirmation tapped with the remote. Failing
both of those, the app's own sync is the way out: playlists and profile travel through NAS sync,
so they can be re-imported on the fresh install.

## Version numbers

Bump `versionCode` and `versionName` in `app/build.gradle.kts`. `versionCode` must increase on
every release; Android uses it, not `versionName`, to decide what counts as an upgrade.
