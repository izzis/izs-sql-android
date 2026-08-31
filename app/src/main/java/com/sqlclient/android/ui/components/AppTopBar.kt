package com.sqlclient.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onDisconnect: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    isLocked: Boolean = false,
    onToggleLock: (() -> Unit)? = null,
    showLock: Boolean = true,
    showRefresh: Boolean = true,
    onSave: (() -> Unit)? = null,
    onStructure: (() -> Unit)? = null,
    onData: (() -> Unit)? = null,
    onBackOrDisconnect: (() -> Unit)? = null,
    onReconnect: (() -> Unit)? = null,
    isReconnecting: Boolean = false
) {
    val effectiveOnBack = onBack ?: onBackOrDisconnect
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                if (subtitle != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        if (isLocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                modifier = Modifier.size(12.dp),
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        navigationIcon = {
            when {
                effectiveOnBack != null -> {
                    IconButton(onClick = effectiveOnBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
                onMenu != null -> {
                    IconButton(onClick = onMenu) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    }
                }
            }
        },
        actions = {
            if (onData != null) {
                IconButton(onClick = onData) {
                    Icon(Icons.Default.ViewColumn, contentDescription = "Data", tint = Color.White)
                }
            }
            if (onStructure != null) {
                IconButton(onClick = onStructure) {
                    Icon(Icons.Default.TableChart, contentDescription = "Structure", tint = Color.White)
                }
            }
            if (onSave != null) {
                IconButton(onClick = onSave) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Check, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("SAVE", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (showLock && onToggleLock != null) {
                IconButton(onClick = onToggleLock) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) "Unlock" else "Lock",
                        tint = Color.White
                    )
                }
            }
            // Reconnect button
            if (onReconnect != null) {
                IconButton(onClick = onReconnect) {
                    if (isReconnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.Link, contentDescription = "Reconnect", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (showRefresh && onRefresh != null) {
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (onDisconnect != null) {
                IconButton(onClick = onDisconnect) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Disconnect", tint = Color.White)
                }
            }
        }
    )
}
