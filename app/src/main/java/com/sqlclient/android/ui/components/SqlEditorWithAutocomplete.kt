package com.sqlclient.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay

enum class SuggestionType { KEYWORD, DATABASE, TABLE, COLUMN }

data class SuggestionItem(
    val display: String,
    val insertText: String,
    val type: SuggestionType,
    val priority: Int
)

private enum class SqlContext {
    START, AFTER_SELECT, AFTER_FROM, AFTER_JOIN,
    AFTER_WHERE, AFTER_SET, AFTER_INSERT, AFTER_UPDATE,
    AFTER_DELETE, AFTER_ORDER, AFTER_GROUP, AFTER_DOT,
    AFTER_ON, GENERIC
}

private fun detectSqlContext(text: String, cursorPos: Int): SqlContext {
    val rawBefore = text.substring(0, cursorPos.coerceAtMost(text.length))
    val textBefore = rawBefore.trimEnd()
    if (textBefore.endsWith('.')) return SqlContext.AFTER_DOT
    val words = textBefore.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val lastRaw = words.lastOrNull() ?: ""
    val last = lastRaw.uppercase().trimEnd(',', ';', '(')
    val secondLastRaw = if (words.size >= 2) words[words.size - 2] else ""
    val secondLast = secondLastRaw.uppercase().trimEnd(',', ';', '(')

    // User finished typing identifier (space/delimiter after it) → not in THAT context anymore
    val cursorAtEnd = cursorPos >= text.length
    val hasTrailingSpace = cursorAtEnd && rawBefore.length > textBefore.length
    // Still typing identifier (no space after it yet) → show suggestions for that context
    val isStillTypingIdent = last.isNotEmpty() && last !in KEYWORD_SET && !hasTrailingSpace

    if (isStillTypingIdent) {
        return when (secondLast) {
            "SELECT" -> SqlContext.AFTER_SELECT
            "FROM" -> SqlContext.AFTER_FROM
            "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "CROSS" -> SqlContext.AFTER_JOIN
            "WHERE", "AND", "OR" -> SqlContext.AFTER_WHERE
            "ON" -> SqlContext.AFTER_ON
            "SET" -> SqlContext.AFTER_SET
            "BY" -> {
                val thirdLast = if (words.size >= 3) words[words.size - 3].uppercase().trimEnd(',') else ""
                if (thirdLast == "ORDER") SqlContext.AFTER_ORDER
                else if (thirdLast == "GROUP") SqlContext.AFTER_GROUP
                else SqlContext.GENERIC
            }
            else -> SqlContext.GENERIC
        }
    }

    return when {
        words.isEmpty() || lastRaw.endsWith(";") -> SqlContext.START
        last == "SELECT" || lastRaw == "," -> SqlContext.AFTER_SELECT
        last == "FROM" -> SqlContext.AFTER_FROM
        last in setOf("JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "CROSS") -> SqlContext.AFTER_JOIN
        last == "WHERE" || last == "AND" || last == "OR" -> SqlContext.AFTER_WHERE
        last == "ON" -> SqlContext.AFTER_ON
        last == "SET" -> SqlContext.AFTER_SET
        secondLast == "INSERT" && last == "INTO" -> SqlContext.AFTER_INSERT
        last == "UPDATE" -> SqlContext.AFTER_UPDATE
        secondLast == "DELETE" && last == "FROM" -> SqlContext.AFTER_DELETE
        last == "ORDER" || (secondLast == "ORDER" && last == "BY") -> SqlContext.AFTER_ORDER
        last == "GROUP" || (secondLast == "GROUP" && last == "BY") -> SqlContext.AFTER_GROUP
        else -> SqlContext.GENERIC
    }
}

private val KEYWORD_SET = setOf(
    "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES",
    "UPDATE", "SET", "DELETE", "CREATE", "ALTER", "DROP",
    "TABLE", "INDEX", "DATABASE", "JOIN", "LEFT", "RIGHT",
    "INNER", "OUTER", "ON", "AND", "OR", "NOT", "NULL",
    "IS", "IN", "LIKE", "BETWEEN", "ORDER", "GROUP",
    "HAVING", "LIMIT", "OFFSET", "AS", "DISTINCT",
    "UNION", "ALL", "EXPLAIN", "SHOW", "DESCRIBE",
    "DESC", "ASC", "CROSS", "FULL", "NATURAL", "USING",
    "CASE", "WHEN", "THEN", "ELSE", "END"
)

private fun extractAliases(text: String): Map<String, String> {
    val pattern = Regex(
        """(?:FROM|JOIN)\s+`?(\w+)`?\s+(?:AS\s+)?`?(\w+)`?""",
        RegexOption.IGNORE_CASE
    )
    return pattern.findAll(text).associate {
        it.groupValues[2].lowercase() to it.groupValues[1]
    }
}

private fun extractCurrentWord(text: String, cursorPos: Int): String {
    val pos = cursorPos.coerceAtMost(text.length)
    if (pos == 0) return ""
    var start = pos - 1
    while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_')) start--
    // If stopped at opening backtick/double-quote, include what's inside
    if (start >= 0 && (text[start] == '`' || text[start] == '"')) {
        start--
        while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_')) start--
    }
    return text.substring(start + 1, pos)
}

private fun extractDotPrefix(text: String, cursorPos: Int): String? {
    val pos = cursorPos.coerceAtMost(text.length)
    if (pos < 2) return null
    if (text[pos - 1] != '.') return null
    var end = pos - 2
    if (end < 0) return null
    while (end >= 0 && (text[end].isLetterOrDigit() || text[end] == '_')) end--
    val prefix = text.substring(end + 1, pos - 1)
    return prefix.ifEmpty { null }
}

private fun isInsideStringOrComment(text: String, pos: Int): Boolean {
    val safePos = pos.coerceAtMost(text.length)
    var inSingleQuote = false
    var inLineComment = false
    var inBlockComment = false
    var i = 0
    while (i < safePos) {
        val c = text[i]
        if (inLineComment) {
            if (c == '\n') inLineComment = false
        } else if (inBlockComment) {
            if (c == '*' && i + 1 < text.length && text[i + 1] == '/') {
                inBlockComment = false
                i++
            }
        } else if (inSingleQuote) {
            if (c == '\'' && i + 1 < text.length && text[i + 1] == '\'') {
                i += 2
                continue
            }
            if (c == '\'') inSingleQuote = false
        } else {
            if (c == '-' && i + 1 < text.length && text[i + 1] == '-') {
                inLineComment = true; i += 2; continue
            }
            if (c == '/' && i + 1 < text.length && text[i + 1] == '*') {
                inBlockComment = true; i += 2; continue
            }
            if (c == '\'') inSingleQuote = true
        }
        i++
    }
    return inSingleQuote || inBlockComment || inLineComment
}

private data class IdentifierInfo(val isOpen: Boolean, val prefix: String)

private fun getIdentifierInfo(text: String, pos: Int): IdentifierInfo {
    val safePos = pos.coerceAtMost(text.length)
    var inBacktick = false
    var inDoubleQuote = false
    var prefix = ""
    var i = 0
    while (i < safePos) {
        val c = text[i]
        if (inBacktick) {
            if (c == '`') { inBacktick = false; prefix = ""; i++; continue }
            prefix += c
        } else if (inDoubleQuote) {
            if (c == '"') { inDoubleQuote = false; prefix = ""; i++; continue }
            prefix += c
        } else {
            if (c == '`') { inBacktick = true; prefix = "" }
            else if (c == '"') { inDoubleQuote = true; prefix = "" }
        }
        i++
    }
    return IdentifierInfo(inBacktick || inDoubleQuote, prefix)
}

private fun insertSuggestion(
    text: String,
    cursorPos: Int,
    suggestion: SuggestionItem
): Pair<String, Int> {
    val pos = cursorPos.coerceAtMost(text.length)
    val identInfo = getIdentifierInfo(text, pos)
    val dotPrefix = extractDotPrefix(text, pos)

    // Inside backtick or double-quote: replace prefix, add closing delimiter
    if (identInfo.isOpen && dotPrefix == null) {
        var replaceStart = pos - identInfo.prefix.length
        if (replaceStart > 0 && (text[replaceStart - 1] == '`' || text[replaceStart - 1] == '"')) {
            replaceStart--
        }
        val insert = suggestion.insertText
        val newText = text.substring(0, replaceStart) + insert + text.substring(pos)
        val newCursor = replaceStart + insert.length
        return newText to newCursor
    }

    val replaceStart = if (dotPrefix != null) {
        pos
    } else {
        var start = pos - 1
        while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_')) start--
        if (start >= 0 && (text[start] == '`' || text[start] == '"')) start--
        start + 1
    }
    val insert = suggestion.insertText
    val addSpace = suggestion.type == SuggestionType.KEYWORD
    val suffix = if (addSpace) " " else ""
    val newText = text.substring(0, replaceStart) + insert + suffix + text.substring(pos)
    val newCursor = replaceStart + insert.length + suffix.length
    return newText to newCursor
}

private class CursorPositionProvider(
    private val cursorX: Float,
    private val cursorY: Float
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        var x = anchorBounds.left + cursorX.toInt()
        var y = anchorBounds.top + cursorY.toInt() + 24
        if (x + popupContentSize.width > windowSize.width) {
            x = windowSize.width - popupContentSize.width
        }
        if (y + popupContentSize.height > windowSize.height) {
            y = anchorBounds.top + cursorY.toInt() - popupContentSize.height
        }
        return IntOffset(
            x.coerceAtLeast(0),
            y.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        )
    }
}

@Composable
private fun SuggestionDropdown(
    suggestions: List<SuggestionItem>,
    isLoading: Boolean,
    onSelect: (SuggestionItem) -> Unit,
    onDismiss: () -> Unit,
    cursorX: Float,
    cursorY: Float
) {
    Popup(
        popupPositionProvider = CursorPositionProvider(cursorX, cursorY),
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .heightIn(max = 240.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(suggestion)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (suggestion.type) {
                                SuggestionType.KEYWORD -> Icons.Default.Code
                                SuggestionType.DATABASE -> Icons.Default.ViewColumn
                                SuggestionType.TABLE -> Icons.Default.TableChart
                                SuggestionType.COLUMN -> Icons.AutoMirrored.Filled.FormatListBulleted
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = suggestion.display,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = suggestion.type.name.lowercase(),
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SqlEditorWithAutocomplete(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    enableAutocomplete: Boolean = true,
    databaseNames: List<String> = emptyList(),
    tableNames: List<String> = emptyList(),
    columnNames: List<String> = emptyList(),
    onFetchExtraColumns: (suspend (tableName: String) -> List<String>?)? = null,
    onFetchTablesForDatabase: (suspend (database: String) -> List<String>?)? = null,
) {
    val scrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    var showSuggestions by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<SuggestionItem>>(emptyList()) }
    var isLoadingColumns by remember { mutableStateOf(false) }
    var cursorX by remember { mutableStateOf(0f) }
    var cursorY by remember { mutableStateOf(0f) }

    // Alias map for the current query
    val aliases = remember(query.text) { extractAliases(query.text) }

    LaunchedEffect(query.text, query.selection, enableAutocomplete) {
        if (!enableAutocomplete) {
            showSuggestions = false
            return@LaunchedEffect
        }

        val cursorPos = query.selection.start
        val text = query.text

        if (isInsideStringOrComment(text, cursorPos)) {
            showSuggestions = false
            return@LaunchedEffect
        }

        val dotPrefix = extractDotPrefix(text, cursorPos)
        val currentWord = extractCurrentWord(text, cursorPos)
        val identInfo = getIdentifierInfo(text, cursorPos)

        // Inside backtick or double-quote identifier: show filtered columns/tables
        if (identInfo.isOpen && dotPrefix == null) {
            val filterPrefix = identInfo.prefix
            val ctx = detectSqlContext(text, cursorPos)
            val items = mutableListOf<SuggestionItem>()
            when (ctx) {
                SqlContext.AFTER_FROM, SqlContext.AFTER_JOIN -> {
                    items += tableNames.map { SuggestionItem(it, it, SuggestionType.TABLE, 1) }
                    items += databaseNames.map { SuggestionItem(it, it, SuggestionType.DATABASE, 5) }
                }
                else -> {
                    items += columnNames.map { SuggestionItem(it, it, SuggestionType.COLUMN, 1) }
                }
            }
            suggestions = items
                .filter { it.display.startsWith(filterPrefix, ignoreCase = true) }
                .sortedBy { it.priority }
            showSuggestions = suggestions.isNotEmpty()
            if (showSuggestions) {
                textLayoutResult?.let { layout ->
                    val rect = layout.getCursorRect(cursorPos)
                    cursorX = rect.left; cursorY = rect.bottom
                }
            }
            return@LaunchedEffect
        }

        if (dotPrefix != null) {
            val cleanPrefix = dotPrefix.trim('`', '"')
            val tableName = aliases[cleanPrefix.lowercase()] ?: cleanPrefix

            // dotPrefix is a database name → show tables from that database
            if (cleanPrefix in databaseNames) {
                isLoadingColumns = true
                val fetchedTables = try { onFetchTablesForDatabase?.invoke(cleanPrefix) } catch (_: Exception) { null }
                isLoadingColumns = false
                val tables = fetchedTables ?: emptyList()
                suggestions = tables.filter { it.startsWith(currentWord, ignoreCase = true) }
                    .map { SuggestionItem(it, it, SuggestionType.TABLE, 1) }
                showSuggestions = suggestions.isNotEmpty()
            } else {
                // dotPrefix is a table name → show columns
                isLoadingColumns = true
                val fetchedCols = try { onFetchExtraColumns?.invoke(tableName) } catch (_: Exception) { null }
                isLoadingColumns = false
                val cols = fetchedCols ?: columnNames
                suggestions = cols.filter { it.startsWith(currentWord, ignoreCase = true) }
                    .map { SuggestionItem(it, it, SuggestionType.COLUMN, 1) }
                showSuggestions = suggestions.isNotEmpty()
            }
        } else {
            // Suppress on empty text
            if (currentWord.isEmpty() && text.isBlank()) {
                showSuggestions = false
                return@LaunchedEffect
            }

            delay(80)

            val ctx = detectSqlContext(text, cursorPos)
            val items = mutableListOf<SuggestionItem>()

            // Resolve columns from SQL context: find table names from FROM/JOIN
            val needsColumns = ctx in setOf(
                SqlContext.AFTER_SELECT, SqlContext.AFTER_WHERE, SqlContext.AFTER_SET,
                SqlContext.AFTER_ORDER, SqlContext.AFTER_GROUP, SqlContext.AFTER_ON
            )
            var resolvedColumns = columnNames
            if (needsColumns && onFetchExtraColumns != null) {
                val tablePattern = Regex("""(?:FROM|JOIN)\s+`?(\w+)`?""", RegexOption.IGNORE_CASE)
                val contextTables = tablePattern.findAll(text).map { it.groupValues[1] }.toList()
                if (contextTables.isNotEmpty()) {
                    val allCols = mutableListOf<String>()
                    isLoadingColumns = true
                    for (tbl in contextTables) {
                        val fetchedCols = try { onFetchExtraColumns.invoke(tbl) } catch (_: Exception) { null }
                        if (fetchedCols != null) allCols.addAll(fetchedCols)
                    }
                    isLoadingColumns = false
                    if (allCols.isNotEmpty()) resolvedColumns = allCols.distinct()
                }
            }

            when (ctx) {
                SqlContext.AFTER_FROM, SqlContext.AFTER_JOIN, SqlContext.AFTER_UPDATE, SqlContext.AFTER_INSERT -> {
                    items += tableNames.map { SuggestionItem(it, it, SuggestionType.TABLE, 1) }
                    items += databaseNames.map { SuggestionItem(it, it, SuggestionType.DATABASE, 5) }
                }
                SqlContext.AFTER_SELECT, SqlContext.AFTER_WHERE, SqlContext.AFTER_SET,
                SqlContext.AFTER_ORDER, SqlContext.AFTER_GROUP, SqlContext.AFTER_ON -> {
                    items += resolvedColumns.map { SuggestionItem(it, it, SuggestionType.COLUMN, 1) }
                }
                SqlContext.AFTER_DOT -> {}
                else -> {}
            }
            if (currentWord.isNotEmpty() || ctx == SqlContext.START) {
                val allowedKeywords: Set<String>? = when (ctx) {
                    SqlContext.START -> null
                    SqlContext.AFTER_SELECT -> setOf("DISTINCT", "AS", "ALL", "TOP")
                    SqlContext.AFTER_FROM -> setOf("JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "CROSS", "ON", "WHERE", "GROUP", "ORDER", "LIMIT", "OFFSET")
                    SqlContext.AFTER_JOIN -> setOf("ON", "AND")
                    SqlContext.AFTER_WHERE -> setOf("AND", "OR", "IS", "NOT", "LIKE", "IN", "BETWEEN", "ORDER", "GROUP", "LIMIT", "OFFSET")
                    SqlContext.AFTER_SET -> emptySet()
                    SqlContext.AFTER_INSERT -> setOf("VALUES")
                    SqlContext.AFTER_UPDATE -> setOf("SET")
                    SqlContext.AFTER_DELETE -> emptySet()
                    SqlContext.AFTER_ORDER -> setOf("ASC", "DESC", "LIMIT", "OFFSET")
                    SqlContext.AFTER_GROUP -> setOf("HAVING", "ORDER", "LIMIT", "OFFSET")
                    SqlContext.AFTER_ON -> setOf("AND", "OR", "ORDER", "GROUP", "LIMIT", "OFFSET")
                    else -> null
                }
                val keywords = if (readOnly) SQL_KEYWORD_LIST.filter { it !in WRITE_KEYWORDS } else SQL_KEYWORD_LIST
                items += keywords
                    .filter { (allowedKeywords == null || it in allowedKeywords) && it.startsWith(currentWord, ignoreCase = true) }
                    .take(4)
                    .map { SuggestionItem(it, it, SuggestionType.KEYWORD, 10) }
            }

            suggestions = items
                .filter { it.display.startsWith(currentWord, ignoreCase = true) }
                .sortedBy { it.priority }
            showSuggestions = suggestions.isNotEmpty()
        }

        // Calculate cursor position for dropdown
        if (showSuggestions) {
            textLayoutResult?.let { layout ->
                val rect = layout.getCursorRect(cursorPos)
                cursorX = rect.left
                cursorY = rect.bottom
            }
        }
    }

    Box(modifier = modifier) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    RoundedCornerShape(8.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .verticalScroll(scrollState),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Normal
            ),
            visualTransformation = sqlHighlightTransformation(),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            onTextLayout = { textLayoutResult = it },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (query.text.isEmpty()) {
                        Text(
                            text = "Enter your SQL query here...",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (showSuggestions && suggestions.isNotEmpty()) {
            SuggestionDropdown(
                suggestions = suggestions,
                isLoading = isLoadingColumns,
                onSelect = { item ->
                    val (newText, newCursor) = insertSuggestion(query.text, query.selection.start, item)
                    onQueryChange(TextFieldValue(text = newText, selection = androidx.compose.ui.text.TextRange(newCursor)))
                    showSuggestions = false
                },
                onDismiss = { showSuggestions = false },
                cursorX = cursorX,
                cursorY = cursorY
            )
        }
    }
}

private val WRITE_KEYWORDS = setOf(
    "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP",
    "TRUNCATE", "RENAME", "GRANT", "REVOKE"
)

private val SQL_KEYWORD_LIST = listOf(
    "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES",
    "UPDATE", "SET", "DELETE", "CREATE", "ALTER", "DROP",
    "TABLE", "INDEX", "DATABASE", "JOIN", "LEFT", "RIGHT",
    "INNER", "OUTER", "ON", "AND", "OR", "NOT", "NULL",
    "IS", "IN", "LIKE", "BETWEEN", "ORDER", "BY", "GROUP",
    "HAVING", "LIMIT", "OFFSET", "AS", "DISTINCT", "COUNT",
    "SUM", "AVG", "MIN", "MAX", "IF", "EXISTS", "PRIMARY",
    "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "UNIQUE",
    "DEFAULT", "ENGINE", "CHARSET", "DESC", "ASC", "UNION",
    "ALL", "EXPLAIN", "SHOW", "DESCRIBE", "TRUNCATE", "RENAME",
    "GRANT", "REVOKE", "COMMIT", "ROLLBACK", "BEGIN", "TRANSACTION",
    "CASE", "WHEN", "THEN", "ELSE", "END", "ANY", "SOME",
    "TOP", "FULL", "CROSS", "NATURAL", "USING"
)
