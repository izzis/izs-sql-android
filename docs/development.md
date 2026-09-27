# Development — izs SQL Android

> Back to [README](../README.md) · Setup: [setup.md](setup.md) · Architecture: [architecture.md](architecture.md)

## Workflow

```bash
# Lint/format (no enforced formatter — keep Compose idiomatic)
./gradlew lint

# Run on device/emulator with live code changes via Android Studio's Apply Changes
# or CLI:
./gradlew installDebug && adb shell am start -n id.web.izs.sqlclient/.MainActivity

# Logs (DB/SSH categories)
adb logcat -s MariaDbConn SshTunnelManager CredentialStore

# Clean
./gradlew clean

# APKs
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (minified)
```

Tips:

- `DatabaseBrowserScreen` is the right place to debug privilege filtering — set a breakpoint in `PrivilegeResolver.filter*()` or watch `visibleDatabases`.
- `MariaDbConnectionManager` logs every `executeQuery` failure; map `JSchException: Auth fail` etc. to user strings in `ConnectionRepository` if you add new error branches.
- Room is currently `fallbackToDestructiveMigration(dropAllTables = true)` — bumping `AppDatabase.version` without a migration will drop `connection_profiles` / `query_history` on upgrade (fine for alpha).

## Testing

JVM unit tests run on every push/PR via `.github/workflows/ci.yml` (`JDK 17` + `./gradlew test` + `assembleDebug`).

```bash
./gradlew test                # JVM unit tests (SqlUtil + query logic)
./gradlew :app:testDebugUnitTest --tests "id.web.izs.sqlclient.util.SqlUtilTest"  # single class
./gradlew connectedAndroidTest  # on-device Compose/Espresso (scaffold only)
```

Current coverage (gate for all query features):
- `util/SqlUtilTest.kt` — 22 tests: `stripLeading` (plain/line/block/mixed), `isWriteQuery` (singles, reads, default-deny unknown-is-write incl. `REPLACE/CALL/SET/BEGIN/KILL/LOAD`, `FLUSH`-is-safe, `SELECT INTO OUTFILE/FOR UPDATE` are write, `INSERTINTO` typo is write, comments leading, multi `SELECT 1; DROP`, case-insensitive), `shouldApplyLimit` (SELECT true, WRITE/SHOW false, multi-write false), `buildLimitedSql` (SELECT without LIMIT -> +200, WRITE/SHOW never +LIMIT, bulk `UPDATE 10k` stays unlimited, `preview == general_log`). **CI fails if `isWriteQuery`/`shouldApplyLimit` regresses.**
- CI workflow `ci.yml` is the gate: any new feature that sends SQL to server (new `WRITE` prefix, new `SELECT` guard, new builder) **must** add/update `SqlUtilTest` (or new `*Test.kt`) and keep `gradlew test` green.
- `util/QueryLogTest.kt` — 7 tests: `QueryLogEntry` default has no error, `withQueryError` marks the matching line (last one wins for repeated SQL), unknown SQL / empty log are no-ops, previous message is replaced, input list is not mutated. **Fails if error-to-log-line matching regresses.**

Planned: `PrivilegeResolver.parseGrants`, `SshTunnelManager` lifecycle, `DataEditorViewModel` staging `buildPendingSqls` grouping. Done: `CredentialCrypto` AES-GCM round-trip/wrong-key/tamper tests (`CredentialCryptoTest`, 7 cases) after the Keystore migration.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `enableEdgeToEdge` content under nav bar | `Scaffold.bottomBar` without `WindowInsets.navigationBars` | `CurrentQueryBar` already applies `windowInsetsPadding(navigationBars)` — check you did not add a second `navigationBarsPadding` in the child screen |
| Query log collapsed text cut off | `CurrentQueryBar` header layout issue | The collapsed state now shows just "Query log (N)" without preview text — verify `weight(1f)` is on the Text composable |
| First open of Databases is empty | `hasLoaded*` cache or stale `visibleDatabases` | Hit **Refresh** in the top bar; verify `PrivilegeResolver.loadGrants` succeeded (logcat `MariaDbConn/JDBC`) |
| `JSchException: Auth fail` on SSH | Password/key/passphrase mismatch | Check password vs key mode; pass `sshPassphrase` when the key is encrypted; verify `sshHost:22` reachability with `Test SSH` |
| `Password not found. Save the connection first.` on connect | Credentials not yet saved to `CredentialStore` | Tap **Save** in the editor before **Connect** (or re-save after clearing app data) |
| Writes still execute while locked | Route not wired to `sessionLocked` | `NavGraph` must pass `isLocked = sessionLocked.collectAsState().value` + `onToggleLock = { setSessionLocked(!value) }` to that screen's `AppTopBar(showLock=true, …)` |
| Writes not appearing in `CurrentQueryBar` | Write VM missed `_currentQuery.value = _currentQuery.value + QueryLogEntry(sql)` | Every write/read path must append to `_currentQuery` before `executeQuery`, including background reads (`SHOW FULL COLUMNS`, `SHOW INDEX`, `SHOW CREATE TABLE`, `information_schema`, `SHOW GRANTS`, `FLUSH PRIVILEGES`) |
| Failed query not shown red in `CurrentQueryBar` | The failing SQL was never logged, or the logged string differs from the string actually sent | Failures are matched by **exact SQL text** via `MariaDbConnectionManager.queryFailures` → `withQueryError(sql, msg)`; log the very same string you pass to `executeQuery` (the message itself is shown verbatim, never rewritten) |
| Staged (unsent) line looks executed / survives Discard | Log line built by hand (`listOf(QueryLogEntry(sql))`) instead of the staged helpers, or Discard forgot to prune | Stage → `withStaged(sqls)`, send → `markExecuted(sql)` (in place, before `executeQuery`), discard → `withoutStaged()`; never inject a placeholder line for a query that did not run |
| Query log wiped by Refresh / stale after Back | Someone re-introduced a clear inside a refresh path, or a screen misses its entry `resetQueryLog()` | Contract: clear **only** in the screen's entry `LaunchedEffect` (route+args) or the bar's clear button — refresh appends; every screen with `CurrentQueryBar` needs both `resetQueryLog()` on entry and `onClear` |
| `UPDATE` affects only 200 rows | Old `LIMIT` guard injected `LIMIT 200` into all queries without `LIMIT` | Fixed: `SqlUtil.shouldApplyLimit` injects `LIMIT` only for `SELECT` (see `SqlUtil.kt:64`), `WRITE` never limited. `SHOW` relies on driver `maxRows=1001` |
| `UPDATE ... WHERE pk = ...` affects 0 rows | Wrong `pkColumnIndex` / `autoIncrementColumn` | `loadColumnInfo` parses `SHOW FULL COLUMNS FROM` — ensure the table has a PK / `auto_increment` |

## Known issues & roadmap

**Alpha gaps (shipped as-is, PRs welcome):**

- Room `v3` `fallbackToDestructiveMigration` — no migration planned (bumping version will drop `connection_profiles` / `query_history`; acceptable for internal alpha, reinstall required).
- SSH `StrictHostKeyChecking=no`; no known-hosts UI.
- `DatabaseTree` column/index lazy loads are not independently cancellable; rapid expand/collapse can briefly show stale children.
- No instrumentation / integration tests beyond scaffolding.

**Near-term:**
- Known-hosts handling for SSH + CA/key-file picker.
- Pagination polish: stable `LIMIT/OFFSET` footer + total-row-count (`COUNT(*)`) opt-in.
- `PrivilegeResolver` cache invalidation hook after `GRANT/REVOKE/FLUSH PRIVILEGES` (today `loadGrants` must be called explicitly per destination).
- Compose previews / screenshot tests for `Browser`, `InlineDataEditorScreen.DataGrid`, and privilege detail.

## TODO — Unimplemented features

> Moved to issue [#3](https://github.com/izzis/izs-sql-android/issues/3) to keep the roadmap persistent and linkable. Contents: Export UI wiring, Insert Row trigger, Index Management route decision, and deferred multi-session support. (History Panel already ✅ done.)

## Contributing

PRs against `main` welcome. For larger changes please open an issue first. Commit messages: imperative, short subject + body with context — e.g. `fix(browser): privilege-filter sidebar cache bypass`.
