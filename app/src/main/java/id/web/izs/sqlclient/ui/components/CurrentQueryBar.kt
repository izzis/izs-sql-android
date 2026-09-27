package id.web.izs.sqlclient.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.web.izs.sqlclient.util.QueryLogEntry
import id.web.izs.sqlclient.util.rememberCopyToClipboard

// Amber for staged (queued, not sent yet): hardcoded so it stays amber on dynamic palettes
// and never collides with `error` red, which always means "the server rejected it".
private val StagedAmber = Color(0xFFB26A00)

@Composable
fun CurrentQueryBar(
    queries: List<QueryLogEntry>,
    modifier: Modifier = Modifier,
    isExecuting: Boolean = false,
    onCancel: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    // show bar even when executing but no queries yet (e.g. immediate after execute)
    if (queries.isEmpty() && !isExecuting) return
    val lastEntry = queries.lastOrNull()
    val lastQuery = lastEntry?.sql ?: ""
    val lastFailed = lastEntry?.error != null
    val lastStaged = lastEntry?.staged == true
    val stagedCount = queries.count { it.staged }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val copyToClipboard = rememberCopyToClipboard()

    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (expanded) {
                        Text(
                            text = "Query log (${queries.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (stagedCount > 0) {
                            Text(
                                text = "  ·  $stagedCount staged",
                                style = MaterialTheme.typography.labelSmall,
                                color = StagedAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    } else if (isExecuting) {
                        Text(
                            text = "Current query  ·  executing ...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = "Current query",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "  ·  ${lastQuery.replace('\n', ' ').take(80)}${if (lastQuery.length > 80) "…" else ""}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = when {
                                lastFailed -> MaterialTheme.colorScheme.error
                                lastStaged -> StagedAmber
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(start = 4.dp)
                        )
                    }
                    if (isExecuting && onCancel != null) {
                        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 24.dp)) {
                            Icon(Icons.Default.Stop, contentDescription = "Cancel", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        if (onClear != null) {
                            IconButton(
                                onClick = onClear,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear query log", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        IconButton(
                            onClick = {
                                copyToClipboard(queries.joinToString(";\n") { it.sql }, "Copied")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy all", modifier = Modifier.size(14.dp))
                        }
                    }
                }

            AnimatedVisibility(
                visible = expanded || (isExecuting && lastQuery.isNotEmpty()),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isExecuting && !expanded && lastQuery.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${queries.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(20.dp).padding(top = 2.dp)
                            )
                            SelectionContainer(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lastQuery,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = when {
                                        lastFailed -> MaterialTheme.colorScheme.error
                                        lastStaged -> StagedAmber
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else if (expanded) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            items(queries.size) { idx ->
                                val originalIdx = queries.lastIndex - idx
                                val isLast = originalIdx == queries.lastIndex
                                val entry = queries[originalIdx]
                                val sql = entry.sql
                                val error = entry.error
                                val failed = error != null
                                val staged = entry.staged

                                if (idx > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "${originalIdx + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = when {
                                            failed -> MaterialTheme.colorScheme.error
                                            staged -> StagedAmber
                                            isLast -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = if (isLast || failed || staged) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.width(20.dp).padding(top = 2.dp)
                                    )
                                    SelectionContainer(modifier = Modifier.weight(1f)) {
                                        Column {
                                            Text(
                                                text = sql,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                color = when {
                                                    failed -> MaterialTheme.colorScheme.error
                                                    staged -> StagedAmber
                                                    isLast -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                                fontWeight = if (isLast) FontWeight.Medium else FontWeight.Normal
                                            )
                                            if (staged) {
                                                Text(
                                                    text = "staged — not sent to the server",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = StagedAmber,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                            if (error != null) {
                                                Row(
                                                    modifier = Modifier.padding(top = 2.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ErrorOutline,
                                                        contentDescription = "Error",
                                                        modifier = Modifier.size(12.dp).padding(top = 2.dp),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = error,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
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
}
