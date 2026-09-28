# Release Builds

Shareable APKs are produced via the `release` build type: non-debuggable,
R8-minified (`isMinifyEnabled` + `isShrinkResources`), and signed so they can
be installed directly. Same scheme as `izs-ssh-android`.

## Required artifacts (back these up)

Two items are required to ship updates. If either is lost, already
distributed APKs **cannot** be updated — recipients must uninstall and
reinstall (losing local data).

| # | Artifact | Location |
|---|----------|----------|
| 1 | Release keystore (`alias: sqlclient`) | `~/.keystore/sql-client-release.jks` |
| 2 | Keystore password | `~/.keystore/sql-client-release.pw` and repo-root `keystore.properties` (gitignored) |

> Both are excluded from git (`.gitignore`: `*.jks`, `keystore.properties`).
> Only the build wiring in `app/build.gradle.kts` is versioned — never the keys.

## Building a release APK locally

```bash
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

Requires repo-root `keystore.properties` (gitignored):

```properties
storeFile=~/.keystore/sql-client-release.jks
storePassword=<password>
keyAlias=sqlclient
keyPassword=<password>
```

Without that file the APK is assembled **unsigned** and cannot be installed
by recipients.

## Keystore backup on GitHub

The keystore and its credentials are also stored as repo secrets
(`gh secret list -R izzis/izs-sql-android`) so they survive local disk loss:

| Secret | Value |
|--------|-------|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 ~/.keystore/sql-client-release.jks` |
| `KEYSTORE_PASSWORD` | contents of `sql-client-release.pw` |
| `KEY_ALIAS` | `sqlclient` |
| `KEY_PASSWORD` | contents of `sql-client-release.pw` |

## Release via GitHub (manual only)

Workflow `.github/workflows/build-apk.yml` runs **only** on manual dispatch
— never on push. To ship: Actions tab -> "Build APK release" -> Run
workflow, enter tag `v1.0.0`. It derives `versionName` from the tag, uses
`github.run_number` as `versionCode`, signs with the keystore restored from
the secrets below, uploads `izs-sql-client-<tag>.apk`, and creates the
GitHub Release.

## Debug builds are unaffected

This configuration applies to `assembleRelease` only. Daily development
is unchanged:

```bash
./gradlew installDebug
```
