package com.sqlclient.android.util

object SqlUtil {
    private val WRITE_PREFIXES = listOf(
        "INSERT", "UPDATE", "DELETE", "ALTER", "DROP", "CREATE", "TRUNCATE", "RENAME", "GRANT", "REVOKE"
    )

    /**
     * Strip leading whitespace and SQL comments (--, #, /* */) repeatedly.
     * Used so isWriteQuery / shouldApplyLimit don't miss " /*comment*/ SELECT"
     */
    fun stripLeading(sql: String): String {
        var s = sql
        while (true) {
            val trimmed = s.trimStart()
            when {
                trimmed.startsWith("--") -> {
                    val nl = trimmed.indexOf('\n')
                    s = if (nl == -1) "" else trimmed.substring(nl + 1)
                }
                trimmed.startsWith("#") -> {
                    val nl = trimmed.indexOf('\n')
                    s = if (nl == -1) "" else trimmed.substring(nl + 1)
                }
                trimmed.startsWith("/*") -> {
                    val end = trimmed.indexOf("*/")
                    s = if (end == -1) "" else trimmed.substring(end + 2)
                }
                else -> return trimmed
            }
        }
    }

    /** True if any statement (split by ;) is a WRITE. Used for confirm dialog & lock gate. */
    fun isWriteQuery(sql: String): Boolean {
        if (sql.isBlank()) return false
        // naive split by ; - sufficient for UX guard (quotes not handled, but safe to over-flag as write)
        val statements = sql.split(";")
        for (raw in statements) {
            val s = stripLeading(raw).trim()
            if (s.isEmpty()) continue
            val up = s.uppercase()
            for (prefix in WRITE_PREFIXES) {
                if (up == prefix) return true
                if (up.startsWith(prefix + " ") || up.startsWith(prefix + "(") || up.startsWith(prefix + "\n") || up.startsWith(prefix + "\t")) {
                    return true
                }
                if (up.startsWith(prefix)) {
                    if (up.length == prefix.length) return true
                    val next = up[prefix.length]
                    if (!next.isLetterOrDigit() && next != '_') return true
                }
            }
        }
        return false
    }

    /**
     * Whether we should auto-append LIMIT for read guard.
     * Per spec: SELECT only (not SHOW etc). Driver already caps via maxRows=1001.
     */
    fun shouldApplyLimit(sql: String): Boolean {
        val s = stripLeading(sql).trim()
        if (s.isEmpty()) return false
        // don't limit writes
        if (isWriteQuery(s)) return false
        val up = s.uppercase()
        return up.startsWith("SELECT")
    }

    /** Build the actual SQL that will be sent (with LIMIT if needed). Shared for preview & execution. */
    fun buildLimitedSql(sql: String, limit: Int): String {
        val trimmed = sql.trim()
        if (trimmed.isEmpty()) return trimmed
        if (!shouldApplyLimit(trimmed)) return trimmed
        if (trimmed.uppercase().contains("LIMIT")) return trimmed
        return "$trimmed LIMIT $limit"
    }
}
