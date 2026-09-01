package com.sqlclient.android.ui.screens.query

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.data.local.entity.QueryHistoryEntity
import com.sqlclient.android.ui.components.AppTopBar
import com.sqlclient.android.ui.components.CurrentQueryBar
import com.sqlclient.android.ui.components.ReconnectBanner
import com.sqlclient.android.ui.viewmodel.ConnectionViewModel
import com.sqlclient.android.ui.viewmodel.QueryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed class ManagedItem {
    data class DatabaseHeader(val dbName: String, val queryCount: Int) : ManagedItem()
    data class QueryEntry(val entity: QueryHistoryEntity, val dbName: String) : ManagedItem()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ManageSavedQueriesScreen(
    queryViewModel: QueryViewModel,
    connectionViewModel: ConnectionViewModel,
    profile: ConnectionProfileEntity,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onBack: () -> Unit,
    onOpenQuery: (String, String) -> Unit,
    onReconnect: () -> Unit,
    isReconnecting: Boolean
) {
    val savedQueries by queryViewModel.allFavorites.collectAsState()
    val currentQuery by queryViewModel.currentQuery.collectAsState()
    val reconnectMessage by connectionViewModel.reconnectMessage.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var expandedDatabases by remember { mutableStateOf(setOf<String>()) }
    var expandedQueryIds by remember { mutableStateOf(setOf<Long>()) }
    var renameTarget by remember { mutableStateOf<QueryHistoryEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<QueryHistoryEntity?>(null) }

    val topBarColor = try {
        Color(android.graphics.Color.parseColor(profile.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    LaunchedEffect(Unit) {
        queryViewModel.loadAllFavorites(profile.id)
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val json = buildBackupJson(savedQueries, profile.name)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export saved", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@launch
                    val imported = parseBackupJson(text)
                    imported.forEach { (name, db, sql) ->
                        queryViewModel.saveFavoriteDirect(profile.id, sql, db, name)
                    }
                    withContext(Dispatchers.Main) {
                        queryViewModel.loadAllFavorites(profile.id)
                        Toast.makeText(context, "Imported ${imported.size} queries", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val grouped = remember(savedQueries) {
        savedQueries.groupBy { it.database?.trim()?.takeIf { d -> d.isNotBlank() } ?: "Other" }
            .toSortedMap(compareBy<String>({ it == "Other" }, { it }))
    }
    LaunchedEffect(grouped) {
        android.util.Log.d("ManageSavedQueries", "grouped keys=${grouped.keys}, total=${savedQueries.size}, perDb=${grouped.mapValues { it.value.size }}")
        if (expandedDatabases.isEmpty() && grouped.isNotEmpty()) {
            expandedDatabases = grouped.keys.toSet()
        }
    }

    val flatItems = remember(grouped, expandedDatabases) {
        grouped.flatMap { (dbName, queries) ->
            val header = ManagedItem.DatabaseHeader(dbName, queries.size)
            if (dbName in expandedDatabases) {
                listOf(header) + queries.map { ManagedItem.QueryEntry(it, dbName) }
            } else {
                listOf(header)
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Manage Saved Queries",
                containerColor = topBarColor,
                onBack = onBack,
                onRefresh = { queryViewModel.loadAllFavorites(profile.id) },
                showLock = true,
                isLocked = isLocked,
                onToggleLock = onToggleLock,
                onReconnect = onReconnect,
                isReconnecting = isReconnecting
            )
        },
        bottomBar = { CurrentQueryBar(queries = currentQuery) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ReconnectBanner(message = reconnectMessage)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { backupLauncher.launch("saved_queries_${profile.name}.json") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export")
                }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import")
                }
            }
            HorizontalDivider()
            if (savedQueries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No saved queries yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(
                        items = flatItems,
                        key = {
                            when (it) {
                                is ManagedItem.DatabaseHeader -> "db_${it.dbName}"
                                is ManagedItem.QueryEntry -> "q_${it.entity.id}"
                            }
                        },
                        contentType = {
                            when (it) {
                                is ManagedItem.DatabaseHeader -> "header"
                                is ManagedItem.QueryEntry -> "query"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is ManagedItem.DatabaseHeader -> {
                                DatabaseFolderHeader(
                                    dbName = item.dbName,
                                    queryCount = item.queryCount,
                                    isExpanded = item.dbName in expandedDatabases,
                                    onClick = {
                                        expandedDatabases = if (item.dbName in expandedDatabases) expandedDatabases - item.dbName else expandedDatabases + item.dbName
                                    }
                                )
                            }
                            is ManagedItem.QueryEntry -> {
                                QueryItem(
                                    entity = item.entity,
                                    isExpanded = item.entity.id in expandedQueryIds,
                                    onToggle = {
                                        expandedQueryIds = if (item.entity.id in expandedQueryIds) expandedQueryIds - item.entity.id else expandedQueryIds + item.entity.id
                                    },
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(item.entity.queryText))
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    },
                                    onRenameClick = {
                                        renameTarget = item.entity
                                        renameText = item.entity.name ?: ""
                                    },
                                    onDeleteClick = { deleteTarget = item.entity },
                                    onOpen = {
                                        queryViewModel.openSavedQuery(item.entity)
                                        onOpenQuery(item.dbName, "_")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Query") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget ?: return@TextButton
                    if (renameText.isNotBlank()) {
                        queryViewModel.renameSavedQuery(target, renameText)
                    }
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Query") },
            text = { Text("Delete this saved query?") },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleteTarget ?: return@TextButton
                    queryViewModel.deleteSavedQuery(target)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DatabaseFolderHeader(
    dbName: String,
    queryCount: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = dbName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "$queryCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun QueryItem(
    entity: QueryHistoryEntity,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entity.name?.takeIf { it.isNotBlank() } ?: "Unnamed",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (entity.name.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        fontStyle = if (entity.name.isNullOrBlank()) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AnimatedVisibility(
                        visible = !isExpanded,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = entity.queryText.replace("\n", " ").take(80) + if (entity.queryText.length > 80) "…" else "",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                }
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = entity.queryText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Single row: square-ish buttons with tight gap
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onRenameClick,
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rename", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = onOpen,
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun buildBackupJson(queries: List<QueryHistoryEntity>, profileName: String): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }
    val root = JSONObject()
    root.put("version", 1)
    root.put("exported_at", sdf.format(Date()))
    root.put("profile_name", profileName)
    val arr = JSONArray()
    queries.forEach { q ->
        val obj = JSONObject()
        obj.put("name", q.name)
        obj.put("database", q.database)
        obj.put("query_text", q.queryText)
        arr.put(obj)
    }
    root.put("queries", arr)
    return root.toString(2)
}

private fun parseBackupJson(text: String): List<Triple<String?, String?, String>> {
    val root = JSONObject(text)
    val arr = root.getJSONArray("queries")
    val result = mutableListOf<Triple<String?, String?, String>>()
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val name = if (obj.isNull("name")) null else obj.optString("name").takeIf { it.isNotEmpty() }
        val db = if (obj.isNull("database")) null else obj.optString("database").takeIf { it.isNotEmpty() }
        val sql = obj.getString("query_text")
        result.add(Triple(name, db, sql))
    }
    return result
}
