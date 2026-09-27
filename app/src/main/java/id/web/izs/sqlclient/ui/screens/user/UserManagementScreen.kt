package id.web.izs.sqlclient.ui.screens.user

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import id.web.izs.sqlclient.data.remote.model.UserInfo
import id.web.izs.sqlclient.ui.components.AppTopBar
import id.web.izs.sqlclient.ui.components.CurrentQueryBar
import id.web.izs.sqlclient.ui.components.ReconnectBanner
import id.web.izs.sqlclient.ui.viewmodel.ConnectionViewModel
import id.web.izs.sqlclient.ui.viewmodel.UserPermissionViewModel
import id.web.izs.sqlclient.util.rememberCopyToClipboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    viewModel: UserPermissionViewModel,
    connectionViewModel: ConnectionViewModel,
    isLocked: Boolean = false,
    onToggleLock: (() -> Unit)? = null,
    onBack: () -> Unit,
    onOpenUserDetail: (String, String) -> Unit = { _, _ -> },
    onReconnect: (() -> Unit)? = null,
    isReconnecting: Boolean = false
) {
    val users by viewModel.users.collectAsState()
    val grants by viewModel.grants.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val allDatabases by viewModel.allDatabases.collectAsState()
    val dbTables by viewModel.dbTables.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Users", "Permissions")

    var selectedUser by remember { mutableStateOf<UserInfo?>(null) }
    var showAddUserDialog by remember { mutableStateOf(false) }
    var showDeleteUserDialog by remember { mutableStateOf<UserInfo?>(null) }
    var showGrantDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pendingSql by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val currentQuery by viewModel.currentQuery.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.resetQueryLog()
        if (!viewModel.hasLoaded.value) viewModel.loadUsers()
        viewModel.loadAllDatabases()
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
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
                title = "User Management",
                subtitle = null,
                containerColor = MaterialTheme.colorScheme.primary,
                onRefresh = { viewModel.refreshUsers() },
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
        bottomBar = { CurrentQueryBar(queries = currentQuery, onClear = { viewModel.resetQueryLog() }) },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showAddUserDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add User") },
                    text = { Text("Add User") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
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
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> UsersTab(
                    users = users,
                    isLoading = isLoading,
                    selectedUser = selectedUser,
                    onSelectUser = { user ->
                        viewModel.loadGrants(user.user, user.host)
                        onOpenUserDetail(user.user, user.host)
                    },
                    onDeleteUser = { showDeleteUserDialog = it }
                )
                1 -> PermissionsTab(
                    selectedUser = selectedUser,
                    grants = grants,
                    isLoading = isLoading,
                    isLocked = isLocked,
                    onGrantClick = { if (!isLocked) showGrantDialog = true },
                    onRevokeClick = { if (!isLocked) showRevokeDialog = true },
                    onRenameClick = { showRenameDialog = true },
                    onChangePasswordClick = { showPasswordDialog = true },
                    onOpenPrivileges = { selectedUser?.let { onOpenUserDetail(it.user, it.host) } }
                )
            }
        }
    }

    if (showAddUserDialog) {
        AddUserDialog(
            onDismiss = { showAddUserDialog = false },
            onConfirm = { user, host, password ->
                if (isLocked) { showAddUserDialog = false; return@AddUserDialog }
                val sql = viewModel.buildCreateUserSql(user, host, password)
                pendingSql = sql
                pendingAction = { viewModel.createUser(user, host, password, isLocked = isLocked); showAddUserDialog = false }
            }
        )
    }

    showDeleteUserDialog?.let { user ->
        DeleteUserDialog(
            user = user,
            onDismiss = { showDeleteUserDialog = null },
            onConfirm = {
                val sql = viewModel.buildDropUserSql(user.user, user.host)
                pendingSql = sql
                pendingAction = { viewModel.dropUser(user.user, user.host, isLocked = isLocked); if (selectedUser?.user == user.user && selectedUser?.host == user.host) selectedUser = null; showDeleteUserDialog = null }
            }
        )
    }

    if (showGrantDialog && selectedUser != null) {
        GrantRevokeDialog(
            title = "Grant Privilege",
            user = selectedUser!!,
            onDismiss = { showGrantDialog = false },
            onConfirm = { privilege, database, table ->
                if (isLocked) { showGrantDialog = false; return@GrantRevokeDialog }
                val sql = viewModel.buildGrantSql(selectedUser!!.user, selectedUser!!.host, privilege, database, table)
                pendingSql = sql
                pendingAction = { viewModel.grantPrivilege(selectedUser!!.user, selectedUser!!.host, privilege, database, table, isLocked = isLocked); showGrantDialog = false }
            }
        )
    }

    if (showRevokeDialog && selectedUser != null) {
        GrantRevokeDialog(
            title = "Revoke Privilege",
            user = selectedUser!!,
            onDismiss = { showRevokeDialog = false },
            onConfirm = { privilege, database, table ->
                if (isLocked) { showRevokeDialog = false; return@GrantRevokeDialog }
                val sql = viewModel.buildRevokeSql(selectedUser!!.user, selectedUser!!.host, privilege, database, table)
                pendingSql = sql
                pendingAction = { viewModel.revokePrivilege(selectedUser!!.user, selectedUser!!.host, privilege, database, table, isLocked = isLocked); showRevokeDialog = false }
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
            confirmButton = { TextButton(onClick = { val a = pendingAction; pendingSql = null; pendingAction = null; a?.invoke() }) { Text("Execute", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingSql = null; pendingAction = null }) { Text("Cancel") } }
        )
    }

    if (showRenameDialog && selectedUser != null) {
        RenameUserDialog(user = selectedUser!!, onDismiss = { showRenameDialog = false }, onConfirm = { newUser, newHost ->
            val sql = viewModel.buildRenameUserSql(selectedUser!!.user, selectedUser!!.host, newUser, newHost)
            pendingSql = sql
            pendingAction = { viewModel.renameUser(selectedUser!!.user, selectedUser!!.host, newUser, newHost, isLocked = isLocked); showRenameDialog = false }
        })
    }
    if (showPasswordDialog && selectedUser != null) {
        ChangePasswordDialog(user = selectedUser!!, onDismiss = { showPasswordDialog = false }, onConfirm = { newPass ->
            val sql = viewModel.buildChangePasswordSql(selectedUser!!.user, selectedUser!!.host, newPass)
            pendingSql = sql
            pendingAction = { viewModel.changePassword(selectedUser!!.user, selectedUser!!.host, newPass, isLocked = isLocked); showPasswordDialog = false }
        })
    }
    // privilege detail now full-page via onOpenUserDetail; no popup sheet
}

@Composable
private fun UsersTab(
    users: List<UserInfo>,
    isLoading: Boolean,
    selectedUser: UserInfo?,
    onSelectUser: (UserInfo) -> Unit,
    onDeleteUser: (UserInfo) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading users...", style = MaterialTheme.typography.bodySmall)
            }
        }
    } else if (users.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No users found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(users, key = { "${it.user}@${it.host}" }) { user ->
                UserCard(
                    user = user,
                    isSelected = selectedUser?.user == user.user && selectedUser.host == user.host,
                    onClick = { onSelectUser(user) },
                    onDelete = { onDeleteUser(user) }
                )
            }
        }
    }
}

@Composable
private fun UserCard(
    user: UserInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${user.user}@${user.host}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete user",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }


        }
    }
}

@Composable
private fun PrivilegeBadge(label: String, granted: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (granted) FontWeight.Bold else FontWeight.Normal,
        color = if (granted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier
            .background(
                color = if (granted) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun PermissionsTab(
    selectedUser: UserInfo?,
    grants: List<String>,
    isLoading: Boolean,
    isLocked: Boolean = false,
    onGrantClick: () -> Unit,
    onRevokeClick: () -> Unit,
    onRenameClick: () -> Unit,
    onChangePasswordClick: () -> Unit,
    onOpenPrivileges: () -> Unit
) {
    if (selectedUser == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Select a user to view permissions",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "${selectedUser.user}@${selectedUser.host}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpenPrivileges) { Text("Databases") }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onGrantClick, enabled = !isLocked) { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Grant") }
                TextButton(onClick = onRevokeClick, enabled = !isLocked) { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Revoke") }
                TextButton(onClick = onRenameClick) { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Rename") }
                TextButton(onClick = onChangePasswordClick) { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Password") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Global Privileges",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrivilegeIndicator("SELECT", selectedUser.selectPriv, Modifier.weight(1f))
            PrivilegeIndicator("INSERT", selectedUser.insertPriv, Modifier.weight(1f))
            PrivilegeIndicator("UPDATE", selectedUser.updatePriv, Modifier.weight(1f))
            PrivilegeIndicator("DELETE", selectedUser.deletePriv, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrivilegeIndicator("CREATE", selectedUser.createPriv, Modifier.weight(1f))
            PrivilegeIndicator("DROP", selectedUser.dropPriv, Modifier.weight(1f))
            PrivilegeIndicator("ALTER", selectedUser.alterPriv, Modifier.weight(1f))
            PrivilegeIndicator("INDEX", selectedUser.indexPriv, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Grant Statements",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (grants.isEmpty()) {
            Text(
                "No grants found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            grants.forEach { grant ->
                GrantStatement(grant = grant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Per-Table Permissions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        val tablePermissions = parseTablePermissions(grants)

        if (tablePermissions.isEmpty()) {
            Text(
                "No per-table permissions found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            TablePermissionTable(tablePermissions)
        }
    }
}

@Composable
private fun PrivilegeIndicator(label: String, granted: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (granted) FontWeight.Bold else FontWeight.Normal,
                color = if (granted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
        }
    }
}

@Composable
private fun GrantStatement(grant: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = grant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class TablePermissionEntry(
    val table: String,
    val select: Boolean,
    val insert: Boolean,
    val update: Boolean,
    val delete: Boolean
)

private fun parseTablePermissions(grants: List<String>): List<TablePermissionEntry> {
    val tableMap = mutableMapOf<String, TablePermissionEntry>()

    for (grant in grants) {
        val upperGrant = grant.uppercase()
        if (upperGrant.contains("ON") && !upperGrant.contains("*.*")) {
            val onMatch = Regex("""ON\s+`?(\w+)`?\.`?(\w+)`?""").find(grant)
            if (onMatch != null) {
                val database = onMatch.groupValues[1]
                val table = onMatch.groupValues[2]
                if (table != "*") {
                    val key = "$database.$table"
                    val existing = tableMap[key]
                    val select = upperGrant.contains("SELECT") || upperGrant.contains("ALL PRIVILEGES")
                    val insert = upperGrant.contains("INSERT") || upperGrant.contains("ALL PRIVILEGES")
                    val update = upperGrant.contains("UPDATE") || upperGrant.contains("ALL PRIVILEGES")
                    val delete = upperGrant.contains("DELETE") || upperGrant.contains("ALL PRIVILEGES")

                    tableMap[key] = TablePermissionEntry(
                        table = key,
                        select = existing?.select ?: select,
                        insert = existing?.insert ?: insert,
                        update = existing?.update ?: update,
                        delete = existing?.delete ?: delete
                    )
                }
            }
        }
    }

    return tableMap.values.sortedBy { it.table }
}

@Composable
private fun TablePermissionTable(permissions: List<TablePermissionEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                    )
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Table",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(2f)
                )
                Text(
                    "SELECT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "INSERT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "UPDATE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "DELETE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            permissions.forEach { perm ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        perm.table,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(2f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "S",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (perm.select) FontWeight.Bold else FontWeight.Normal,
                        color = if (perm.select) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "I",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (perm.insert) FontWeight.Bold else FontWeight.Normal,
                        color = if (perm.insert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "U",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (perm.update) FontWeight.Bold else FontWeight.Normal,
                        color = if (perm.update) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "D",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (perm.delete) FontWeight.Bold else FontWeight.Normal,
                        color = if (perm.delete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (user: String, host: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("%") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add User") },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(username, host, password) },
                enabled = username.isNotBlank() && host.isNotBlank() && password.isNotBlank()
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

@Composable
private fun DeleteUserDialog(
    user: UserInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete User") },
        text = { Text("Are you sure you want to delete \"${user.user}@${user.host}\"?") },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun GrantRevokeDialog(
    title: String,
    user: UserInfo,
    onDismiss: () -> Unit,
    onConfirm: (privilege: String, database: String, table: String) -> Unit
) {
    var privilege by remember { mutableStateOf("SELECT") }
    var database by remember { mutableStateOf("*") }
    var table by remember { mutableStateOf("*") }

    val privileges = listOf("SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER", "INDEX", "ALL PRIVILEGES")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "User: ${user.user}@${user.host}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Privilege", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    privileges.forEach { priv ->
                        TextButton(
                            onClick = { privilege = priv },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                containerColor = if (privilege == priv) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                }
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                priv.take(6),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (privilege == priv) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = database,
                    onValueChange = { database = it },
                    label = { Text("Database") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = table,
                    onValueChange = { table = it },
                    label = { Text("Table") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(privilege, database, table) },
                enabled = database.isNotBlank() && table.isNotBlank()
            ) {
                Text(if (title.contains("Grant")) "Grant" else "Revoke")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RenameUserDialog(user: UserInfo, onDismiss: () -> Unit, onConfirm: (String,String)->Unit) {
    var newUser by remember { mutableStateOf(user.user) }
    var newHost by remember { mutableStateOf(user.host) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Rename User") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = newUser, onValueChange = { newUser = it }, label = { Text("New username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = newHost, onValueChange = { newHost = it }, label = { Text("New host") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(newUser, newHost) }, enabled = newUser.isNotBlank() && newHost.isNotBlank()) { Text("Rename") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ChangePasswordDialog(user: UserInfo, onDismiss: () -> Unit, onConfirm: (String)->Unit) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Change Password") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${user.user}@${user.host}", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("New password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = confirm, onValueChange = { confirm = it }, label = { Text("Confirm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(pass) }, enabled = pass.isNotBlank() && pass == confirm) { Text("Change") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
