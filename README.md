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
- **SQL editor with autocomplete** — monospace editor with syntax highlighting (`SqlSyntaxHighlight` tokenizer + `VisualTransformation`), context-aware autocomplete dropdown (`SqlEditorWithAutocomplete`): SQL keywords, dot-prefix `db.table.column` navigation, backtick/quote identifiers, multi-table column merge across FROM/JOIN clauses, JOIN ON autocomplete with columns from all joined tables. Write queries show a confirm dialog before execution. TopBar refresh re-executes only read queries.
- **Inline data editor** — paginated `LIMIT/OFFSET` grid (limit presets 100/200/500/1000) with infinite scroll (`snapshotFlow` + `loadMore`), **quick WHERE filter** (`WHERE <raw>`) with lite autocomplete chips, per-cell edit (dialog), batch staging (see below), row select/delete, query-timing in the status bar. Built-in SQL editor bar for custom queries — write queries show confirm dialog before execution.
- **Batch write flow** — every write goes through **Save → Confirm → Execute**: edits/deletes are staged locally (red `errorContainer` highlight in the grid / bold for privileges) until the top-bar **Save** (Check icon) opens a multi-statement SQL preview; Execute commits sequentially with per-statement `currentQuery` updates.
- **User & privilege management** — list `mysql.user`, full-page privilege detail per `user@host` (`SHOW GRANTS` parsing, bold for any grant on `*.*`/`db.*`/`db.table`, 8-privilege checkbox matrix per `ON` target).
- **Indexes / table structure** — view/alter structure and indexes (read-gated in `TableStructureScreen`/`IndexManagementScreen`).
- **Session lock vs profile default** — `ConnectionProfileEntity.isReadonly` is the **default on connect**; `ConnectionViewModel.sessionLocked` is the **live session lock** toggled from the top bar (does not persist to Room) and wired to every route via `NavGraph`.
- **Current query bar** — `CurrentQueryBar` bottom bar shows **all SQL queries** (`List<String>`) executed to render the current page (query log). Collapsed = "Query log (N)" pill; expanded = numbered per-line list (most recent highlighted), word-wrapped, selectable monospace + copy-to-clipboard. Every `connectionManager.executeQuery` call is tracked in `_currentQuery` — no hidden queries.
- **Export** — `ExportUtil` helper (CSV/etc.) via `FileProvider`.

---

## Requirements

| Tooling | Version |
|---------|---------|
| Android Studio | Ladybug or newer (AGP 8.5.2, Kotlin 2.0.21) |
| JDK | 17 ( `compileSdk 34`, `minSdk 26`, `targetSdk 34`, `jvmTarget 17` ) |
| Android SDK | `compileSdk 34`, NDK not required |
| Gradle | Wrapper `gradlew` checked in (`gradle-8.7`), no local install needed |

Runtime dependencies (see `app/build.gradle.kts`):

- `androidx.compose:compose-bom:2024.09.03`, `material3`, extended icons, `navigation-compose`, `activity-compose`
- `hilt-android:2.51.1` + `ksp 2.0.21-1.0.28`, `room:2.6.1` + `ksp`
- `security-crypto:1.1.0-alpha06` (EncryptedSharedPreferences)
- `mariadb-java-client:2.4.4` (latest version compatible with Android's `java.sql`/regex)
- `jsch:0.1.55`, `kotlinx-coroutines-android:1.7.3`
- `lifecycle-runtime-compose:2.8.7`, `lifecycle-viewmodel-compose:2.8.7`

---

## Quick start — from zero to APK (Option C: build from source)

This is the **developer build path** (no Play Store / no prebuilt APK yet). Do it once — every next build is just `./gradlew assembleDebug`.

### 1. Download the source

**Via git (recommended):**
```bash
git clone git@github.com:izzais/sql-client-android.git
# or HTTPS if you have no SSH key:
# git clone https://github.com/izzais/sql-client-android.git
cd sql-client-android
```

**Via ZIP (no git needed):**
GitHub → **Code ▼ → Download ZIP** → extract → `cd sql-client-android` (same folder as `gradlew`). Functionally identical to `git clone`.

### 2. Install prerequisites

#### Ubuntu / Debian via `apt` (includes everything you need except the Android SDK)

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk git wget unzip adb
# verify
java -version      # must be 17.x
adb --version
git --version
```

Other distros:
- Fedora/RHEL: `sudo dnf install java-17-openjdk git wget unzip android-tools`
- Arch: `sudo pacman -S jdk17-openjdk git wget unzip android-tools`
- macOS: `brew install openjdk@17 android-platform-tools git wget unzip`
- Windows: install **Android Studio Hedgehog+** (bundles JDK 17 + SDK + adb) and use PowerShell/`gradlew.bat` — no `apt` needed.

> No separate Gradle install needed — the repo ships the **Gradle Wrapper** (`gradlew`/`gradlew.bat`). Do not `apt install gradle`.

#### Android SDK — pick one of the two:

**A) You already have Android Studio Hedgehog or newer**
Nothing extra to install. Just tell Gradle where the SDK is (next section). Default locations: Linux `~/Android/Sdk`, macOS `~/Library/Android/sdk`, Windows `%LOCALAPPDATA%\Android\Sdk`.

**B) Headless / without Android Studio (command-line tools only)**

```bash
# 1. Create SDK root
mkdir -p ~/Android/Sdk/cmdline-tools
cd /tmp

# 2. Download command-line tools (Linux)
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip

# 3. Unpack as "latest"
unzip -q commandlinetools-linux-11076708_latest.zip
mkdir -p ~/Android/Sdk/cmdline-tools/latest
mv cmdline-tools/* ~/Android/Sdk/cmdline-tools/latest/

# 4. Add to PATH for this shell (and add to ~/.bashrc / ~/.zshrc for persistence)
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# 5. Accept licenses + install required SDK components (Android 34 matches compileSdk 34)
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# 6. Verify
sdkmanager --list | grep -E "platforms;android-34|build-tools;34"
adb --version
```

`build-tools` `34.0.0` and `platforms;android-34` are the minimum required by `app/build.gradle.kts` (`compileSdk 34`, `targetSdk 34`). Newer patch versions also work.

### 3. Point Gradle at the SDK

`local.properties` is **gitignored** — never commit it.

```bash
cd /path/to/sql-client-android

# Option 1: create local.properties (most reliable)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
cat local.properties
# expected: sdk.dir=/home/al/Android/Sdk  (adjust to your actual path)

# Option 2: env var fallback (works if local.properties is absent)
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
```

If Gradle still says `SDK location not found`, check `echo $ANDROID_HOME` and `ls ~/Android/Sdk/platforms`.

### 4. Build the APK

```bash
# Make wrapper executable (first time only, Linux/macOS)
chmod +x gradlew

# Debug APK (fast, unminified, debuggable) — ~15–30 s on first build
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk

# Install to a connected device/emulator (USB debugging ON, or an AVD running)
adb devices                          # must list your device
adb install -r app/build/outputs/apk/debug/app-debug.apk
# or in one step:
./gradlew installDebug

# Without adb — manual install:
# Copy app/build/outputs/apk/debug/app-debug.apk to the phone
# and tap it in File Manager → Allow "Install unknown apps".

# Release APK (minified via ProGuard/R8, needs signing for distribution)
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk  (unsigned by default)
# To distribute, sign with your keystore — see Android docs "Sign your app".
```

Open the app, tap **+ New Connection**, fill host/port/username/password, toggle **Read only** if you want the session locked by default, optionally configure **SSH Tunnel** / **SSL**, then **Test** and **Save**.

### 5. Next builds

After the first setup you only need:

```bash
git pull            # or re-download ZIP if you used ZIP
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

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
│       │   │   │                              # SqlEditorWithAutocomplete, SqlSyntaxHighlight,
│       │   │   │                              # CurrentQueryBar, QueryTabBar, ConnectionCard
│       │   │   ├── screens/
│       │   │   │   ├── browser/DatabaseBrowserScreen.kt
│       │   │   │   ├── connection/ConnectionListScreen.kt, ConnectionEditorScreen.kt
│       │   │   │   ├── dataeditor/InlineDataEditorScreen.kt
│       │   │   │   ├── query/SQLEditorScreen.kt
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

`CurrentQueryBar` is the `Scaffold.bottomBar` on `Browser`, `SQLEditor`, `InlineDataEditor`, `TableStructure`, `IndexManagement`, `UserManagement`, `UserPrivilegeDetailScreen`. Contract:

- `currentQuery: List<String>` — **all queries** executed to render the current page state. Every ViewModel appends (`+=`) each SQL before execution; refresh/reset clears the list. Collapsed = "Query log (N)" pill; expanded = numbered per-line list (most recent highlighted), word-wrapped, selectable monospace + copy.
- `rememberSaveable` for collapse state, `windowInsetsPadding(navigationBars)` so it never sits under gesture nav after `enableEdgeToEdge()`.
- **No hidden queries**: every `connectionManager.executeQuery` call (including background reads like `SHOW FULL COLUMNS`, `SHOW INDEX`, `SHOW CREATE TABLE`, `information_schema` size queries, `SHOW GRANTS`, and `FLUSH PRIVILEGES`) is reflected in the list. This ensures the query log matches the server's `general_log`.

### Session lock vs profile default

`ConnectionProfileEntity.isReadonly` — **default at connect time**, persisted in Room via `updateProfile` / `saveProfile` (only from `ConnectionEditorScreen`).
`ConnectionViewModel.sessionLocked: StateFlow<Boolean>` — **live session state**, initialized `profile.isReadonly` on `connect(profile)`, cleared on `disconnect()`, toggled by `setSessionLocked()` from `AppTopBar` Lock/Unlock (no DB write). `NavGraph` threads `sessionLocked` + `onToggleLock` to every destination. All write paths (query exec, `INSERT/UPDATE/DELETE`, `GRANT/REVOKE/CREATE/DROP/RENAME/ALTER USER`) early-return `Locked — unlock to write` when the passed `isLocked` snapshot is true.

### Write query safety rules

Every write query that mutates the server **must** follow these rules:

1. **SQL preview before execution** — The user must see the exact SQL before it is sent. This is done via:
   - `pendingSql` pattern: dialog shows SQL with "Execute" button (used by InsertRow, Grant/Revoke, Create/Drop/Rename User, Change Password, Create/Drop Index, Rename Table, Add/Drop Column).
   - `showSaveConfirm` pattern: batch staging shows all pending SQL in `Confirm Write (N)` dialog (used by DataEditor staged edits/deletes, privilege detail batch changes).
   - `showWriteConfirm` pattern: SQL editor bars detect write queries via `isWriteQuery()` and show a confirm dialog before executing (used by SQLEditorScreen Execute button and DataEditorScreen SQL editor bar).

2. **Confirm button required** — Every write path must have an explicit user action (tap "Execute" in a dialog) before the query reaches the server. No write query should execute on a simple button tap without a confirm step.

3. **Readonly lock enforcement** — When `isLocked = true`:
   - All ViewModel write methods check `isLocked` and return `Locked — unlock to write` error.
   - UI elements are disabled (`enabled = !isLocked`): FABs, Grant/Revoke buttons, checkboxes, save buttons.
   - Dialogs early-return without executing (`if (isLocked) { dialog = false; return@... }`).
   - The Execute button in SQLEditor is disabled for write queries when locked.
   - TopBar refresh in SQLEditor skips write queries entirely (re-executes only SELECT/read queries).

4. **TopBar refresh must not execute write queries** — The refresh button re-executes the current page's read queries. In SQLEditor, it skips if `isWriteQuery()` is true. In DataEditor, it calls `refreshData()` (SELECT only).

### Batch write staging (uniform pattern)

All writes that mutate the server or local DB go **Save → Confirm (SQL preview) → Execute**. Staged state is held in the ViewModel until Save commits:

- **Data editor** — `DataEditorViewModel.StagedEdit` + `_pendingEdits: Map<Pair<rowIndex,colIndex>, StagedEdit>` + `_pendingDeletes: Set<rowIndex>`; grouping `by rowIndex → 1 UPDATE per row` + single `DELETE IN`. UI: cell dialog `Cancel/OK` stages locally (red `errorContainer` highlight + staged value), row delete stages; top-bar `Check` (Save) shows `Confirm Write (N)` with the full `;\n`-joined SQL; Execute → `commitPending(...)` (per-statement `currentQuery` append + sequential `executeQuery`, stop on first error), then reload.
- **Privilege detail** — `UserPermissionViewModel._pendingPrivChanges: Map<"PRIV@onKey", Boolean>` (desired vs `onToPrivs`), `stagePrivToggle` / `buildPendingPrivSqls` / `commitPendingPrivs` (per-`GRANT/REVOKE` + `FLUSH PRIVILEGES` + `loadGrants`). UI: `effectiveChecked()` layer over `hasPrivOnTarget()`, DB/table bold via `effectiveHasAnyPriv()` (any pending `true` makes its `*.*`/`db.*`/`db.table` bold), banner `N pending change(s) | Discard | Save`, `Confirm Write (N)` with `GRANT/REVOKE …` statements.

`IndexManagement` / `SQLEditor` custom SQL follow the same rule via `pendingSql/pendingAction + Confirm Write` where applicable.

### Query execution

`MariaDbConnectionManager.executeQuery(sql): QueryResult` (`Success(columns, rows)` | `UpdateSuccess` | `Error(message)`) — single JDBC `Connection`, `Properties { connectTimeout 8000, socketTimeout 30000, useSSL/trustServerCertificate, queryTimeout 30s }`, `StrictHostKeyChecking=no` for SSH. `_currentQuery: MutableStateFlow<List<String>>` / `_query` / `_error` / `_lastQueryDurationMs` are the ViewModel contracts driving `CurrentQueryBar` (query log), error Snackbars and the status bar. Every query that hits the server must be appended to `_currentQuery` before execution.

---

## Navigation & screens

| Route | Screen | Key file | Notes |
|-------|--------|----------|-------|
| `connections` | Connection list | `ui/screens/connection/ConnectionListScreen.kt` | `ConnectionCard` grid, `ThemeManager`-aware |
| `connection/new` | Create connection | `ui/screens/connection/ConnectionEditorScreen.kt` | Build `ConnectionProfileEntity` + credential snapshot (id `999999` for Test) |
| `connection/edit/{profileId}` | Edit connection | same | `remember(profile.id)` for `isReadonly` checkbox |
| `browser` | Database browser | `ui/screens/browser/DatabaseBrowserScreen.kt` | Drawer + `AppTopBar` (Menu + Disconnect + Refresh + Lock), lazy columns/indexes, `BackHandler` for expanded state |
| `query/{database}/{table}` | SQL Editor | `ui/screens/query/SQLEditorScreen.kt` | Editor + autocomplete + history tabs, Execute, timing |
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
| Query log collapsed text cut off | `CurrentQueryBar` header layout issue | The collapsed state now shows just "Query log (N)" without preview text — verify `weight(1f)` is on the Text composable |
| First open of Databases is empty | `hasLoaded*` cache or stale `visibleDatabases` | Hit **Refresh** in the top bar; verify `PrivilegeResolver.loadGrants` succeeded (logcat `MariaDbConn/JDBC`) |
| `JSchException: Auth fail` on SSH | Password/key/passphrase mismatch | Check password vs key mode; pass `sshPassphrase` when the key is encrypted; verify `sshHost:22` reachability with `Test SSH` |
| `Password not found. Save the connection first.` on connect | Credentials not yet saved to `CredentialStore` | Tap **Save** in the editor before **Connect** (or re-save after clearing app data) |
| Writes still execute while locked | Route not wired to `sessionLocked` | `NavGraph` must pass `isLocked = sessionLocked.collectAsState().value` + `onToggleLock = { setSessionLocked(!value) }` to that screen's `AppTopBar(showLock=true, …)` |
| Writes not appearing in `CurrentQueryBar` | Write VM missed `_currentQuery.value += sql` | Every write/read path must append to `_currentQuery` before `executeQuery`, including background reads (`SHOW FULL COLUMNS`, `SHOW INDEX`, `SHOW CREATE TABLE`, `information_schema`, `SHOW GRANTS`, `FLUSH PRIVILEGES`) |
| `UPDATE ... WHERE pk = ...` affects 0 rows | Wrong `pkColumnIndex` / `autoIncrementColumn` | `loadColumnInfo` parses `SHOW FULL COLUMNS FROM` — ensure the table has a PK / `auto_increment` |

---

## Known issues & roadmap

**Alpha gaps (shipped as-is, PRs welcome):**

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

## TODO — Unimplemented features

> Features that are **already implemented in code** but **not yet wired into the UI**. A future AI or developer can pick these up without re-crawling the entire codebase.

### 1. Export (CSV, JSON, SQL INSERT)

**Status:** `ExportUtil.kt` fully implemented (129 lines), zero UI references.

| Method | Description |
|--------|-------------|
| `exportToCsv(context, columns, rows, fileName)` | Writes query results to a `.csv` file via `FileProvider` |
| `exportToJson(context, columns, rows, fileName)` | Writes query results to a `.json` file |
| `exportToSqlInsert(context, table, columns, rows, fileName)` | Generates `INSERT INTO table VALUES (...)` statements |
| `shareFile(context, file, mimeType)` | Android share intent for the exported file |

**Where to add UI:** Add export buttons to `InlineDataEditorScreen` (top bar or overflow menu) and `SQLEditorScreen` (after results are shown). Pass `columns`/`rows` from the ViewModel's current result set.

### 2. Insert Row dialog

**Status:** `InsertRowDialog` composable implemented at `InlineDataEditorScreen.kt:1102-1154`. `showInsertDialog` state at line 121. No UI element triggers it.

**Where to add UI:** Add a FAB (`FloatingActionButton`) or a `+` icon in the top bar of `InlineDataEditorScreen` that sets `showInsertDialog = true`. The dialog takes column metadata, builds an `INSERT INTO table (cols) VALUES (vals)` statement, and submits it through the existing `executeUpdate` flow.

### 3. History Panel (query history browser)

**Status:** `HistoryPanel` composable is a placeholder (`"Query history will appear here"`) at `DatabaseBrowserScreen.kt:715-719`. The data layer is fully wired: `QueryHistoryDao.getHistoryByConnection()`, `QueryRepository.getHistoryByConnection()`, `QueryHistoryEntity` with `id`, `connectionProfileId`, `query`, `executedAt`, `database` (saved queries scoped per database).

**Where to add UI:** Replace the placeholder `HistoryPanel` with a `LazyColumn` that calls `viewModel.getHistory(profileId)` (needs a new method on `BrowserViewModel` that delegates to `QueryRepository`). Display `QueryHistoryEntity.query` + `executedAt` with tap-to-copy.

### 4. Index Management standalone route

**Status:** Route `index_management/{database}/{table}` defined at `NavGraph.kt:280-308`. `IndexManagementScreen` composable exists. But no screen ever navigates to this route — index management is already embedded inline in `TableStructureScreen`.

**Decision needed:** Either wire the route (add a button in `TableStructureScreen` to navigate to standalone index management) or remove the dead route + unreachable `IndexManagementScreen` if inline is preferred.

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
   - Write flow — [Key concepts — Write query safety rules](#key-concepts) + [Key concepts — Batch write staging](#key-concepts) + `ui/viewmodel/DataEditorViewModel.kt` + `ui/viewmodel/UserPermissionViewModel.kt` + `ui/screens/dataeditor/InlineDataEditorScreen.kt` + `ui/screens/user/UserPrivilegeDetailScreen.kt`
   - Persistence & creds — `data/local/AppDatabase.kt` + `util/CredentialStore.kt` + `data/repository/ConnectionRepository.kt`
   - DB connectivity — `data/remote/MariaDbConnectionManager.kt` + `data/remote/SshTunnelManager.kt`
2. **Prefer `Glob`/`Grep`** for discovery over reading every file. The [Project structure](#project-structure) map above is authoritative; treat `ui/screens/export` and `ui/screens/settings` as empty placeholders.
3. **Batch any writes** you stage through the `Save → Confirm → Execute` preview — that is the expected UX pattern after the current refactor (see `DataEditorViewModel.buildPendingSqls/commitPending`).
4. **Do not persist `sessionLocked` to Room** — it is `ConnectionViewModel` live state only; `ConnectionProfileEntity.isReadonly` is the persisted default.
5. **Bottom bars**: `CurrentQueryBar` is the `Scaffold.bottomBar` contract on six browser/query/data/structure/index/user screens — do not duplicate `WindowInsets` handling there; `enableEdgeToEdge()` is in `MainActivity`.
6. **Query log (`_currentQuery`)**: every ViewModel uses `MutableStateFlow<List<String>>`. Append each SQL (`_currentQuery.value += sql`) before `connectionManager.executeQuery()`. Reset the list on full refresh. This ensures no hidden queries — every query hitting the server must appear in `CurrentQueryBar`.
7. **Write query safety**: every write query MUST show SQL preview + confirm dialog before execution. Use `isWriteQuery()` to detect write queries (checks for INSERT/UPDATE/DELETE/ALTER/DROP/CREATE/TRUNCATE/RENAME/GRANT/REVOKE prefix). When `isLocked = true`, ALL write paths must return early with error. TopBar refresh must NEVER execute write queries.

---

## License

No license published yet. Treat this repo as **all rights reserved** until a `LICENSE` file is added — do not redistribute built APKs without permission.
