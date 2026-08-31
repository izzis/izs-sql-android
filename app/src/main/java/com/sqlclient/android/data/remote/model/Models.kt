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
    val maxLength: Int?
)

data class IndexInfo(
    val name: String,
    val columns: List<String>,
    val isUnique: Boolean,
    val type: String
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
