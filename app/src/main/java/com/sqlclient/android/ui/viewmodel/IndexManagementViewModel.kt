package com.sqlclient.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.QueryResult
import com.sqlclient.android.data.repository.QueryRepository
import com.sqlclient.android.data.remote.model.IndexInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IndexManagementViewModel @Inject constructor(
    private val connectionManager: MariaDbConnectionManager,
    private val queryRepository: QueryRepository
) : ViewModel() {

    private val _indexes = MutableStateFlow<List<IndexInfo>>(emptyList())
    val indexes: StateFlow<List<IndexInfo>> = _indexes.asStateFlow()

    private val _columns = MutableStateFlow<List<String>>(emptyList())
    val columns: StateFlow<List<String>> = _columns.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _operationSuccess = MutableStateFlow<String?>(null)
    val operationSuccess: StateFlow<String?> = _operationSuccess.asStateFlow()

    private val _currentQuery = MutableStateFlow<List<String>>(emptyList())
    val currentQuery: StateFlow<List<String>> = _currentQuery.asStateFlow()

    /** Full page (re)entry — clears the query log. Loaders below only append. */
    fun resetQueryLog() {
        _currentQuery.value = emptyList()
    }

    fun loadIndexes(database: String, table: String) {
        _currentQuery.value = _currentQuery.value + "SHOW INDEX FROM `$database`.`$table`"
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                when (val result = connectionManager.executeQuery("SHOW INDEX FROM `$database`.`$table`")) {
                    is QueryResult.Success -> {
                        val indexMap = mutableMapOf<String, MutableList<Pair<String, Int>>>()
                        val indexTypes = mutableMapOf<String, String>()
                        val indexCardinality = mutableMapOf<String, Long>()

                        result.rows.forEach { row ->
                            val keyName = row[2].toString()
                            val columnName = row[4].toString()
                            val nonUnique = (row[1] as? Number)?.toInt() ?: 0
                            val indexType = row.getOrNull(10)?.toString() ?: "BTREE"
                            val cardinality = (row.getOrNull(6) as? Number)?.toLong()

                            indexMap.getOrPut(keyName) { mutableListOf() }.add(columnName to nonUnique)
                            indexTypes[keyName] = indexType
                            if (cardinality != null) indexCardinality[keyName] = cardinality
                        }

                        _indexes.value = indexMap.map { (name, columns) ->
                            IndexInfo(
                                name = name,
                                columns = columns.map { it.first },
                                isUnique = columns.first().second == 0,
                                type = indexTypes[name] ?: "BTREE",
                                cardinality = indexCardinality[name]
                            )
                        }
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load indexes: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadColumns(database: String, table: String) {
        viewModelScope.launch {
            try {
                val sql = "SHOW COLUMNS FROM `$database`.`$table`"
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        _columns.value = result.rows.map { row ->
                            row[0].toString()
                        }
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load columns: ${e.message}"
            }
        }
    }

    fun createIndex(name: String, columns: List<String>, unique: Boolean, database: String, table: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val uniqueStr = if (unique) "UNIQUE " else ""
                val colList = columns.joinToString(", ") { "`$it`" }
                val sql = "CREATE ${uniqueStr}INDEX `$name` ON `$database`.`$table` ($colList)"
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.UpdateSuccess -> {
                        recordWrite(sql, database)
                        _operationSuccess.value = "Index '$name' created"
                        loadIndexes(database, table)
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to create index: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dropIndex(name: String, database: String, table: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "DROP INDEX `$name` ON `$database`.`$table`"
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.UpdateSuccess -> {
                        recordWrite(sql, database)
                        _operationSuccess.value = "Index '$name' dropped"
                        loadIndexes(database, table)
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to drop index: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun modifyColumn(database: String, table: String, columnDef: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "ALTER TABLE `$database`.`$table` MODIFY COLUMN $columnDef"
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.UpdateSuccess -> {
                        recordWrite(sql, database)
                        _operationSuccess.value = "Column modified"
                        loadColumns(database, table)
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to modify column: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addColumn(database: String, table: String, columnDef: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "ALTER TABLE `$database`.`$table` ADD COLUMN $columnDef"
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.UpdateSuccess -> {
                        recordWrite(sql, database)
                        _operationSuccess.value = "Column added"
                        loadColumns(database, table)
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to add column: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dropColumn(database: String, table: String, columnName: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "ALTER TABLE `$database`.`$table` DROP COLUMN `$columnName`"
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.UpdateSuccess -> {
                        recordWrite(sql, database)
                        _operationSuccess.value = "Column dropped"
                        loadColumns(database, table)
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to drop column: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun buildCreateIndexSql(name: String, columns: List<String>, unique: Boolean, database: String, table: String): String {
        val uniqueStr = if (unique) "UNIQUE " else ""
        val colList = columns.joinToString(", ") { "`$it`" }
        return "CREATE ${uniqueStr}INDEX `$name` ON `$database`.`$table` ($colList)"
    }
    fun buildDropIndexSql(name: String, database: String, table: String): String = "DROP INDEX `$name` ON `$database`.`$table`"
    fun buildModifyColumnSql(database: String, table: String, columnDef: String): String = "ALTER TABLE `$database`.`$table` MODIFY COLUMN $columnDef"
    fun buildAddColumnSql(database: String, table: String, columnDef: String): String = "ALTER TABLE `$database`.`$table` ADD COLUMN $columnDef"
    fun buildDropColumnSql(database: String, table: String, columnName: String): String = "ALTER TABLE `$database`.`$table` DROP COLUMN `$columnName`"

    /** Persist a user-confirmed write to History panel (success only, like SQL editor). */
    private fun recordWrite(sql: String, database: String) {
        val profileId = connectionManager.currentProfileId ?: return
        viewModelScope.launch {
            try { queryRepository.saveToHistory(profileId, sql, database) } catch (_: Exception) {}
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSuccess() {
        _operationSuccess.value = null
    }
}
