package id.web.izs.sqlclient.ui.screens.user

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.web.izs.sqlclient.data.remote.model.UserInfo
import id.web.izs.sqlclient.ui.components.AppTopBar
import id.web.izs.sqlclient.ui.components.CurrentQueryBar
import id.web.izs.sqlclient.ui.components.ReconnectBanner
import id.web.izs.sqlclient.ui.viewmodel.ConnectionViewModel
import id.web.izs.sqlclient.ui.viewmodel.UserPermissionViewModel
import id.web.izs.sqlclient.util.rememberCopyToClipboard

private val PRIVS = listOf("SELECT","INSERT","UPDATE","DELETE","CREATE","DROP","ALTER","INDEX")

// Flattened rows so an expanded database contributes one item per table instead of
// composing every table inside a single eager item.
private sealed interface PrivilegeRow {
    val rowKey: String

    data class Database(val db: String) : PrivilegeRow {
        override val rowKey: String get() = "db:$db"
    }

    data class Status(val db: String, val label: String) : PrivilegeRow {
        override val rowKey: String get() = "status:$db"
    }

    data class Table(val db: String, val table: String) : PrivilegeRow {
        override val rowKey: String get() = "table:$db.$table"
    }

    data class Separator(val db: String) : PrivilegeRow {
        override val rowKey: String get() = "sep:$db"
    }
}

@Composable
fun UserPrivilegeDetailScreen(
    viewModel: UserPermissionViewModel,
    connectionViewModel: ConnectionViewModel,
    user: String,
    host: String,
    isLocked: Boolean = false,
    onToggleLock: (() -> Unit)? = null,
    onBack: () -> Unit,
    onReconnect: (() -> Unit)? = null,
    isReconnecting: Boolean = false,
    topBarColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val grants by viewModel.grants.collectAsState()
    val allDatabases by viewModel.allDatabases.collectAsState()
    val dbTables by viewModel.dbTables.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentQuery by viewModel.currentQuery.collectAsState()
    var expandedDb by remember { mutableStateOf<String?>(null) }
    var expandedPriv by remember { mutableStateOf<String?>(null) } // key db or db.table
    val pendingPrivChanges by viewModel.pendingPrivChanges.collectAsState()
    val hasPendingPriv = pendingPrivChanges.isNotEmpty()
    var showPrivSaveConfirm by remember { mutableStateOf(false) }
    var pendingSql by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(user, host) {
        viewModel.resetQueryLog()
        viewModel.loadGrants(user, host)
        if (allDatabases.isEmpty()) viewModel.loadAllDatabases()
    }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val reconnectMessage by connectionViewModel.reconnectMessage.collectAsState()
    LaunchedEffect(reconnectMessage) {
        reconnectMessage?.let {
            kotlinx.coroutines.delay(2000)
            connectionViewModel.clearReconnectMessage()
        }
    }

    // Parse grants into sets per target: key = normalized ON part (e.g. "*.*", "mydb.*", "mydb.mytable")
    // priv -> set of ONs, or ON -> set of privs
    fun parseGrants(): Map<String, Set<String>> {
        // on -> privs
        val map = mutableMapOf<String, MutableSet<String>>()
        for (g in grants) {
            val u = g.uppercase()
            if (!u.contains(" ON ")) continue
            val onRaw = u.substringAfter(" ON ").substringBefore(" TO ").trim().replace("`","").replace(" ","")
            val beforeOn = u.substringBefore(" ON ").trim() // "GRANT SELECT, INSERT ..." or "GRANT ALL PRIVILEGES ON"
            val privPart = beforeOn.removePrefix("GRANT").trim()
            val privs: Set<String> = if (privPart.contains("ALL PRIVILEGES") || privPart == "ALL") setOf("ALL") else privPart.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            map.getOrPut(onRaw.lowercase()) { mutableSetOf() }.addAll(privs)
        }
        return map
    }
    val onToPrivs = parseGrants()

    fun hasAnyPriv(db: String, table: String?): Boolean {
        // global
        if (onToPrivs["*.*"]?.let { it.contains("ALL") || it.isNotEmpty() } == true) return true
        val dbKey = "${db.lowercase()}.*"
        if (onToPrivs[dbKey]?.let { it.contains("ALL") || it.isNotEmpty() } == true) return true
        if (table != null) {
            val tblKey = "${db.lowercase()}.${table.lowercase()}"
            if (onToPrivs[tblKey]?.let { it.contains("ALL") || it.isNotEmpty() } == true) return true
            // also consider db.* covers table bold
            if (onToPrivs[dbKey]?.isNotEmpty() == true) return true
        } else {
            // db bold if any table under it has any priv (so scanning)
            val prefix = "${db.lowercase()}."
            for ((k, v) in onToPrivs) {
                if (k.startsWith(prefix) && v.isNotEmpty()) return true
            }
        }
        return false
    }
    fun hasPrivOnTarget(priv: String, onKey: String): Boolean {
        val s = onToPrivs[onKey.lowercase()] ?: return false
        return s.contains("ALL") || s.contains(priv)
    }
    fun stagedKey(priv: String, onKey: String) = "${priv.uppercase()}@${onKey.lowercase()}"
    fun isStaged(priv: String, onKey: String) = pendingPrivChanges.containsKey(stagedKey(priv, onKey))
    fun anyStaged(onKey: String) = PRIVS.any { isStaged(it, onKey) }
    fun effectiveChecked(priv: String, onKey: String): Boolean =
        pendingPrivChanges[stagedKey(priv, onKey)] ?: hasPrivOnTarget(priv, onKey)
    fun effectiveHasAnyPriv(db: String, table: String?): Boolean {
        for (priv in PRIVS) if (effectiveChecked(priv, "*.*")) return true
        val dbKey = "${db}.*"
        for (priv in PRIVS) if (effectiveChecked(priv, dbKey)) return true
        if (table != null) {
            val tblKey = "${db}.${table}"
            for (priv in PRIVS) if (effectiveChecked(priv, tblKey)) return true
        } else {
            val tables = dbTables[db]
            if (tables != null) {
                for (t in tables) {
                    val k = "${db}.${t}"
                    for (priv in PRIVS) if (effectiveChecked(priv, k)) return true
                }
            }
            val prefix = "${db.lowercase()}."
            for ((k, v) in onToPrivs) {
                if (k.startsWith(prefix) && k != dbKey.lowercase() && v.isNotEmpty()) return true
            }
            for ((pk, want) in pendingPrivChanges) {
                if (!want) continue
                val on = pk.substringAfter("@", "")
                if (on.lowercase().startsWith(prefix) && on != "${db.lowercase()}.*".lowercase() && on != "*.*") return true
            }
        }
        return false
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "$user@$host",
                subtitle = if (isLocked) "Read-only" else null,
                containerColor = topBarColor,
                onBack = onBack,
                onRefresh = {
                    viewModel.refreshGrants(user, host)
                    expandedDb?.let { viewModel.loadTablesForDb(it) }
                },
                isRefreshing = isLoading,
                isLocked = isLocked,
                onToggleLock = onToggleLock,
                showLock = true,
                onSave = if (hasPendingPriv) ({ showPrivSaveConfirm = true }) else null,
                onReconnect = onReconnect,
                isReconnecting = isReconnecting
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            CurrentQueryBar(
                queries = currentQuery,
                onClear = ({ viewModel.resetQueryLog() }).takeIf { !viewModel.hasPendingPrivChanges() }
            )
        }
    ) { paddingValues ->
        val dbRows = remember(allDatabases, expandedDb, dbTables) {
            buildList {
                for (db in allDatabases) {
                    add(PrivilegeRow.Database(db))
                    if (expandedDb == db) {
                        val tables = dbTables[db]
                        when {
                            tables == null -> add(PrivilegeRow.Status(db, "Loading..."))
                            tables.isEmpty() -> add(PrivilegeRow.Status(db, "No tables"))
                            else -> for (table in tables) add(PrivilegeRow.Table(db, table))
                        }
                    }
                    add(PrivilegeRow.Separator(db))
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(12.dp)
        ) {
            item(key = "reconnect") {
                ReconnectBanner(message = reconnectMessage)
            }
            item(key = "actions") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !isLocked, onClick = { showRenameDialog = true }) { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Rename") }
                    TextButton(enabled = !isLocked, onClick = { showPasswordDialog = true }) { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Password") }
                }
            }
            if (isLoading && grants.isEmpty()) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
            if (hasPendingPriv) {
                item(key = "pending") {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${pendingPrivChanges.size} pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Text("— Save to confirm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearPendingPrivs(user, host) }, modifier = Modifier.height(28.dp)) { Text("Discard", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            item(key = "databases-header") {
                Spacer(Modifier.height(8.dp))
                Text("Databases", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }
            if (allDatabases.isEmpty()) {
                item(key = "no-databases") {
                    Text("No databases loaded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(dbRows, key = { it.rowKey }) { entry ->
                    when (entry) {
                        is PrivilegeRow.Database -> {
                            val db = entry.db
                            val boldDb = effectiveHasAnyPriv(db, null)
                            val onKey = "${db}.*"
                            val allCheckedDb = PRIVS.all { effectiveChecked(it, onKey) }
                            val stagedDb = anyStaged(onKey)
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(db, style = MaterialTheme.typography.bodyMedium, fontWeight = if (boldDb) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                if (expandedPriv == db) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = allCheckedDb,
                                            onCheckedChange = { want: Boolean ->
                                                if (isLocked) return@Checkbox
                                                PRIVS.forEach { priv ->
                                                    val base = hasPrivOnTarget(priv, onKey)
                                                    viewModel.stagePrivToggle(user, host, priv, onKey, want, base)
                                                }
                                            },
                                            enabled = !isLocked,
                                            modifier = Modifier.size(24.dp),
                                            colors = if (stagedDb) {
                                                CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error, uncheckedColor = MaterialTheme.colorScheme.error)
                                            } else {
                                                CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                            }
                                        )
                                        Text(
                                            "All",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (stagedDb) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            fontWeight = if (stagedDb) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
                                }
                                TextButton(onClick = { expandedPriv = if (expandedPriv == db) null else db }, enabled = true) { Text(if (expandedPriv == db) "Hide" else "Privileges", style = MaterialTheme.typography.labelSmall) }
                                IconButton(onClick = { val e = expandedDb == db; expandedDb = if (e) null else db; if (!e) viewModel.loadTablesForDb(db) }, modifier = Modifier.size(28.dp)) {
                                    Icon(if (expandedDb == db) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (expandedPriv == db) {
                                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(6.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PRIVS.chunked(4).forEach { row ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            row.forEach { priv ->
                                                val baseChecked = hasPrivOnTarget(priv, onKey)
                                                val checked = effectiveChecked(priv, onKey)
                                                val staged = isStaged(priv, onKey)
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)
                                                    .then(if (staged) Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f), RoundedCornerShape(4.dp)) else Modifier)
                                                    .clickable(enabled = !isLocked) {
                                                    viewModel.stagePrivToggle(user, host, priv, onKey, !checked, baseChecked)
                                                }) {
                                                    Checkbox(checked = checked, onCheckedChange = { want: Boolean ->
                                                        if (isLocked) return@Checkbox
                                                        viewModel.stagePrivToggle(user, host, priv, onKey, want, baseChecked)
                                                    }, enabled = !isLocked, modifier = Modifier.size(24.dp),
                                                        colors = if (staged) {
                                                            CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error, uncheckedColor = MaterialTheme.colorScheme.error)
                                                        } else {
                                                            CheckboxDefaults.colors()
                                                        })
                                                    Text(
                                                        priv,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (staged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = if (staged) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        is PrivilegeRow.Status -> {
                            Text(entry.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
                        }
                        is PrivilegeRow.Table -> {
                            val db = entry.db
                            val tbl = entry.table
                            val boldTbl = effectiveHasAnyPriv(db, tbl)
                            val tKey = "$db.$tbl"
                            val allCheckedTbl = PRIVS.all { effectiveChecked(it, tKey) }
                            val stagedTbl = anyStaged(tKey)
                            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp).padding(vertical = 2.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(tbl, style = MaterialTheme.typography.bodySmall, fontWeight = if (boldTbl) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                    if (expandedPriv == tKey) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = allCheckedTbl,
                                                onCheckedChange = { want: Boolean ->
                                                    if (isLocked) return@Checkbox
                                                    PRIVS.forEach { priv ->
                                                        val base = hasPrivOnTarget(priv, tKey)
                                                        viewModel.stagePrivToggle(user, host, priv, tKey, want, base)
                                                    }
                                                },
                                                enabled = !isLocked,
                                                modifier = Modifier.size(24.dp),
                                                colors = if (stagedTbl) {
                                                    CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error, uncheckedColor = MaterialTheme.colorScheme.error)
                                                } else {
                                                    CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                                }
                                            )
                                            Text(
                                                "All",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (stagedTbl) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                fontWeight = if (stagedTbl) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                        }
                                    }
                                    TextButton(onClick = { expandedPriv = if (expandedPriv == tKey) null else tKey }) { Text(if (expandedPriv == tKey) "Hide" else "Privileges", style = MaterialTheme.typography.labelSmall) }
                                }
                                if (expandedPriv == tKey) {
                                    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(6.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        PRIVS.chunked(4).forEach { row ->
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                row.forEach { priv ->
                                                    val baseCheckedTbl = hasPrivOnTarget(priv, tKey)
                                                    val checkedTbl = effectiveChecked(priv, tKey)
                                                    val stagedTblCell = isStaged(priv, tKey)
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)
                                                        .then(if (stagedTblCell) Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f), RoundedCornerShape(4.dp)) else Modifier)
                                                        .clickable(enabled = !isLocked) {
                                                        viewModel.stagePrivToggle(user, host, priv, tKey, !checkedTbl, baseCheckedTbl)
                                                    }) {
                                                        Checkbox(checked = checkedTbl, onCheckedChange = { want: Boolean ->
                                                            if (isLocked) return@Checkbox
                                                            viewModel.stagePrivToggle(user, host, priv, tKey, want, baseCheckedTbl)
                                                        }, enabled = !isLocked, modifier = Modifier.size(24.dp),
                                                            colors = if (stagedTblCell) {
                                                                CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error, uncheckedColor = MaterialTheme.colorScheme.error)
                                                            } else {
                                                                CheckboxDefaults.colors()
                                                            })
                                                        Text(
                                                            priv,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (stagedTblCell) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = if (stagedTblCell) FontWeight.SemiBold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is PrivilegeRow.Separator -> {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
            item(key = "grants-header") {
                Spacer(Modifier.height(12.dp))
                Text("Grant Statements", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
            }
            itemsIndexed(grants, key = { index, _ -> "grant:$index" }) { _, g ->
                SelectionContainer { Text(g, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(8.dp)) }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
    if (showPrivSaveConfirm) {
        val sqls = viewModel.buildPendingPrivSqls(user, host)
        val fullSql = sqls.joinToString(";\n")
        val copyToClipboard = rememberCopyToClipboard()
        AlertDialog(
            onDismissRequest = { showPrivSaveConfirm = false },
            title = { Text("Confirm Write (${sqls.size})") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Query to be executed:", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer { Text(fullSql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp)) }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { copyToClipboard(fullSql, "Copied") }) { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Copy") }
                }
            },
            confirmButton = { TextButton(onClick = { showPrivSaveConfirm = false; viewModel.commitPendingPrivs(user, host, isLocked = isLocked) }) { Text("Execute", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showPrivSaveConfirm = false }) { Text("Cancel") } }
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
    if (showRenameDialog) {
        DetailRenameDialog(user = UserInfo(user = user, host = host), onDismiss = { showRenameDialog = false }, onConfirm = { newUser, newHost ->
            val sql = viewModel.buildRenameUserSql(user, host, newUser, newHost)
            pendingSql = sql
            pendingAction = { viewModel.renameUser(user, host, newUser, newHost, isLocked = isLocked); showRenameDialog = false }
        })
    }
    if (showPasswordDialog) {
        DetailPasswordDialog(user = UserInfo(user = user, host = host), onDismiss = { showPasswordDialog = false }, onConfirm = { newPass ->
            val sql = viewModel.buildChangePasswordSql(user, host, newPass)
            pendingSql = sql
            pendingAction = { viewModel.changePassword(user, host, newPass, isLocked = isLocked); showPasswordDialog = false }
        })
    }
}

@Composable
private fun DetailRenameDialog(user: UserInfo, onDismiss: () -> Unit, onConfirm: (String,String)->Unit) {
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
private fun DetailPasswordDialog(user: UserInfo, onDismiss: () -> Unit, onConfirm: (String)->Unit) {
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
