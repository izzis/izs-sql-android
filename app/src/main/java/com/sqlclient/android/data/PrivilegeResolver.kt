package com.sqlclient.android.data

import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.QueryResult
import com.sqlclient.android.data.remote.model.PrivilegeSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves MySQL/MariaDB privileges for the current connection user.
 * Parse result of `SHOW GRANTS` into [PrivilegeSet] and provide helpers
 * to filter visible databases/tables.
 *
 * Cache-first: caller (ViewModel) decides when to refresh; this class
 * does not auto-poll.
 */
@Singleton
class PrivilegeResolver @Inject constructor(
    private val connectionManager: MariaDbConnectionManager
) {
    @Volatile
    private var cached: PrivilegeSet? = null

    fun getCached(): PrivilegeSet? = cached

    suspend fun loadGrants(force: Boolean = false): PrivilegeSet? {
        if (!force && cached != null) return cached
        val grants = fetchGrants() ?: return null
        val parsed = parseGrants(grants)
        cached = parsed
        return parsed
    }

    fun invalidate() {
        cached = null
    }

    private suspend fun fetchGrants(): List<String>? {
        // SHOW GRANTS without user shows for CURRENT_USER; SHOW GRANTS FOR CURRENT_USER() is more explicit
        val candidates = listOf("SHOW GRANTS", "SHOW GRANTS FOR CURRENT_USER()")
        for (sql in candidates) {
            when (val result = connectionManager.executeQuery(sql)) {
                is QueryResult.Success -> return result.rows.mapNotNull { it.getOrNull(0)?.toString() }
                is QueryResult.Error -> {
                    // try next candidate if access denied / syntax
                    if (result.message.contains("Access denied", ignoreCase = true)) continue
                    // other errors -> try next
                    continue
                }
                else -> continue
            }
        }
        return null
    }

    fun parseGrants(grants: List<String>): PrivilegeSet {
        val global = mutableSetOf<String>()
        val dbMap = mutableMapOf<String, MutableSet<String>>()
        val tableMap = mutableMapOf<String, MutableSet<String>>()
        var hasGlobalAll = false

        for (raw in grants) {
            val upper = raw.uppercase()
            // Extract privilege list and object: GRANT <privs> ON <object> TO ...
            // object is *.*, `db`.*, `db`.`table`
            val onIndex = upper.indexOf(" ON ")
            val toIndex = upper.indexOf(" TO ")
            if (onIndex == -1 || toIndex == -1) continue
            val privPart = raw.substring(5, onIndex).trim() // after "GRANT"
            val objectPart = raw.substring(onIndex + 4, toIndex).trim()

            val privs = privPart.split(",").map { it.trim().uppercase() }
            // Normalize ALL PRIVILEGES -> ALL
            val normalizedPrivs = privs.map { if (it.contains("ALL PRIVILEGES")) "ALL" else it }.toSet()

            when {
                objectPart == "*.*" || objectPart == "`*`.* ``*``" -> {
                    global.addAll(normalizedPrivs)
                    if ("ALL" in normalizedPrivs) hasGlobalAll = true
                }
                objectPart.endsWith(".*") -> {
                    val db = objectPart.removeSuffix(".*").trim().removeSurrounding("`").removeSurrounding("\"")
                    val set = dbMap.getOrPut(db.lowercase()) { mutableSetOf() }
                    set.addAll(normalizedPrivs)
                }
                else -> {
                    // db.table
                    val dbTable = objectPart.replace("`", "").replace("\"", "")
                    val key = dbTable.lowercase()
                    val set = tableMap.getOrPut(key) { mutableSetOf() }
                    set.addAll(normalizedPrivs)
                }
            }
        }
        return PrivilegeSet(
            globalPrivileges = global,
            dbPrivileges = dbMap.mapValues { it.value.toSet() },
            tablePrivileges = tableMap.mapValues { it.value.toSet() },
            hasGlobalAll = hasGlobalAll || "ALL" in global
        )
    }

    fun filterDatabases(all: List<String>, privilegeSet: PrivilegeSet?): List<String> {
        if (privilegeSet == null) return all // unknown -> show all from SHOW DATABASES (server already filters)
        if (privilegeSet.hasGlobalAll) return all
        // If has any global SELECT/SHOW, can see all
        if (privilegeSet.globalPrivileges.any { it.contains("SELECT") || it == "ALL" || it.contains("SHOW") }) return all
        // If user has any db-level grant, show intersecting dbs; if no grants at all, fallback to server list
        if (privilegeSet.dbPrivileges.isEmpty() && privilegeSet.tablePrivileges.isEmpty()) return all
        val lowerAll = all.map { it.lowercase() to it }.toMap()
        val visible = mutableSetOf<String>()
        for (db in privilegeSet.dbPrivileges.keys) {
            lowerAll[db]?.let { visible.add(it) }
        }
        for (key in privilegeSet.tablePrivileges.keys) {
            val db = key.substringBefore(".")
            lowerAll[db]?.let { visible.add(it) }
        }
        // If filtering yields empty but server returned dbs, don't hide everything (fallback)
        return if (visible.isEmpty()) all else visible.sortedWith(String.CASE_INSENSITIVE_ORDER).mapNotNull { name -> all.find { it.equals(name, ignoreCase = true) } ?: name }
    }

    fun filterTables(database: String, tables: List<String>, privilegeSet: PrivilegeSet?): List<String> {
        if (privilegeSet == null) return tables
        if (privilegeSet.hasGlobalAll) return tables
        val dbLower = database.lowercase()
        if (privilegeSet.globalPrivileges.any { it.contains("SELECT") || it == "ALL" }) return tables
        val dbPrivs = privilegeSet.dbPrivileges[dbLower]
        if (dbPrivs != null && (dbPrivs.contains("ALL") || dbPrivs.any { it.contains("SELECT") })) return tables
        // Check per-table grants
        val allowed = tables.filter { table ->
            val key = "$dbLower.${table.lowercase()}"
            val tp = privilegeSet.tablePrivileges[key]
            tp != null && (tp.contains("ALL") || tp.any { it.contains("SELECT") })
        }
        // If no per-table grants but db has no explicit deny, show all (fallback to server)
        return if (privilegeSet.tablePrivileges.keys.none { it.startsWith("$dbLower.") } && dbPrivs == null) tables else allowed
    }

    fun canWrite(database: String, table: String?, privilegeSet: PrivilegeSet?): Boolean {
        if (privilegeSet == null) return true // unknown -> allow, server will enforce
        if (privilegeSet.hasGlobalAll) return true
        // Check global write privs
        val writePrivs = setOf("INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER", "INDEX", "ALL")
        if (privilegeSet.globalPrivileges.any { it in writePrivs }) return true
        val dbLower = database.lowercase()
        val dbPrivs = privilegeSet.dbPrivileges[dbLower] ?: emptySet()
        if (dbPrivs.any { it in writePrivs }) return true
        if (table != null) {
            val key = "$dbLower.${table.lowercase()}"
            val tp = privilegeSet.tablePrivileges[key] ?: emptySet()
            if (tp.any { it in writePrivs }) return true
        }
        // If no explicit write grants, but has SELECT, still deny write
        return false
    }
}
