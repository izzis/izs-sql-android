package id.web.izs.sqlclient.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.web.izs.sqlclient.data.remote.model.ColumnInfo
import id.web.izs.sqlclient.data.remote.model.DatabaseInfo
import id.web.izs.sqlclient.data.remote.model.IndexInfo
import id.web.izs.sqlclient.data.remote.model.UserInfo

// Flattened rows so the tree is lazy per row: one database, table, folder, column or
// index per item, instead of composing an entire database subtree inside one eager item.
private sealed interface TreeRow {
    val rowKey: String

    data class Database(val db: String) : TreeRow {
        override val rowKey: String get() = "db:$db"
    }

    data class DbStatus(val db: String, val loading: Boolean) : TreeRow {
        override val rowKey: String get() = "status:$db"
    }

    data class NewTable(val db: String) : TreeRow {
        override val rowKey: String get() = "new:$db"
    }

    data class Table(val db: String, val table: String) : TreeRow {
        override val rowKey: String get() = "tbl:$db.$table"
    }

    data class ColumnsFolder(val tableKey: String) : TreeRow {
        override val rowKey: String get() = "cols:$tableKey"
    }

    data class ColumnItem(val tableKey: String, val column: ColumnInfo) : TreeRow {
        override val rowKey: String get() = "col:$tableKey:${column.name}"
    }

    data class IndexesFolder(val tableKey: String) : TreeRow {
        override val rowKey: String get() = "idx:$tableKey"
    }

    data class IndexItem(val tableKey: String, val index: IndexInfo) : TreeRow {
        override val rowKey: String get() = "idxi:$tableKey:${index.name}"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DatabaseTree(
    databases: List<DatabaseInfo>,
    tables: Map<String, List<String>>,
    columns: Map<String, List<ColumnInfo>>,
    indexes: Map<String, List<IndexInfo>>,
    tableSizes: Map<String, String>,
    expandedDatabases: Set<String>,
    expandedTables: Set<String>,
    modifier: Modifier = Modifier,
    loadingDatabases: Set<String> = emptySet(),
    searchQuery: String,
    users: List<UserInfo>,
    onDatabaseClick: (String) -> Unit,
    onDatabaseOpen: (String) -> Unit = {},
    onTableClick: (String, String) -> Unit,
    onTableSelect: (String, String) -> Unit,
    onTableDataClick: (String, String) -> Unit = { _, _ -> },
    onCreateTable: (String) -> Unit = {},
    onTableActions: (String, String) -> Unit = { _, _ -> },
    isLocked: Boolean = false,
    onRefreshDatabase: (String) -> Unit = {},
    onRefreshSizes: (String) -> Unit = {},
    onUsersClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val filteredDatabases = if (searchQuery.isBlank()) {
        databases
    } else {
        databases.filter { db ->
            db.name.contains(searchQuery, ignoreCase = true) ||
                    tables[db.name]?.any { it.contains(searchQuery, ignoreCase = true) } == true
        }
    }

    val treeRows = remember(filteredDatabases, expandedDatabases, tables, columns, indexes, expandedTables, loadingDatabases, searchQuery, isLocked) {
        buildList {
            for (database in filteredDatabases) {
                val name = database.name
                add(TreeRow.Database(name))
                if (!expandedDatabases.contains(name)) continue

                val dbTables = tables[name]
                val filteredTables = if (searchQuery.isBlank()) {
                    dbTables ?: emptyList()
                } else {
                    (dbTables ?: emptyList()).filter { it.contains(searchQuery, ignoreCase = true) }
                }

                when {
                    loadingDatabases.contains(name) -> add(TreeRow.DbStatus(name, loading = true))
                    dbTables != null && dbTables.isEmpty() -> add(TreeRow.DbStatus(name, loading = false))
                    else -> {}
                }
                if (!isLocked && dbTables != null && searchQuery.isBlank()) {
                    add(TreeRow.NewTable(name))
                }
                if (dbTables != null && dbTables.isNotEmpty()) {
                    for (table in filteredTables) {
                        val tableKey = "$name.$table"
                        add(TreeRow.Table(name, table))
                        if (expandedTables.contains(tableKey)) {
                            val tableColumns = columns[tableKey]
                            val tableIndexList = indexes[tableKey]
                            add(TreeRow.ColumnsFolder(tableKey))
                            if (tableColumns != null) {
                                for (column in tableColumns) add(TreeRow.ColumnItem(tableKey, column))
                            }
                            add(TreeRow.IndexesFolder(tableKey))
                            if (tableIndexList != null) {
                                for (index in tableIndexList) add(TreeRow.IndexItem(tableKey, index))
                            }
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "users") {
            TreeSectionHeader(
                icon = Icons.Default.People,
                title = "Users",
                subtitle = if (users.isNotEmpty()) "${users.size}" else null,
                onClick = onUsersClick
            )
        }

        item(key = "history") {
            TreeSectionHeader(
                icon = Icons.Default.History,
                title = "Query History",
                subtitle = null,
                onClick = onHistoryClick
            )
        }

        item(key = "databases-header") {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Databases",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // Refresh via TopBar only — no duplicate here
            }
        }

        items(treeRows, key = { it.rowKey }) { entry ->
            when (entry) {
                is TreeRow.Database -> {
                    val name = entry.db
                    val isExpanded = expandedDatabases.contains(name)
                    val dbTables = tables[name]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.IconButton(
                            onClick = { onDatabaseClick(name) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.weight(1f).clickable { onDatabaseOpen(name) }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (loadingDatabases.contains(name)) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        } else if (dbTables != null && dbTables.isNotEmpty()) {
                            Text(
                                text = "${dbTables.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            androidx.compose.material3.IconButton(onClick = { onRefreshDatabase(name) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh tables", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (tableSizes.isEmpty() || dbTables.any { tableSizes["$name.$it"] == null }) {
                                androidx.compose.material3.IconButton(onClick = { onRefreshSizes(name) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Storage, contentDescription = "Load sizes", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                is TreeRow.DbStatus -> {
                    if (entry.loading) {
                        Row(
                            modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(
                            text = "No tables",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                }

                is TreeRow.NewTable -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCreateTable(entry.db) }
                            .padding(start = 32.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New table",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                is TreeRow.Table -> {
                    val tableKey = "${entry.db}.${entry.table}"
                    val isTableExpanded = expandedTables.contains(tableKey)
                    val tableSize = tableSizes[tableKey]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp)
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.IconButton(
                            onClick = { onTableClick(entry.db, entry.table) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = if (isTableExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isTableExpanded) "Collapse" else "Expand",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .combinedClickable(
                                    onClick = { onTableDataClick(entry.db, entry.table) },
                                    onLongClick = { onTableActions(entry.db, entry.table) }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = entry.table,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (tableSize != null) {
                                Text(
                                    text = tableSize,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is TreeRow.ColumnsFolder -> {
                    val tableColumns = columns[entry.tableKey]
                    TreeSubFolder(
                        title = "Columns",
                        count = tableColumns?.size,
                        isLoading = tableColumns == null,
                        modifier = Modifier.padding(start = 32.dp)
                    )
                }

                is TreeRow.ColumnItem -> {
                    val column = entry.column
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp)
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (column.isPrimaryKey) Icons.Default.Key else Icons.Default.ViewColumn,
                            contentDescription = null,
                            tint = if (column.isPrimaryKey) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(12.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = column.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (column.isPrimaryKey) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = column.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is TreeRow.IndexesFolder -> {
                    val tableIndexList = indexes[entry.tableKey]
                    TreeSubFolder(
                        title = "Indexes",
                        count = tableIndexList?.size,
                        isLoading = tableIndexList == null,
                        modifier = Modifier.padding(start = 32.dp)
                    )
                }

                is TreeRow.IndexItem -> {
                    val index = entry.index
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp)
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (index.name == "PRIMARY") Icons.Default.Key else Icons.Default.Menu,
                                contentDescription = null,
                                tint = if (index.name == "PRIMARY") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = index.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (index.name == "PRIMARY") FontWeight.Bold else FontWeight.Normal
                            )

                            if (index.isUnique) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "UNIQUE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        Text(
                            text = "→ ${index.columns.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 18.dp, top = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeSubFolder(
    title: String,
    count: Int?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isLoading) {
            Spacer(modifier = Modifier.width(6.dp))
            CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.dp)
        } else if (count != null) {
            Text(
                text = " ($count)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TreeSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
