# Write-query safety — izs SQL Android

> Back to [README](../README.md) · Architecture: [architecture.md](architecture.md)
>
> These rules are the **audit contract**: every query sent to the server that
> is a write **must** pass a confirmation window (preview + Execute button)
> and the lock gate. The displayed preview must exactly equal the string sent
> (== what lands in the server `general_log` when enabled).

## Classification: default-deny (`util/SqlUtil.kt`)

`isWriteQuery()` treats every statement as WRITE **unless** it matches the
small READ whitelist (reads are fewer, so this list stays short):

`SELECT, SHOW, DESCRIBE, DESC, EXPLAIN, WITH, USE, HELP, VALUES, TABLE`

Details:

- Multi-statements are split per `;` — a single write marks the whole input write.
- Leading comments (`--`, `#`, `/* */`) are stripped first so nothing can hide.
- `SELECT ... INTO OUTFILE/DUMPFILE`, `... FOR UPDATE`, `... LOCK IN SHARE MODE`
  still count as write (and are excluded from auto-`LIMIT`).
- Typo/unknown (`INSERTINTO`, foreign statements) = write (confirm first, safe).
- Side effect: `MariaDbConnectionManager.executeQuery()` only retries
  statements where `!isWriteQuery` — writes are never double-applied.

### The single exception: `FLUSH`

`FLUSH` is **deliberately not a write** — the safest write-like statement (no
data/schema change, only reloads privileges/caches). The app auto-fires
`FLUSH PRIVILEGES` after GRANT/user ops by design: no dialog, but still appended
to the query log (`CurrentQueryBar`) to match `general_log`. Staged statements
(queued in the UI, not sent yet) are drawn **amber + "staged"** so they never
pass for executed ones; they flip to a normal line when sent and leave the log
when discarded.

## Mandatory rules

1. **SQL preview before execution** — the user must see the exact SQL being sent:
   - `pendingSql` pattern: preview dialog + Execute button (InsertRow, Grant/Revoke,
     Create/Drop/Rename User, Change Password, Create/Drop Index, Rename Table,
     Add/Drop Column, all DbStructure view/trigger/event/routine).
   - `showSaveConfirm` pattern: batch staging shows all SQL in
     `Confirm Write (N)` (data editor staged edits/deletes, privilege batch).
   - `showWriteConfirm` pattern: free-text editor bars detect via `isWriteQuery()`
     (SQL editor + data editor custom query; preview via `getCustomQueryPreview()`
     = `buildLimitedSql` so dialog == `general_log`).
2. **Confirm button required** — no write runs on a plain tap.
3. **Lock gate in the ViewModel** — when `isLocked = true`, every write function
   returns `Locked — unlock to write`. Check in the ViewModel, never rely on the
   UI button `enabled` state alone.
4. **TopBar refresh must never execute writes** — SQL editor skips when
   `isWriteQuery()`; data editor calls `refreshData()` (SELECT only).

## Per-path coverage (audit 2026-09-23)

| Path | Confirm | Lock gate |
|---|---|---|
| Free SQL editor (`SQLEditorScreen` + `QueryViewModel`) | `showWriteConfirm` | `executeQuery(isLocked)` |
| Data editor custom query | `showWriteConfirm` | `executeCustomQuery(isLocked)` |
| Data editor staged save/insert | `showSaveConfirm` → `commitPending` / `pendingSql` → `insertRow` | ✅ both |
| Browser create/drop/rename/truncate table | `pendingSql` (2 dialogs) | `runTableWrite(isLocked)` + rejects non-write |
| Table structure columns/indexes | `pendingSql` | 5 `IndexManagementViewModel` functions ✅ |
| DbStructure view/trigger/event/routine | `pendingSql` | 12 `DbStructureViewModel` functions ✅ |
| User/privilege (7 ops + batch) | `pendingSql` / `Confirm Write (N)` | 7 `UserPermissionViewModel` functions ✅ |
| Auto `USE db` / `executeQueryIfFree` (autocomplete, ping, size) | by-design without dialog (internal reads) | N/A — not free user input |
| Auto `FLUSH PRIVILEGES` | by-design exception (in query log) | follows parent op |
| `executeUpdate()` | dead code, no callers | — |

## Audit history

- **Audit 1 (earlier):** only `FLUSH PRIVILEGES` bypassed confirmation —
  deliberate (not a real write). Status: kept as exception.
- **Audit 2 (2026-09-23):** classification changed from a 10-prefix blocklist
  (`INSERT..REVOKE`) to **default-deny** so `REPLACE/CALL/SET/LOAD/BEGIN/KILL/...`
  can't slip through silently. Finding: `DataEditorViewModel.executeCustomQuery()`
  had no `isLocked` param (UI-button gate only) → fixed
  (`executeCustomQuery(isLocked)` + `isLocked` passed from 2 call-sites).
  Verified: `SqlUtilTest` 22/22 green, `assembleDebug` success.
