package com.sqlclient.android.ui.screens.table

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.sqlclient.android.data.remote.model.ColumnInfo
import com.sqlclient.android.data.remote.model.IndexInfo
import com.sqlclient.android.ui.components.AppTopBar
import com.sqlclient.android.ui.components.CurrentQueryBar
import com.sqlclient.android.ui.viewmodel.IndexManagementViewModel
import com.sqlclient.android.ui.viewmodel.TableStructureViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableStructureScreen(
    viewModel: TableStructureViewModel,
    indexViewModel: IndexManagementViewModel,
    database: String,
    table: String,
    isLocked: Boolean = false,
    onToggleLock: (() -> Unit)? = null,
    onBack: () -> Unit,
    onReconnect: (() -> Unit)? = null,
    isReconnecting: Boolean = false
) {
    val columns by viewModel.columns.collectAsState()
    val createTable by viewModel.createTable.collectAsState()
    val indexes by viewModel.indexes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val idxIndexes by indexViewModel.indexes.collectAsState()
    val idxColumns by indexViewModel.columns.collectAsState()
    val idxLoading by indexViewModel.isLoading.collectAsState()
    val idxError by indexViewModel.error.collectAsState()
    val idxSuccess by indexViewModel.operationSuccess.collectAsState()
    val idxQuery by indexViewModel.currentQuery.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Columns", "DDL", "Indexes")
    val currentQuery by viewModel.currentQuery.collectAsState()

    var showCreateIndex by remember { mutableStateOf(false) }
    var indexToDelete by remember { mutableStateOf<IndexInfo?>(null) }
    var pendingSql by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(database, table) {
        viewModel.loadStructure(database, table)
        indexViewModel.loadIndexes(database, table)
        indexViewModel.loadColumns(database, table)
    }

    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(idxError) {
        idxError?.let { snackbarHostState.showSnackbar(it); indexViewModel.clearError() }
    }
    LaunchedEffect(idxSuccess) {
        idxSuccess?.let { snackbarHostState.showSnackbar(it); indexViewModel.clearSuccess() }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = table,
                subtitle = database,
                containerColor = MaterialTheme.colorScheme.primary,
                onRefresh = {
                    viewModel.loadStructure(database, table)
                    indexViewModel.loadIndexes(database, table)
                },
                isRefreshing = isLoading || idxLoading,
                isLocked = isLocked,
                showLock = true,
                onToggleLock = onToggleLock,
                onBack = onBack,
                onReconnect = onReconnect,
                isReconnecting = isReconnecting
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { CurrentQueryBar(queries = if (idxQuery.isNotEmpty()) idxQuery else currentQuery) },
        floatingActionButton = {
            if (selectedTab == 2) {
                ExtendedFloatingActionButton(
                    onClick = { if (!isLocked) showCreateIndex = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Create Index") },
                    text = { Text("Create Index") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }

            if (isLoading && selectedTab != 2) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                when (selectedTab) {
                    0 -> ColumnsTab(columns)
                    1 -> DdlTab(createTable)
                    2 -> IndexesTabEditable(
                        indexes = if (idxIndexes.isNotEmpty()) idxIndexes else indexes,
                        isLocked = isLocked,
                        onDeleteIndex = { indexToDelete = it }
                    )
                }
            }
        }
    }

    if (showCreateIndex) {
        CreateIndexDialog(
            columns = idxColumns.ifEmpty { columns.map { it.name } },
            onDismiss = { showCreateIndex = false },
            onConfirm = { name, cols, unique ->
                if (isLocked) { showCreateIndex = false; return@CreateIndexDialog }
                val sql = indexViewModel.buildCreateIndexSql(name, cols, unique, database, table)
                pendingSql = sql
                pendingAction = {
                    indexViewModel.createIndex(name, cols, unique, database, table, isLocked = isLocked)
                    showCreateIndex = false
                }
            }
        )
    }

    indexToDelete?.let { idx ->
        val dropSql = indexViewModel.buildDropIndexSql(idx.name, database, table)
        AlertDialog(
            onDismissRequest = { indexToDelete = null },
            title = { Text("Drop Index") },
            text = {
                Column {
                    Text("Drop index \"${idx.name}\" from `$database`.`$table`?")
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(dropSql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !isLocked, onClick = {
                    pendingSql = dropSql
                    pendingAction = { indexViewModel.dropIndex(idx.name, database, table, isLocked = isLocked); indexToDelete = null }
                    indexToDelete = null
                }) { Text("Drop", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { indexToDelete = null }) { Text("Cancel") } }
        )
    }

    pendingSql?.let { sql ->
        val clipboard = LocalClipboardManager.current
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { pendingSql = null; pendingAction = null },
            title = { Text("Confirm Write") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Query to be executed:", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(sql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { clipboard.setText(AnnotatedString(sql)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("Copy")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { val a = pendingAction; pendingSql = null; pendingAction = null; a?.invoke() }) {
                    Text("Execute", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingSql = null; pendingAction = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ColumnsTab(columns: List<ColumnInfo>) {
    if (columns.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No columns found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Name", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
            Text(text = "Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
            Text(text = "Null", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
            Text(text = "Key", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
            Text(text = "Default", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(text = "Comment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(columns) { column -> ColumnRow(column) }
        }
    }
}

@Composable
private fun ColumnRow(column: ColumnInfo) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = column.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (column.isPrimaryKey) FontWeight.Bold else FontWeight.Normal,
            color = if (column.isPrimaryKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = column.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = if (column.nullable) "YES" else "NO", style = MaterialTheme.typography.bodySmall,
            color = if (column.nullable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error, modifier = Modifier.weight(0.6f))
        Text(text = when { column.isPrimaryKey -> "PRI"; column.isAutoIncrement -> "AUTO"; else -> "" },
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.6f))
        Text(text = column.defaultValue ?: "NULL", style = MaterialTheme.typography.bodySmall,
            color = if (column.defaultValue != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = column.comment ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DdlTab(createTable: String?) {
    if (createTable.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No DDL available", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    SelectionContainer {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(text = createTable, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

@Composable
private fun IndexesTabEditable(indexes: List<IndexInfo>, isLocked: Boolean, onDeleteIndex: (IndexInfo) -> Unit) {
    if (indexes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(text = "No indexes found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Tap Create Index to add one", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        items(indexes) { index -> IndexCardEditable(index, isLocked, onDeleteIndex) }
    }
}

@Composable
private fun IndexCardEditable(index: IndexInfo, isLocked: Boolean, onDeleteIndex: (IndexInfo) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = index.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = index.columns.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "${index.type} • ${if (index.isUnique) "Unique" else "Non-unique"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onDeleteIndex(index) }, enabled = !isLocked) {
                Icon(Icons.Default.Delete, contentDescription = "Drop index", tint = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateIndexDialog(
    columns: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, columns: List<String>, unique: Boolean) -> Unit
) {
    var indexName by remember { mutableStateOf("") }
    var isUnique by remember { mutableStateOf(false) }
    val selectedColumns = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Index") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = indexName, onValueChange = { indexName = it }, label = { Text("Index Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = isUnique, onCheckedChange = { isUnique = it })
                    Text("Unique Index", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select Columns", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                columns.forEach { column ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = column in selectedColumns, onCheckedChange = { checked -> if (checked) selectedColumns.add(column) else selectedColumns.remove(column) })
                        Text(text = column, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(indexName, selectedColumns.toList(), isUnique) }, enabled = indexName.isNotBlank() && selectedColumns.isNotEmpty()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
