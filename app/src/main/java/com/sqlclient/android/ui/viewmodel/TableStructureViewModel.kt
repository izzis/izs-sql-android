package com.sqlclient.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.QueryResult
import com.sqlclient.android.data.remote.model.ColumnInfo
import com.sqlclient.android.data.remote.model.IndexInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TableStructureViewModel @Inject constructor(
    private val connectionManager: MariaDbConnectionManager
) : ViewModel() {

    private val _columns = MutableStateFlow<List<ColumnInfo>>(emptyList())
    val columns: StateFlow<List<ColumnInfo>> = _columns

    private val _createTable = MutableStateFlow<String?>(null)
    val createTable: StateFlow<String?> = _createTable

    private val _indexes = MutableStateFlow<List<IndexInfo>>(emptyList())
    val indexes: StateFlow<List<IndexInfo>> = _indexes

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentQuery = MutableStateFlow("")
    val currentQuery: StateFlow<String> = _currentQuery

    fun loadStructure(database: String, table: String) {
        _currentQuery.value = "SHOW FULL COLUMNS FROM `$database`.`$table`"
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _columns.value = emptyList()
            _createTable.value = null
            _indexes.value = emptyList()
            try {
                // Columns first — unlock UI as soon as they arrive so the tab is usable
                loadColumns(database, table)
            } catch (e: Exception) {
                _error.value = "Failed to load structure: ${e.message}"
            } finally {
                _isLoading.value = false
            }
            // DDL + indexes are lower priority and share the same queryMutex;
            // run them in parallel in background so they don't block the Columns tab.
            launch {
                try { loadCreateTable(database, table) } catch (_: Exception) {}
            }
            launch {
                try { loadIndexes(database, table) } catch (_: Exception) {}
            }
        }
    }

    private suspend fun loadColumns(database: String, table: String) {
        when (val result = connectionManager.executeQuery("SHOW FULL COLUMNS FROM `$database`.`$table`")) {
            is QueryResult.Success -> {
                _columns.value = result.rows.map { row ->
                    ColumnInfo(
                        name = row[0].toString(),
                        type = row[1].toString(),
                        nullable = row[3].toString() == "YES",
                        defaultValue = row[5]?.toString(),
                        isPrimaryKey = row[4].toString().contains("PRI"),
                        isAutoIncrement = row[1].toString().contains("auto_increment") ||
                                row[5]?.toString()?.contains("auto_increment") == true,
                        comment = row.getOrNull(8)?.toString(),
                        maxLength = null
                    )
                }
            }
            is QueryResult.Error -> _error.value = result.message
            else -> {}
        }
    }

    private suspend fun loadCreateTable(database: String, table: String) {
        when (val result = connectionManager.executeQuery("SHOW CREATE TABLE `$database`.`$table`")) {
            is QueryResult.Success -> {
                _createTable.value = result.rows.firstOrNull()?.getOrNull(1)?.toString()
            }
            is QueryResult.Error -> _error.value = result.message
            else -> {}
        }
    }

    private suspend fun loadIndexes(database: String, table: String) {
        when (val result = connectionManager.executeQuery("SHOW INDEX FROM `$database`.`$table`")) {
            is QueryResult.Success -> {
                val indexMap = mutableMapOf<String, MutableList<Pair<String, Int>>>()
                val indexTypes = mutableMapOf<String, String>()

                result.rows.forEach { row ->
                    val keyName = row[2].toString()
                    val columnName = row[4].toString()
                    val nonUnique = (row[1] as? Number)?.toInt() ?: 0
                    val indexType = row.getOrNull(10)?.toString() ?: "BTREE"

                    indexMap.getOrPut(keyName) { mutableListOf() }.add(columnName to nonUnique)
                    indexTypes[keyName] = indexType
                }

                _indexes.value = indexMap.map { (name, columns) ->
                    IndexInfo(
                        name = name,
                        columns = columns.map { it.first },
                        isUnique = columns.first().second == 0,
                        type = indexTypes[name] ?: "BTREE"
                    )
                }
            }
            is QueryResult.Error -> _error.value = result.message
            else -> {}
        }
    }

    fun clearError() {
        _error.value = null
    }
}
