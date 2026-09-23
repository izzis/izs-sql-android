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
| 1 | Release keystore (`alias: sqlclient`) | `/home/al/.keystore/sql-client-release.jks` |
| 2 | Keystore password | `/home/al/.keystore/sql-client-release.pw` and repo-root `keystore.properties` (gitignored) |

> Both are excluded from git (`.gitignore`: `*.jks`, `keystore.properties`).
> Only the build wiring in `app/build.gradle.kts` is versioned — never the keys.

Fingerprint (SHA-256): `28:F9:21:4A:99:CE:81:4A:1C:58:3C:7C:B4:B4:0B:E2:30:AC:CF:18:69:9F:09:C3:16:97:0E:C2:3F:68:08:7E`

## Building a release APK locally

```bash
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

Requires repo-root `keystore.properties` (gitignored):

```properties
storeFile=/home/al/.keystore/sql-client-release.jks
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
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 /home/al/.keystore/sql-client-release.jks` |
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
