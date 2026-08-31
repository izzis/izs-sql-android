package com.sqlclient.android.ui.screens.dataeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.data.remote.ColumnMetadata
import com.sqlclient.android.ui.components.AppTopBar
import com.sqlclient.android.ui.components.CurrentQueryBar
import com.sqlclient.android.ui.viewmodel.DataEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineDataEditorScreen(
    viewModel: DataEditorViewModel,
    profile: ConnectionProfileEntity,
    database: String,
    table: String,
    isLocked: Boolean = false,
    onToggleLock: (() -> Unit)? = null,
    onBack: () -> Unit
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

    val snackbarHostState = remember { SnackbarHostState() }
    val isReadonly = isLocked
    var showInsertDialog by remember { mutableStateOf(false) }
    var editingCell by remember { mutableStateOf<Triple<Int, Int, Any?>?>(null) }
    var pendingSql by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var showQueryEditor by remember { mutableStateOf(false) }
    var editableQuery by remember { mutableStateOf("") }
    val pendingEdits by viewModel.pendingEdits.collectAsState()
    val pendingDeletes by viewModel.pendingDeletes.collectAsState()
    val hasPending = pendingEdits.isNotEmpty() || pendingDeletes.isNotEmpty()

    LaunchedEffect(query) {
        editableQuery = query
    }

    LaunchedEffect(database, table) {
        viewModel.loadData(database, table)
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

    val topBarColor = try {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(profile.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
    val currentQuery by viewModel.currentQuery.collectAsState()
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
                onSave = if (hasPending) ({ showSaveConfirm = true }) else null
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { CurrentQueryBar(query = currentQuery) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SqlEditorBar(
                query = editableQuery,
                onQueryChange = { editableQuery = it },
                onExecute = {
                    if (isLocked) {
                        viewModel.setQuery(editableQuery)
                        return@SqlEditorBar
                    }
                    viewModel.setQuery(editableQuery)
                    viewModel.executeCustomQuery()
                },
                isExpanded = showQueryEditor,
                onToggle = { showQueryEditor = !showQueryEditor }
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
                    onSave = if (hasPending) ({ showSaveConfirm = true }) else null,
                    onDiscard = { viewModel.clearStaged() }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (columns.isEmpty() || rows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (columns.isEmpty()) "No columns found" else "No data found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                DataGrid(
                    columns = columns,
                    rows = rows,
                    selectedRows = selectedRows,
                    pendingEdits = pendingEdits,
                    pendingDeletes = pendingDeletes,
                    isReadonly = isReadonly,
                    onCellClick = { rowIndex, colIndex, value ->
                        if (!isReadonly) {
                            editingCell = Triple(rowIndex, colIndex, value)
                        }
                    },
                    onRowToggle = { viewModel.toggleRowSelection(it) },
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
                onLimitChange = { viewModel.changeLimit(it) }
            )
        }
    }

    editingCell?.let { (rowIndex, colIndex, value) ->
        val columnName = columns.getOrNull(colIndex)?.name ?: ""
        // OK -> stage locally, highlight red, no immediate write
        CellEditDialog(
            columnName = columnName,
            initialValue = value?.toString() ?: "",
            onConfirm = { newValue ->
                viewModel.stageEdit(rowIndex, colIndex, columnName, newValue)
                editingCell = null
            },
            onDismiss = { editingCell = null }
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
                        Text(sql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { clipboard.setText(AnnotatedString(sql)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show() }) {
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
        val clipboard = LocalClipboardManager.current
        val ctx = LocalContext.current
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
                    TextButton(onClick = { clipboard.setText(AnnotatedString(fullSql)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show() }) {
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
}

@Composable
private fun SqlEditorBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onExecute: () -> Unit,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 5
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onExecute) {
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
    onSave: (() -> Unit)? = null,
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
                TextButton(onClick = { onSave?.invoke() }, enabled = !isLocked, modifier = Modifier.height(28.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Save", style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Text("$totalCount rows", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasPending) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onDiscard?.invoke() }, modifier = Modifier.height(28.dp)) { Text("Discard", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { onSave?.invoke() }, enabled = !isLocked, modifier = Modifier.height(28.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Save", style = MaterialTheme.typography.labelSmall)
                }
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
    // Hide suggestions when input ends with space or frag already equals a column name
    val frag = whereInput.trimEnd().split(Regex("[^a-zA-Z0-9_]+")).lastOrNull()?.lowercase() ?: ""
    val endsWithSpace = whereInput.isNotEmpty() && whereInput.last().isWhitespace()
    val exactMatch = columns.any { it.name.lowercase() == frag }
    val showSuggestions = frag.length >= 1 && !endsWithSpace && !exactMatch
    val suggestions = if (showSuggestions) columns.map { it.name }.filter { it.lowercase().contains(frag) }.take(6) else emptyList()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "WHERE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = whereInput,
                onValueChange = onWhereChange,
                placeholder = { Text("kolom = 'nilai'", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                trailingIcon = {
                    if (whereInput.isNotEmpty()) {
                        IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            )
            TextButton(onClick = onApply, modifier = Modifier.height(32.dp)) { Text("Filter", style = MaterialTheme.typography.labelSmall) }
        }
        if (suggestions.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 44.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { col ->
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            val cur = whereInput
                            val lastFrag = cur.trimEnd().split(Regex("[^a-zA-Z0-9_]+")).lastOrNull() ?: ""
                            val prefix = if (lastFrag.isNotEmpty() && cur.trimEnd().endsWith(lastFrag)) cur.trimEnd().dropLast(lastFrag.length) else cur.trimEnd()
                            val sep = if (prefix.isEmpty() || prefix.endsWith(" ")) "" else " "
                            val next = prefix + sep + col + " "
                            onWhereChange(next)
                        },
                        label = { Text(col, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
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
    onLimitChange: (Int) -> Unit
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
                    if (hasMoreData) append(" (more available)")
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
        }
    }
}

@Composable
private fun DataGrid(
    columns: List<ColumnMetadata>,
    rows: List<List<Any?>>,
    selectedRows: Set<Int>,
    pendingEdits: Map<Pair<Int, Int>, com.sqlclient.android.ui.viewmodel.DataEditorViewModel.StagedEdit> = emptyMap(),
    pendingDeletes: Set<Int> = emptySet(),
    isReadonly: Boolean,
    onCellClick: (rowIndex: Int, colIndex: Int, value: Any?) -> Unit,
    onRowToggle: (Int) -> Unit,
    onLoadMore: () -> Unit,
    hasMoreData: Boolean,
    isLoadingMore: Boolean,
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberScrollState()
    val lazyListState = rememberLazyListState()
    val cellWidth = 150.dp
    val checkboxWidth = 48.dp
    val rowHeight = 40.dp
    val displayRows = androidx.compose.runtime.remember(rows) {
        rows.map { r -> r.map { v -> if (v == null) "NULL" else { val s = v.toString(); if (s.length > 200) s.take(200) + "…" else s } } }
    }

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
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                    if (!isReadonly) {
                        Box(
                            modifier = Modifier.width(checkboxWidth).height(rowHeight).padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "#", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    columns.forEach { column ->
                        Box(
                            modifier = Modifier.width(cellWidth).height(rowHeight).padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = column.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(rows, key = { index, _ -> index }, contentType = { _, _ -> "row" }) { rowIndex, row ->
                        val displayRow = displayRows.getOrNull(rowIndex) ?: emptyList()
                        Column {
                            Row(modifier = Modifier.fillMaxWidth().height(rowHeight)
                                .background(if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))) {
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
                                    Box(
                                        modifier = Modifier.width(cellWidth).height(rowHeight)
                                            .background(bg)
                                            .clickable { onCellClick(rowIndex, colIndex, if (staged != null) staged else cellValue) }
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
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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
