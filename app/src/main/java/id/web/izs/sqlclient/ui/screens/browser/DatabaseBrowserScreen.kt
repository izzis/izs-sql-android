
package id.web.izs.sqlclient.ui.screens.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import id.web.izs.sqlclient.data.local.entity.ConnectionProfileEntity
import id.web.izs.sqlclient.data.remote.model.UserInfo
import id.web.izs.sqlclient.ui.components.AppSidebar
import id.web.izs.sqlclient.ui.components.AppTopBar
import id.web.izs.sqlclient.ui.components.CurrentQueryBar
import id.web.izs.sqlclient.ui.components.DatabaseTree
import id.web.izs.sqlclient.ui.viewmodel.BrowserViewModel
import id.web.izs.sqlclient.ui.viewmodel.BrowserViewModel.BrowserPanel
import id.web.izs.sqlclient.ui.viewmodel.ConnectionViewModel
import id.web.izs.sqlclient.ui.viewmodel.QueryViewModel
import id.web.izs.sqlclient.ui.components.TypeLenPicker
import id.web.izs.sqlclient.ui.viewmodel.UserPermissionViewModel
import id.web.izs.sqlclient.util.QueryLogEntry
import id.web.izs.sqlclient.util.SqlUtil
import id.web.izs.sqlclient.util.TableSql
import id.web.izs.sqlclient.util.rememberCopyToClipboard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DatabaseBrowserScreen(
    viewModel: BrowserViewModel,
    userViewModel: UserPermissionViewModel,
    connectionViewModel: ConnectionViewModel,
    queryViewModel: QueryViewModel,
    profile: ConnectionProfileEntity,
    onDisconnect: () -> Unit,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onOpenQuery: (String, String) -> Unit,
    onOpenTableStructure: (String, String) -> Unit,
    onOpenDataEditor: (String, String) -> Unit,
    onOpenDbStructure: (String) -> Unit = {},
    onOpenUserDetail: (String, String) -> Unit = { _, _ -> },
    onNavigateToSavedQueries: () -> Unit = {},
    onReconnect: (() -> Unit)? = null,
    isReconnecting: Boolean = false
) {
    val copyToClipboard = rememberCopyToClipboard()
    val databases by viewModel.databases.collectAsState()
    val visibleDatabases by viewModel.visibleDatabases.collectAsState()
    val tables by viewModel.tables.collectAsState()
    val columns by viewModel.columns.collectAsState()
    val indexes by viewModel.indexes.collectAsState()
    val columnsFailed by viewModel.columnsFailed.collectAsState()
    val indexesFailed by viewModel.indexesFailed.collectAsState()
    val tableSizes by viewModel.tableSizes.collectAsState()
    val expandedDatabases by viewModel.expandedDatabases.collectAsState()
    val expandedTables by viewModel.expandedTables.collectAsState()
    val selectedTable by viewModel.selectedTable.collectAsState()
    val selectedDatabase by viewModel.selectedDatabase.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshingTables by viewModel.isRefreshingTables.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val tableSearchQuery by viewModel.tableSearchQuery.collectAsState()
    val hasLoadedDatabases by viewModel.hasLoadedDatabases.collectAsState()
    val currentQuery by viewModel.currentQuery.collectAsState()
    val users by userViewModel.users.collectAsState()
    val usersLoading by userViewModel.isLoading.collectAsState()
    val userCurrentQuery by userViewModel.currentQuery.collectAsState()

    // isLocked comes from sessionLocked (parent)
    val snackbarHostState = remember { SnackbarHostState() }
    // Table DDL dialog state (create/drop/rename/truncate via preview + confirm)
    var showCreateTableDb by remember { mutableStateOf<String?>(null) }
    var tableMenuTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showRenameTable by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingSql by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val tableOpSuccess by viewModel.operationSuccess.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Browser", "Info")
    val activePanel by viewModel.activePanel.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val canGoBack = selectedDatabase != null || activePanel != BrowserPanel.TABLE_INFO
    val onBrowserBack: () -> Unit = {
        if (selectedDatabase != null) viewModel.clearSelectedDatabase()
        else if (activePanel != BrowserPanel.TABLE_INFO) viewModel.setActivePanel(BrowserPanel.TABLE_INFO)
    }
    // Drawer has its own back handling — only handle browser back when drawer is closed
    BackHandler(enabled = canGoBack && !drawerState.isOpen) { onBrowserBack() }
    // At browser root, consume system back so it doesn't pop to "connections" without explicit Disconnect
    BackHandler(enabled = !canGoBack && !drawerState.isOpen) { }

    // Page entry — the bar shows what was sent since this screen opened; refresh appends.
    LaunchedEffect(Unit) {
        viewModel.resetQueryLog()
        userViewModel.resetQueryLog()
    }

    // Manual refresh only: first open if cache empty, otherwise use cache (back preserves cache)
    LaunchedEffect(hasLoadedDatabases) {
        if (!hasLoadedDatabases) viewModel.loadDatabases()
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(tableOpSuccess) {
        tableOpSuccess?.let {
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

    val allFavoritesList by queryViewModel.allFavorites.collectAsState()
    val savedQueryCount = allFavoritesList.size
    LaunchedEffect(Unit) { queryViewModel.loadAllFavorites(profile.id) }

    val topBarColor = try {
        Color(profile.color.toColorInt())
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    // TopBar refresh scope: depends on activePanel/selectedDatabase
    val onTopBarRefresh: () -> Unit = {
        when (activePanel) {
            BrowserPanel.TABLE_INFO -> {
                if (selectedDatabase != null) viewModel.refreshTables(selectedDatabase!!)
                else viewModel.refreshDatabases()
            }
            BrowserPanel.USERS -> userViewModel.refreshUsers()
            BrowserPanel.HISTORY -> {} // history is local, no remote refresh
        }
    }
    val isTopBarRefreshing = when (activePanel) {
        BrowserPanel.TABLE_INFO -> isLoading || isRefreshingTables
        BrowserPanel.USERS -> usersLoading
        else -> false
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                AppSidebar(
                    profile = profile,
                    databases = databases,
                    visibleDatabases = if (visibleDatabases.isNotEmpty()) visibleDatabases else databases,
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    users = users,
                    isLoading = isLoading && !hasLoadedDatabases,
                    onRefreshDatabases = { viewModel.refreshDatabases() },
                    onDatabaseClick = { db ->
                        viewModel.selectDatabase(db)
                        viewModel.setActivePanel(BrowserPanel.TABLE_INFO)
                        scope.launch { drawerState.close() }
                    },
                    onUsersClick = {
                        viewModel.setActivePanel(BrowserPanel.USERS)
                        if (!userViewModel.hasLoaded.value) userViewModel.loadUsers()
                        scope.launch { drawerState.close() }
                    },
                    onHistoryClick = {
                        viewModel.setActivePanel(BrowserPanel.HISTORY)
                        scope.launch { drawerState.close() }
                    },
                    onSavedQueriesClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToSavedQueries()
                    },
                    savedQueryCount = savedQueryCount,
                    selectedDatabase = selectedDatabase
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = profile.name,
                    subtitle = "${profile.host}:${profile.port}" + if (!profile.database.isNullOrBlank()) " • DB: ${profile.database}" else "",
                    containerColor = topBarColor,
                    onMenu = { scope.launch { drawerState.open() } },
                    onBack = if (canGoBack) onBrowserBack else null,
                    onRefresh = onTopBarRefresh,
                    isRefreshing = isTopBarRefreshing,
                    isLocked = isLocked,
                    onToggleLock = onToggleLock,
                    onDisconnect = onDisconnect,
                    onReconnect = onReconnect,
                    isReconnecting = isReconnecting
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                val browserQuery = when (activePanel) {
                    BrowserPanel.TABLE_INFO -> currentQuery
                    BrowserPanel.USERS -> userCurrentQuery
                    BrowserPanel.HISTORY -> emptyList<QueryLogEntry>()
                }

                CurrentQueryBar(
                    queries = browserQuery,
                    onClear = when (activePanel) {
                        BrowserPanel.TABLE_INFO -> ({ viewModel.resetQueryLog() })
                        BrowserPanel.USERS -> ({ userViewModel.resetQueryLog() })
                        BrowserPanel.HISTORY -> null
                    }
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                id.web.izs.sqlclient.ui.components.ReconnectBanner(message = reconnectMessage)
            when (activePanel) {
                    BrowserPanel.TABLE_INFO -> {
                        // Main content: Database list on left-style + Table list for selected DB
                        Column(modifier = Modifier.fillMaxSize()) {
                        // Selected DB header with table search + refresh
                        if (selectedDatabase != null) {
                            val db = selectedDatabase!!
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = db,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    modifier = Modifier.clickable { onOpenDbStructure(db) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ViewColumn, contentDescription = "Database Structure", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Structure", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                                }
                                Row(
                                    modifier = Modifier.clickable { onOpenQuery(db, "_") },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = "SQL Editor", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("SQL Editor", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = tableSearchQuery,
                                    onValueChange = { viewModel.setTableSearchQuery(it) },
                                    label = { Text("Search tables...") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (tableSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.setTableSearchQuery("") }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                )
                                if (!isLocked) {
                                    IconButton(
                                        onClick = { showCreateTableDb = db },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "New table", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider()
                        }

                        if (selectedDatabase == null) {
                            // No DB selected: show DatabaseTree as main list (lightweight)
                            if (!hasLoadedDatabases && isLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Loading databases...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            } else {
                                DatabaseTree(
                                    databases = if (visibleDatabases.isNotEmpty()) visibleDatabases else databases,
                                    tables = tables,
                                    columns = columns,
                                    indexes = indexes,
                                    tableSizes = tableSizes,
                                    expandedDatabases = expandedDatabases,
                                    expandedTables = expandedTables,
                                    loadingDatabases = viewModel.loadingDatabases.collectAsState().value,
                                    tableLoadFailed = viewModel.tableLoadFailed.collectAsState().value,
                                    columnsFailed = columnsFailed,
                                    indexesFailed = indexesFailed,
                                    searchQuery = searchQuery,
                                    users = users,
                                    onDatabaseClick = { db ->
                                        viewModel.toggleDatabase(db)
                                    },
                                    onDatabaseOpen = { db ->
                                        viewModel.selectDatabase(db)
                                    },
                                    onRefreshDatabase = { viewModel.refreshDatabase(it) },
                                    onRefreshSizes = { viewModel.refreshTableSizes(it) },
                                    onTableClick = { db, table -> viewModel.toggleTable(db, table) },
                                    onTableSelect = { db, table ->
                                        viewModel.selectTable(db, table)
                                        viewModel.selectDatabase(db)
                                    },
                                    onTableDataClick = { db, table ->
                                        onOpenDataEditor(db, table)
                                    },
                                    onCreateTable = { db -> showCreateTableDb = db },
                                    onTableActions = { db, table -> tableMenuTarget = db to table },
                                    isLocked = isLocked,
                                    onUsersClick = {
                                        viewModel.setActivePanel(BrowserPanel.USERS)
                                        if (!userViewModel.hasLoaded.value) userViewModel.loadUsers()
                                    },
                                    onHistoryClick = { viewModel.setActivePanel(BrowserPanel.HISTORY) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            // Table list for selected DB — manual refresh, cache-first, columns/indexes lazy on arrow
                            val dbTables = tables[selectedDatabase] ?: emptyList()
                            val filteredTables = if (tableSearchQuery.isBlank()) dbTables
                            else dbTables.filter { it.contains(tableSearchQuery, ignoreCase = true) }

                            if (isRefreshingTables && dbTables.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Loading tables...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            } else if (dbTables.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "No tables — tap refresh",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                // Reuse DatabaseTree filtered to single DB for table rows with expand columns/indexes
                                // We render a lightweight table-only tree here to keep DB list out of main.
                                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    items(filteredTables, key = { "table_${selectedDatabase}.$it" }) { table ->
                                        val key = "${selectedDatabase}.$table"
                                        val isTableExpanded = expandedTables.contains(key)
                                        val tableColumns = columns[key]
                                        val tableIndexes = indexes[key]
                                        val tableSize = tableSizes[key]
                                        androidx.compose.runtime.key(table) {
                                            Column {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp, horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    IconButton(
                                                        onClick = { viewModel.toggleTable(selectedDatabase!!, table) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isTableExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                            contentDescription = if (isTableExpanded) "Collapse" else "Expand",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Row(
                                                        modifier = Modifier.weight(1f).combinedClickable(
                                                            onClick = { onOpenDataEditor(selectedDatabase!!, table) },
                                                            onLongClick = { tableMenuTarget = selectedDatabase!! to table }
                                                        )
                                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.TableChart,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = table,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        if (tableSize != null) {
                                                            Text(
                                                                text = tableSize,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        IconButton(onClick = { onOpenTableStructure(selectedDatabase!!, table) }, modifier = Modifier.size(32.dp)) {
                                                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Structure", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                                                        }
                                                    }
                                                }
                                                if (isTableExpanded) {
                                                    Column(modifier = Modifier.padding(start = 16.dp)) {
                                                        // Columns
                                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                                            Text("Columns", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                            if (tableColumns != null) {
                                                                Text(" (${tableColumns.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            } else {
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                if (columnsFailed.contains(key)) {
                                                                    Text("failed to load", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                                } else {
                                                                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                                                }
                                                            }
                                                        }
                                                        tableColumns?.forEach { col ->
                                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    imageVector = if (col.isPrimaryKey) Icons.Default.Key else Icons.Default.ViewColumn,
                                                                    contentDescription = null,
                                                                    tint = if (col.isPrimaryKey) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(col.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                                Text(col.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                if (col.isPrimaryKey) {
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text("PK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }

                                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                                        // Indexes
                                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                                            Text("Indexes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                            if (tableIndexes != null) {
                                                                Text(" (${tableIndexes.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            } else {
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                if (indexesFailed.contains(key)) {
                                                                    Text("failed to load", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                                } else {
                                                                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                                                }
                                                            }
                                                        }
                                                        tableIndexes?.forEach { idx ->
                                                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp)) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(
                                                                        imageVector = if (idx.name == "PRIMARY") Icons.Default.Key else Icons.Default.Menu,
                                                                        contentDescription = null,
                                                                        tint = if (idx.name == "PRIMARY") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        modifier = Modifier.size(12.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text(idx.name, style = MaterialTheme.typography.bodySmall, fontWeight = if (idx.name == "PRIMARY") FontWeight.Bold else FontWeight.Normal)
                                                                    if (idx.isUnique) {
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Text("UNIQUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                                                    }
                                                                }
                                                                Text(
                                                                    text = "→ ${idx.columns.joinToString(", ")}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                                    modifier = Modifier.padding(start = 24.dp, top = 1.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Keep selectedTable detail (Browser/Info tabs) when a table is selected inside selectedDatabase
                        if (selectedTable != null && selectedDatabase != null) {
                            val (db, table) = selectedTable!!
                            if (db == selectedDatabase) {
                                HorizontalDivider()
                                PrimaryTabRow(selectedTabIndex = selectedTab) {
                                    tabs.forEachIndexed { index, title ->
                                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                                    }
                                }
                                when (selectedTab) {
                                    0 -> TableInfoPanel(
                                        database = db,
                                        table = table,
                                        columns = columns["$db.$table"] ?: emptyList(),
                                        onOpenQuery = { onOpenQuery(db, table) },
                                        onOpenStructure = { onOpenTableStructure(db, table) },
                                        onOpenDataEditor = { onOpenDataEditor(db, table) }
                                    )
                                    1 -> ServerInfoPanel(viewModel)
                                }
                            }
                        }
                    }
                }
                BrowserPanel.USERS -> {
                    UsersPanel(
                        users = users,
                        isLoading = usersLoading,
                        userViewModel = userViewModel,
                        isLocked = isLocked,
                        onOpenUserDetail = onOpenUserDetail,
                        modifier = Modifier
                    )
                }
                BrowserPanel.HISTORY -> {
                    HistoryPanel(
                        viewModel = viewModel,
                        profile = profile,
                        modifier = Modifier
                    )
                }
                }
    // ---- Table DDL: menu (long-press) ----
    tableMenuTarget?.let { (db, table) ->
        AlertDialog(
            onDismissRequest = { tableMenuTarget = null },
            title = { Text("$db.$table") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            if (isLocked) { tableMenuTarget = null; return@TextButton }
                            tableMenuTarget = null
                            showRenameTable = db to table
                        },
                        enabled = !isLocked
                    ) { Text("Rename") }
                    TextButton(
                        onClick = {
                            if (isLocked) { tableMenuTarget = null; return@TextButton }
                            val sql = TableSql.buildTruncateTableSql(db, table)
                            pendingSql = sql
                            pendingAction = { viewModel.truncateTable(db, table, isLocked = isLocked) }
                            tableMenuTarget = null
                        },
                        enabled = !isLocked
                    ) { Text("Truncate (delete all rows)", color = MaterialTheme.colorScheme.error) }
                    TextButton(
                        onClick = {
                            if (isLocked) { tableMenuTarget = null; return@TextButton }
                            val sql = TableSql.buildDropTableSql(db, table)
                            pendingSql = sql
                            pendingAction = { viewModel.dropTable(db, table, isLocked = isLocked) }
                            tableMenuTarget = null
                        },
                        enabled = !isLocked
                    ) { Text("Drop table", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { tableMenuTarget = null }) { Text("Cancel") } }
        )
    }

    // ---- Table DDL: rename ----
    showRenameTable?.let { (db, table) ->
        var newName by remember(db, table) { mutableStateOf(table) }
        AlertDialog(
            onDismissRequest = { showRenameTable = null },
            title = { Text("Rename table") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isLocked) { showRenameTable = null; return@TextButton }
                        val sql = try {
                            TableSql.buildRenameTableSql(db, table, newName)
                        } catch (e: IllegalArgumentException) {
                            showRenameTable = null
                            scope.launch { snackbarHostState.showSnackbar(e.message ?: "Invalid name") }
                            return@TextButton
                        }
                        pendingSql = sql
                        pendingAction = { viewModel.renameTable(db, table, newName.trim(), isLocked = isLocked) }
                        showRenameTable = null
                    },
                    enabled = !isLocked && newName.isNotBlank()
                ) { Text("Preview") }
            },
            dismissButton = { TextButton(onClick = { showRenameTable = null }) { Text("Cancel") } }
        )
    }

    // ---- Table DDL: guided create ----
    showCreateTableDb?.let { db ->
        CreateTableDialog(
            database = db,
            onDismiss = { showCreateTableDb = null },
            onPreview = { name, columns, engine ->
                if (isLocked) { showCreateTableDb = null; return@CreateTableDialog }
                val sql = try {
                    TableSql.buildCreateTableSql(db, name, columns, engine)
                } catch (e: IllegalArgumentException) {
                    scope.launch { snackbarHostState.showSnackbar(e.message ?: "Invalid definition") }
                    return@CreateTableDialog
                }
                pendingSql = sql
                pendingAction = { viewModel.createTable(db, name.trim(), columns, engine, isLocked = isLocked) }
                showCreateTableDb = null
            }
        )
    }

    // Shared Confirm Write for table DDL (also reused by Users panel pattern):
    // executes ONLY when the previewed SQL classifies as a write query.
    pendingSql?.let { sql ->
        AlertDialog(
            onDismissRequest = { pendingSql = null; pendingAction = null },
            title = { Text("Confirm Write") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Query to be executed:", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            sql,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        copyToClipboard(sql, "Copied")
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = pendingAction
                    pendingSql = null
                    pendingAction = null
                    if (SqlUtil.isWriteQuery(sql)) a?.invoke()
                    else scope.launch { snackbarHostState.showSnackbar("Refused: not a write query") }
                }) { Text("Execute", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingSql = null; pendingAction = null }) { Text("Cancel") } }
        )
    }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, message: String) {
    Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UsersPanel(
    users: List<UserInfo>,
    isLoading: Boolean,
    userViewModel: UserPermissionViewModel,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    onOpenUserDetail: (String, String) -> Unit = { _, _ -> }
) {
    val listState = rememberLazyListState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingSql by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Users (${users.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { if (!isLocked) showCreateDialog = true }, enabled = !isLocked) { Icon(Icons.Default.Add, contentDescription = "Create User") }
        }
        if (isLoading && users.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
        } else if (users.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No users found", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(users, key = { "${it.user}@${it.host}" }) { user ->
                    UserCard(
                        userInfo = user,
                        isSelected = false,
                        isLocked = isLocked,
                        onClick = {
                            userViewModel.loadGrants(user.user, user.host)
                            onOpenUserDetail(user.user, user.host)
                        },
                        onDelete = {
                            val sql = userViewModel.buildDropUserSql(user.user, user.host)
                            pendingSql = sql
                            pendingAction = { userViewModel.dropUser(user.user, user.host, isLocked = isLocked) }
                        }
                    )
                }
            }
        }
    }
    if (showCreateDialog) {
        CreateUserDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { user, host, password ->
                if (isLocked) { showCreateDialog = false; return@CreateUserDialog }
                val sql = userViewModel.buildCreateUserSql(user, host, password)
                pendingSql = sql
                pendingAction = { userViewModel.createUser(user, host, password, isLocked = isLocked); showCreateDialog = false }
            }
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
                    SelectionContainer { Text(sql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp)) }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { copyToClipboard(sql, "Copied") }) { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Copy") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = pendingAction
                    pendingSql = null
                    pendingAction = null
                    // Only write queries may execute from a Confirm Write path.
                    // (Refusal is silent here: this panel has no snackbar of its own,
                    // and all its callers send writes. Main screen has its own gated dialog.)
                    if (SqlUtil.isWriteQuery(sql)) a?.invoke()
                    else android.util.Log.w("Browser", "Refused non-write in Confirm Write: $sql")
                }) { Text("Execute", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingSql = null; pendingAction = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun UserCard(
    userInfo: UserInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isLocked: Boolean = false
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "${userInfo.user}@${userInfo.host}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { if (!isLocked) showDeleteConfirm = true }, enabled = !isLocked, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(16.dp)) }
            }

        }
    }
    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete User") },
            text = { Text("Are you sure you want to drop ${userInfo.user}@${userInfo.host}?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onDelete(); showDeleteConfirm = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PrivilegeBadge(label: String, granted: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (granted) FontWeight.Bold else FontWeight.Normal,
        color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(1.dp)
    )
}

@Composable
private fun CreateUserDialog(onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var user by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("%") }
    var password by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onCreate(user, host, password) }, enabled = user.isNotBlank() && password.isNotBlank()) { Text("Create") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun HistoryPanel(
    viewModel: BrowserViewModel,
    profile: ConnectionProfileEntity,
    modifier: Modifier = Modifier
) {
    val history by viewModel.history.collectAsState()
    val search by viewModel.historySearch.collectAsState()
    val copyToClipboard = rememberCopyToClipboard()
    var showClearConfirm by remember { mutableStateOf(false) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]

    LaunchedEffect(profile.id) { viewModel.loadHistory(profile.id) }

    Column(modifier = modifier.fillMaxSize()) {
        // Search + Clear
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { viewModel.setHistorySearch(it) },
                placeholder = { Text("Search history...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setHistorySearch("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { showClearConfirm = true },
                enabled = history.isNotEmpty()
            ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
        }
        HorizontalDivider()

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (search.isNotBlank()) "No results for \"$search\"" else "No queries yet — run a query in SQL Editor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    val timeStr = try {
                        val sdf = java.text.SimpleDateFormat("dd/MM HH:mm", locale)
                        sdf.format(java.util.Date(item.executedAt))
                    } catch (_: Exception) { "" }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (item.isFavorite) {
                                    Icon(Icons.Default.Bookmark, contentDescription = "Saved", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                if (!item.database.isNullOrBlank()) {
                                    Text(
                                        text = item.database,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    copyToClipboard(item.queryText, "Copied")
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = { viewModel.deleteHistoryItem(item) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            SelectionContainer {
                                Text(
                                    text = item.queryText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    copyToClipboard(item.queryText, "Copied")
                                }) { Text("Copy", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear history") },
            text = { Text("Delete all query history for this connection? Saved queries will be kept.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory(profile.id)
                    showClearConfirm = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }
}

private var colDraftUid = 0
private data class ColDraft(
    val uid: Int = ++colDraftUid,
    var name: String = "",
    var type: String = "VARCHAR",
    var length: String = "100",
    var nullable: Boolean = true,
    var default: String = "",
    var primaryKey: Boolean = false,
    var autoIncrement: Boolean = false,
    var unique: Boolean = false,
    var onUpdate: Boolean = false
)

private val tableEngines = listOf("InnoDB", "MyISAM", "MEMORY")

@Composable
private fun CreateTableDialog(
    database: String,
    onDismiss: () -> Unit,
    onPreview: (String, List<TableSql.NewColumnSpec>, String) -> Unit
) {
    var tableName by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("InnoDB") }
    var engineMenu by remember { mutableStateOf(false) }
    var columns by remember { mutableStateOf(listOf(ColDraft())) }
    var formError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New table in $database") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = tableName,
                    onValueChange = { tableName = it; formError = null },
                    label = { Text("Table name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Engine dropdown
                Box {
                    OutlinedTextField(
                        value = engine,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Engine") },
                        trailingIcon = {
                            IconButton(onClick = { engineMenu = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ExpandMore, contentDescription = "Engine", modifier = Modifier.size(16.dp))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = engineMenu,
                        onDismissRequest = { engineMenu = false }
                    ) {
                        tableEngines.forEach { e ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(e) },
                                onClick = { engine = e; engineMenu = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                columns.forEachIndexed { index, col ->
                    androidx.compose.runtime.key(col.uid) {
                    var colState by remember { mutableStateOf(col) }
                    // keep list in sync when a row edits
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = colState.name,
                                    onValueChange = {
                                        colState = colState.copy(name = it)
                                        columns = columns.toMutableList().also { l -> l[index] = colState }
                                        formError = null
                                    },
                                    label = { Text("Column ${index + 1}") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { if (columns.size > 1) columns = columns.filterIndexed { i, _ -> i != index } },
                                    enabled = columns.size > 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove column", modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            TypeLenPicker(
                                initialType = TableSql.withLength(colState.type, colState.length),
                                onTypeChange = { combined ->
                                    val (b, l) = TableSql.splitType(combined)
                                    // Custom raw types land here verbatim as the base; Len ignored.
                                    if (b in TableSql.columnBaseTypes) {
                                        colState = colState.copy(type = b, length = l)
                                    } else {
                                        colState = colState.copy(type = combined, length = "")
                                    }
                                    columns = columns.toMutableList().also { l2 -> l2[index] = colState }
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                fun sync(next: ColDraft) {
                                    colState = next
                                    columns = columns.toMutableList().also { l -> l[index] = next }
                                }
                                androidx.compose.material3.FilterChip(
                                    selected = colState.nullable,
                                    onClick = { sync(colState.copy(nullable = !colState.nullable)) },
                                    label = { Text("NULL", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(30.dp)
                                )
                                androidx.compose.material3.FilterChip(
                                    selected = colState.primaryKey,
                                    onClick = { sync(colState.copy(primaryKey = !colState.primaryKey)) },
                                    label = { Text("PK", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(30.dp)
                                )
                                androidx.compose.material3.FilterChip(
                                    selected = colState.autoIncrement,
                                    onClick = {
                                        val ai = !colState.autoIncrement
                                        val next = colState.copy(
                                            autoIncrement = ai,
                                            // AUTO_INCREMENT requires a key: force PK + NOT NULL
                                            primaryKey = if (ai) true else colState.primaryKey,
                                            nullable = if (ai) false else colState.nullable
                                        )
                                        colState = next
                                        columns = if (ai) {
                                            // only one AUTO_INCREMENT column allowed
                                            columns.mapIndexed { i, c ->
                                                if (i == index) next
                                                else if (c.autoIncrement) c.copy(autoIncrement = false) else c
                                            }
                                        } else {
                                            columns.toMutableList().also { l -> l[index] = next }
                                        }
                                    },
                                    label = { Text("AI", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(30.dp)
                                )
                                androidx.compose.material3.FilterChip(
                                    selected = colState.unique,
                                    onClick = { sync(colState.copy(unique = !colState.unique)) },
                                    label = { Text("UNIQUE", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(30.dp)
                                )
                                if (TableSql.isTemporalType(colState.type)) {
                                    androidx.compose.material3.FilterChip(
                                        selected = colState.onUpdate,
                                        onClick = { sync(colState.copy(onUpdate = !colState.onUpdate)) },
                                        label = { Text("AUTO-UPDATE", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(30.dp)
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = colState.default,
                                onValueChange = {
                                    colState = colState.copy(default = it)
                                    columns = columns.toMutableList().also { l -> l[index] = colState }
                                },
                                label = { Text("Default (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    } // key(col.uid)
                }
                TextButton(onClick = { columns = columns + ColDraft() }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add column")
                }
                if (formError != null) {
                    Text(formError!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (tableName.isBlank()) { formError = "Table name required"; return@TextButton }
                if (columns.any { it.name.isBlank() }) { formError = "All columns need a name"; return@TextButton }
                if (columns.any { it.type.isBlank() }) { formError = "All columns need a type"; return@TextButton }
                val badLen = columns.firstOrNull { !TableSql.isValidLength(it.type, it.length) }
                if (badLen != null) { formError = "Len of '${badLen.name}' must be numeric, e.g. 100 or 10,2"; return@TextButton }
                val specs = columns.map {
                    TableSql.NewColumnSpec(
                        name = it.name.trim(), type = it.type.trim(),
                        length = it.length.trim().ifEmpty { null },
                        nullable = it.nullable, default = it.default.trim().ifEmpty { null },
                        primaryKey = it.primaryKey, autoIncrement = it.autoIncrement,
                        unique = it.unique, onUpdateCurrentTimestamp = it.onUpdate
                    )
                }
                onPreview(tableName.trim(), specs, engine)
            }) { Text("Preview") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TableInfoPanel(
    database: String,
    table: String,
    columns: List<id.web.izs.sqlclient.data.remote.model.ColumnInfo>,
    onOpenQuery: () -> Unit,
    onOpenStructure: () -> Unit,
    onOpenDataEditor: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "$database.$table", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = onOpenQuery) { Text("Query") }
            androidx.compose.material3.OutlinedButton(onClick = onOpenStructure) { Text("Structure") }
            androidx.compose.material3.OutlinedButton(onClick = onOpenDataEditor) { Text("Data") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Columns (${columns.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn {
            items(columns) { column ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = column.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (column.isPrimaryKey) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    Text(text = column.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (column.isPrimaryKey) { Spacer(modifier = Modifier.width(8.dp)); Text(text = "PK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                    if (!column.nullable) { Spacer(modifier = Modifier.width(4.dp)); Text(text = "NOT NULL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun ServerInfoPanel(viewModel: BrowserViewModel) {
    val databases by viewModel.databases.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Server Information", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Databases", style = MaterialTheme.typography.titleMedium)
            Text(text = "${databases.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        databases.forEach { db -> Text(text = "  ${db.name}", style = MaterialTheme.typography.bodyMedium) }
    }
}
