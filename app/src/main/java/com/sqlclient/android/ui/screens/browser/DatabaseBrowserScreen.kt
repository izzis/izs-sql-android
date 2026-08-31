
package com.sqlclient.android.ui.screens.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.data.remote.model.UserInfo
import com.sqlclient.android.ui.components.AppSidebar
import com.sqlclient.android.ui.components.AppTopBar
import com.sqlclient.android.ui.components.CurrentQueryBar
import com.sqlclient.android.ui.components.DatabaseTree
import com.sqlclient.android.ui.viewmodel.BrowserViewModel
import com.sqlclient.android.ui.viewmodel.BrowserViewModel.BrowserPanel
import com.sqlclient.android.ui.viewmodel.UserPermissionViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseBrowserScreen(
    viewModel: BrowserViewModel,
    userViewModel: UserPermissionViewModel,
    profile: ConnectionProfileEntity,
    onDisconnect: () -> Unit,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onOpenQuery: (String, String) -> Unit,
    onOpenTableStructure: (String, String) -> Unit,
    onOpenDataEditor: (String, String) -> Unit,
    onOpenUserDetail: (String, String) -> Unit = { _, _ -> }
) {
    val databases by viewModel.databases.collectAsState()
    val visibleDatabases by viewModel.visibleDatabases.collectAsState()
    val tables by viewModel.tables.collectAsState()
    val columns by viewModel.columns.collectAsState()
    val indexes by viewModel.indexes.collectAsState()
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Browser", "Info")
    val activePanel by viewModel.activePanel.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = selectedDatabase != null) {
        viewModel.clearSelectedDatabase()
    }
    BackHandler(enabled = activePanel != BrowserPanel.TABLE_INFO && selectedDatabase == null) {
        viewModel.setActivePanel(BrowserPanel.TABLE_INFO)
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

    val topBarColor = try {
        Color(android.graphics.Color.parseColor(profile.color))
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
                    onRefresh = onTopBarRefresh,
                    isRefreshing = isTopBarRefreshing,
                    isLocked = isLocked,
                    onToggleLock = onToggleLock,
                    onDisconnect = onDisconnect
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                val browserQuery = when (activePanel) {
                    BrowserPanel.TABLE_INFO -> currentQuery
                    BrowserPanel.USERS -> userCurrentQuery
                    BrowserPanel.HISTORY -> ""
                }
                CurrentQueryBar(query = browserQuery)
            }
        ) { paddingValues ->
            when (activePanel) {
                BrowserPanel.TABLE_INFO -> {
                    // Main content: Database list on left-style + Table list for selected DB
                    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        // Selected DB header with table search + refresh
                        if (selectedDatabase != null) {
                            val db = selectedDatabase!!
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = db,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                // Manual refresh via TopBar only — no duplicate button here
                            }
                            OutlinedTextField(
                                value = tableSearchQuery,
                                onValueChange = { viewModel.setTableSearchQuery(it) },
                                label = { Text("Search tables...") },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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
                                    searchQuery = searchQuery,
                                    users = users,
                                    onDatabaseClick = { db ->
                                        // Expand toggles only (no auto-load); refresh explicitly via button
                                        viewModel.toggleDatabase(db)
                                    },
                                    onRefreshDatabase = { viewModel.refreshDatabase(it) },
                                    onRefreshSizes = { viewModel.refreshTableSizes(it) },
                                    onTableClick = { db, table -> viewModel.toggleTable(db, table) },
                                    onTableSelect = { db, table ->
                                        viewModel.selectTable(db, table)
                                        viewModel.selectDatabase(db)
                                    },
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
                                                        .clickable {
                                                            viewModel.toggleTable(selectedDatabase!!, table)
                                                            viewModel.selectTable(selectedDatabase!!, table)
                                                        }
                                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (isTableExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                        contentDescription = if (isTableExpanded) "Collapse" else "Expand",
                                                        modifier = Modifier.size(20.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = table,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Medium,
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
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        androidx.compose.material3.TextButton(onClick = { onOpenQuery(selectedDatabase!!, table) }) { Text("Query", style = MaterialTheme.typography.labelSmall) }
                                                        androidx.compose.material3.TextButton(onClick = { onOpenDataEditor(selectedDatabase!!, table) }) { Text("Data", style = MaterialTheme.typography.labelSmall) }
                                                    }
                                                }
                                                if (isTableExpanded) {
                                                    Column(modifier = Modifier.padding(start = 16.dp)) {
                                                        // Columns — lazy, show loading if null
                                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                                            Text("Columns", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                            if (tableColumns == null) {
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                                            } else {
                                                                Text(" (${tableColumns.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                        }
                                                        tableColumns?.forEach { col ->
                                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                Text(col.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                                Text(col.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                if (col.isPrimaryKey) {
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text("PK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                                                            Text("Indexes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                            if (tableIndexes == null) {
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                                            } else {
                                                                Text(" (${tableIndexes.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                        }
                                                        tableIndexes?.forEach { idx ->
                                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                Text(idx.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                                Text(idx.columns.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            androidx.compose.material3.OutlinedButton(onClick = { onOpenTableStructure(selectedDatabase!!, table) }) { Text("Structure") }
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
                                TabRow(selectedTabIndex = selectedTab) {
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
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                BrowserPanel.HISTORY -> {
                    HistoryPanel(viewModel = viewModel, modifier = Modifier.padding(paddingValues))
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
    isLocked: Boolean = false,
    onOpenUserDetail: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
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
private fun UserCard(
    userInfo: UserInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
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
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(16.dp)) }
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
private fun HistoryPanel(viewModel: BrowserViewModel, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text = "Query history will appear here", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TableInfoPanel(
    database: String,
    table: String,
    columns: List<com.sqlclient.android.data.remote.model.ColumnInfo>,
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
