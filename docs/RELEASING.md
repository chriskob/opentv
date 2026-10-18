# Releasing

Releases are cut by pushing a tag. GitHub Actions does the rest.

```bash
git tag v0.1.0
git push origin v0.1.0
```

`.github/workflows/release.yml` runs the tests, assembles the release APK and attaches it to a
GitHub release.

## Signing

Release builds are signed with **the project key** — keystore `upload.jks`, alias `upload`,
certificate `CN=OpenTV`, SHA-256
`F1:C2:6E:A3:B7:AD:F2:0C:7C:E3:BD:50:54:C8:99:62:60:46:B2:F5:0C:FA:0D:B0:5A:D3:CF:8F:00:70:55:75`.

Where the pieces live (all outside this repo, on the maintainer's machine):

| Piece | Location |
| --- | --- |
| Keystore | `C:\Users\buick\opentv-keys\upload.jks` |
| Passwords | `C:\Users\buick\opentv-keys\PASSWORDS.txt` (`path=`, `storepass=`, `keypass=`, `alias=`) |
| Base64 for CI | `C:\Users\buick\opentv-keys\KEYSTORE_BASE64.txt` |
| Local builds | `deploy-fast.ps1` reads `PASSWORDS.txt` and exports the four variables |
| CI builds | repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` |

The repository secrets are set and hold this key: every release published so far verifies
against the fingerprint above, so every existing install updates in place.

### Rebuilding the signing setup

This is already done for `chriskob/opentv`. If it ever has to be redone — a new repository, a
new machine — this is the recipe:

1. Generate a keystore:

   ```bash
   keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 4096 \
     -validity 10000 -alias upload
   ```

2. Add these repository secrets under **Settings → Secrets and variables → Actions**:
   `KEYSTORE_BASE64` (the output of `base64 -i upload.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
   `KEY_PASSWORD`. The values are the ones in `PASSWORDS.txt` beside the keystore.

3. That is all — `release.yml` and `build.gradle.kts` already pick them up, and the release
   notes report the `Signing:` line above.

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

This is worth stating because it hid a real bug for a long time, back when releases really were
debug-signed: a release signed with *the runner's* debug key is signed differently by every
runner, so every release was signed differently and none could install over the one before it.
No amount of fixing the in-app updater could have helped — the installer at the bottom was
refusing on signature alone.

Two consequences worth remembering:

- **A missing keystore is not a cosmetic CI wart.** It silently disables in-place updates for
  everyone.
- **The release notes carry a `Signing:` line** saying which key signed the APK. Read it before
  assuming an update bug is in the app.

### Verifying a published release

```bash
apksigner verify --print-certs opentv.apk
```

The printed SHA-256 must be the project fingerprint above. A different digest — or a release
noted with the debug key — cannot install over an existing app; remove the release and re-issue
it before anyone downloads it. The in-app updater is stricter still: it only offers an APK from
`github.com` hosts that carries a `sha256:` digest, and only when the tag is a strictly newer
version than the installed one.

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

## The bundled torrent engine

The 64 MB TorrServer binary is deliberately not committed to git. As part of a release there is
nothing to do by hand: the workflow runs `sh ./fetch-torrserver.sh`, which downloads the pinned
upstream asset and refuses to continue unless its SHA-256 matches, and then verifies the file is
actually inside the APK before anything is published. A fresh clone runs the same script once.
`docs/TORRENTS.md` has the pin, the checksum and how to move to a newer engine.
