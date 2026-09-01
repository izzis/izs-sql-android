package com.sqlclient.android.ui.screens.query

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.ui.components.AppTopBar
import com.sqlclient.android.ui.components.CurrentQueryBar
import com.sqlclient.android.ui.components.DataTable
import com.sqlclient.android.ui.components.QueryTabBar
import com.sqlclient.android.ui.components.ReconnectBanner
import com.sqlclient.android.data.local.entity.QueryHistoryEntity
import com.sqlclient.android.ui.components.SqlEditor
import com.sqlclient.android.ui.viewmodel.BrowserViewModel
import com.sqlclient.android.ui.viewmodel.ConnectionViewModel
import com.sqlclient.android.ui.viewmodel.QueryResultState
import com.sqlclient.android.ui.viewmodel.QueryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SQLEditorScreen(
    viewModel: QueryViewModel,
    connectionViewModel: ConnectionViewModel,
    browserViewModel: BrowserViewModel,
    profile: ConnectionProfileEntity,
    database: String,
    table: String,
    isLocked: Boolean = false,
    onToggleLock: (() -> Unit)? = null,
    onBack: () -> Unit,
    onReconnect: (() -> Unit)? = null,
    isReconnecting: Boolean = false
) {
    val queryTabs by viewModel.queryTabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val queryResult by viewModel.queryResult.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()
    val savedQueries by viewModel.savedQueries.collectAsState()

    // Tabs are per-database to avoid mixing db1 tabs into db2
    val tabsForDb = remember(queryTabs, database) { queryTabs.filter { it.database == database } }
    val activeTab = queryTabs.find { it.id == activeTabId }?.takeIf { it.database == database } ?: tabsForDb.firstOrNull()

    LaunchedEffect(profile.id) {
        viewModel.setCurrentProfileId(profile.id)
    }

    // Auto-select database on editor open and ensure at least one tab for this database
    LaunchedEffect(database, profile.id) {
        viewModel.loadFavorites(profile.id, database)
        viewModel.useDatabase(database)
        viewModel.ensureTabForDatabase(database)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val reconnectMessage by connectionViewModel.reconnectMessage.collectAsState()
    LaunchedEffect(reconnectMessage) {
        reconnectMessage?.let {
            kotlinx.coroutines.delay(2000)
            connectionViewModel.clearReconnectMessage()
        }
    }

    val topBarColor = try {
        Color(android.graphics.Color.parseColor(profile.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
    val currentQuery by viewModel.currentQuery.collectAsState()
    var showFavorites by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(true) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var renamingQuery by remember { mutableStateOf<QueryHistoryEntity?>(null) }
    var deletingQuery by remember { mutableStateOf<QueryHistoryEntity?>(null) }

    // Autocomplete
    var enableAutocomplete by remember { mutableStateOf(true) }
    val databases by browserViewModel.databases.collectAsState()
    val tablesByDb by browserViewModel.tables.collectAsState()
    val allColumns by browserViewModel.columns.collectAsState()
    var columnCache by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val tableNames = remember(tablesByDb, database) { tablesByDb[database] ?: emptyList() }
    val databaseNames = remember(databases) { databases.map { it.name } }
    val currentColumns = remember(allColumns, database, table, columnCache) {
        allColumns["$database.$table"]?.map { it.name }
            ?: columnCache["$database.$table"]
            ?: emptyList()
    }
    val fetchExtraColumns: (suspend (String) -> List<String>?)? = if (enableAutocomplete) {
        { tableName: String ->
            val (dbName, tblName) = if (tableName.contains('.')) {
                val parts = tableName.split('.', limit = 2)
                parts[0] to parts[1]
            } else {
                database to tableName
            }
            val key = "$dbName.$tblName"
            columnCache[key] ?: run {
                val fetched = viewModel.fetchColumns(dbName, tblName)
                if (fetched != null) columnCache = columnCache + (key to fetched)
                fetched
            }
        }
    } else null

    val fetchTablesForDb: (suspend (String) -> List<String>?)? = if (enableAutocomplete) {
        { dbName: String ->
            browserViewModel.getCachedTables(dbName)
                ?: run {
                    browserViewModel.requestTablesSync(dbName)
                    browserViewModel.getCachedTables(dbName)
                }
        }
    } else null

    LaunchedEffect(database, table, enableAutocomplete) {
        if (enableAutocomplete && currentColumns.isEmpty() && table != "_") {
            val key = "$database.$table"
            if (columnCache[key] == null) {
                val fetched = viewModel.fetchColumns(database, table)
                if (fetched != null) columnCache = columnCache + (key to fetched)
            }
        }
    }
    Scaffold(
        topBar = {
            AppTopBar(
                title = "SQL Editor",
                subtitle = if (table == "_") database else "$database.$table",
                containerColor = topBarColor,
                onRefresh = {
                    if (!viewModel.isWriteQuery()) {
                        viewModel.executeQuery(isLocked = isLocked)
                    }
                },
                isRefreshing = isExecuting,
                isLocked = isLocked,
                showLock = true,
                onToggleLock = onToggleLock,
                onBack = onBack,
                onSave = if (activeTab?.query.isNullOrBlank() == false) ({ showSaveDialog = true }) else null,
                onReconnect = onReconnect,
                isReconnecting = isReconnecting
            )
        },
        bottomBar = {
            CurrentQueryBar(
                queries = if (currentQuery.isNotEmpty()) currentQuery else listOfNotNull(activeTab?.query),
                isExecuting = isExecuting,
                onCancel = { viewModel.cancelQuery() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ReconnectBanner(message = reconnectMessage)
            QueryTabBar(
                tabs = tabsForDb,
                activeTabId = activeTab?.id ?: activeTabId,
                onTabClick = { viewModel.setActiveTab(it) },
                onCloseTab = { viewModel.closeTab(it) },
                onAddTab = { viewModel.addTab(database) }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showEditor = !showEditor }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (showEditor) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showEditor) "Hide editor" else "Show editor",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Editor", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (savedQueries.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showFavorites = !showFavorites }) {
                        Icon(
                            imageVector = if (showFavorites) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text("Saved (${savedQueries.size})", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (showFavorites && savedQueries.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(max = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(savedQueries.size) { idx ->
                        val q = savedQueries[idx]
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .clickable { viewModel.openSavedQuery(q) }
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
                            IconButton(onClick = { renamingQuery = q }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { deletingQuery = q }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            if (showEditor) {
                activeTab?.let { tab ->
                    var queryText by remember(tab.id, tab.cursorOffset) { mutableStateOf(TextFieldValue(tab.query, selection = TextRange((tab.cursorOffset ?: tab.query.length).coerceIn(0, tab.query.length)))) }
                    LaunchedEffect(tab.query, tab.cursorOffset) {
                        if (queryText.text != tab.query) {
                            val pos = (tab.cursorOffset ?: tab.query.length).coerceIn(0, tab.query.length)
                            queryText = TextFieldValue(tab.query, selection = TextRange(pos))
                        }
                    }

                    SqlEditor(
                        query = queryText,
                        onQueryChange = {
                            queryText = it
                            viewModel.updateQuery(tab.id, it.text)
                        },
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        readOnly = isLocked,
                        enableAutocomplete = enableAutocomplete,
                        databaseNames = databaseNames,
                        tableNames = tableNames,
                        columnNames = currentColumns,
                        onFetchExtraColumns = fetchExtraColumns,
                        onFetchTablesForDatabase = fetchTablesForDb
                    )
                }
            }

            activeTab?.let { tab ->
                var showWriteConfirm by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { enableAutocomplete = !enableAutocomplete }
                    ) {
                        IconButton(onClick = { enableAutocomplete = !enableAutocomplete }, modifier = Modifier.size(28.dp)) {
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
                    Button(
                        onClick = {
                            if (viewModel.isWriteQuery()) {
                                showWriteConfirm = true
                            } else {
                                viewModel.executeQuery(isLocked = isLocked)
                            }
                        },
                        enabled = !isExecuting && !(isLocked && viewModel.isWriteQuery()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Execute",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Execute", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (showWriteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showWriteConfirm = false },
                        title = { Text("Confirm Write Query") },
                        text = {
                            Column {
                                Text("This query modifies data. Do you want to execute it?", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Query:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = viewModel.getActiveQueryText(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showWriteConfirm = false
                                viewModel.executeQuery(isLocked = isLocked)
                            }) {
                                Text("Execute", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWriteConfirm = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }

            if (showSaveDialog) {
                val isEdit = activeTab?.savedQueryId != null
                var saveName by remember(activeTab?.id, activeTab?.savedQueryId) { mutableStateOf(activeTab?.title?.take(40) ?: "") }
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text(if (isEdit) "Update Saved Query" else "Save Query") },
                    text = {
                        Column {
                            Text("Name:", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = saveName,
                                onValueChange = { saveName = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Query:", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = activeTab?.query ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showSaveDialog = false
                            val name = saveName.ifBlank { null }
                            viewModel.saveFavoriteForActiveTab(name, database)
                        }) { Text(if (isEdit) "Update" else "Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (renamingQuery != null) {
                var renameText by remember { mutableStateOf(renamingQuery!!.name ?: "") }
                AlertDialog(
                    onDismissRequest = { renamingQuery = null },
                    title = { Text("Rename Query") },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.renameSavedQuery(renamingQuery!!, renameText)
                            renamingQuery = null
                        }) { Text("Rename") }
                    },
                    dismissButton = {
                        TextButton(onClick = { renamingQuery = null }) { Text("Cancel") }
                    }
                )
            }

            if (deletingQuery != null) {
                AlertDialog(
                    onDismissRequest = { deletingQuery = null },
                    title = { Text("Delete Saved Query") },
                    text = { Text("Delete \"${deletingQuery?.name ?: "Unnamed"}\"?") },
                    confirmButton = {
                        TextButton(onClick = {
                            deletingQuery?.let { viewModel.deleteSavedQuery(it) }
                            deletingQuery = null
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { deletingQuery = null }) { Text("Cancel") }
                    }
                )
            }

            when (val result = queryResult) {
                is QueryResultState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Executing query...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is QueryResultState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (result.truncated) "Result: 1000 rows (truncated — add LIMIT to see more)" else "Result: ${result.rowCount} rows",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (result.truncated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        DataTable(
                            columns = result.columns,
                            rows = result.rows,
                            modifier = Modifier.fillMaxSize(),
                            database = database,
                            table = table,
                            onQuickUpdate = { col, row ->
                                viewModel.openQuickUpdate(database, table, col, row, result.columns)
                            }
                        )
                    }
                }
                is QueryResultState.UpdateSuccess -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Query executed successfully",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${result.affectedRows} rows affected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is QueryResultState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is QueryResultState.Idle -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Enter a SQL query and click Execute",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
