# izs SQL — Android

Native Android SQL client for **MariaDB/MySQL** — browse databases/tables, run queries, edit data inline, manage users/privileges, and work through an **SSH tunnel**. Built with **Kotlin + Jetpack Compose (Material 3)**.

> **Status: alpha.** Core flows work; expect bugs, incomplete flows and rough edges. See [Known issues & roadmap](docs/development.md#known-issues--roadmap).

## Docs

| Document | Contents |
|---|---|
| [docs/setup.md](docs/setup.md) | Requirements, build an APK from scratch, SDK configuration |
| [docs/architecture.md](docs/architecture.md) | Project structure, architecture, key concepts, navigation, security |
| [docs/write-safety.md](docs/write-safety.md) | Rules + write-query audit history (must read before touching the query flow) |
| [docs/development.md](docs/development.md) | Workflow, testing, troubleshooting, roadmap |

## Features

- **Connection management** — create/edit/delete profiles, colored badges, read-only default per connection.
- **Direct & SSH tunnel connections** — MariaDB JDBC 2.4.4 + JSch tunnelling (password or key + passphrase).
- **Database browser** — privilege-filtered sidebar + main panel, manual refresh.
- **SQL editor with autocomplete** — syntax highlighting, context-aware `db.table.column` + JOIN autocomplete. Write queries show a confirm dialog; TopBar refresh only re-executes reads.
- **Inline data editor** — paginated grid with infinite scroll, WHERE filter, per-cell edit, batch staging, custom query bar with write-confirm.
- **Batch write flow** — every write goes **Save → Confirm (SQL preview) → Execute**.
- **User & privilege management** — `mysql.user` list, per-`user@host` privilege matrix; staged (not yet saved) grants are highlighted red until Save → Confirm → Execute.
- **Indexes / table structure** — structure + index health (duplicate/redundant/low-selectivity), create/drop with preview.
- **Views / Triggers / Events / Routines** — per-database create/edit/drop + preview.
- **Session lock vs profile default** — live lock toggle, wired to every route.
- **Saved queries** — grouped by database, backup/restore JSON.
- **Current query bar** — bottom query log; every query hitting the server is shown (matches `general_log`), queued-but-unsent statements show amber `staged`, cleared on page change (or its trash button), refresh keeps the history.
- **Export** — CSV/etc. via `FileProvider`.
- **Profile backup (encrypted)** — SAF `.enc` (`PBKDF2 120k + AES-256-CBC`), per-duplicate Replace/Skip/Insert.
- **Import from DBeaver** — `.dbp` / `data-sources.json` (MySQL/MariaDB only).

Per-feature details: [docs/architecture.md](docs/architecture.md).

## Quick start

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

From scratch (install JDK/SDK): [docs/setup.md](docs/setup.md).

## AI session bootstrap

> `opencode.json` declares `instructions: ["README.md"]` so this file is auto-loaded.

1. **Read this file first**, then go to what you need:
   - Setup/build — [docs/setup.md](docs/setup.md)
   - Architecture/VM/routes — [docs/architecture.md](docs/architecture.md)
   - Write flow & audit — **[docs/write-safety.md](docs/write-safety.md)** (required before touching the query flow)
   - Workflow/test/troubleshoot — [docs/development.md](docs/development.md)
2. **Prefer `Glob`/`Grep`** for discovery; the structure map lives in [docs/architecture.md](docs/architecture.md#project-structure).
3. **Write query safety**: every write MUST have a preview + confirm dialog + lock gate in the ViewModel. `SqlUtil.isWriteQuery()` classification is **default-deny** (`FLUSH` is the only by-design exception). Refresh must never execute a write. Details: [docs/write-safety.md](docs/write-safety.md).
4. **Query log**: every query sent to the server must be appended to `_currentQuery` before `executeQuery()` — no hidden queries. A failed query → red row + the server error message (verbatim) under the same log number (`QueryLogEntry.error`, from `connectionManager.queryFailures`). A **staged** query (not yet sent) → amber row + `staged` label, removed on Discard, flips to a normal row once executed. The log **clears only on page change** / the bar's clear button — refresh never clears it.
5. **Never persist `sessionLocked`** — live state on `ConnectionViewModel` only.
6. **Testing gate**: new query features MUST add/update tests (`SqlUtilTest`, 22 tests) and keep CI green.
7. `permission: { bash: { "git commit*": "ask", "git push*": "ask" } }` — commit/push require approval.

## License

[MIT](LICENSE) — free to use, modify, and redistribute, with attribution.

DBeaver import support (`DbeaverImport.kt`) reimplements DBeaver's project
format (Apache-2.0); see the file header for details.
