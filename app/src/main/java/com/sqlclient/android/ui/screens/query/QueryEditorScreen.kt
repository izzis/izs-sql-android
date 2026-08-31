package com.sqlclient.android.ui.screens.query

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
    onBack: () -> Unit
) {
    val queryTabs by viewModel.queryTabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val queryResult by viewModel.queryResult.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()

    val activeTab = queryTabs.find { it.id == activeTabId }

    LaunchedEffect(profile.id) {
        viewModel.setCurrentProfileId(profile.id)
    }

    LaunchedEffect(database, table) {
        viewModel.setInitialQuery(database, table)
    }

    // isLocked from sessionLocked passed via NavGraph
    val topBarColor = try {
        Color(android.graphics.Color.parseColor(profile.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
    val currentQuery by viewModel.currentQuery.collectAsState()
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Query Editor",
                subtitle = "$database.$table",
                containerColor = topBarColor,
                onRefresh = { viewModel.executeQuery() },
                isRefreshing = isExecuting,
                isLocked = isLocked,
                showLock = true,
                onToggleLock = onToggleLock,
                onBack = onBack
            )
        },
        bottomBar = { CurrentQueryBar(query = currentQuery.ifBlank { activeTab?.query ?: "" }) }
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                        OutlinedButton(
                            onClick = { viewModel.clearResult() },
                            enabled = queryResult !is QueryResultState.Idle
                        ) {
                            Text("Clear")
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
            }

            when (val result = queryResult) {
                is QueryResultState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Executing query...")
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
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
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
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodyMedium,
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
