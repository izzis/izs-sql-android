package id.web.izs.sqlclient.util

/**
 * One line of the query log rendered by
 * [id.web.izs.sqlclient.ui.components.CurrentQueryBar].
 *
 * [sql] is the exact statement sent to the server (what `general_log` would show).
 * [error] is the verbatim driver/server message when that statement failed, `null` while
 * it is still running or when it succeeded — a failed line is drawn in red with the message
 * underneath it.
 */
data class QueryLogEntry(
    val sql: String,
    val error: String? = null
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
