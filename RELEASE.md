# Release Builds

Shareable APKs are produced via the `release` build type: non-debuggable,
R8-minified, and signed so they can be installed directly.

## Required artifacts (back these up)

Two items are required to ship updates. If either is lost, already
distributed APKs **cannot** be updated — recipients must uninstall and
reinstall (losing local data).

| # | Artifact | Location |
|---|----------|----------|
| 1 | Release keystore (`alias: sqlclient`) | `~/.keystore/sql-client-release.jks` |
| 2 | Keystore password | `~/.keystore/sql-client-release.pw` and `local.properties` (`release.storePassword` / `release.keyPassword`) |

> Both are excluded from git (`.gitignore`). Only the build wiring in
> `app/build.gradle.kts` is versioned — never the keys.

## Building a release APK

```bash
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

Requires the keystore and password in the locations above. Alternatively,
provide the password via environment:

```bash
SQL_RELEASE_STORE_PASSWORD='<password>' ./gradlew assembleRelease
```

Without a password the APK is assembled **unsigned** and cannot be
installed by recipients.

## Debug builds are unaffected

This configuration applies to `assembleRelease` only. Daily development
is unchanged:

```bash
./gradlew installDebug
```
