package com.sqlclient.android.ui.components

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable
fun CurrentQueryBar(
    queries: List<String>,
    modifier: Modifier = Modifier,
) {
    if (queries.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

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
                Text(
                    text = "Query log (${queries.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(queries.joinToString(";\n")))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy all", modifier = Modifier.size(14.dp))
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val reversed = queries.indices.reversed()
                    for (reversedIdx in reversed) {
                        val originalIdx = queries.lastIndex - reversedIdx
                        val isLast = originalIdx == queries.lastIndex
                        val sql = queries[originalIdx]

                        if (reversedIdx < queries.lastIndex) {
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
                                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.width(20.dp).padding(top = 2.dp)
                            )
                            SelectionContainer(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sql,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isLast) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
