package com.sqlclient.android.data.remote.model

data class TableInfo(
    val name: String,
    val schema: String,
    val type: String = "BASE TABLE",
    val engine: String? = null,
    val rows: Long? = null,
    val comment: String? = null
)

data class ColumnInfo(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val defaultValue: String?,
    val isPrimaryKey: Boolean,
    val isAutoIncrement: Boolean,
    val comment: String?,
    val maxLength: Int?,
    /** Raw Key flag from SHOW FULL COLUMNS: PRI / UNI / MUL / "" (priority order, as MySQL reports). */
    val keyType: String = ""
)

data class IndexInfo(
    val name: String,
    val columns: List<String>,
    val isUnique: Boolean,
    val type: String,
    /** Estimated distinct values from SHOW INDEX (null when unknown / not parsed). */
    val cardinality: Long? = null
)

/** Row of SHOW TRIGGERS (Statement = action body, no extra query needed to display). */
data class TriggerInfo(
    val name: String,
    val event: String,
    val table: String,
    val statement: String,
    val timing: String,
    val definer: String? = null
)

/** Row of SHOW EVENTS (schedule detail comes from SHOW CREATE EVENT). */
data class EventInfo(
    val name: String,
    val status: String,
    val eventType: String,
    val executeAt: String?,
    val intervalValue: String?,
    val intervalField: String?,
    val starts: String?,
    val ends: String?
)

/** Row of information_schema.ROUTINES (kind = PROCEDURE or FUNCTION). */
data class RoutineInfo(
    val name: String,
    val kind: String
)

data class ForeignKeyInfo(
    val name: String,
    val columns: List<String>,
    val referencedTable: String,
    val referencedColumns: List<String>,
    val onDelete: String?,
    val onUpdate: String?
)

data class UserInfo(
    val user: String,
    val host: String,
    val selectPriv: Boolean = false,
    val insertPriv: Boolean = false,
    val updatePriv: Boolean = false,
    val deletePriv: Boolean = false,
    val createPriv: Boolean = false,
    val dropPriv: Boolean = false,
    val alterPriv: Boolean = false,
    val indexPriv: Boolean = false
)

data class PermissionInfo(
    val grant: String,
    val privilege: String,
    val onObject: String,
    val isGranted: Boolean = true
)

data class DatabaseInfo(
    val name: String
)

data class PrivilegeSet(
    val globalPrivileges: Set<String> = emptySet(),
    val dbPrivileges: Map<String, Set<String>> = emptyMap(),
    val tablePrivileges: Map<String, Set<String>> = emptyMap(),
    val hasGlobalAll: Boolean = false
)
