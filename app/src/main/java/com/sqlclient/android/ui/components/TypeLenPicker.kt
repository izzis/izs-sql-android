package com.sqlclient.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sqlclient.android.util.TableSql

/**
 * Split Type (left) + Len (right) picker shared by column dialogs.
 * The Type field is editable with inline autosuggestion: typing filters the
 * base list shown directly under the field (no popup, never covers the
 * keyboard), and any free text (e.g. "INT UNSIGNED", "ENUM('a','b')") is
 * used verbatim as custom. Emits the combined type ("VARCHAR(255)" / "INT").
 */
@Composable
fun TypeLenPicker(
    initialType: String,
    onTypeChange: (String) -> Unit,
    onValidityChange: (Boolean) -> Unit = {}
) {
    val (initBase, initLen) = remember(initialType) { TableSql.splitType(initialType) }
    val initIsCustom = initBase !in TableSql.columnBaseTypes
    var text by remember(initialType) { mutableStateOf(TextFieldValue(if (initIsCustom) initialType else initBase)) }
    var len by remember(initialType) { mutableStateOf(if (initIsCustom) "" else initLen) }
    var focused by remember { mutableStateOf(false) }

    fun matchOf(input: String) =
        TableSql.columnBaseTypes.firstOrNull { it.equals(input.trim(), ignoreCase = true) }

    fun emit(textValue: String = text.text, lenValue: String = len) {
        val match = matchOf(textValue)
        if (match != null) {
            onTypeChange(TableSql.withLength(match, lenValue))
            onValidityChange(TableSql.isValidLength(match, lenValue))
        } else {
            // Free text = custom: used verbatim, Len ignored.
            onTypeChange(textValue.trim())
            onValidityChange(true)
        }
    }

    fun pickSuggestion(t: String) {
        // Cursor to the end so the user can keep typing (e.g. append modifiers).
        text = TextFieldValue(t, selection = TextRange(t.length))
        emit(t, len)
    }

    val query = text.text.trim()
    val match = matchOf(text.text)
    // Len is shown only for list types that accept it; custom and bare types hide it.
    val showLen = match != null && TableSql.takesLength(match)
    val suggestions = if (query.isEmpty()) TableSql.columnBaseTypes.take(6)
    else TableSql.columnBaseTypes.filter { it.contains(query, ignoreCase = true) }
    val showSuggestions = focused && (query.isEmpty() || match == null)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; emit(it.text, len) },
                label = { Text("Type") },
                singleLine = true,
                modifier = (if (showLen) Modifier.weight(1f) else Modifier.fillMaxWidth())
                    .onFocusChanged { focused = it.isFocused }
            )
            if (showLen) {
                OutlinedTextField(
                    value = len, onValueChange = { len = it; emit(text.text, it) },
                    label = { Text("Len") }, singleLine = true,
                    modifier = Modifier.weight(0.6f)
                )
            }
        }
        if (match == null && query.isNotEmpty()) {
            Text(
                "Custom type — used verbatim",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showSuggestions) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (suggestions.isEmpty()) {
                    Text(
                        "No match — keeping \"$query\" as custom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    suggestions.forEach { t ->
                        Column(modifier = Modifier.fillMaxWidth().clickable { pickSuggestion(t) }) {
                            Text(
                                t,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
