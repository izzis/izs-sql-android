package id.web.izs.sqlclient.ui.screens.dataeditor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.web.izs.sqlclient.data.local.entity.ConnectionProfileEntity
import id.web.izs.sqlclient.data.remote.ColumnMetadata
import id.web.izs.sqlclient.ui.components.AppSidebar
import id.web.izs.sqlclient.ui.components.AppTopBar
import id.web.izs.sqlclient.ui.components.CurrentQueryBar
import id.web.izs.sqlclient.ui.components.ReconnectBanner
import id.web.izs.sqlclient.ui.components.SqlEditor
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.launch
import id.web.izs.sqlclient.ui.viewmodel.BrowserViewModel
import id.web.izs.sqlclient.ui.viewmodel.ConnectionViewModel
import id.web.izs.sqlclient.ui.viewmodel.DataEditorViewModel
import id.web.izs.sqlclient.ui.viewmodel.QueryViewModel
import id.web.izs.sqlclient.util.CellDisplay
import id.web.izs.sqlclient.util.rememberCopyToClipboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineDataEditorScreen(
    viewModel: DataEditorViewModel,
    connectionViewModel: ConnectionViewModel,
    queryViewModel: QueryViewModel,
    browserViewModel: BrowserViewModel,
    profile: ConnectionProfileEntity,
    database: String,
    table: String,
    isLocked: Boolean = false,
    onToggleLock: (() -> Unit)? = null,
    onBack: () -> Unit,
    onOpenStructure: (() -> Unit)? = null,
    onReconnect: (() -> Unit)? = null,
    isReconnecting: Boolean = false
) {
    val columns by viewModel.columns.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val selectedRows by viewModel.selectedRows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val operationSuccess by viewModel.operationSuccess.collectAsState()
    val query by viewModel.query.collectAsState()
    val dataLimit by viewModel.dataLimit.collectAsState()
    val hasMoreData by viewModel.hasMoreData.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val whereInput by viewModel.whereInput.collectAsState()
    val queryTimeMs by viewModel.lastQueryDurationMs.collectAsState()
    val sortColumn by viewModel.sortColumn.collectAsState()
    val sortAsc by viewModel.sortAsc.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val isReadonly = isLocked
    var showInsertDialog by remember { mutableStateOf(false) }
    var editingCell by remember { mutableStateOf<Triple<Int, Int, Any?>?>(null) }
    var viewingCell by remember { mutableStateOf<Triple<Int, Int, Any?>?>(null) }
    var pendingSql by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var showSQLEditor by remember { mutableStateOf(false) }
    var showWriteConfirm by remember { mutableStateOf(false) }
    var editableQuery by remember { mutableStateOf(TextFieldValue("")) }
    val savedQueries by queryViewModel.savedQueries.collectAsState()
    var showSavedQueries by remember { mutableStateOf(false) }
    var renamingQuery by remember { mutableStateOf<id.web.izs.sqlclient.data.local.entity.QueryHistoryEntity?>(null) }
    var deletingSavedQuery by remember { mutableStateOf<id.web.izs.sqlclient.data.local.entity.QueryHistoryEntity?>(null) }
    val pendingEdits by viewModel.pendingEdits.collectAsState()
    val pendingDeletes by viewModel.pendingDeletes.collectAsState()
    val hasPending = pendingEdits.isNotEmpty() || pendingDeletes.isNotEmpty()
    // Multi-select batch edit: when editing a cell while rows are selected
    var multiEditTarget by remember { mutableStateOf<Triple<Int, Int, Any?>?>(null) }

    // Autocomplete
    var enableAutocomplete by remember { mutableStateOf(true) }
    val databases by browserViewModel.databases.collectAsState()
    val tablesByDb by browserViewModel.tables.collectAsState()
    val allColumns by browserViewModel.columns.collectAsState()
    var columnCache by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val tableNames = remember(tablesByDb, database) { tablesByDb[database] ?: emptyList() }
    val databaseNames = remember(databases) { databases.map { it.name } }
    val currentTableColumns = remember(allColumns, database, table, columns) {
        allColumns["$database.$table"]?.map { it.name }
            ?: columns.map { it.name }
            ?: emptyList()
    }
    val fetchExtraColumns: (suspend (String) -> List<String>?)? = if (enableAutocomplete) {
        { tableName: String ->
            val key = "$database.$tableName"
            columnCache[key] ?: run {
                val fetched = viewModel.fetchColumnsForAutocomplete(database, tableName)
                if (fetched != null) columnCache = columnCache + (key to fetched)
                fetched
            }
        }
    } else null

    LaunchedEffect(query) {
        if (editableQuery.text != query) {
            editableQuery = TextFieldValue(query, selection = TextRange(query.length))
        }
    }

    LaunchedEffect(database, table) {
        viewModel.resetQueryLog()
        viewModel.loadData(database, table)
    }

    LaunchedEffect(profile.id) {
        queryViewModel.setCurrentProfileId(profile.id)
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

    val topBarColor = try {
        androidx.compose.ui.graphics.Color(profile.color.toColorInt())
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
    val currentQuery by viewModel.currentQuery.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val visibleDatabases by browserViewModel.visibleDatabases.collectAsState()
    val searchQuerySidebar by browserViewModel.searchQuery.collectAsState()
    val isLoadingSidebar by browserViewModel.isLoading.collectAsState()
    val hasLoadedSidebar by browserViewModel.hasLoadedDatabases.collectAsState()
    val selectedDatabaseSidebar by browserViewModel.selectedDatabase.collectAsState()
    val allFav by queryViewModel.allFavorites.collectAsState()
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                AppSidebar(
                    profile = profile,
                    databases = databases,
                    visibleDatabases = if (visibleDatabases.isNotEmpty()) visibleDatabases else databases,
                    searchQuery = searchQuerySidebar,
                    onSearchChange = { browserViewModel.setSearchQuery(it) },
                    users = emptyList(),
                    isLoading = isLoadingSidebar && !hasLoadedSidebar,
                    onRefreshDatabases = { browserViewModel.refreshDatabases() },
                    onDatabaseClick = { scope.launch { drawerState.close() } },
                    onUsersClick = { scope.launch { drawerState.close() } },
                    onHistoryClick = { scope.launch { drawerState.close() } },
                    onSavedQueriesClick = { scope.launch { drawerState.close() } },
                    savedQueryCount = allFav.size,
                    selectedDatabase = selectedDatabaseSidebar
                )
            }
        }
    ) {
        Scaffold(
        topBar = {
            AppTopBar(
                title = table,
                subtitle = database,
                containerColor = topBarColor,
                onRefresh = { viewModel.refreshData() },
                isRefreshing = isLoading,
                isLocked = isLocked,
                showLock = true,
                onToggleLock = onToggleLock,
                onBack = onBack,
                onStructure = onOpenStructure,
                onSave = if (hasPending) ({ showSaveConfirm = true }) else null,
                onReconnect = onReconnect,
                isReconnecting = isReconnecting
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val isExec = isLoading || isLoadingMore
            CurrentQueryBar(
                queries = currentQuery,
                isExecuting = isExec,
                onCancel = if (isExec) ({ viewModel.cancelCurrentQuery() }) else null,
                onClear = ({ viewModel.resetQueryLog() }).takeIf { !viewModel.hasPendingChanges() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ReconnectBanner(message = reconnectMessage)
            SqlEditorBar(
                query = editableQuery,
                onQueryChange = {
                    editableQuery = it
                    viewModel.setQuery(it.text)
                },
                onExecute = {
                    if (viewModel.isWriteQuery()) {
                        showWriteConfirm = true
                    } else {
                        viewModel.executeCustomQuery(isLocked = isLocked)
                    }
                },
                isExpanded = showSQLEditor,
                onToggle = { showSQLEditor = !showSQLEditor },
                enabled = !(isLocked && viewModel.isWriteQuery()),
                readOnly = isLocked,
                savedQueries = savedQueries,
                showSavedQueries = showSavedQueries,
                onToggleSavedQueries = { showSavedQueries = !showSavedQueries },
                onSelectSavedQuery = { query ->
                    editableQuery = TextFieldValue(query, selection = TextRange(query.length))
                    viewModel.setQuery(query)
                },
                onDeleteSavedQuery = { q -> deletingSavedQuery = q },
                onRenameSavedQuery = { q -> renamingQuery = q },
                onSaveSavedQuery = { q ->
                    val current = editableQuery.text.ifBlank { q.queryText }
                    queryViewModel.updateSavedQuery(q.id, current, q.name, q.database ?: database)
                },
                enableAutocomplete = enableAutocomplete,
                onToggleAutocomplete = { enableAutocomplete = !enableAutocomplete },
                databaseNames = databaseNames,
                tableNames = tableNames,
                columnNames = currentTableColumns,
                onFetchExtraColumns = fetchExtraColumns
            )

            WhereFilterBar(
                whereInput = whereInput,
                onWhereChange = { viewModel.setWhereInput(it) },
                onApply = { viewModel.applyWhereFilter() },
                onClear = { viewModel.clearWhereFilter() },
                columns = columns
            )

            // Compact selection strip — only when enabled; compact bar
            if (!isReadonly && rows.isNotEmpty()) {
                SelectionBar(
                    selectedCount = selectedRows.size,
                    totalCount = rows.size,
                    isLocked = isLocked,
                    hasPending = hasPending,
                    onSelectAll = { viewModel.selectAll() },
                    onClear = { viewModel.deselectAll() },
                    onDelete = {
                        // Stage deletes: mark selected rows for batch delete
                        val sel = selectedRows.toList()
                        sel.forEach { viewModel.stageDelete(it) }
                        viewModel.deselectAll()
                    },
                    onDiscard = { viewModel.clearStaged() }
                )
            }

            if (error != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Report, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(18.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(10.dp))
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (columns.isEmpty() || rows.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (columns.isEmpty()) "No columns found" else "No data found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isReadonly && columns.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { showInsertDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Insert first row")
                        }
                    }
                }
            } else {
                DataGrid(
                    columns = columns,
                    rows = rows,
                    selectedRows = selectedRows,
                    pendingEdits = pendingEdits,
                    pendingDeletes = pendingDeletes,
                    isReadonly = isReadonly,
                    sortColumn = sortColumn,
                    sortAsc = sortAsc,
                    onCellClick = { rowIndex, colIndex, value ->
                        if (!isReadonly) {
                            if (selectedRows.size > 1 && selectedRows.contains(rowIndex)) {
                                multiEditTarget = Triple(rowIndex, colIndex, value)
                            } else {
                                editingCell = Triple(rowIndex, colIndex, value)
                            }
                        } else {
                            viewingCell = Triple(rowIndex, colIndex, value)
                        }
                    },
                    onColumnHeaderClick = { colIndex -> viewModel.toggleSort(colIndex) },
                    onRowToggle = { viewModel.toggleRowSelection(it) },
                    onFilterByRow = { rowIndex ->
                        val row = rows.getOrNull(rowIndex) ?: return@DataGrid
                        val filterParts = mutableListOf<String>()
                        row.forEachIndexed { idx, value ->
                            val colName = columns.getOrNull(idx)?.name ?: return@forEachIndexed
                            val filterVal = if (value == null) "IS NULL" else "= '${CellDisplay.trim(value.toString()).replace("'", "''")}'"
                            filterParts.add("`$colName` $filterVal")
                        }
                        viewModel.setWhereInput(filterParts.joinToString(" AND "))
                        viewModel.applyWhereFilter()
                    },
                    onFilterByCell = { rowIndex, colIdx ->
                        val row = rows.getOrNull(rowIndex) ?: return@DataGrid
                        val colName = columns.getOrNull(colIdx)?.name ?: return@DataGrid
                        val cellValue = row.getOrNull(colIdx)
                        if (cellValue != null) {
                            val filterVal = "'${CellDisplay.trim(cellValue.toString()).replace("'", "''")}'"
                            viewModel.setWhereInput("`$colName` = $filterVal")
                            viewModel.applyWhereFilter()
                        }
                    },
                    onDeleteRow = { rowIndex -> viewModel.stageDelete(rowIndex) },
                    onLoadMore = { viewModel.loadMore() },
                    hasMoreData = hasMoreData,
                    isLoadingMore = isLoadingMore,
                    modifier = Modifier.weight(1f)
                )
            }

            StatusBar(
                rowCount = rows.size,
                hasMoreData = hasMoreData,
                isLoadingMore = isLoadingMore,
                isReadonly = isReadonly,
                queryTimeMs = queryTimeMs,
                limit = dataLimit,
                onLimitChange = { viewModel.changeLimit(it) },
                onInsertRow = if (!isReadonly) ({ showInsertDialog = true }) else null
            )
        }
        }
    }

    editingCell?.let { (rowIndex, colIndex, value) ->
        val columnName = columns.getOrNull(colIndex)?.name ?: ""
        CellEditDialog(
            columnName = columnName,
            initialValue = if (value == null) "" else CellDisplay.trim(value.toString()),
            onConfirm = { newValue ->
                viewModel.stageEdit(rowIndex, colIndex, columnName, newValue)
                editingCell = null
            },
            onDismiss = { editingCell = null }
        )
    }

    viewingCell?.let { (_, colIndex, value) ->
        val columnName = columns.getOrNull(colIndex)?.name ?: ""
        CellViewDialog(
            columnName = columnName,
            value = CellDisplay.format(value),
            onCopy = { viewingCell = null },
            onDismiss = { viewingCell = null }
        )
    }

    // Multi-select batch edit confirmation
    multiEditTarget?.let { (_, colIndex, _) ->
        val columnName = columns.getOrNull(colIndex)?.name ?: ""
        val currentValue = multiEditTarget?.third?.let { CellDisplay.trim(it.toString()) } ?: ""
        var multiEditValue by remember { mutableStateOf(currentValue) }
        AlertDialog(
            onDismissRequest = { multiEditTarget = null },
            title = { Text("Edit $columnName for ${selectedRows.size} rows") },
            text = {
                Column {
                    Text("This value will be applied to all ${selectedRows.size} selected rows.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = multiEditValue,
                        onValueChange = { multiEditValue = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.stageEditMultiple(colIndex, columnName, multiEditValue)
                    multiEditTarget = null
                }) { Text("Apply to all") }
            },
            dismissButton = { TextButton(onClick = { multiEditTarget = null }) { Text("Cancel") } }
        )
    }

    if (showInsertDialog) {
        InsertRowDialog(
            columns = columns,
            autoIncrementColumn = viewModel.getPkColumn(),
            onConfirm = { values ->
                if (isLocked) { showInsertDialog = false; return@InsertRowDialog }
                val sql = viewModel.buildInsertSql(database, table, values)
                pendingSql = sql
                pendingAction = { viewModel.insertRow(database, table, values, isLocked = isLocked); showInsertDialog = false }
            },
            onDismiss = { showInsertDialog = false }
        )
    }

    pendingSql?.let { sql ->
        val copyToClipboard = rememberCopyToClipboard()
        AlertDialog(
            onDismissRequest = { pendingSql = null; pendingAction = null },
            title = { Text("Confirm Write") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Query to be executed:", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(sql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { copyToClipboard(sql, "Copied") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Copy")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { val a = pendingAction; pendingSql = null; pendingAction = null; a?.invoke() }) { Text("Execute", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingSql = null; pendingAction = null }) { Text("Cancel") } }
        )
    }

    if (showSaveConfirm) {
        val sqls = viewModel.buildPendingSqls(database, table)
        val fullSql = sqls.joinToString(";\n")
        val copyToClipboard = rememberCopyToClipboard()
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text("Confirm Write (${sqls.size})") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Query to be executed:", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(fullSql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { copyToClipboard(fullSql, "Copied") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Copy")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSaveConfirm = false; viewModel.commitPending(database, table, isLocked = isLocked) }) { Text("Execute", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showSaveConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showWriteConfirm) {
        val sql = viewModel.getCustomQueryPreview()
        val copyToClipboard = rememberCopyToClipboard()
        AlertDialog(
            onDismissRequest = { showWriteConfirm = false },
            title = { Text("Confirm Write Query") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("This query modifies data. Do you want to execute it?", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Query:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = sql,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { copyToClipboard(sql, "Copied") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Copy")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWriteConfirm = false; viewModel.executeCustomQuery(isLocked = isLocked) }) { Text("Execute", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showWriteConfirm = false }) { Text("Cancel") } }
        )
    }

    renamingQuery?.let { entity ->
        var renameValue by remember { mutableStateOf(entity.name ?: entity.queryText.take(40)) }
        AlertDialog(
            onDismissRequest = { renamingQuery = null },
            title = { Text("Rename Saved Query") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    queryViewModel.renameSavedQuery(entity, renameValue)
                    renamingQuery = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingQuery = null }) { Text("Cancel") }
            }
        )
    }

    deletingSavedQuery?.let { entity ->
        AlertDialog(
            onDismissRequest = { deletingSavedQuery = null },
            title = { Text("Delete Saved Query") },
            text = { Text("Delete \"${entity.name ?: "Unnamed"}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    queryViewModel.deleteSavedQuery(entity)
                    deletingSavedQuery = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingSavedQuery = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SqlEditorBar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onExecute: () -> Unit,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    savedQueries: List<id.web.izs.sqlclient.data.local.entity.QueryHistoryEntity> = emptyList(),
    showSavedQueries: Boolean = false,
    onToggleSavedQueries: () -> Unit = {},
    onSelectSavedQuery: (String) -> Unit = {},
    onDeleteSavedQuery: (id.web.izs.sqlclient.data.local.entity.QueryHistoryEntity) -> Unit = {},
    onRenameSavedQuery: (id.web.izs.sqlclient.data.local.entity.QueryHistoryEntity) -> Unit = {},
    onSaveSavedQuery: (id.web.izs.sqlclient.data.local.entity.QueryHistoryEntity) -> Unit = {},
    enableAutocomplete: Boolean = true,
    onToggleAutocomplete: () -> Unit = {},
    databaseNames: List<String> = emptyList(),
    tableNames: List<String> = emptyList(),
    columnNames: List<String> = emptyList(),
    onFetchExtraColumns: (suspend (tableName: String) -> List<String>?)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse editor" else "Expand editor",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SQL Editor",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (savedQueries.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onToggleSavedQueries() }, modifier = Modifier.height(28.dp)) {
                    Icon(
                        imageVector = if (showSavedQueries) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text("Saved (${savedQueries.size})", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (showSavedQueries && savedQueries.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(max = 100.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(savedQueries.size) { idx ->
                    val q = savedQueries[idx]
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .clickable { onSelectSavedQuery(q.queryText) }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = q.name ?: "Unnamed",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = q.queryText.take(40),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { onRenameSavedQuery(q) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onSaveSavedQuery(q) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDeleteSavedQuery(q) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                SqlEditor(
                    query = query,
                    onQueryChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = readOnly,
                    enableAutocomplete = enableAutocomplete,
                    databaseNames = databaseNames,
                    tableNames = tableNames,
                    columnNames = columnNames,
                    onFetchExtraColumns = onFetchExtraColumns
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onToggleAutocomplete() }
                    ) {
                        IconButton(onClick = { onToggleAutocomplete() }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = if (enableAutocomplete) "Disable autocomplete" else "Enable autocomplete",
                                modifier = Modifier.size(16.dp),
                                tint = if (enableAutocomplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Autocomplete",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enableAutocomplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onExecute,
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Execute")
                    }
                }
            }
        }
    }
}


@Composable
private fun SelectionBar(
    selectedCount: Int,
    totalCount: Int,
    isLocked: Boolean,
    hasPending: Boolean = false,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onDiscard: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val allSelected = selectedCount == totalCount && totalCount > 0
        androidx.compose.material3.FilterChip(
            selected = allSelected,
            onClick = { if (allSelected) onClear() else onSelectAll() },
            label = { Text(if (allSelected) "Clear" else "Select all", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.height(28.dp)
        )
        if (selectedCount > 0) {
            Text("$selectedCount / $totalCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDelete, enabled = !isLocked, modifier = Modifier.height(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete ($selectedCount)", style = MaterialTheme.typography.labelSmall)
            }
            if (hasPending) {
                TextButton(onClick = { onDiscard?.invoke() }, modifier = Modifier.height(28.dp)) { Text("Discard", style = MaterialTheme.typography.labelSmall) }
            }
        } else {
            Text("$totalCount rows", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasPending) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onDiscard?.invoke() }, modifier = Modifier.height(28.dp)) { Text("Discard", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun WhereFilterBar(
    whereInput: String,
    onWhereChange: (String) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    columns: List<ColumnMetadata> = emptyList()
) {
    var textFieldValue by remember(whereInput) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(
            text = whereInput,
            selection = androidx.compose.ui.text.TextRange(whereInput.length)
        ))
    }
    LaunchedEffect(whereInput) {
        if (textFieldValue.text != whereInput) {
            textFieldValue = textFieldValue.copy(text = whereInput, selection = androidx.compose.ui.text.TextRange(whereInput.length))
        }
    }

    val frag = whereInput.trimEnd().split(Regex("[^a-zA-Z0-9_]+")).lastOrNull()?.lowercase() ?: ""
    val endsWithSpace = whereInput.isNotEmpty() && whereInput.last().isWhitespace()
    val exactMatch = columns.any { it.name.lowercase() == frag }
    val showSuggestions = frag.length >= 1 && !endsWithSpace && !exactMatch
    val suggestions = if (showSuggestions) columns.map { it.name }.filter { it.lowercase().contains(frag) }.take(6) else emptyList()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    onWhereChange(newValue.text)
                },
                placeholder = { Text("e.g. name = 'John'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                modifier = Modifier.weight(1f).height(48.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                trailingIcon = {
                    if (whereInput.isNotEmpty()) {
                        IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                        }
                    }
                }
            )
            TextButton(onClick = onApply, modifier = Modifier.height(32.dp)) { Text("Go", style = MaterialTheme.typography.labelSmall) }
        }
        if (suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                shape = RoundedCornerShape(6.dp),
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                    suggestions.forEach { col ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val cur = textFieldValue.text
                                    val lastFrag = cur.trimEnd().split(Regex("[^a-zA-Z0-9_]+")).lastOrNull() ?: ""
                                    val prefix = if (lastFrag.isNotEmpty() && cur.trimEnd().endsWith(lastFrag)) cur.trimEnd().dropLast(lastFrag.length) else cur.trimEnd()
                                    val sep = if (prefix.isEmpty() || prefix.endsWith(" ")) "" else " "
                                    val next = prefix + sep + col + " "
                                    textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                        text = next,
                                        selection = androidx.compose.ui.text.TextRange(next.length)
                                    )
                                    onWhereChange(next)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = col,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun StatusBar(
    rowCount: Int,
    hasMoreData: Boolean,
    isLoadingMore: Boolean,
    isReadonly: Boolean,
    queryTimeMs: Long?,
    limit: Int,
    onLimitChange: (Int) -> Unit,
    onInsertRow: (() -> Unit)? = null
) {
    val presets = listOf(100, 200, 500, 1000)
    val currentIndex = presets.indexOf(limit).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append("$rowCount row(s)")
                    if (hasMoreData) append(" +")
                    if (queryTimeMs != null) append(" \u2022 ${queryTimeMs}ms")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isLoadingMore) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(text = "Loading...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isReadonly) {
                Text(text = "Read-only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "LIMIT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = { if (currentIndex > 0) onLimitChange(presets[currentIndex - 1]) }, enabled = currentIndex > 0, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
            }
            Text(text = limit.toString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp))
            IconButton(onClick = { if (currentIndex < presets.size - 1) onLimitChange(presets[currentIndex + 1]) }, enabled = currentIndex < presets.size - 1, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp))
            }
            if (onInsertRow != null) {
                IconButton(onClick = onInsertRow, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Insert row", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DataGrid(
    columns: List<ColumnMetadata>,
    rows: List<List<Any?>>,
    selectedRows: Set<Int>,
    modifier: Modifier = Modifier,
    pendingEdits: Map<Pair<Int, Int>, id.web.izs.sqlclient.ui.viewmodel.DataEditorViewModel.StagedEdit> = emptyMap(),
    pendingDeletes: Set<Int> = emptySet(),
    isReadonly: Boolean,
    sortColumn: Int? = null,
    sortAsc: Boolean = true,
    onCellClick: (rowIndex: Int, colIndex: Int, value: Any?) -> Unit,
    onColumnHeaderClick: (colIndex: Int) -> Unit = {},
    onRowToggle: (Int) -> Unit,
    onFilterByRow: (Int) -> Unit = {},
    onFilterByCell: (rowIndex: Int, colIndex: Int) -> Unit = { _, _ -> },
    onDeleteRow: (Int) -> Unit = {},
    onLoadMore: () -> Unit,
    hasMoreData: Boolean,
    isLoadingMore: Boolean
) {
    val horizontalScrollState = rememberScrollState()
    val lazyListState = rememberLazyListState()
    val rowNumWidth = 48.dp
    val checkboxWidth = 48.dp
    val rowHeight = 40.dp
    val displayRows = androidx.compose.runtime.remember(rows) {
        rows.map { r -> r.map { v -> val s = CellDisplay.format(v); if (s.length > 200) s.take(200) + "…" else s } }
    }
    val columnWidths: List<androidx.compose.ui.unit.Dp> = remember(columns, displayRows) {
        if (columns.isEmpty()) emptyList() else {
            val sample = displayRows.take(30)
            columns.mapIndexed { idx, col ->
                val headerW = col.name.length * 8 + 24
                var maxDataLen = 0
                for (r in sample) {
                    val len = r.getOrNull(idx)?.length ?: 0
                    if (len > maxDataLen) maxDataLen = len
                }
                val dataW = maxDataLen * 8 + 24
                maxOf(headerW, dataW).coerceIn(90, 360).dp
            }
        }
    }
    var contextMenuRow by remember { mutableStateOf<Int?>(null) }
    var contextMenuColIndex by remember { mutableStateOf<Int?>(null) } // null = row number, >= 0 = cell column
    val copyToClipboard = rememberCopyToClipboard()

    LaunchedEffect(lazyListState, hasMoreData, isLoadingMore) {
        snapshotFlow {
            val info = lazyListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 4
        }.collect { shouldLoad ->
            if (shouldLoad && hasMoreData && !isLoadingMore) onLoadMore()
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(horizontalScrollState)
        ) {
            Column {
                // Header row
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                    // Row number header
                    Box(
                        modifier = Modifier.width(rowNumWidth).height(rowHeight).padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "#", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    if (!isReadonly) {
                        Box(
                            modifier = Modifier.width(checkboxWidth).height(rowHeight).padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Checkbox(
                                checked = selectedRows.size == rows.size && rows.isNotEmpty(),
                                onCheckedChange = { checked ->
                                    if (checked) onRowToggle(-1) else onRowToggle(-2)
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    columns.forEachIndexed { colIndex, column ->
                        val isSorted = sortColumn == colIndex
                        val colW = columnWidths.getOrNull(colIndex) ?: 150.dp
                        Box(
                            modifier = Modifier.width(colW).height(rowHeight).padding(horizontal = 8.dp)
                                .clickable { onColumnHeaderClick(colIndex) },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = column.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isSorted) {
                                    Icon(
                                        imageVector = if (sortAsc) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropUp,
                                        contentDescription = if (sortAsc) "Ascending" else "Descending",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
                androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(rows, key = { index, _ -> index }, contentType = { _, _ -> "row" }) { rowIndex, row ->
                        val displayRow = displayRows.getOrNull(rowIndex) ?: emptyList()
                        val isContextMenuTarget = contextMenuRow == rowIndex
                        Column {
                            Box(modifier = Modifier.pointerInput(rowIndex, isReadonly, row.size, columnWidths) {
                                detectTapGestures(
                                    onLongPress = { offset ->
                                        val rowNumPx = with(this@pointerInput) { rowNumWidth.toPx() }
                                        val isRowNumber = offset.x < rowNumPx
                                        contextMenuRow = rowIndex
                                        contextMenuColIndex = if (isRowNumber) null else {
                                            val checkboxPx = with(this@pointerInput) { checkboxWidth.toPx() }
                                            val widthsPx = with(this@pointerInput) { columnWidths.map { it.toPx() } }
                                            val startX = rowNumPx + if (!isReadonly) checkboxPx else 0f
                                            var acc = startX
                                            var found: Int? = null
                                            for (i in widthsPx.indices) {
                                                val next = acc + widthsPx[i]
                                                if (offset.x >= acc && offset.x < next) { found = i; break }
                                                acc = next
                                            }
                                            if (found != null && found in 0 until row.size) found else null
                                        }
                                    },
                                    onTap = { offset ->
                                        val rowNumPx = with(this@pointerInput) { rowNumWidth.toPx() }
                                        val checkboxPx = with(this@pointerInput) { checkboxWidth.toPx() }
                                        val widthsPx = with(this@pointerInput) { columnWidths.map { it.toPx() } }
                                        val startX = rowNumPx + if (!isReadonly) checkboxPx else 0f
                                        var acc = startX
                                        var colIdx = -1
                                        for (i in widthsPx.indices) {
                                            val next = acc + widthsPx[i]
                                            if (offset.x >= acc && offset.x < next) { colIdx = i; break }
                                            acc = next
                                        }
                                        if (colIdx in 0 until row.size) {
                                            val staged = pendingEdits[rowIndex to colIdx]?.newValue
                                            val cellValue = row.getOrNull(colIdx)
                                            onCellClick(rowIndex, colIdx, if (staged != null) staged else cellValue)
                                        }
                                    }
                                )
                            }) {
                                Row(modifier = Modifier.fillMaxWidth().height(rowHeight)
                                    .background(if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))) {
                                    // Row number
                                    Box(
                                        modifier = Modifier.width(rowNumWidth).height(rowHeight).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (rowIndex + 1).toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    if (!isReadonly) {
                                        Box(
                                            modifier = Modifier.width(checkboxWidth).height(rowHeight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Checkbox(
                                                checked = selectedRows.contains(rowIndex),
                                                onCheckedChange = { onRowToggle(rowIndex) },
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    row.forEachIndexed { colIndex, cellValue ->
                                        val isPending = pendingEdits.containsKey(rowIndex to colIndex)
                                        val isDeletedRow = pendingDeletes.contains(rowIndex)
                                        val staged = pendingEdits[rowIndex to colIndex]?.newValue
                                        val cellText = when {
                                            staged != null -> staged
                                            else -> displayRow.getOrNull(colIndex) ?: "NULL"
                                        }
                                        val isNull = cellValue == null && staged == null
                                        val bg = when {
                                            isDeletedRow -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                                            isPending -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                            else -> androidx.compose.ui.graphics.Color.Transparent
                                        }
                                        val colW2 = columnWidths.getOrNull(colIndex) ?: 150.dp
                                        Box(
                                            modifier = Modifier.width(colW2).height(rowHeight)
                                                .background(bg)
                                                .padding(horizontal = 8.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = cellText,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                color = if (isDeletedRow || isPending) MaterialTheme.colorScheme.error else if (isNull) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (isPending || isDeletedRow) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                // Context menu
                                DropdownMenu(
                                    expanded = isContextMenuTarget,
                                    onDismissRequest = { contextMenuRow = null; contextMenuColIndex = null },
                                    modifier = Modifier.width(220.dp)
                                ) {
                                    val colIdx = contextMenuColIndex
                                    if (colIdx == null) {
                                        // Long press on row number: row-level actions
                                        DropdownMenuItem(
                                            text = { Text("Copy row") },
                                            onClick = {
                                                val rowText = row.joinToString(" | ") { CellDisplay.format(it) }
                                                copyToClipboard(rowText, "Row copied")
                                                contextMenuRow = null; contextMenuColIndex = null
                                            },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Filter by this row") },
                                            onClick = {
                                                onFilterByRow(rowIndex)
                                                contextMenuRow = null; contextMenuColIndex = null
                                            },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                        )
                                        if (!isReadonly) {
                                            DropdownMenuItem(
                                                text = { Text("Select row") },
                                                onClick = {
                                                    onRowToggle(rowIndex)
                                                    contextMenuRow = null; contextMenuColIndex = null
                                                },
                                                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete row") },
                                                onClick = {
                                                    onDeleteRow(rowIndex)
                                                    contextMenuRow = null; contextMenuColIndex = null
                                                },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
                                            )
                                        }
                                    } else {
                                        // Long press on cell: cell-level actions
                                        val cellValue = row.getOrNull(colIdx)
                                        val colName = columns.getOrNull(colIdx)?.name ?: ""
                                        DropdownMenuItem(
                                            text = { Text("Copy value") },
                                            onClick = {
                                                val text = CellDisplay.format(cellValue)
                                                copyToClipboard(text, "Copied")
                                                contextMenuRow = null; contextMenuColIndex = null
                                            },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                        )
                                        if (cellValue != null && colName.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("Filter: $colName = ${CellDisplay.format(cellValue).take(20)}") },
                                                onClick = {
                                                    onFilterByCell(rowIndex, colIdx)
                                                    contextMenuRow = null; contextMenuColIndex = null
                                                },
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                            )
                                        }
                                    }
                                }
                            }
                            androidx.compose.material3.HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        }
                    }
                    if (isLoadingMore) {
                        item(contentType = "loader") {
                            Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CellEditDialog(
    columnName: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit $columnName") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                minLines = 3,
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp)); Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp)); Text("Cancel")
            }
        }
    )
}

@Composable
private fun CellViewDialog(
    columnName: String,
    value: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    val copyToClipboard = rememberCopyToClipboard()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(columnName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) },
        text = {
            SelectionContainer {
                Text(
                    text = value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                copyToClipboard(value, "Copied")
                onDismiss()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp)); Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp)); Text("Close")
            }
        }
    )
}

@Composable
private fun InsertRowDialog(
    columns: List<ColumnMetadata>,
    autoIncrementColumn: String?,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val fieldValues = remember {
        mutableStateOf(columns.associate { it.name to "" })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Row") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                columns.forEach { column ->
                    val isAutoInc = column.name == autoIncrementColumn
                    OutlinedTextField(
                        value = fieldValues.value[column.name] ?: "",
                        onValueChange = { newValue ->
                            fieldValues.value = fieldValues.value + (column.name to newValue)
                        },
                        label = {
                            Text(
                                text = if (isAutoInc) "${column.name} (auto)" else column.name
                            )
                        },
                        enabled = !isAutoInc,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val filtered = fieldValues.value.filter { (key, _) -> key != autoIncrementColumn }
                    .filter { (_, value) -> value.isNotEmpty() }
                onConfirm(filtered)
            }) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
