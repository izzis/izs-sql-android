package com.sqlclient.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sqlclient.android.data.remote.ColumnMetadata

@Composable
fun DataTable(
    columns: List<ColumnMetadata>,
    rows: List<List<Any?>>,
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberScrollState()
    val displayRows = androidx.compose.runtime.remember(rows) {
        rows.map { r -> r.map { v -> if (v == null) "NULL" else { val s = v.toString(); if (s.length > 200) s.take(200) + "…" else s } } }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(horizontalScrollState)
        ) {
            Column {
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                    columns.forEach { column ->
                        Box(modifier = Modifier.width(150.dp).padding(8.dp)) {
                            Text(
                                text = column.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(rows, key = { index, _ -> index }, contentType = { _, _ -> "row" }) { rowIndex, row ->
                        val displayRow = displayRows.getOrNull(rowIndex) ?: emptyList()
                        Column {
                            Row(
                                modifier = Modifier.background(
                                    if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                displayRow.forEachIndexed { cIdx, cellText ->
                                    val isNull = row.getOrNull(cIdx) == null
                                    Box(modifier = Modifier.width(150.dp).padding(8.dp)) {
                                        Text(
                                            text = cellText,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp
                                            ),
                                            color = if (isNull) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = "${rows.size} rows", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
