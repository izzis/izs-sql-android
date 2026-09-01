package com.sqlclient.android.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sqlclient.android.data.remote.ColumnMetadata

private const val MIN_COL_DP = 90
private const val MAX_COL_DP = 360
private const val HEADER_CHAR_DP = 8
private const val DATA_CHAR_DP = 8
private const val CELL_H_PADDING_DP = 24
private const val SAMPLE_ROWS = 30

@Composable
fun DataTable(
    columns: List<ColumnMetadata>,
    rows: List<List<Any?>>,
    modifier: Modifier = Modifier,
    database: String? = null,
    table: String? = null,
    onQuickUpdate: ((targetColumn: String, row: List<Any?>) -> Unit)? = null
) {
    val horizontalScrollState = rememberScrollState()
    val displayRows = remember(rows) {
        rows.map { r -> r.map { v -> if (v == null) "NULL" else { val s = v.toString(); if (s.length > 200) s.take(200) + "…" else s } } }
    }
    val columnWidths: List<Dp> = remember(columns, displayRows) {
        if (columns.isEmpty()) emptyList() else {
            val sample = displayRows.take(SAMPLE_ROWS)
            columns.mapIndexed { idx, col ->
                val headerW = col.name.length * HEADER_CHAR_DP + CELL_H_PADDING_DP
                var maxDataLen = 0
                for (r in sample) {
                    val len = r.getOrNull(idx)?.length ?: 0
                    if (len > maxDataLen) maxDataLen = len
                }
                val dataW = maxDataLen * DATA_CHAR_DP + CELL_H_PADDING_DP
                maxOf(headerW, dataW).coerceIn(MIN_COL_DP, MAX_COL_DP).dp
            }
        }
    }
    var viewingCell by remember { mutableStateOf<Triple<Int, Int, List<Any?>>?>(null) }
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current

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
                    columns.forEachIndexed { idx, column ->
                        val w = columnWidths.getOrNull(idx) ?: 150.dp
                        Box(modifier = Modifier.width(w).padding(8.dp)) {
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
                            Box(
                                modifier = Modifier.pointerInput(rowIndex, columnWidths) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            val widthsPx = with(this@pointerInput) { columnWidths.map { it.toPx() } }
                                            var acc = 0f
                                            var colIdx = -1
                                            for (i in widthsPx.indices) {
                                                val next = acc + widthsPx[i]
                                                if (offset.x >= acc && offset.x < next) { colIdx = i; break }
                                                acc = next
                                            }
                                            if (colIdx in row.indices) {
                                                viewingCell = Triple(rowIndex, colIdx, row)
                                            }
                                        }
                                    )
                                }
                            ) {
                                Row(
                                    modifier = Modifier.background(
                                        if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surface
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    displayRow.forEachIndexed { cIdx, cellText ->
                                        val isNull = row.getOrNull(cIdx) == null
                                        val w = columnWidths.getOrNull(cIdx) ?: 150.dp
                                        Box(modifier = Modifier.width(w).padding(8.dp)) {
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

    viewingCell?.let { (_, colIdx, row) ->
        val colName = columns.getOrNull(colIdx)?.name ?: "Value"
        val raw = row.getOrNull(colIdx)?.toString() ?: "NULL"
        val canQuickUpdate = onQuickUpdate != null
        AlertDialog(
            onDismissRequest = { viewingCell = null },
            title = { Text(colName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) },
            text = {
                SelectionContainer {
                    Text(
                        text = raw,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 400.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                androidx.compose.foundation.layout.Row {
                    if (canQuickUpdate) {
                        TextButton(onClick = {
                            val c = viewingCell
                            viewingCell = null
                            c?.let { (_, cIdx, r) ->
                                val cName = columns.getOrNull(cIdx)?.name ?: return@let
                                onQuickUpdate?.invoke(cName, r)
                            }
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp)); Text("Quick Update")
                        }
                    }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(raw))
                        Toast.makeText(ctx, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                        viewingCell = null
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp)); Text("Copy")
                    }
                }
            }
        )
    }
}
