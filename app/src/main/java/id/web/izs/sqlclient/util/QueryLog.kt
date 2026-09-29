package id.web.izs.sqlclient.util

/**
 * One line of the query log rendered by
 * [id.web.izs.sqlclient.ui.components.CurrentQueryBar].
 *
 * [sql] is the exact statement sent to the server (what `general_log` would show).
 * [error] is the verbatim driver/server message when that statement failed, `null` while
 * it is still running or when it succeeded — a failed line is drawn in red with the message
 * underneath it.
 * [staged] marks a statement that is only *queued* in the UI (pending cell/privilege edits or
 * the active editor tab) and has not been sent yet — drawn amber with a "staged" label. A staged
 * line becomes a normal line the moment it is executed and is dropped when the edits are
 * discarded, so the log never shows a cancelled statement as if it had run.
 */
data class QueryLogEntry(
    val sql: String,
    val error: String? = null,
    val staged: Boolean = false
)

/**
 * Copy of this log with [message] attached to the LAST entry whose SQL equals [sql]
 * (the last one is the one that was just executed when the same statement is logged more
 * than once). Entries the log does not contain are ignored — a failure only ever decorates
 * a line that was already appended, so "no hidden queries" still holds both ways.
 */
fun List<QueryLogEntry>.withQueryError(sql: String, message: String): List<QueryLogEntry> {
    val idx = indexOfLast { it.sql == sql }
    if (idx < 0) return this
    if (this[idx].error == message) return this
    return toMutableList().also { it[idx] = it[idx].copy(error = message) }
}

/**
 * Copy of this log whose staged lines are replaced by one staged line per [sqls]. Executed
 * history is untouched — staging must never hide what already reached the server.
 */
fun List<QueryLogEntry>.withStaged(sqls: List<String>): List<QueryLogEntry> {
    if (filter { it.staged }.map { it.sql } == sqls) return this
    val kept = filterNot { it.staged }
    return kept + sqls.map { QueryLogEntry(it, staged = true) }
}

/** Copy of this log without its staged lines (pending edits discarded). */
fun List<QueryLogEntry>.withoutStaged(): List<QueryLogEntry> {
    if (none { it.staged }) return this
    return filterNot { it.staged }
}

/**
 * One query-log event: [sql] exactly as sent and [error] the verbatim driver/server message
 * when it failed. [append] adds the line for a statement the connection layer sent; `false`
 * marks the line a caller already logged for [sql] instead — a failed first attempt is
 * reported against the line written before it went out, so it is never confused with a send.
 *
 * [origin] is the statement that provoked this one. Every screen keeps its own log (its own
 * [id.web.izs.sqlclient.ui.components.CurrentQueryBar]), so an appended line is only kept by
 * a log holding [origin]: a retry inside the data editor must not turn up in the browser's
 * log, where nobody sent it. `null` means no statement provoked it — the schema restore when
 * a connection opens — which is a session-wide event any log may show.
 */
data class QueryStatement(
    val sql: String,
    val error: String? = null,
    val append: Boolean = true,
    val origin: String? = null,
)

/**
 * Copy of this log with [statement] applied: a failure marks the last line for its SQL (a
 * no-op when this log never sent it — see [withQueryError]), an appended line is kept only
 * when it belongs here, per [QueryStatement.origin].
 */
fun List<QueryLogEntry>.withStatement(statement: QueryStatement): List<QueryLogEntry> {
    if (!statement.append) return withQueryError(statement.sql, statement.error.orEmpty())
    if (statement.origin != null && none { it.sql == statement.origin }) return this
    return this + QueryLogEntry(statement.sql, error = statement.error)
}

/**
 * The statement [sql] is about to be sent: an existing staged line for it flips to executed
 * in place (no duplicate after Save), and when nothing was staged for it the line is appended.
 * Call before `executeQuery` so "logged before send" still holds.
 */
fun List<QueryLogEntry>.markExecuted(sql: String): List<QueryLogEntry> {
    val idx = indexOfLast { it.staged && it.sql == sql }
    if (idx >= 0) return toMutableList().also { it[idx] = it[idx].copy(staged = false) }
    return this + QueryLogEntry(sql)
}
