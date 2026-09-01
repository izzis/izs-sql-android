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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.Scaffold
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
import com.sqlclient.android.ui.components.SqlEditor
import com.sqlclient.android.ui.viewmodel.QueryResultState
import com.sqlclient.android.ui.viewmodel.QueryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueryEditorScreen(
    viewModel: QueryViewModel,
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

    val activeTab = queryTabs.find { it.id == activeTabId }

    LaunchedEffect(profile.id) {
        viewModel.setCurrentProfileId(profile.id)
    }

    LaunchedEffect(database, table) {
        viewModel.setInitialQuery(database, table)
    }

    val topBarColor = try {
        Color(android.graphics.Color.parseColor(profile.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
    val currentQuery by viewModel.currentQuery.collectAsState()
    var showFavorites by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(true) }
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Query Editor",
                subtitle = "$database.$table",
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
                onSave = if (activeTab?.query.isNullOrBlank() == false) ({ viewModel.saveFavorite(activeTab!!.query) }) else null,
                onReconnect = onReconnect,
                isReconnecting = isReconnecting
            )
        },
        bottomBar = { CurrentQueryBar(queries = if (currentQuery.isNotEmpty()) currentQuery else listOfNotNull(activeTab?.query)) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            QueryTabBar(
                tabs = queryTabs,
                activeTabId = activeTabId,
                onTabClick = { viewModel.setActiveTab(it) },
                onCloseTab = { viewModel.closeTab(it) },
                onAddTab = { viewModel.addTab() }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showEditor = !showEditor }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (showEditor) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showEditor) "Hide editor" else "Show editor",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Editor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = q.queryText.take(60),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f).clickable { viewModel.openSavedQuery(q) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { viewModel.deleteSavedQuery(q) }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(10.dp))
                            }
                        }
                    }
                }
            }

            if (showEditor) {
                activeTab?.let { tab ->
                    var queryText by remember(tab.id) { mutableStateOf(TextFieldValue(tab.query)) }
                    LaunchedEffect(tab.query) {
                        if (queryText.text != tab.query) {
                            queryText = TextFieldValue(tab.query, selection = TextRange(tab.query.length))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        SqlEditor(
                            query = queryText,
                            onQueryChange = {
                                queryText = it
                                viewModel.updateQuery(tab.id, it.text)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        var showWriteConfirm by remember { mutableStateOf(false) }

                        Button(
                            onClick = {
                                if (isLocked && viewModel.isWriteQuery()) {
                                    viewModel.executeQuery(isLocked = true)
                                    return@Button
                                }
                                if (viewModel.isWriteQuery()) {
                                    showWriteConfirm = true
                                } else {
                                    viewModel.executeQuery(isLocked = isLocked)
                                }
                            },
                            enabled = !isExecuting && queryText.text.isNotBlank() && !(isLocked && viewModel.isWriteQuery()),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isExecuting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Execute",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text("Execute (Ctrl+Enter)")
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
                }
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
                            text = "Result: ${result.rowCount} rows",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        DataTable(
                            columns = result.columns,
                            rows = result.rows,
                            modifier = Modifier.fillMaxSize()
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
