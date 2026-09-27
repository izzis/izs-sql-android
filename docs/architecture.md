# Architecture — izs SQL Android

> Back to [README](../README.md) · Write safety: [write-safety.md](write-safety.md) · Setup: [setup.md](setup.md)

## Project structure

```
.
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/id/web/izs/sqlclient/
│       │   ├── App.kt                          # @HiltAndroidApp
│       │   ├── MainActivity.kt                 # enableEdgeToEdge() + theme + NavGraph host
│       │   ├── data/
│       │   │   ├── PrivilegeResolver.kt        # SHOW GRANTS -> PrivilegeSet (singleton, cached)
│       │   │   ├── local/
│       │   │   │   ├── AppDatabase.kt          # Room DB v3 (fallbackToDestructiveMigration)
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
│       │   │   │                              # CurrentQueryBar, QueryTabBar, ConnectionCard, DrawerScaffold
│       │   │   ├── screens/
│       │   │   │   ├── browser/DatabaseBrowserScreen.kt
│       │   │   │   ├── connection/ConnectionListScreen.kt, ConnectionEditorScreen.kt
│       │   │   │   ├── dataeditor/InlineDataEditorScreen.kt
│       │   │   │   ├── query/SQLEditorScreen.kt
│       │   │   │   ├── table/TableStructureScreen.kt (index health box + cardinality via IndexAnalyzer)
│       │   │   │   ├── user/UserManagementScreen.kt, UserPrivilegeDetailScreen.kt
│       │   │   │   ├── export/, settings/
│       │   │   │   └── …
│       │   │   ├── theme/                      # Color.kt, Theme.kt, Type.kt, SqlClientTheme + ConnectionColors
│       │   │   └── viewmodel/                  # BrowserViewModel, ConnectionViewModel, DataEditorViewModel,
│       │   │                                   # IndexManagementViewModel, QueryViewModel, TableStructureViewModel,
│       │   │                                   # UserPermissionViewModel
│       │   └── util/
│       │       ├── CredentialStore.kt          # Keystore AES-GCM (sql_client_secure_prefs_v2) + CredentialCrypto/KeystoreKeyProvider
│       │       ├── ExportUtil.kt
│       │       ├── ProfileCrypto.kt            # PBKDF2 120k + AES-256-CBC Salted__ (profile backup, master password not stored)
│       │       ├── SqlUtil.kt                  # stripLeading/isWriteQuery (default-deny)/shouldApplyLimit (SELECT only)/buildLimitedSql
│       │       ├── QueryLog.kt                 # QueryLogEntry(sql, error) + withQueryError (query-log line + server error)
│       │       ├── Clipboard.kt                # rememberCopyToClipboard via LocalClipboard (copy + optional toast)
│       │       ├── DbeaverImport.kt            # MySQL/MariaDB connection import from .dbp/data-sources.json + credentials-config.json decryption
│       │       └── ThemeManager.kt
│       └── res/                                # strings, themes, file_provider_paths.xml, mipmap icons
├── build.gradle.kts                            # root plugins block
├── settings.gradle.kts
├── gradle/wrapper/
├── gradlew / gradlew.bat
└── README.md
```

> The lists under `ui/screens/export` and `ui/screens/settings` are placeholders — safe to leave unimplemented until needed. Likewise `export/`/`settings/` ViewModels do not yet exist.

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
        │              CredentialStore (Keystore AES-GCM) + Room (AppDatabase)
        ↓
  MariaDbConnectionManager (DriverManager + single Connection + Mutex, 8 s connect / 30 s socket, queryTimeout 30 s)
        │
        └── SshTunnelManager (JSch) when profile.useSshTunnel
```

- **DI** — Hilt (`@HiltAndroidApp` `App`, `@AndroidEntryPoint` `MainActivity`, `hiltViewModel()` in `NavGraph`, `@Singleton` managers/resolver, `DatabaseModule`/`NetworkModule`/`RepositoryModule`).
- **Persistence** — Room (`connection_profiles`, `query_history`) + `CredentialStore` (Android Keystore AES-256-GCM, ciphertexts in `sql_client_secure_prefs_v2`) keyed by `profileId`. No legacy migration (fresh install starts empty). Credential prefs are excluded from Auto Backup (Keystore keys are never backed up). Room `fallbackToDestructiveMigration(dropAllTables = true)` for now.
- **Concurrency** — single JDBC `Connection` + `Mutex queryMutex`; all queries on `Dispatchers.IO`; `viewModelScope` drives `StateFlow`. Pagination via `LIMIT/OFFSET` + `snapshotFlow` (`InlineDataEditorScreen.DataGrid` + `loadMore`).
- **Navigation** — route set (see [Navigation & screens](#navigation--screens)); `NavGraph` is the only place that reads `ConnectionViewModel.connectionState` + `sessionLocked` and fans them out to destinations.

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

`CurrentQueryBar` is the `Scaffold.bottomBar` on `Browser`, `SQLEditor`, `InlineDataEditor`, `TableStructure`, `UserManagement`, `UserPrivilegeDetailScreen`. Contract:

- `currentQuery: List<QueryLogEntry>` — **all queries** executed to render the current page state (`QueryLogEntry(sql, error)` lives in `util/QueryLog.kt`). Every ViewModel appends (`+= QueryLogEntry(sql)`) each SQL before execution; refresh/reset clears the list. Collapsed = "Query log (N)" pill; expanded = numbered per-line list (most recent highlighted), word-wrapped, selectable monospace + copy. A **failed** line is drawn in red with the verbatim driver/server error shown underneath the same log number (`QueryLogEntry.error`, filled from `MariaDbConnectionManager.queryFailures` — the message is never rewritten).
- `rememberSaveable` for collapse state, `windowInsetsPadding(navigationBars)` so it never sits under gesture nav after `enableEdgeToEdge()`.
- **No hidden queries**: every `connectionManager.executeQuery` call (including background reads like `SHOW FULL COLUMNS`, `SHOW INDEX`, `SHOW CREATE TABLE`, `information_schema` size queries, `SHOW GRANTS`, and `FLUSH PRIVILEGES`) is reflected in the list. This ensures the query log matches the server's `general_log`.

### Session lock vs profile default

`ConnectionProfileEntity.isReadonly` — **default at connect time**, persisted in Room via `updateProfile` / `saveProfile` (only from `ConnectionEditorScreen`).
`ConnectionViewModel.sessionLocked: StateFlow<Boolean>` — **live session state**, initialized `profile.isReadonly` on `connect(profile)`, cleared on `disconnect()`, toggled by `setSessionLocked()` from `AppTopBar` Lock/Unlock (no DB write). `NavGraph` threads `sessionLocked` + `onToggleLock` to every destination. All write paths early-return `Locked — unlock to write` when the passed `isLocked` snapshot is true.

### Query execution

`MariaDbConnectionManager.executeQuery(sql): QueryResult` (`Success(columns, rows)` | `UpdateSuccess` | `Error(message)`) — single JDBC `Connection`, `Properties { connectTimeout 8000, socketTimeout 30000, useSSL/trustServerCertificate, queryTimeout 30s }`, `StrictHostKeyChecking=no` for SSH. `_currentQuery: MutableStateFlow<List<QueryLogEntry>>` / `_query` / `_error` / `_lastQueryDurationMs` are the ViewModel contracts driving `CurrentQueryBar` (query log), error Snackbars and the status bar. Every query that hits the server must be appended to `_currentQuery` before execution.

**Query failures → red log line.** Every execute call that returns `QueryResult.Error` also publishes `QueryFailure(sql, message)` on `MariaDbConnectionManager.queryFailures` (`SharedFlow`, message verbatim from driver/server). Each ViewModel collects it in `init` and calls `withQueryError(sql, message)`, which decorates the matching log entry — a failed statement then shows up red under its own query-log number with the server message beneath it, with no per-error-site wiring. A failure whose SQL is not in that VM's log is ignored.

**Read guard `LIMIT`:** `util/SqlUtil.buildLimitedSql(sql, limit)` / `shouldApplyLimit(sql)` auto-appends `LIMIT` **only for `SELECT`** (stripping leading `--/#//* */`). This prevents accidental `SELECT * FROM large_table` without `LIMIT` from loading 100k rows. `WRITE` queries are never limited — `DataEditorViewModel.getCustomQueryPreview()` guarantees the dialog preview equals the SQL sent to `general_log`. `SHOW`/`DESCRIBE` etc. are not limited (`maxRows=1001` in the driver already caps them). `DataEditorViewModel.executeWithLimit` and `loadMore` respect the same guard.

Write-query rules (preview + confirm + lock) live in [write-safety.md](write-safety.md).

## Navigation & screens

| Route | Screen | Key file | Notes |
|-------|--------|----------|-------|
| `connections` | Connection list | `ui/screens/connection/ConnectionListScreen.kt` | `ConnectionCard` grid, `ThemeManager`-aware, top bar `Upload`/`Download` for encrypted SAF backup (`ProfileCrypto`), per-duplicate `Replace/Skip/Insert + All` dialog, `tap outside = skip remaining` |
| `connection/new` | Create connection | `ui/screens/connection/ConnectionEditorScreen.kt` | Build `ConnectionProfileEntity` + credential snapshot (id `999999` for Test) |
| `connection/edit/{profileId}` | Edit connection | same | `remember(profile.id)` for `isReadonly` checkbox |
| `browser` | Database browser | `ui/screens/browser/DatabaseBrowserScreen.kt` | Drawer + `AppTopBar` (Menu + Disconnect + Refresh + Lock), lazy columns/indexes, `BackHandler` for expanded state, **Manage Saved Queries** entry fixed bottom |
| `manage_saved_queries` | Manage Saved Queries | `ui/screens/query/ManageSavedQueriesScreen.kt` | Grouped by database (`compareBy({it=="Other"}, {it})`), `AnimatedVisibility` expand, Backup/Restore, Rename/Delete/Copy/Open → `query/{db}/_` |
| `query/{database}/{table}` | SQL Editor | `ui/screens/query/SQLEditorScreen.kt` | Per-database tabs (`QueryTab.database` + `savedQueryId`), autocomplete (no auto-popup on open), history tabs, Execute, timing |
| `structure/{database}/{table}` | Table structure | `ui/screens/table/TableStructureScreen.kt` | Full columns/types/keys (add/drop/edit + position/auto-inc, PRI/UNI/MUL/AUTO flags) + index list (cardinality, auto health box, create/drop) |
| `db_structure/{database}` | Database structure | `ui/screens/browser/DbStructureScreen.kt` + `DbStructureViewModel` + `DbStructureSql` | Views / Triggers / Events / Routines per database: lazy definitions, create/edit/drop + preview, DROP+CREATE for trigger/routine edit, event enable toggle |
| `data_editor/{database}/{table}` | Inline data editor | `ui/screens/dataeditor/InlineDataEditorScreen.kt` | Grid, WHERE bar, staging, limit + timing status bar |
| `users` | User management | `ui/screens/user/UserManagementScreen.kt` | Users + Grants tabs |
| `user_detail/{user}/{host}` | Privilege detail | `ui/screens/user/UserPrivilegeDetailScreen.kt` | Full-page drill `user → databases → tables`, per-`ON` 8-priv matrix (staged toggles render red until saved), Rename/Password dialogs |

`NavGraph` holds the only `rememberNavController()` and owns the four shared VMs (`ConnectionViewModel`, `BrowserViewModel`, `QueryViewModel`) across destinations; the remaining VMs are `hiltViewModel()` per destination.

## Security & credentials

- Passwords (DB + SSH) and SSH passphrase are **never** in Room; they live in plain `SharedPreferences` file `sql_client_secure_prefs_v2` as AES-256-GCM ciphertexts keyed by `profile.id` via `CredentialStore` (key in Android Keystore, no user auth required). The file is excluded from Auto Backup (Keystore keys are never backed up).
- `saveProfile` / `updateProfile` persist `ConnectionProfileEntity` (no password column) and delegate sensitive updates to `CredentialStore.savePassword/saveSshPassword/saveSshPassphrase` only when the caller supplies a non-blank value.
- `testConnection` snapshots credentials under ephemeral id `999999` so the DB tunnel does not pollute the real profile's stored creds; `connect(profile)` reads the persisted creds by `profile.id`.
- All writes respect the live `sessionLocked` guard; in read-only sessions even staged mutations are blocked server-side (error path, not just UI disable).
- SSH `StrictHostKeyChecking=no` is intentional for mobile/host-hopping; tighten if your deployment requires known-hosts.
- **Profile backup encryption** — `util/ProfileCrypto.kt` encrypts the JSON export (`version` + `exported_at` + `profiles[]` with `id` + plain passwords) via `PBKDF2WithHmacSHA256` (120k iter) → `AES-256-CBC/PKCS5`, header `Salted__` + 8B salt, **master password not stored** (file is the key, like an SSH key). Any app knowing the password can decrypt. Import: per-duplicate `id` conflict → `Replace` (REPLACE `id`) / `Skip` / `Insert as New` (`id=0` → new autoGenerate) + `Apply to all remaining duplicates (x left)` (2-line, checkbox), `tap outside = skip remaining` (abort same, no rollback), file without `id` (old) auto `Insert`. No DB schema change.

  **Decrypt `.enc` outside the app (1-line `openssl`):**
  ```bash
  # Export from app: Connection list → Upload → set master password → save .enc
  # Pull from device (adjust path):
  adb pull /sdcard/Download/profiles_export_*.enc /tmp/profiles.enc
  # Decrypt with same master password (requires openssl 1.1.1+):
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 120000 -in /tmp/profiles.enc -out /tmp/profiles.json -pass pass:111222333
  cat /tmp/profiles.json | jq .
  # Re-encrypt for import via app:
  openssl enc -aes-256-cbc -pbkdf2 -iter 120000 -in /tmp/profiles.json -out /tmp/profiles.enc -pass pass:111222333
  ```
  Wrong password → `bad decrypt` / app shows `Wrong master password or corrupt file` (PKCS5/GCM tag failure).

## Theming

`ui/theme/SqlClientTheme` driven by `ThemeManager` (persisted preference via `SharedPreferences`):

- `followSystem: Boolean` (default `true`) — when `true`, `isSystemInDarkTheme()` wins; when `false`, `isDarkMode: Boolean` is authoritative.
- `MainActivity` resolves `useDarkTheme` and passes to `SqlClientTheme(darkTheme = ...)`.

Connection cards + `AppTopBar` use per-profile `ConnectionColors` (tinted badge / `containerColor`).
