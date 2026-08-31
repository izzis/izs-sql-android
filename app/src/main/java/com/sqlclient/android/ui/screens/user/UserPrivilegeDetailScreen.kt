package com.sqlclient.android.ui.screens.user

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.sqlclient.android.data.remote.model.UserInfo
import com.sqlclient.android.ui.components.AppTopBar
import com.sqlclient.android.ui.components.CurrentQueryBar
import com.sqlclient.android.ui.viewmodel.UserPermissionViewModel

private val PRIVS = listOf("SELECT","INSERT","UPDATE","DELETE","CREATE","DROP","ALTER","INDEX")

@Composable
fun UserPrivilegeDetailScreen(
    viewModel: UserPermissionViewModel,
    user: String,
    host: String,
    isLocked: Boolean = false,
    onToggleLock: (() -> Unit)? = null,
    onBack: () -> Unit
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
        viewModel.loadGrants(user, host)
        if (allDatabases.isEmpty()) viewModel.loadAllDatabases()
    }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
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
    fun effectiveChecked(priv: String, onKey: String): Boolean {
        val k = "${priv.uppercase()}@${onKey.lowercase()}"
        val pending = pendingPrivChanges[k]
        if (pending != null) return pending
        return hasPrivOnTarget(priv, onKey)
    }
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
            // also check any pending table key that may not be in dbTables yet
            val prefix = "${db.lowercase()}."
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
                onBack = onBack,
                isLocked = isLocked,
                onToggleLock = onToggleLock,
                showLock = true,
                onSave = if (hasPendingPriv) ({ showPrivSaveConfirm = true }) else null
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { CurrentQueryBar(query = currentQuery) }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showRenameDialog = true }) { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Rename") }
                TextButton(onClick = { showPasswordDialog = true }) { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Password") }
            }
            if (isLoading && grants.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
            }
            if (hasPendingPriv) {
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${pendingPrivChanges.size} pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text("— Save to confirm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearPendingPrivs(user, host) }, modifier = Modifier.height(28.dp)) { Text("Discard", style = MaterialTheme.typography.labelSmall) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Databases", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (allDatabases.isEmpty()) {
                Text("No databases loaded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                allDatabases.forEach { db ->
                    val boldDb = effectiveHasAnyPriv(db, null)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(db, style = MaterialTheme.typography.bodyMedium, fontWeight = if (boldDb) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        TextButton(onClick = { expandedPriv = if (expandedPriv == db) null else db }, enabled = true) { Text(if (expandedPriv == db) "Hide" else "Privileges", style = MaterialTheme.typography.labelSmall) }
                        IconButton(onClick = { val e = expandedDb == db; expandedDb = if (e) null else db; if (!e) viewModel.loadTablesForDb(db) }, modifier = Modifier.size(28.dp)) {
                            Icon(if (expandedDb == db) Icons.Default.Close else Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (expandedPriv == db) {
                        val onKey = "${db}.*"
                        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(6.dp)).padding(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                PRIVS.chunked(4).forEach { chunk ->
                                    Column(modifier = Modifier.weight(1f)) {
                                        chunk.forEach { priv ->
                                            val baseChecked = hasPrivOnTarget(priv, onKey)
                                            val checked = effectiveChecked(priv, onKey)
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                Checkbox(checked = checked, onCheckedChange = { want: Boolean ->
                                                    if (isLocked) return@Checkbox
                                                    viewModel.stagePrivToggle(user, host, priv, onKey, want, baseChecked)
                                                }, enabled = !isLocked, modifier = Modifier.size(28.dp))
                                                Text(priv, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    if (expandedDb == db) {
                        val tables = dbTables[db]
                        if (tables == null) {
                            Text("Loading...", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp))
                        } else if (tables.isEmpty()) {
                            Text("No tables", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
                        } else {
                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                tables.forEach { tbl ->
                                    val boldTbl = effectiveHasAnyPriv(db, tbl)
                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(tbl, style = MaterialTheme.typography.bodySmall, fontWeight = if (boldTbl) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                            TextButton(onClick = { expandedPriv = if (expandedPriv == "$db.$tbl") null else "$db.$tbl" }) { Text(if (expandedPriv == "$db.$tbl") "Hide" else "Privileges", style = MaterialTheme.typography.labelSmall) }
                                        }
                                        if (expandedPriv == "$db.$tbl") {
                                            val tKey = "$db.$tbl"
                                            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(6.dp)).padding(6.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    PRIVS.chunked(4).forEach { chunk ->
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            chunk.forEach { priv ->
                                                                val baseCheckedTbl = hasPrivOnTarget(priv, tKey)
                                                                val checkedTbl = effectiveChecked(priv, tKey)
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Checkbox(checked = checkedTbl, onCheckedChange = { want: Boolean ->
                                                                        if (isLocked) return@Checkbox
                                                                        viewModel.stagePrivToggle(user, host, priv, tKey, want, baseCheckedTbl)
                                                                    }, enabled = !isLocked, modifier = Modifier.size(28.dp))
                                                                    Text(priv, style = MaterialTheme.typography.labelSmall)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Grant Statements", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            grants.forEach { g ->
                SelectionContainer { Text(g, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(8.dp)) }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
    if (showPrivSaveConfirm) {
        val sqls = viewModel.buildPendingPrivSqls(user, host)
        val fullSql = sqls.joinToString(";\n")
        val clipboard = LocalClipboardManager.current
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { showPrivSaveConfirm = false },
            title = { Text("Confirm Write (${sqls.size})") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Query to be executed:", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer { Text(fullSql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp)) }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { clipboard.setText(AnnotatedString(fullSql)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Copy") }
                }
            },
            confirmButton = { TextButton(onClick = { showPrivSaveConfirm = false; viewModel.commitPendingPrivs(user, host, isLocked = isLocked) }) { Text("Execute", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showPrivSaveConfirm = false }) { Text("Cancel") } }
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
