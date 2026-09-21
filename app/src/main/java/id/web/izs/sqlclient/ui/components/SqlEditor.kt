package id.web.izs.sqlclient.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun SqlEditor(
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
    SqlEditorWithAutocomplete(
        query = query,
        onQueryChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        readOnly = readOnly,
        enableAutocomplete = enableAutocomplete,
        databaseNames = databaseNames,
        tableNames = tableNames,
        columnNames = columnNames,
        onFetchExtraColumns = onFetchExtraColumns,
        onFetchTablesForDatabase = onFetchTablesForDatabase
    )
}
