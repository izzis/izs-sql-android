package com.sqlclient.android.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sqlclient.android.data.remote.model.ColumnInfo
import com.sqlclient.android.data.remote.model.DatabaseInfo
import com.sqlclient.android.data.remote.model.IndexInfo
import com.sqlclient.android.data.remote.model.UserInfo

@Composable
fun DatabaseTree(
    databases: List<DatabaseInfo>,
    tables: Map<String, List<String>>,
    columns: Map<String, List<ColumnInfo>>,
    indexes: Map<String, List<IndexInfo>>,
    tableSizes: Map<String, String>,
    expandedDatabases: Set<String>,
    expandedTables: Set<String>,
    loadingDatabases: Set<String> = emptySet(),
    searchQuery: String,
    users: List<UserInfo>,
    onDatabaseClick: (String) -> Unit,
    onDatabaseOpen: (String) -> Unit = {},
    onTableClick: (String, String) -> Unit,
    onTableSelect: (String, String) -> Unit,
    onTableDataClick: (String, String) -> Unit = { _, _ -> },
    onRefreshDatabase: (String) -> Unit = {},
    onRefreshSizes: (String) -> Unit = {},
    onUsersClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredDatabases = if (searchQuery.isBlank()) {
        databases
    } else {
        databases.filter { db ->
            db.name.contains(searchQuery, ignoreCase = true) ||
                    tables[db.name]?.any { it.contains(searchQuery, ignoreCase = true) } == true
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            TreeSectionHeader(
                icon = Icons.Default.People,
                title = "Users",
                subtitle = if (users.isNotEmpty()) "${users.size}" else null,
                onClick = onUsersClick
            )
        }

        item {
            TreeSectionHeader(
                icon = Icons.Default.History,
                title = "Query History",
                subtitle = null,
                onClick = onHistoryClick
            )
        }

        item {
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

        items(filteredDatabases, key = { "db_${it.name}" }) { database ->
            val isExpanded = expandedDatabases.contains(database.name)
            val dbTables = tables[database.name]

            val filteredTables = if (searchQuery.isBlank()) {
                dbTables ?: emptyList()
            } else {
                (dbTables ?: emptyList()).filter { it.contains(searchQuery, ignoreCase = true) }
            }

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.IconButton(
                        onClick = { onDatabaseClick(database.name) },
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
                        modifier = Modifier.weight(1f).clickable { onDatabaseOpen(database.name) }
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
                            text = database.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (loadingDatabases.contains(database.name)) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    } else if (dbTables != null && dbTables.isNotEmpty()) {
                        Text(
                            text = "${dbTables.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!loadingDatabases.contains(database.name)) {
                            androidx.compose.material3.IconButton(onClick = { onRefreshDatabase(database.name) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh tables", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (tableSizes.isEmpty() || dbTables.any { tableSizes["${database.name}.$it"] == null }) {
                                androidx.compose.material3.IconButton(onClick = { onRefreshSizes(database.name) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Storage, contentDescription = "Load sizes", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                if (isExpanded) {
                    Column(
                        modifier = Modifier.padding(start = 12.dp, top = 0.dp, end = 0.dp, bottom = 0.dp)
                    ) {
                        if (loadingDatabases.contains(database.name)) {
                            Row(
                                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Loading...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else if (dbTables == null || dbTables.isEmpty()) {
                            // empty or failed -> show empty hint, no endless loading
                            if (dbTables != null && dbTables.isEmpty()) {
                                Text(
                                    text = "No tables",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp)
                                )
                            }
                        }
                        if (dbTables != null && dbTables.isNotEmpty()) {
                            filteredTables.forEach { table ->
                                key(table) {
                                val tableKey = "${database.name}.$table"
                                val isTableExpanded = expandedTables.contains(tableKey)
                                val tableColumns = columns[tableKey]
                                val tableIndexList = indexes[tableKey]
                                val tableSize = tableSizes[tableKey]

                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.IconButton(
                                            onClick = { onTableClick(database.name, table) },
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
                                                .clickable { onTableDataClick(database.name, table) }
                                                .padding(vertical = 6.dp),
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
                                                text = table,
                                                style = MaterialTheme.typography.bodySmall,
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

                                    if (isTableExpanded) {
                                        Column(
                                            modifier = Modifier.padding(start = 16.dp, top = 0.dp, end = 0.dp, bottom = 0.dp)
                                        ) {
                                            // Columns folder
                                            TreeSubFolder(
                                                title = "Columns",
                                                count = tableColumns?.size,
                                                isLoading = tableColumns == null
                                            ) {
                                                tableColumns?.forEach { column ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 2.dp, horizontal = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (column.isPrimaryKey) Icons.Default.Key else Icons.Default.TableChart,
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
                                            }

                                            // Indexes folder
                                            TreeSubFolder(
                                                title = "Indexes",
                                                count = tableIndexList?.size,
                                                isLoading = tableIndexList == null
                                            ) {
                                                tableIndexList?.forEach { index ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 2.dp, horizontal = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (index.name == "PRIMARY") Icons.Default.Key else Icons.Default.Folder,
                                                            contentDescription = null,
                                                            tint = if (index.name == "PRIMARY") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(12.dp)
                                                        )

                                                        Spacer(modifier = Modifier.width(6.dp))

                                                        Text(
                                                            text = index.name,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = if (index.name == "PRIMARY") FontWeight.Bold else FontWeight.Normal,
                                                            modifier = Modifier.weight(1f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )

                                                        Text(
                                                            text = index.columns.joinToString(", "),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                }
                            }
                        }
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
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(start = 4.dp, top = 0.dp, end = 0.dp, bottom = 0.dp)) {
        Row(
            modifier = Modifier
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
        Column(modifier = Modifier.padding(start = 16.dp, top = 0.dp, end = 0.dp, bottom = 0.dp)) {
            content()
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
