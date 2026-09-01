package com.sqlclient.android.ui.screens.table

import com.sqlclient.android.ui.components.AppTopBar
import com.sqlclient.android.ui.components.CurrentQueryBar
import com.sqlclient.android.ui.components.ReconnectBanner
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.sqlclient.android.data.remote.model.IndexInfo
import com.sqlclient.android.ui.viewmodel.ConnectionViewModel
import com.sqlclient.android.ui.viewmodel.IndexManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexManagementScreen(
    viewModel: IndexManagementViewModel,
    connectionViewModel: ConnectionViewModel,
    database: String,
    table: String,
    isLocked: Boolean = false,
    onToggleLock: (() -> Unit)? = null,
    onBack: () -> Unit,
    onReconnect: (() -> Unit)? = null,
    isReconnecting: Boolean = false,
    topBarColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val indexes by viewModel.indexes.collectAsState()
    val columns by viewModel.columns.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val operationSuccess by viewModel.operationSuccess.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Indexes", "Table Edit")

    var showCreateIndexDialog by remember { mutableStateOf(false) }
    var indexToDelete by remember { mutableStateOf<IndexInfo?>(null) }
    var pendingSql by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val currentQuery by viewModel.currentQuery.collectAsState()

    LaunchedEffect(database, table) {
        viewModel.loadIndexes(database, table)
        viewModel.loadColumns(database, table)
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(operationSuccess) {
        operationSuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    val reconnectMessage by connectionViewModel.reconnectMessage.collectAsState()
    LaunchedEffect(reconnectMessage) {
        reconnectMessage?.let {
            kotlinx.coroutines.delay(2000)
            connectionViewModel.clearReconnectMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = table,
                subtitle = database,
                containerColor = topBarColor,
                onRefresh = { viewModel.loadIndexes(database, table) },
                isRefreshing = isLoading,
                isLocked = isLocked,
                showLock = true,
                onToggleLock = onToggleLock,
                onBack = onBack,
                onReconnect = onReconnect,
                isReconnecting = isReconnecting
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { CurrentQueryBar(queries = currentQuery) },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { if (!isLocked) showCreateIndexDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Create Index") },
                    text = { Text("Create Index") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ReconnectBanner(message = reconnectMessage)
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> IndexesTab(
                        indexes = indexes,
                        onDeleteIndex = { indexToDelete = it }
                    )
                    1 -> TableEditTab(
                        database = database,
                        table = table,
                        columns = columns,
                        viewModel = viewModel,
                        isLocked = isLocked
                    )
                }
            }
        }
    }

    if (showCreateIndexDialog) {
        CreateIndexDialog(
            columns = columns,
            onDismiss = { showCreateIndexDialog = false },
            onConfirm = { name, selectedColumns, unique ->
                if (isLocked) { showCreateIndexDialog = false; return@CreateIndexDialog }
                val sql = viewModel.buildCreateIndexSql(name, selectedColumns, unique, database, table)
                pendingSql = sql
                pendingAction = { viewModel.createIndex(name, selectedColumns, unique, database, table, isLocked = isLocked); showCreateIndexDialog = false }
            }
        )
    }

    indexToDelete?.let { index ->
        val sql = viewModel.buildDropIndexSql(index.name, database, table)
        AlertDialog(
            onDismissRequest = { indexToDelete = null },
            title = { Text("Drop Index") },
            text = {
                Column {
                    Text("Are you sure you want to drop index \"${index.name}\"?")
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer { Text(sql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp)) }
                }
            },
            confirmButton = {
                TextButton(enabled = !isLocked, onClick = {
                    viewModel.dropIndex(index.name, database, table, isLocked = isLocked)
                    indexToDelete = null
                }) {
                    Text("Drop", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { indexToDelete = null }) {
                    Text("Cancel")
                }
            }
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
                    SelectionContainer { Text(sql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp)) }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { clipboard.setText(AnnotatedString(sql)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Copy") }
                }
            },
            confirmButton = { TextButton(onClick = { val a = pendingAction; pendingSql = null; pendingAction = null; a?.invoke() }) { Text("Execute", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingSql = null; pendingAction = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun IndexesTab(
    indexes: List<IndexInfo>,
    onDeleteIndex: (IndexInfo) -> Unit
) {
    if (indexes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No indexes found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(indexes) { index ->
            IndexCard(
                index = index,
                onDelete = { onDeleteIndex(index) }
            )
        }
    }
}

@Composable
private fun IndexCard(
    index: IndexInfo,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = index.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = index.columns.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = index.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Text(
                    text = if (index.isUnique) "Unique" else "Non-unique",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index.isUnique) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Drop index",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = indexName,
                    onValueChange = { indexName = it },
                    label = { Text("Index Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isUnique,
                        onCheckedChange = { isUnique = it }
                    )
                    Text("Unique Index", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Select Columns",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                columns.forEach { column ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = column in selectedColumns,
                            onCheckedChange = { checked ->
                                if (checked) selectedColumns.add(column)
                                else selectedColumns.remove(column)
                            }
                        )
                        Text(
                            text = column,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(indexName, selectedColumns.toList(), isUnique) },
                enabled = indexName.isNotBlank() && selectedColumns.isNotEmpty()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableEditTab(
    database: String,
    table: String,
    columns: List<String>,
    viewModel: IndexManagementViewModel,
    isLocked: Boolean = false
) {
    var newName by remember { mutableStateOf(table) }
    var newColumnName by remember { mutableStateOf("") }
    var newColumnType by remember { mutableStateOf("VARCHAR(255)") }
    var newColumnNullable by remember { mutableStateOf(true) }
    var newColumnDefault by remember { mutableStateOf("") }
    var columnToDelete by remember { mutableStateOf<String?>(null) }
    var pendingSql by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Rename Table",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Table Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        if (isLocked) return@TextButton
                        val sql = viewModel.buildRenameTableSql(database, table, newName)
                        pendingSql = sql
                        pendingAction = { viewModel.renameTable(database, table, newName, isLocked = isLocked) }
                    },
                    enabled = newName.isNotBlank() && newName != table && !isLocked
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rename")
                }
            }
        }

        HorizontalDivider()

        Text(
            "Add Column",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = newColumnName,
                    onValueChange = { newColumnName = it },
                    label = { Text("Column Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newColumnType,
                    onValueChange = { newColumnType = it },
                    label = { Text("Type") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newColumnDefault,
                    onValueChange = { newColumnDefault = it },
                    label = { Text("Default Value (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = newColumnNullable,
                        onCheckedChange = { newColumnNullable = it }
                    )
                    Text("Nullable", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        if (isLocked) return@TextButton
                        val nullable = if (newColumnNullable) "NULL" else "NOT NULL"
                        val default = if (newColumnDefault.isNotBlank()) " DEFAULT '${newColumnDefault.replace("'","''")}'" else ""
                        val colDef = "`$newColumnName` $newColumnType $nullable$default"
                        val sql = viewModel.buildAddColumnSql(database, table, colDef)
                        pendingSql = sql
                        pendingAction = { viewModel.addColumn(database, table, colDef, isLocked = isLocked); newColumnName = ""; newColumnType = "VARCHAR(255)"; newColumnDefault = ""; newColumnNullable = true }
                    },
                    enabled = newColumnName.isNotBlank() && newColumnType.isNotBlank() && !isLocked
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Column")
                }
            }
        }

        HorizontalDivider()

        Text(
            "Drop Column",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (columns.isEmpty()) {
                    Text(
                        "No columns available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    val selectedColumn = columnToDelete ?: ""

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedColumn,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Column") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            columns.forEach { column ->
                                DropdownMenuItem(
                                    text = { Text(column) },
                                    onClick = {
                                        columnToDelete = column
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (columnToDelete != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AlertDialog(
                            onDismissRequest = { columnToDelete = null },
                            title = { Text("Drop Column") },
                            text = { Text("Are you sure you want to drop column \"${columnToDelete}\"? This cannot be undone.") },
                            confirmButton = {
                                TextButton(enabled = !isLocked, onClick = {
                                    val sql = viewModel.buildDropColumnSql(database, table, columnToDelete!!)
                                    pendingSql = sql
                                    pendingAction = { viewModel.dropColumn(database, table, columnToDelete!!, isLocked = isLocked); columnToDelete = null }
                                    // keep dialog open until preview confirm; but close selector
                                }) {
                                    Text("Drop", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { columnToDelete = null }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
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
                    SelectionContainer { Text(sql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp)) }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { clipboard.setText(AnnotatedString(sql)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Copy") }
                }
            },
            confirmButton = { TextButton(onClick = { val a = pendingAction; pendingSql = null; pendingAction = null; a?.invoke() }) { Text("Execute", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingSql = null; pendingAction = null }) { Text("Cancel") } }
        )
    }

}