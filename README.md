# SQL Client — Android

Native Android SQL client for **MariaDB/MySQL** — browse databases/tables, run queries, edit data inline, manage users/privileges, and work through an **SSH tunnel**. Built with **Kotlin + Jetpack Compose (Material 3)**.

> **Status: alpha.** Core flows work; expect bugs, incomplete flows and rough edges. See [**Known issues & roadmap**](#known-issues--roadmap).

---

## Table of contents

- [Features](#features)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Project structure](#project-structure)
- [Architecture](#architecture)
- [Key concepts](#key-concepts)
- [Navigation & screens](#navigation--screens)
- [Security & credentials](#security--credentials)
- [Theming](#theming)
- [Development workflow](#development-workflow)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Known issues & roadmap](#known-issues--roadmap)
- [Contributing](#contributing)
- [AI session bootstrap](#ai-session-bootstrap)
- [License](#license)

---

## Features

- **Connection management** — create/edit/delete profiles, colored badges, read-only default per connection, `enableEdgeToEdge` UI.
- **Direct & SSH tunnel connections** — MariaDB JDBC 2.4.4 (`org.mariadb.jdbc:mariadb-java-client:2.4.4`) + JSch (`com.jcraft:jsch:0.1.55`) for `host:22` tunnelling (password or key + passphrase, `StrictHostKeyChecking=no`).
- **Database browser** — sidebar (`AppSidebar`/`DatabaseTree`) + main panel; database/table lists are **privilege-filtered** via `PrivilegeResolver` (`PrivilegeSet`), manual refresh, `windowInsets` cache.
- **Table inspection** — `SHOW TABLES` / `SHOW FULL COLUMNS FROM db.table` lazy-loaded on expand (columns/indexes), `SYSTEM_SCHEMAS` filtered out.
- **Query editor** — monospace editor with history tab, current-query bar at the bottom, syntax-agnostic (single `statement.execute(sql)`; no client-side parsing), 30 s timeout, read-only guard.
- **Inline data editor** — paginated `LIMIT/OFFSET` grid (limit presets 100/200/500/1000) with infinite scroll (`snapshotFlow` + `loadMore`), **quick WHERE filter** (`WHERE <raw>`) + column autocomplete chips, per-cell edit (dialog), batch staging (see below), row select/delete, query-timing in the status bar.
- **Batch write flow** — every write goes through **Save → Confirm → Execute**: edits/deletes are staged locally (red `errorContainer` highlight in the grid / bold for privileges) until the top-bar **Save** (Check icon) opens a multi-statement SQL preview; Execute commits sequentially with per-statement `currentQuery` updates.
- **User & privilege management** — list `mysql.user`, full-page privilege detail per `user@host` (`SHOW GRANTS` parsing, bold for any grant on `*.*`/`db.*`/`db.table`, 8-privilege checkbox matrix per `ON` target).
- **Indexes / table structure** — view/alter structure and indexes (read-gated in `TableStructureScreen`/`IndexManagementScreen`).
- **Session lock vs profile default** — `ConnectionProfileEntity.isReadonly` is the **default on connect**; `ConnectionViewModel.sessionLocked` is the **live session lock** toggled from the top bar (does not persist to Room) and wired to every route via `NavGraph`.
- **Current query bar** — `CurrentQueryBar` bottom bar shows `currentQuery` for the active page (collapsible), copy-to-clipboard, monospace `SelectionContainer`.
- **Export** — `ExportUtil` helper (CSV/etc.) via `FileProvider`.

---

## Requirements

| Tooling | Version |
|---------|---------|
| Android Studio | Hedgehog or newer (AGP 8.2.2, Kotlin 1.9.22) |
| JDK | 17 ( `compileSdk 34`, `minSdk 26`, `targetSdk 34`, `jvmTarget 17` ) |
| Android SDK | `compileSdk 34`, NDK not required |
| Gradle | Wrapper `gradlew` checked in (no local install needed) |

Runtime dependencies (see `app/build.gradle.kts`):

- `androidx.compose:compose-bom:2024.02.00`, `material3`, extended icons, `navigation-compose`, `activity-compose`
- `hilt-android:2.50` + `ksp`, `room:2.6.1` + `ksp`
- `security-crypto:1.1.0-alpha06` (EncryptedSharedPreferences)
- `mariadb-java-client:2.4.4` (latest version compatible with Android's `java.sql`/regex)
- `jsch:0.1.55`, `kotlinx-coroutines-android:1.7.3`

---

## Quick start

```bash
# Clone
git clone git@github.com:izzais/sql-client-android.git
cd sql-client-android

# Point Gradle at your SDK if the default does not exist
# local.properties is gitignored — either set ANDROID_SDK_ROOT or create it:
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
# e.g. sdk.dir=/home/al/Android/Sdk

# Debug build
./gradlew assembleDebug

# Install to a connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or build & install in one step
./gradlew installDebug

# Release (minified, ProGuard)
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

Open the app, tap **+ New Connection**, fill host/port/username/password, toggle **Read only** if you want the session locked by default, optionally configure **SSH Tunnel** / **SSL**, then **Test** and **Save**.

---

## Configuration

- `local.properties` — `sdk.dir` only; never commit secrets.
- `gradle.properties` — `org.gradle.jvmargs=-Xmx2048m`, `android.useAndroidX`, `kotlin.code.style=official`.
- `settings.gradle.kts` — single module `:app`, `repositories { google(), mavenCentral(), gradlePluginPortal() }`.
- `app/build.gradle.kts` — single source-of-truth for versions/SDK (see [Requirements](#requirements)).

No `.env`: DB/SSH passwords are stored per profile via `CredentialStore` (encrypted prefs) and never written to `Room`.

---

## Project structure

```
.
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/sqlclient/android/
│       │   ├── App.kt                          # @HiltAndroidApp
│       │   ├── MainActivity.kt                 # enableEdgeToEdge() + theme + NavGraph host
│       │   ├── data/
│       │   │   ├── PrivilegeResolver.kt        # SHOW GRANTS -> PrivilegeSet (singleton, cached)
│       │   │   ├── local/
│       │   │   │   ├── AppDatabase.kt          # Room DB v1 (fallbackToDestructiveMigration)
│       │   │   │   ├── dao/ConnectionProfileDao.kt, QueryHistoryDao.kt
│       │   │   │   └── entity/ConnectionProfileEntity.kt, QueryHistoryEntity.kt
│       │   │   ├── remote/
│       │   │   │   ├── MariaDbConnectionManager.kt  # single JDBC Connection + Mutex, SSH delegate
│       │   │   │   ├── SshTunnelManager.kt          # JSch session, port forwarding, TestSsh
│       │   │   │   └── model/Models.kt         # TableInfo, ColumnInfo, IndexInfo, UserInfo, PrivilegeSet, …
│       │   │   └── repository/
│       │   │       ├── ConnectionRepository.kt  # profile CRUD + connect/test + credential snapshot (id 999999)
│       │   │       ├── DatabaseRepository.kt
│       │   │       └── QueryRepository.kt
│       │   ├── di/
│       │   │   └── DatabaseModule.kt, NetworkModule.kt, RepositoryModule.kt
│       │   ├── navigation/
│       │   │   └── NavGraph.kt                 # NavHost routes, threads sessionLocked to all destinations
│       │   ├── ui/
│       │   │   ├── components/                 # AppTopBar, AppSidebar/DatabaseTree, DataTable, SqlEditor,
│       │   │   │                              # CurrentQueryBar, QueryTabBar, ConnectionCard
│       │   │   ├── screens/
│       │   │   │   ├── browser/DatabaseBrowserScreen.kt
│       │   │   │   ├── connection/ConnectionListScreen.kt, ConnectionEditorScreen.kt
│       │   │   │   ├── dataeditor/InlineDataEditorScreen.kt
│       │   │   │   ├── query/QueryEditorScreen.kt
│       │   │   │   ├── table/TableStructureScreen.kt, IndexManagementScreen.kt
│       │   │   │   ├── user/UserManagementScreen.kt, UserPrivilegeDetailScreen.kt
│       │   │   │   ├── export/, settings/
│       │   │   │   └── …
│       │   │   ├── theme/                      # Color.kt, Theme.kt, Type.kt, SqlClientTheme + ConnectionColors
│       │   │   └── viewmodel/                  # BrowserViewModel, ConnectionViewModel, DataEditorViewModel,
│       │   │                                   # IndexManagementViewModel, QueryViewModel, TableStructureViewModel,
│       │   │                                   # UserPermissionViewModel
│       │   └── util/
│       │       ├── CredentialStore.kt          # EncryptedSharedPreferences (sql_client_secure_prefs)
│       │       ├── ExportUtil.kt
│       │       ├── ThemeManager.kt
│       │       └── ThreadUtil.kt
│       └── res/                                # strings, themes, file_provider_paths.xml, mipmap icons
├── build.gradle.kts                            # root plugins block
├── settings.gradle.kts
├── gradle/wrapper/
├── gradlew / gradlew.bat
└── README.md
```

> The lists under `ui/screens/export` and `ui/screens/settings` are placeholders — safe to leave unimplemented until needed. Likewise `export/`/`settings/` ViewModels do not yet exist.

---

## Architecture

```
Compose (Material 3) — NavGraph (Navigation Compose, NavHost `connections` start)
        │                │
        │   hiltViewModel() (+ @HiltViewModel, viewModelScope)
        ↓                ↓
  ViewModels (StateFlow) — Browser / Connection / DataEditor / Query / TableStructure / IndexManagement / UserPermission
        │                      PrivilegeResolver (singleton, SHOW GRANTS cache)
        ↓
Repositories — ConnectionRepository / DatabaseRepository / QueryRepository
        │              CredentialStore (EncryptedSharedPreferences) + Room (AppDatabase)
        ↓
  MariaDbConnectionManager (DriverManager + single Connection + Mutex, 8 s connect / 30 s socket, queryTimeout 30 s)
        │
        └── SshTunnelManager (JSch) when profile.useSshTunnel
```

- **DI** — Hilt (`@HiltAndroidApp` `App`, `@AndroidEntryPoint` `MainActivity`, `hiltViewModel()` in `NavGraph`, `@Singleton` managers/resolver, `DatabaseModule`/`NetworkModule`/`RepositoryModule`).
- **Persistence** — Room (`connection_profiles`, `query_history`) + `EncryptedSharedPreferences` file `sql_client_secure_prefs` keyed by `profileId`. Room `fallbackToDestructiveMigration()` for now.
- **Concurrency** — single JDBC `Connection` + `Mutex queryMutex`; all queries on `Dispatchers.IO`; `viewModelScope` drives `StateFlow`. Pagination via `LIMIT/OFFSET` + `snapshotFlow` (`InlineDataEditorScreen.DataGrid` + `loadMore`).
- **Navigation** — route set (see [Navigation & screens](#navigation--screens)); `NavGraph` is the only place that reads `ConnectionViewModel.connectionState` + `sessionLocked` and fans them out to destinations.

---

## Key concepts

### Privilege-filtered browsing

`PrivilegeResolver` is the single source: `loadGrants(user) → SHOW GRANTS` (or DB-level probe) cached per session, parsed into `PrivilegeSet { globalPrivileges, dbPrivileges: Map<db, Set<priv>>, tablePrivileges: Map<"db.table", Set<priv>>, hasGlobalAll }`. Helpers:

- `filterDatabases(allDatabases)` — returns only DBs the user can `USE`/`SELECT`.
- `filterTables(db, allTables)` — same per DB.
- `hasPrivilegeOn(db, table?, priv)` used for bold highlighting (see Users).

System schemas `information_schema / performance_schema / sys` are always hidden.

### Cache-first + manual refresh

- `BrowserViewModel`: `hasLoaded/hasLoadedDatabases` guards; `refreshDatabases()/refreshTables()/refreshUsers()` set `force=true` or clear derived `visibleDatabases`. Sidebar expand is lazy: columns/indexes via `toggleDatabase(db)` only when the tree arrow is tapped. Navigating back reuses cached lists unless the user hits Refresh in the top bar.
- `DataEditorViewModel`: `hasLoaded` + `currentDatabase/currentTable` guard; `refreshData()` invalidates and reloads `LIMIT` page 0. `whereInput/activeWhere` survive pagination (`loadMore` preserves the active `WHERE`).

### Current query bar

`CurrentQueryBar` is the `Scaffold.bottomBar` on `Browser`, `QueryEditor`, `InlineDataEditor`, `TableStructure`, `IndexManagement`, `UserManagement`, `UserPrivilegeDetailScreen`. Single contract:

- `currentQuery: String` (per-page; `rememberSaveable` for collapse state, `windowInsetsPadding(navigationBars)` so it never sits under gesture nav after `enableEdgeToEdge()`).
- Collapsed = 80-char preview, expanded = selectable monospace + copy.

### Session lock vs profile default

`ConnectionProfileEntity.isReadonly` — **default at connect time**, persisted in Room via `updateProfile` / `saveProfile` (only from `ConnectionEditorScreen`).
`ConnectionViewModel.sessionLocked: StateFlow<Boolean>` — **live session state**, initialized `profile.isReadonly` on `connect(profile)`, cleared on `disconnect()`, toggled by `setSessionLocked()` from `AppTopBar` Lock/Unlock (no DB write). `NavGraph` threads `sessionLocked` + `onToggleLock` to every destination. All write paths (query exec, `INSERT/UPDATE/DELETE`, `GRANT/REVOKE/CREATE/DROP/RENAME/ALTER USER`) early-return `Locked — unlock to write` when the passed `isLocked` snapshot is true.

### Batch write staging (uniform pattern)

All writes that mutate the server or local DB go **Save → Confirm (SQL preview) → Execute**. Staged state is held in the ViewModel until Save commits:

- **Data editor** — `DataEditorViewModel.StagedEdit` + `_pendingEdits: Map<Pair<rowIndex,colIndex>, StagedEdit>` + `_pendingDeletes: Set<rowIndex>`; grouping `by rowIndex → 1 UPDATE per row` + single `DELETE IN`. UI: cell dialog `Cancel/OK` stages locally (red `errorContainer` highlight + staged value), row delete stages; top-bar `Check` (Save) shows `Confirm Write (N)` with the full `;\n`-joined SQL; Execute → `commitPending(...)` (per-statement `currentQuery` + sequential `executeQuery`, stop on first error), then reload.
- **Privilege detail** — `UserPermissionViewModel._pendingPrivChanges: Map<"PRIV@onKey", Boolean>` (desired vs `onToPrivs`), `stagePrivToggle` / `buildPendingPrivSqls` / `commitPendingPrivs` (per-`GRANT/REVOKE` + `FLUSH PRIVILEGES` + `loadGrants`). UI: `effectiveChecked()` layer over `hasPrivOnTarget()`, DB/table bold via `effectiveHasAnyPriv()` (any pending `true` makes its `*.*`/`db.*`/`db.table` bold), banner `N pending change(s) | Discard | Save`, `Confirm Write (N)` with `GRANT/REVOKE …` statements.

`IndexManagement` / `QueryEditor` custom SQL follow the same rule via `pendingSql/pendingAction + Confirm Write` where applicable.

### Query execution

`MariaDbConnectionManager.executeQuery(sql): QueryResult` (`Success(columns, rows)` | `UpdateSuccess` | `Error(message)`) — single JDBC `Connection`, `Properties { connectTimeout 8000, socketTimeout 30000, useSSL/trustServerCertificate, queryTimeout 30s }`, `StrictHostKeyChecking=no` for SSH. `_currentQuery`/`_query`/`_error`/`_lastQueryDurationMs` are the ViewModel contracts driving `CurrentQueryBar`, error Snackbars and the status bar.

---

## Navigation & screens

| Route | Screen | Key file | Notes |
|-------|--------|----------|-------|
| `connections` | Connection list | `ui/screens/connection/ConnectionListScreen.kt` | `ConnectionCard` grid, `ThemeManager`-aware |
| `connection/new` | Create connection | `ui/screens/connection/ConnectionEditorScreen.kt` | Build `ConnectionProfileEntity` + credential snapshot (id `999999` for Test) |
| `connection/edit/{profileId}` | Edit connection | same | `remember(profile.id)` for `isReadonly` checkbox |
| `browser` | Database browser | `ui/screens/browser/DatabaseBrowserScreen.kt` | Drawer + `AppTopBar` (Menu + Disconnect + Refresh + Lock), lazy columns/indexes, `BackHandler` for expanded state |
| `query/{database}/{table}` | Query editor | `ui/screens/query/QueryEditorScreen.kt` | Editor + history tabs, Execute, timing |
| `structure/{database}/{table}` | Table structure | `ui/screens/table/TableStructureScreen.kt` | Full columns/types/keys |
| `data_editor/{database}/{table}` | Inline data editor | `ui/screens/dataeditor/InlineDataEditorScreen.kt` | Grid, WHERE bar, staging, limit + timing status bar |
| `users` | User management | `ui/screens/user/UserManagementScreen.kt` | Users + Grants tabs |
| `user_detail/{user}/{host}` | Privilege detail | `ui/screens/user/UserPrivilegeDetailScreen.kt` | Full-page drill `user → databases → tables`, per-`ON` 8-priv matrix, Rename/Password dialogs |
| `index_management/{database}/{table}` | Index management | `ui/screens/table/IndexManagementScreen.kt` | Index list + create/drop |

`NavGraph` holds the only `rememberNavController()` and owns the four shared VMs (`ConnectionViewModel`, `BrowserViewModel`, `QueryViewModel`) across destinations; the remaining VMs are `hiltViewModel()` per destination.

---

## Security & credentials

- Passwords (DB + SSH) and SSH passphrase are **never** in Room; they live in `EncryptedSharedPreferences` file `sql_client_secure_prefs` keyed by `profile.id` via `CredentialStore` (`MasterKey` + `AES256_GCM`).
- `saveProfile` / `updateProfile` persist `ConnectionProfileEntity` (no password column) and delegate sensitive updates to `CredentialStore.savePassword/saveSshPassword/saveSshPassphrase` only when the caller supplies a non-blank value.
- `testConnection` snapshots credentials under ephemeral id `999999` so the DB tunnel does not pollute the real profile's stored creds; `connect(profile)` reads the persisted creds by `profile.id`.
- All writes respect the live `sessionLocked` guard; in read-only sessions even staged mutations are blocked server-side (error path, not just UI disable).
- SSH `StrictHostKeyChecking=no` is intentional for mobile/host-hopping; tighten if your deployment requires known-hosts.

---

## Theming

`ui/theme/SqlClientTheme` driven by `ThemeManager` (persisted preference via `SharedPreferences`):

- `followSystem: Boolean` (default `true`) — when `true`, `isSystemInDarkTheme()` wins; when `false`, `isDarkMode: Boolean` is authoritative.
- `MainActivity` resolves `useDarkTheme` and passes to `SqlClientTheme(darkTheme = ...)`.

Connection cards + `AppTopBar` use per-profile `ConnectionColors` (tinted badge / `containerColor`).

---

## Development workflow

```bash
# Lint/format (no enforced formatter — keep Compose idiomatic)
./gradlew lint

# Run on device/emulator with live code changes via Android Studio's Apply Changes
# or CLI:
./gradlew installDebug && adb shell am start -n com.sqlclient.android/.MainActivity

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
- Room is currently `fallbackToDestructiveMigration()` — bumping `AppDatabase.version` without a migration will drop `connection_profiles` / `query_history` on upgrade (fine for alpha).

---

## Testing

Skeleton only at this point (`src/test`, `src/androidTest` with Compose test rule). Run:

```bash
./gradlew test                # JVM unit tests
./gradlew connectedAndroidTest  # on-device Compose/Espresso
```

Planned coverage: `PrivilegeResolver.parseGrants` (glob/`ALL`/`ON` edge cases), `CredentialStore` round-trip, `SshTunnelManager` open/close lifecycle, `DataEditorViewModel` staging `buildPendingSqls` / grouping.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `enableEdgeToEdge` content under nav bar | `Scaffold.bottomBar` without `WindowInsets.navigationBars` | `CurrentQueryBar` already applies `windowInsetsPadding(navigationBars)` — check you did not add a second `navigationBarsPadding` in the child screen |
| First open of Databases is empty | `hasLoaded*` cache or stale `visibleDatabases` | Hit **Refresh** in the top bar; verify `PrivilegeResolver.loadGrants` succeeded (logcat `MariaDbConn/JDBC`) |
| `JSchException: Auth fail` on SSH | Password/key/passphrase mismatch | Check password vs key mode; pass `sshPassphrase` when the key is encrypted; verify `sshHost:22` reachability with `Test SSH` |
| `Password not found. Save the connection first.` on connect | Credentials not yet saved to `CredentialStore` | Tap **Save** in the editor before **Connect** (or re-save after clearing app data) |
| Writes still execute while locked | Route not wired to `sessionLocked` | `NavGraph` must pass `isLocked = sessionLocked.collectAsState().value` + `onToggleLock = { setSessionLocked(!value) }` to that screen's `AppTopBar(showLock=true, …)` |
| Writes not appearing in `CurrentQueryBar` | Write VM missed `_currentQuery.value = sql` | Every write path should set `_currentQuery` before `executeQuery`, even for staged preview rerenders |
| `UPDATE ... WHERE pk = ...` affects 0 rows | Wrong `pkColumnIndex` / `autoIncrementColumn` | `loadColumnInfo` parses `SHOW FULL COLUMNS FROM` — ensure the table has a PK / `auto_increment` |

---

## Known issues & roadmap

**Alpha gaps (shipped as-is, PRs welcome):**

- Several post-refactor flows are unpolished: `export/` and `settings/` screens are scaffolded but not functional; no offline/queue support.
- Room version `1` with destructive migration — upgrade path will drop data until a real migration ships.
- SSH `StrictHostKeyChecking=no`; no known-hosts UI.
- `DatabaseTree` column/index lazy loads are not independently cancellable; rapid expand/collapse can briefly show stale children.
- No instrumentation / integration tests beyond scaffolding.

**Near-term:**

- Real migrations for `AppDatabase` (starting at v2) and an in-app export for `connection_profiles`.
- Known-hosts handling for SSH + CA/key-file picker.
- Pagination polish: stable `LIMIT/OFFSET` footer + total-row-count (`COUNT(*)`) opt-in.
- `PrivilegeResolver` cache invalidation hook after `GRANT/REVOKE/FLUSH PRIVILEGES` (today `loadGrants` must be called explicitly per destination).
- Compose previews / screenshot tests for `Browser`, `InlineDataEditorScreen.DataGrid`, and privilege detail.

---

## Contributing

PRs against `main` welcome. For larger changes please open an issue first. Commit messages: imperative, short subject + body with context — e.g. `fix(browser): privilege-filter sidebar cache bypass`.

---

## AI session bootstrap

> **For an AI starting a fresh session on this repo** — use this section so you do not have to crawl the tree blind.

1. **Read this README first**, then only open what you need:
   - Stack/SDK — [Requirements](#requirements) + `app/build.gradle.kts`
   - Routes & VM wiring — [Navigation & screens](#navigation--screens) + `navigation/NavGraph.kt`
   - Privilege rules — [Key concepts — Privilege-filtered browsing](#key-concepts) + `data/PrivilegeResolver.kt` + `data/remote/model/Models.kt:PrivilegeSet`
   - Session semantics — [Key concepts — Session lock vs profile default](#key-concepts) + `data/local/entity/ConnectionProfileEntity.kt` + `ui/viewmodel/ConnectionViewModel.kt`
   - Write flow — [Key concepts — Batch write staging](#key-concepts) + `ui/viewmodel/DataEditorViewModel.kt` + `ui/viewmodel/UserPermissionViewModel.kt` + `ui/screens/dataeditor/InlineDataEditorScreen.kt` + `ui/screens/user/UserPrivilegeDetailScreen.kt`
   - Persistence & creds — `data/local/AppDatabase.kt` + `util/CredentialStore.kt` + `data/repository/ConnectionRepository.kt`
   - DB connectivity — `data/remote/MariaDbConnectionManager.kt` + `data/remote/SshTunnelManager.kt`
2. **Prefer `Glob`/`Grep`** for discovery over reading every file. The [Project structure](#project-structure) map above is authoritative; treat `ui/screens/export` and `ui/screens/settings` as empty placeholders.
3. **Batch any writes** you stage through the `Save → Confirm → Execute` preview — that is the expected UX pattern after the current refactor (see `DataEditorViewModel.buildPendingSqls/commitPending`).
4. **Do not persist `sessionLocked` to Room** — it is `ConnectionViewModel` live state only; `ConnectionProfileEntity.isReadonly` is the persisted default.
5. **Bottom bars**: `CurrentQueryBar` is the `Scaffold.bottomBar` contract on six browser/query/data/structure/index/user screens — do not duplicate `WindowInsets` handling there; `enableEdgeToEdge()` is in `MainActivity`.

---

## License

No license published yet. Treat this repo as **all rights reserved** until a `LICENSE` file is added — do not redistribute built APKs without permission.
