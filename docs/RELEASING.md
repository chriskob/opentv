# Releasing

Releases are cut by pushing a tag. GitHub Actions does the rest.

```bash
git tag v0.1.0
git push origin v0.1.0
```

`.github/workflows/release.yml` runs the tests, assembles the release APK and attaches it to a
GitHub release.

## Signing

The release APK is currently **unsigned**, so Android will refuse to install it as an upgrade
over a differently-signed build. Before the first public release, set up signing:

1. Generate a keystore and keep it somewhere safe and backed up. If it is lost, no future
   build can upgrade an installed copy — every user has to uninstall and reinstall, losing
   their settings.
2. Add these repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
   `KEY_PASSWORD`.
3. Add a `signingConfigs` block to `app/build.gradle.kts` reading them from the environment,
   and wire it to the `release` build type.

**The keystore must belong to the project, not to one person.** A single maintainer holding
the only copy is the same single point of failure this project exists to avoid — if they go
quiet, nobody can ship an upgrade. Share it among at least two maintainers.

## Version numbers

Bump `versionCode` and `versionName` in `app/build.gradle.kts`. `versionCode` must increase on
every release; Android uses it, not `versionName`, to decide what counts as an upgrade.
