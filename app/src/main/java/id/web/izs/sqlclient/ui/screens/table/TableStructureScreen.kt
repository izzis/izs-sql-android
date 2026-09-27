package id.web.izs.sqlclient.ui.screens.table

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import id.web.izs.sqlclient.data.remote.model.ColumnInfo
import id.web.izs.sqlclient.data.remote.model.IndexInfo
import id.web.izs.sqlclient.ui.components.TypeLenPicker
import id.web.izs.sqlclient.util.IndexAnalyzer
import id.web.izs.sqlclient.util.TableSql
import id.web.izs.sqlclient.ui.components.AppTopBar
import id.web.izs.sqlclient.ui.components.CurrentQueryBar
import id.web.izs.sqlclient.ui.components.ReconnectBanner
import id.web.izs.sqlclient.ui.viewmodel.ConnectionViewModel
import id.web.izs.sqlclient.ui.viewmodel.IndexManagementViewModel
import id.web.izs.sqlclient.ui.viewmodel.TableStructureViewModel
import id.web.izs.sqlclient.util.rememberCopyToClipboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableStructureScreen(
    viewModel: TableStructureViewModel,
    indexViewModel: IndexManagementViewModel,
    connectionViewModel: ConnectionViewModel,
    database: String,
    table: String,
    isLocked: Boolean = false,
    onToggleLock: (() -> Unit)? = null,
    onBack: () -> Unit,
    onData: (() -> Unit)? = null,
    onReconnect: (() -> Unit)? = null,
    isReconnecting: Boolean = false,
    topBarColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
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

    // Analysis is pure in-memory over the already-loaded list: no extra query, always shown.
    val effectiveIndexes = if (idxIndexes.isNotEmpty()) idxIndexes else indexes
    val indexIssues = remember(effectiveIndexes) { IndexAnalyzer.analyze(database, table, effectiveIndexes) }

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Columns", "DDL", "Indexes")
    val currentQuery by viewModel.currentQuery.collectAsState()

    var showCreateIndex by remember { mutableStateOf(false) }
    var indexToDelete by remember { mutableStateOf<IndexInfo?>(null) }
    var editingColumn by remember { mutableStateOf<ColumnInfo?>(null) }
    var showAddColumn by remember { mutableStateOf(false) }
    var columnToDelete by remember { mutableStateOf<ColumnInfo?>(null) }
    var pendingSql by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(database, table) {
        viewModel.resetQueryLog()
        viewModel.loadStructure(database, table)
        indexViewModel.resetQueryLog()
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
        idxSuccess?.let {
            snackbarHostState.showSnackbar(it)
            indexViewModel.clearSuccess()
            // Column add/modify/drop only reload the index VM's own list — refresh
            // the displayed Columns tab (+ DDL + indexes) so nothing goes stale.
            viewModel.loadStructure(database, table)
            indexViewModel.loadIndexes(database, table)
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
                onRefresh = {
                    viewModel.loadStructure(database, table)
                    indexViewModel.loadIndexes(database, table)
                },
                isRefreshing = isLoading || idxLoading,
                isLocked = isLocked,
                showLock = true,
                onToggleLock = onToggleLock,
                onBack = onBack,
                onData = onData,
                onReconnect = onReconnect,
                isReconnecting = isReconnecting
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            CurrentQueryBar(
                queries = if (idxQuery.isNotEmpty()) idxQuery else currentQuery,
                onClear = {
                    viewModel.resetQueryLog()
                    indexViewModel.resetQueryLog()
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { if (!isLocked) showAddColumn = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add Column") },
                    text = { Text("Add Column") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
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
            ReconnectBanner(message = reconnectMessage)
            PrimaryTabRow(selectedTabIndex = selectedTab) {
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
                    0 -> ColumnsTab(columns, isLocked) { editingColumn = it }
                    1 -> DdlTab(createTable)
                    2 -> IndexesTabEditable(
                        indexes = effectiveIndexes,
                        isLocked = isLocked,
                        onDeleteIndex = { indexToDelete = it },
                        issues = indexIssues,
                        onDropIssue = { issue ->
                            effectiveIndexes.firstOrNull { it.name == issue.indexName }?.let { indexToDelete = it }
                        }
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

    editingColumn?.let { col ->
        EditColumnDialog(
            column = col,
            allColumnNames = columns.map { it.name },
            onDismiss = { editingColumn = null },
            onDelete = { columnToDelete = col; editingColumn = null },
            onConfirm = { name, type, nullable, default, comment, autoIncrement, position, unique, onUpdate ->
                if (isLocked) { editingColumn = null; return@EditColumnDialog }
                val colDef = buildColumnDef(name, type, nullable, default, comment, autoIncrement, position, unique, onUpdate)
                val sql = indexViewModel.buildModifyColumnSql(database, table, colDef)
                pendingSql = sql
                pendingAction = {
                    indexViewModel.modifyColumn(database, table, colDef, isLocked = isLocked)
                    editingColumn = null
                }
            }
        )
    }

    if (showAddColumn) {
        AddColumnDialog(
            allColumnNames = columns.map { it.name },
            onDismiss = { showAddColumn = false },
            onConfirm = { name, type, nullable, default, comment, autoIncrement, primaryKey, position, unique, onUpdate ->
                if (isLocked) { showAddColumn = false; return@AddColumnDialog }
                val pkStr = if (primaryKey) " PRIMARY KEY" else ""
                val colDef = buildColumnDef(name, type, nullable, default, comment, autoIncrement, position, unique && !primaryKey, onUpdate) + pkStr
                val sql = indexViewModel.buildAddColumnSql(database, table, colDef)
                pendingSql = sql
                pendingAction = {
                    indexViewModel.addColumn(database, table, colDef, isLocked = isLocked)
                    showAddColumn = false
                }
            }
        )
    }

    columnToDelete?.let { col ->
        val dropSql = indexViewModel.buildDropColumnSql(database, table, col.name)
        AlertDialog(
            onDismissRequest = { columnToDelete = null },
            title = { Text("Drop Column") },
            text = {
                Column {
                    Text("Drop column \"${col.name}\" from `$database`.`$table`? Data in this column will be lost.")
                    if (col.isPrimaryKey) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Warning: this is a PRIMARY KEY column — the server will reject the drop unless the key is removed first.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(dropSql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !isLocked, onClick = {
                    pendingSql = dropSql
                    pendingAction = { indexViewModel.dropColumn(database, table, col.name, isLocked = isLocked); columnToDelete = null }
                    columnToDelete = null
                }) { Text("Drop", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { columnToDelete = null }) { Text("Cancel") } }
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
                        Text(sql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { copyToClipboard(sql, "Copied") }) {
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
private fun ColumnsTab(columns: List<ColumnInfo>, isLocked: Boolean = false, onEditColumn: (ColumnInfo) -> Unit = {}) {
    if (columns.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No columns found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    // Whole table (header + rows) shares one horizontal scroll state so nothing
    // wraps on narrow screens — swipe right to reveal the rest. Each column is
    // sized to its widest content (header included) so there is no dead space.
    val tableScroll = rememberScrollState()
    val headerStyle = MaterialTheme.typography.titleSmall
    val widths = ColumnWidths(
        name = measureColumnWidth("Name", headerStyle, columns.map { it.name },
            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), 64.dp, 280.dp),
        type = measureColumnWidth("Type", headerStyle, columns.map { it.type },
            MaterialTheme.typography.bodySmall, 64.dp, 220.dp),
        nullFlag = measureColumnWidth("Null", headerStyle, columns.map { if (it.nullable) "YES" else "NO" },
            MaterialTheme.typography.bodySmall, 48.dp, 80.dp),
        key = measureColumnWidth("Key", headerStyle, columns.map { keyLabel(it) },
            MaterialTheme.typography.labelSmall, 48.dp, 96.dp),
        default = measureColumnWidth("Default", headerStyle, columns.map { it.defaultValue ?: "NULL" },
            MaterialTheme.typography.bodySmall, 64.dp, 240.dp),
        comment = measureColumnWidth("Comment", headerStyle, columns.map { it.comment ?: "" },
            MaterialTheme.typography.bodySmall, 64.dp, 240.dp)
    )
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.horizontalScroll(tableScroll).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Name", style = headerStyle, fontWeight = FontWeight.Bold, modifier = Modifier.width(widths.name), maxLines = 1)
            Text(text = "Type", style = headerStyle, fontWeight = FontWeight.Bold, modifier = Modifier.width(widths.type), maxLines = 1)
            Text(text = "Null", style = headerStyle, fontWeight = FontWeight.Bold, modifier = Modifier.width(widths.nullFlag), maxLines = 1)
            Text(text = "Key", style = headerStyle, fontWeight = FontWeight.Bold, modifier = Modifier.width(widths.key), maxLines = 1)
            Text(text = "Default", style = headerStyle, fontWeight = FontWeight.Bold, modifier = Modifier.width(widths.default), maxLines = 1)
            Text(text = "Comment", style = headerStyle, fontWeight = FontWeight.Bold, modifier = Modifier.width(widths.comment), maxLines = 1)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(columns) { column -> ColumnRow(column, isLocked, tableScroll, widths, onEditColumn) }
        }
    }
}

/**
 * Display string for the Key cell: raw MySQL flag (PRI/UNI/MUL) plus AUTO when
 * the column is auto-increment, e.g. "PRI·AUTO". Empty when neither applies.
 */
private fun keyLabel(column: ColumnInfo): String = buildString {
    append(column.keyType)
    if (column.isAutoIncrement) {
        if (isNotEmpty()) append("·")
        append("AUTO")
    }
}

/**
 * Builds the `<name> <type> NULL.. [AUTO_INCREMENT] [DEFAULT ..] [COMMENT ..] [FIRST|AFTER ..]`
 * fragment shared by ADD/MODIFY COLUMN. Position: "" = keep/append, "FIRST" = first, else AFTER column.
 */
private fun buildColumnDef(
    name: String,
    type: String,
    nullable: Boolean,
    default: String,
    comment: String,
    autoIncrement: Boolean,
    position: String,
    unique: Boolean = false,
    onUpdateCurrentTimestamp: Boolean = false
): String {
    val nullStr = if (nullable) "NULL" else "NOT NULL"
    val autoStr = if (autoIncrement) " AUTO_INCREMENT" else ""
    val uniqueStr = if (unique) " UNIQUE" else ""
    // Same DEFAULT quoting as the Create Table builder (bare numerics/CURRENT_TIMESTAMP).
    val defaultStr = if (default.isNotBlank()) " DEFAULT ${TableSql.qDefault(default)}" else ""
    val onUpdateStr = if (onUpdateCurrentTimestamp) " ON UPDATE CURRENT_TIMESTAMP" else ""
    val commentStr = if (comment.isNotBlank()) " COMMENT '${comment.replace("'", "''")}'" else ""
    val posStr = when {
        position.isEmpty() -> ""
        position == "FIRST" -> " FIRST"
        else -> " AFTER `$position`"
    }
    return "`$name` $type $nullStr$autoStr$uniqueStr$defaultStr$onUpdateStr$commentStr$posStr"
}

/** Fixed widths for the Columns tab, measured from content (see [measureColumnWidth]). */
private data class ColumnWidths(
    val name: Dp,
    val type: Dp,
    val nullFlag: Dp,
    val key: Dp,
    val default: Dp,
    val comment: Dp
)

/**
 * Measures the widest string (header + values, each in its own [TextStyle]) and
 * returns it as a [Dp] width with a small padding, clamped to [min]..[max].
 * Recomputed only when the measured strings change.
 */
@Composable
private fun measureColumnWidth(
    header: String,
    headerStyle: TextStyle,
    values: List<String>,
    valueStyle: TextStyle,
    min: Dp,
    max: Dp
): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(header, values) {
        val widest = max(
            measurer.measure(header, headerStyle).size.width,
            values.maxOfOrNull { measurer.measure(it, valueStyle).size.width } ?: 0
        )
        (with(density) { widest.toDp() } + 8.dp).coerceIn(min, max)
    }
}

@Composable
private fun ColumnRow(column: ColumnInfo, isLocked: Boolean = false, tableScroll: ScrollState, widths: ColumnWidths, onEditColumn: (ColumnInfo) -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = !isLocked, onClickLabel = "Edit column") { onEditColumn(column) }
            .horizontalScroll(tableScroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name cell: names longer than the measured cap scroll inside their own cell.
        Box(modifier = Modifier.width(widths.name).horizontalScroll(rememberScrollState())) {
            Text(text = column.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (column.isPrimaryKey) FontWeight.Bold else FontWeight.Normal,
                color = if (column.isPrimaryKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1)
        }
        Text(text = column.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(widths.type), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = if (column.nullable) "YES" else "NO", style = MaterialTheme.typography.bodySmall,
            color = if (column.nullable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error, modifier = Modifier.width(widths.nullFlag))
        Text(text = keyLabel(column),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(widths.key))
        Text(text = column.defaultValue ?: "NULL", style = MaterialTheme.typography.bodySmall,
            color = if (column.defaultValue != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(widths.default), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = column.comment ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(widths.comment), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun IndexesTabEditable(
    indexes: List<IndexInfo>,
    isLocked: Boolean,
    onDeleteIndex: (IndexInfo) -> Unit,
    issues: List<IndexAnalyzer.IndexIssue> = emptyList(),
    onDropIssue: (IndexAnalyzer.IndexIssue) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Auto health box: pure in-memory analysis over the loaded list, no tap needed.
        if (indexes.isNotEmpty()) {
            if (issues.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Indexes healthy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    issues.forEach { issue ->
                        IndexIssueBox(issue = issue, isLocked = isLocked, onDrop = { onDropIssue(issue) })
                    }
                }
            }
        }
        if (indexes.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(text = "No indexes found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Tap Create Index to add one", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                items(indexes) { index -> IndexCardEditable(index, isLocked, onDeleteIndex) }
            }
        }
    }
}

@Composable
private fun IndexIssueBox(
    issue: IndexAnalyzer.IndexIssue,
    isLocked: Boolean,
    onDrop: () -> Unit
) {
    val (icon, tint) = when (issue.severity) {
        IndexAnalyzer.Severity.ERROR -> Icons.Default.Error to MaterialTheme.colorScheme.error
        IndexAnalyzer.Severity.WARNING -> Icons.Default.Warning to MaterialTheme.colorScheme.tertiary
        IndexAnalyzer.Severity.INFO -> Icons.Default.Info to MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = issue.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onDrop, enabled = !isLocked) {
                Text("Drop", color = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun IndexCardEditable(index: IndexInfo, isLocked: Boolean, onDeleteIndex: (IndexInfo) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = index.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = index.columns.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${index.type} • ${if (index.isUnique) "Unique" else "Non-unique"}" +
                        (index.cardinality?.let { " • ~${IndexAnalyzer.formatCardinality(it)}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

/**
 * Position picker shared by Add/Edit dialogs. Position: "" = default
 * ([defaultLabel]), "FIRST" = first, else AFTER that column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PositionDropdown(
    allColumnNames: List<String>,
    exclude: String?,
    position: String,
    defaultLabel: String,
    onPositionChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when {
        position.isEmpty() -> defaultLabel
        position == "FIRST" -> "First"
        else -> "After $position"
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label, onValueChange = {},
            readOnly = true, label = { Text("Position") }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(defaultLabel) }, onClick = { onPositionChange(""); expanded = false })
            DropdownMenuItem(text = { Text("First") }, onClick = { onPositionChange("FIRST"); expanded = false })
            allColumnNames.filter { it != exclude }.forEach { other ->
                DropdownMenuItem(text = { Text("After $other") }, onClick = { onPositionChange(other); expanded = false })
            }
        }
    }
}

@Composable
private fun EditColumnDialog(
    column: ColumnInfo,
    allColumnNames: List<String>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (name: String, type: String, nullable: Boolean, defaultValue: String, comment: String, autoIncrement: Boolean, position: String, unique: Boolean, onUpdate: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(column.name) }
    var fullType by remember { mutableStateOf(column.type) }
    var typeOk by remember { mutableStateOf(true) }
    var formError by remember { mutableStateOf<String?>(null) }
    var nullable by remember { mutableStateOf(column.nullable) }
    var defaultValue by remember { mutableStateOf(column.defaultValue ?: "") }
    var comment by remember { mutableStateOf(column.comment ?: "") }
    var autoIncrement by remember { mutableStateOf(column.isAutoIncrement) }
    var unique by remember { mutableStateOf(column.keyType == "UNI") }
    var onUpdate by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf("") }
    val wasUnique = column.keyType == "UNI"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Column: ${column.name}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Column Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TypeLenPicker(
                    initialType = column.type,
                    onTypeChange = { fullType = it },
                    onValidityChange = { typeOk = it }
                )
                if (!typeOk) {
                    Text("Len must be numeric, e.g. 100 or 10,2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = defaultValue, onValueChange = { defaultValue = it },
                    label = { Text("Default Value (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment, onValueChange = { comment = it },
                    label = { Text("Comment (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Compact horizontally-scrollable flag strip (one row instead of four).
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.material3.FilterChip(
                        selected = nullable,
                        onClick = { nullable = !nullable },
                        label = { Text("NULL", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(30.dp)
                    )
                    androidx.compose.material3.FilterChip(
                        selected = autoIncrement,
                        onClick = { autoIncrement = !autoIncrement },
                        label = { Text("AI", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(30.dp)
                    )
                    androidx.compose.material3.FilterChip(
                        selected = unique,
                        onClick = { unique = !unique; formError = null },
                        label = { Text("UNIQUE", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(30.dp)
                    )
                    if (TableSql.isTemporalType(TableSql.splitType(fullType).first)) {
                        androidx.compose.material3.FilterChip(
                            selected = onUpdate,
                            onClick = { onUpdate = !onUpdate },
                            label = { Text("AUTO-UPDATE", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }
                Text("AI needs integer type + key (PRI/AUTO) • UNIQUE removal via Indexes tab",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (formError != null) {
                    Text(formError!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(8.dp))
                PositionDropdown(
                    allColumnNames = allColumnNames,
                    exclude = column.name,
                    position = position,
                    defaultLabel = "Don't move",
                    onPositionChange = { position = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // MODIFY cannot drop an existing UNIQUE index — redirect to Indexes tab.
                    if (wasUnique && !unique) {
                        formError = "To remove UNIQUE, Drop its index from the Indexes tab"
                        return@TextButton
                    }
                    onConfirm(name, fullType, nullable, defaultValue, comment, autoIncrement, position, unique, onUpdate)
                },
                enabled = name.isNotBlank() && fullType.isNotBlank() && typeOk
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text("Modify", style = MaterialTheme.typography.labelSmall)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(2.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    )
}

@Composable
private fun AddColumnDialog(
    allColumnNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, nullable: Boolean, defaultValue: String, comment: String, autoIncrement: Boolean, primaryKey: Boolean, position: String, unique: Boolean, onUpdate: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var fullType by remember { mutableStateOf("VARCHAR(255)") }
    var typeOk by remember { mutableStateOf(true) }
    var nullable by remember { mutableStateOf(true) }
    var defaultValue by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var autoIncrement by remember { mutableStateOf(false) }
    var primaryKey by remember { mutableStateOf(false) }
    var unique by remember { mutableStateOf(false) }
    var onUpdate by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Column") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Column Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TypeLenPicker(
                    initialType = "VARCHAR(255)",
                    onTypeChange = { fullType = it },
                    onValidityChange = { typeOk = it }
                )
                if (!typeOk) {
                    Text("Len must be numeric, e.g. 100 or 10,2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = defaultValue, onValueChange = { defaultValue = it },
                    label = { Text("Default Value (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment, onValueChange = { comment = it },
                    label = { Text("Comment (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Compact horizontally-scrollable flag strip (one row instead of five).
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.material3.FilterChip(
                        selected = nullable,
                        onClick = { nullable = !nullable },
                        label = { Text("NULL", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(30.dp)
                    )
                    androidx.compose.material3.FilterChip(
                        selected = autoIncrement,
                        onClick = { autoIncrement = !autoIncrement },
                        label = { Text("AI", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(30.dp)
                    )
                    androidx.compose.material3.FilterChip(
                        selected = primaryKey,
                        onClick = { primaryKey = !primaryKey },
                        label = { Text("PK", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(30.dp)
                    )
                    androidx.compose.material3.FilterChip(
                        selected = unique,
                        onClick = { unique = !unique },
                        label = { Text("UNIQUE", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(30.dp)
                    )
                    if (TableSql.isTemporalType(TableSql.splitType(fullType).first)) {
                        androidx.compose.material3.FilterChip(
                            selected = onUpdate,
                            onClick = { onUpdate = !onUpdate },
                            label = { Text("AUTO-UPDATE", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }
                Text("AI needs integer type + key • UNIQUE skipped when PK is set",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                PositionDropdown(
                    allColumnNames = allColumnNames,
                    exclude = null,
                    position = position,
                    defaultLabel = "Last (default)",
                    onPositionChange = { position = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, fullType, nullable, defaultValue, comment, autoIncrement, primaryKey, position, unique, onUpdate) },
                enabled = name.isNotBlank() && fullType.isNotBlank() && typeOk && !allColumnNames.any { it.equals(name, ignoreCase = true) }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel")
            }
        }
    )
}
