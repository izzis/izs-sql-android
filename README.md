# izs SQL — Android

Native Android SQL client for **MariaDB/MySQL** — browse databases/tables, run queries, edit data inline, manage users/privileges, and work through an **SSH tunnel**. Built with **Kotlin + Jetpack Compose (Material 3)**.

> **Status: alpha.** Core flows work; expect bugs, incomplete flows and rough edges. See [Known issues & roadmap](docs/development.md#known-issues--roadmap).

## Docs

| Document | Contents |
|---|---|
| [docs/setup.md](docs/setup.md) | Requirements, build APK dari nol, konfigurasi SDK |
| [docs/architecture.md](docs/architecture.md) | Struktur project, arsitektur, key concepts, navigasi, security |
| [docs/write-safety.md](docs/write-safety.md) | Aturan + riwayat audit write-query (wajib baca sebelum ubah query flow) |
| [docs/development.md](docs/development.md) | Workflow, testing, troubleshooting, roadmap |

## Features

- **Connection management** — create/edit/delete profiles, colored badges, read-only default per connection.
- **Direct & SSH tunnel connections** — MariaDB JDBC 2.4.4 + JSch tunnelling (password or key + passphrase).
- **Database browser** — privilege-filtered sidebar + main panel, manual refresh.
- **SQL editor with autocomplete** — syntax highlighting, context-aware `db.table.column` + JOIN autocomplete. Write queries show a confirm dialog; TopBar refresh only re-executes reads.
- **Inline data editor** — paginated grid with infinite scroll, WHERE filter, per-cell edit, batch staging, custom query bar with write-confirm.
- **Batch write flow** — every write goes **Save → Confirm (SQL preview) → Execute**.
- **User & privilege management** — `mysql.user` list, per-`user@host` privilege matrix.
- **Indexes / table structure** — structure + index health (duplicate/redundant/low-selectivity), create/drop with preview.
- **Views / Triggers / Events / Routines** — per-database create/edit/drop + preview.
- **Session lock vs profile default** — live lock toggle, wired to every route.
- **Saved queries** — grouped by database, backup/restore JSON.
- **Current query bar** — bottom query log; every query hitting the server is shown (matches `general_log`).
- **Export** — CSV/etc. via `FileProvider`.
- **Profile backup (encrypted)** — SAF `.enc` (`PBKDF2 120k + AES-256-CBC`), per-duplicate Replace/Skip/Insert.
- **Import from DBeaver** — `.dbp` / `data-sources.json` (MySQL/MariaDB only).

Detail tiap fitur: [docs/architecture.md](docs/architecture.md).

## Quick start

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Dari nol (install JDK/SDK): [docs/setup.md](docs/setup.md).

## AI session bootstrap

> `opencode.json` declares `instructions: ["README.md"]` so this file is auto-loaded.

1. **Baca file ini**, lalu sesuai kebutuhan:
   - Setup/build — [docs/setup.md](docs/setup.md)
   - Arsitektur/VM/routes — [docs/architecture.md](docs/architecture.md)
   - Write flow & audit — **[docs/write-safety.md](docs/write-safety.md)** (wajib sebelum menyentuh query)
   - Workflow/test/troubleshoot — [docs/development.md](docs/development.md)
2. **Prefer `Glob`/`Grep`** untuk discovery; peta struktur ada di [docs/architecture.md](docs/architecture.md#project-structure).
3. **Write query safety**: semua write WAJIB preview + confirm dialog + lock gate di ViewModel. Klasifikasi `SqlUtil.isWriteQuery()` **default-deny** (`FLUSH` satu-satunya exception by-design). Refresh tidak boleh eksekusi write. Detail: [docs/write-safety.md](docs/write-safety.md).
4. **Query log**: setiap query ke server harus di-append ke `_currentQuery` sebelum `executeQuery()` — no hidden queries. Query gagal → barisnya merah + pesan error dari server (verbatim) di bawah nomor log yang sama (`QueryLogEntry.error`, dari `connectionManager.queryFailures`).
5. **Jangan persist `sessionLocked`** — live state `ConnectionViewModel` saja.
6. **Testing gate**: fitur query baru WAJIB tambah/update test (`SqlUtilTest`, 22 tests) dan jaga CI hijau.
7. `permission: { bash: { "git commit*": "ask", "git push*": "ask" } }` — commit/push butuh approval.

## License

No license published yet. Treat this repo as **all rights reserved** until a `LICENSE` file is added — do not redistribute built APKs without permission.
