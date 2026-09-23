package id.web.izs.sqlclient.util

object SqlUtil {
    /**
     * Known-safe READ statement prefixes. Everything else is conservatively
     * treated as WRITE (default-deny) so an unknown/dangerous statement always
     * requires the Confirm Write dialog instead of executing silently.
     *
     * FLUSH is deliberately NOT a write: it is the safest write-like statement
     * (no data/schema change, only reloads privileges/caches) and the app
     * auto-fires "FLUSH PRIVILEGES" after GRANT/user ops by design.
     */
    private val READ_PREFIXES = listOf(
        "SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN", "WITH", "USE", "HELP", "VALUES", "TABLE"
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
            // FLUSH is explicitly safe (see above) — never a write, even typed manually.
            if (hasPrefixWord(up, "FLUSH")) continue
            var matchedRead: String? = null
            for (prefix in READ_PREFIXES) {
                if (hasPrefixWord(up, prefix)) { matchedRead = prefix; break }
            }
            if (matchedRead == null) return true // default-deny: unknown statement = write
            if (matchedRead == "SELECT" || matchedRead == "WITH" || matchedRead == "TABLE" || matchedRead == "VALUES") {
                // SELECT with side effects is still a write (and must not get auto-LIMIT).
                if (up.contains("INTO OUTFILE") || up.contains("INTO DUMPFILE")) return true
                if (up.contains("FOR UPDATE") || up.contains("LOCK IN SHARE MODE")) return true
            }
        }
        return false
    }

    /** Prefix match on a word boundary (handles "SELECT ", "SELECT(", "SELECT\n", "SELECT;" etc). */
    private fun hasPrefixWord(up: String, prefix: String): Boolean {
        if (up == prefix) return true
        if (!up.startsWith(prefix)) return false
        if (up.length == prefix.length) return true
        val next = up[prefix.length]
        return !next.isLetterOrDigit() && next != '_'
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
