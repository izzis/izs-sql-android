package com.sqlclient.android.ui.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sqlclient.android.data.remote.ColumnMetadata
import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.QueryResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataEditorViewModel @Inject constructor(
    private val connectionManager: MariaDbConnectionManager
) : ViewModel() {

    private val _columns = MutableStateFlow<List<ColumnMetadata>>(emptyList())
    val columns: StateFlow<List<ColumnMetadata>> = _columns.asStateFlow()

    private val _rows = MutableStateFlow<List<List<Any?>>>(emptyList())
    val rows: StateFlow<List<List<Any?>>> = _rows.asStateFlow()

    private val _selectedRows = MutableStateFlow<Set<Int>>(emptySet())
    val selectedRows: StateFlow<Set<Int>> = _selectedRows.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _operationSuccess = MutableStateFlow<String?>(null)
    val operationSuccess: StateFlow<String?> = _operationSuccess.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _dataLimit = MutableStateFlow(200)
    val dataLimit: StateFlow<Int> = _dataLimit.asStateFlow()

    private val _totalRowCount = MutableStateFlow(0)
    val totalRowCount: StateFlow<Int> = _totalRowCount.asStateFlow()

    private val _hasMoreData = MutableStateFlow(false)
    val hasMoreData: StateFlow<Boolean> = _hasMoreData.asStateFlow()

    private var currentDatabase: String = ""
    private var currentTable: String = ""
    private var autoIncrementColumn: String? = null
    private var pkColumnIndex: Int = -1
    private var pkColumns: List<Pair<Int, String>> = emptyList()
    private var currentOffset: Int = 0

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    private val _currentQuery = MutableStateFlow<List<String>>(emptyList())
    val currentQuery: StateFlow<List<String>> = _currentQuery.asStateFlow()

    // Quick WHERE filter
    private val _whereInput = MutableStateFlow("")
    val whereInput: StateFlow<String> = _whereInput.asStateFlow()

    private val _activeWhere = MutableStateFlow("")
    val activeWhere: StateFlow<String> = _activeWhere.asStateFlow()

    private val _lastQueryDurationMs = MutableStateFlow<Long?>(null)
    val lastQueryDurationMs: StateFlow<Long?> = _lastQueryDurationMs.asStateFlow()

    private var currentLoadJob: Job? = null

    fun cancelCurrentQuery() {
        currentLoadJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) { try { connectionManager.cancelCurrentQuery() } catch (_: Exception) {} }
        _isLoading.value = false
        _isLoadingMore.value = false
        if (_error.value == null) _error.value = "Cancelled"
    }

    // ORDER BY state
    private val _sortColumn = MutableStateFlow<Int?>(null)
    val sortColumn: StateFlow<Int?> = _sortColumn.asStateFlow()

    private val _sortAsc = MutableStateFlow(true)
    val sortAsc: StateFlow<Boolean> = _sortAsc.asStateFlow()

    // --- Batched staging: OK -> red highlight -> Save -> Confirm -> Execute ---
    // wherePairs is the WHERE clause to identify the row (PKs if available, else full row)
    data class StagedEdit(
        val rowIndex: Int,
        val colIndex: Int,
        val columnName: String,
        val newValue: String,
        val wherePairs: List<Pair<String, Any?>>
    )

    private val _pendingEdits = MutableStateFlow<Map<Pair<Int, Int>, StagedEdit>>(emptyMap())
    val pendingEdits: StateFlow<Map<Pair<Int, Int>, StagedEdit>> = _pendingEdits.asStateFlow()

    private val _pendingDeletes = MutableStateFlow<Set<Int>>(emptySet())
    val pendingDeletes: StateFlow<Set<Int>> = _pendingDeletes.asStateFlow()

    fun hasPendingChanges(): Boolean = _pendingEdits.value.isNotEmpty() || _pendingDeletes.value.isNotEmpty()

    fun isCellPending(rowIndex: Int, colIndex: Int): Boolean = _pendingEdits.value.containsKey(rowIndex to colIndex)

    fun getStagedValue(rowIndex: Int, colIndex: Int): String? = _pendingEdits.value[rowIndex to colIndex]?.newValue

    fun stageEdit(rowIndex: Int, colIndex: Int, columnName: String, newValue: String) {
        val original = _rows.value.getOrNull(rowIndex)?.getOrNull(colIndex)?.toString() ?: ""
        if (newValue == original) {
            _pendingEdits.value = _pendingEdits.value - (rowIndex to colIndex)
        } else {
            val wherePairs = when {
                pkColumns.isNotEmpty() -> pkColumns.map { (idx, name) -> name to _rows.value.getOrNull(rowIndex)?.getOrNull(idx) }
                else -> {
                    // No PK: use all columns as WHERE (best-effort)
                    _columns.value.mapIndexed { idx, col -> col.name to _rows.value.getOrNull(rowIndex)?.getOrNull(idx) }
                }
            }
            if (wherePairs.isEmpty()) {
                _error.value = "Cannot edit: table has no columns/PK info yet. Refresh or try again."
                return
            }
            val edit = StagedEdit(rowIndex, colIndex, columnName, newValue, wherePairs)
            _pendingEdits.value = _pendingEdits.value + ((rowIndex to colIndex) to edit)
        }
        refreshCurrentQueryPreview()
    }

    /** Stage edit for all selected rows in the same column (multi-select batch edit) */
    fun stageEditMultiple(colIndex: Int, columnName: String, newValue: String) {
        val selected = _selectedRows.value
        if (selected.isEmpty()) return
        var edits = _pendingEdits.value
        for (rowIndex in selected) {
            val original = _rows.value.getOrNull(rowIndex)?.getOrNull(colIndex)?.toString() ?: ""
            if (newValue == original) {
                edits = edits - (rowIndex to colIndex)
            } else {
                val wherePairs = when {
                    pkColumns.isNotEmpty() -> pkColumns.map { (idx, name) -> name to _rows.value.getOrNull(rowIndex)?.getOrNull(idx) }
                    else -> _columns.value.mapIndexed { idx, col -> col.name to _rows.value.getOrNull(rowIndex)?.getOrNull(idx) }
                }
                if (wherePairs.isEmpty()) continue
                edits = edits + ((rowIndex to colIndex) to StagedEdit(rowIndex, colIndex, columnName, newValue, wherePairs))
            }
        }
        _pendingEdits.value = edits
        refreshCurrentQueryPreview()
    }

    fun stageDelete(rowIndex: Int) {
        val cur = _pendingDeletes.value.toMutableSet()
        if (cur.contains(rowIndex)) cur.remove(rowIndex) else cur.add(rowIndex)
        _pendingDeletes.value = cur
        refreshCurrentQueryPreview()
    }

    fun clearStaged() {
        _pendingEdits.value = emptyMap()
        _pendingDeletes.value = emptySet()
        refreshCurrentQueryPreview()
    }

    private fun refreshCurrentQueryPreview() {
        val sqls = buildPendingSqls(currentDatabase, currentTable)
        _currentQuery.value = if (sqls.isNotEmpty()) sqls else listOf(_query.value)
    }

    private fun formatWhere(pairs: List<Pair<String, Any?>>): String {
        return pairs.joinToString(" AND ") { (col, v) ->
            if (v == null) "`$col` IS NULL" else "`$col` = ${formatSqlValue(v)}"
        }
    }

    fun buildPendingSqls(database: String = currentDatabase, table: String = currentTable): List<String> {
        if (database.isBlank() || table.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        // Group edits by rowIndex -> single UPDATE per row
        val byRow = _pendingEdits.value.values.groupBy { it.rowIndex }
        for ((_, edits) in byRow) {
            if (edits.isEmpty()) continue
            val first = edits.first()
            val setClause = edits.joinToString(", ") { "`${it.columnName}` = ${formatSqlValue(it.newValue)}" }
            val whereClause = formatWhere(first.wherePairs)
            result.add("UPDATE `$database`.`$table` SET $setClause WHERE $whereClause")
        }
        if (_pendingDeletes.value.isNotEmpty()) {
            // For deletes: if PK exists, use IN for efficiency; else per-row AND
            if (pkColumns.isNotEmpty()) {
                // Check if single PK column -> can use IN
                if (pkColumns.size == 1) {
                    val pkName = pkColumns.first().second
                    val pkIdx = pkColumns.first().first
                    val pkVals = _pendingDeletes.value.mapNotNull { idx -> _rows.value.getOrNull(idx)?.getOrNull(pkIdx)?.let { formatSqlValue(it) } }
                    if (pkVals.isNotEmpty()) {
                        if (pkVals.size == 1) result.add("DELETE FROM `$database`.`$table` WHERE `$pkName` = ${pkVals.first()}")
                        else result.add("DELETE FROM `$database`.`$table` WHERE `$pkName` IN (${pkVals.joinToString(", ")})")
                    }
                } else {
                    // composite PK -> one DELETE per row
                    for (rowIdx in _pendingDeletes.value) {
                        val wherePairs = pkColumns.map { (idx, name) -> name to _rows.value.getOrNull(rowIdx)?.getOrNull(idx) }
                        result.add("DELETE FROM `$database`.`$table` WHERE ${formatWhere(wherePairs)}")
                    }
                }
            } else {
                // No PK: one DELETE per row with full-row WHERE
                for (rowIdx in _pendingDeletes.value) {
                    val wherePairs = _columns.value.mapIndexed { idx, col -> col.name to _rows.value.getOrNull(rowIdx)?.getOrNull(idx) }
                    if (wherePairs.isNotEmpty()) {
                        result.add("DELETE FROM `$database`.`$table` WHERE ${formatWhere(wherePairs)}")
                    }
                }
            }
        }
        return result
    }

    fun commitPending(database: String = currentDatabase, table: String = currentTable, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked — unlock to write"; return }
        val sqls = buildPendingSqls(database, table)
        if (sqls.isEmpty()) return
        viewModelScope.launch {
            _error.value = null
            _isLoading.value = true
            try {
                for (sql in sqls) {
                    _currentQuery.value = _currentQuery.value + sql
                    when (val r = connectionManager.executeQuery(sql)) {
                        is QueryResult.Error -> { _error.value = r.message; return@launch }
                        else -> {}
                    }
                }
                _operationSuccess.value = "${sqls.size} statement(s) executed"
                clearStaged()
                loadData(database, table, force = true)
            } catch (e: Exception) {
                _error.value = "Save failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun buildBaseSql(): String = "SELECT * FROM `$currentDatabase`.`$currentTable`"

    private fun buildBrowseSql(limit: Int = _dataLimit.value, offset: Int = 0): String {
        val whereClause = if (_activeWhere.value.isNotBlank()) " WHERE ${_activeWhere.value}" else ""
        val sortClause = if (_sortColumn.value != null) {
            val colName = _columns.value.getOrNull(_sortColumn.value!!)?.name ?: ""
            if (colName.isNotEmpty()) " ORDER BY `$colName` ${if (_sortAsc.value) "ASC" else "DESC"}" else ""
        } else ""
        return if (offset > 0) "${buildBaseSql()}$whereClause${sortClause} LIMIT $limit OFFSET $offset"
        else "${buildBaseSql()}$whereClause${sortClause} LIMIT $limit"
    }

    fun toggleSort(colIndex: Int) {
        if (_sortColumn.value == colIndex) {
            if (_sortAsc.value) {
                _sortAsc.value = false
            } else {
                // Third tap: remove sort
                _sortColumn.value = null
                _sortAsc.value = true
            }
        } else {
            _sortColumn.value = colIndex
            _sortAsc.value = true
        }
        // Reload with new sort
        currentOffset = 0
        _selectedRows.value = emptySet()
        _rows.value = emptyList()
        val sql = buildBrowseSql()
        _query.value = sql
        _currentQuery.value = _currentQuery.value + sql
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val t0 = SystemClock.elapsedRealtime()
            try {
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        _columns.value = result.columns
                        _rows.value = result.rows
                        _hasMoreData.value = result.rows.size >= _dataLimit.value
                        _hasLoaded.value = true
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load data: ${e.message}"
            } finally {
                _isLoading.value = false
                _lastQueryDurationMs.value = SystemClock.elapsedRealtime() - t0
            }
        }
    }

    private fun isBrowseMode(): Boolean {
        val q = _query.value.trim()
        if (q.isBlank()) return true
        val upper = q.uppercase()
        // browse mode if query starts with SELECT * FROM `db`.`table` (case-insensitive)
        return upper.startsWith("SELECT * FROM")
    }

    fun setWhereInput(s: String) { _whereInput.value = s }

    fun applyWhereFilter() {
        _activeWhere.value = _whereInput.value.trim()
        currentOffset = 0
        _selectedRows.value = emptySet()
        _rows.value = emptyList()
        val sql = buildBrowseSql()
        _query.value = sql
        _currentQuery.value = _currentQuery.value + sql
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val t0 = SystemClock.elapsedRealtime()
            try {
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        _columns.value = result.columns
                        _rows.value = result.rows
                        _hasMoreData.value = result.rows.size >= _dataLimit.value
                        _hasLoaded.value = true
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
                launch { try { loadColumnInfo(currentDatabase, currentTable) } catch (_: Exception) {} }
            } catch (e: Exception) {
                _error.value = "Failed to load data: ${e.message}"
            } finally {
                _isLoading.value = false
                _lastQueryDurationMs.value = SystemClock.elapsedRealtime() - t0
            }
        }
    }

    fun clearWhereFilter() {
        if (_whereInput.value.isEmpty() && _activeWhere.value.isEmpty()) return
        _whereInput.value = ""
        _activeWhere.value = ""
        currentOffset = 0
        _selectedRows.value = emptySet()
        _rows.value = emptyList()
        val sql = buildBrowseSql()
        _query.value = sql
        _currentQuery.value = _currentQuery.value + sql
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val t0 = SystemClock.elapsedRealtime()
            try {
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        _columns.value = result.columns
                        _rows.value = result.rows
                        _hasMoreData.value = result.rows.size >= _dataLimit.value
                        _hasLoaded.value = true
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load data: ${e.message}"
            } finally {
                _isLoading.value = false
                _lastQueryDurationMs.value = SystemClock.elapsedRealtime() - t0
            }
        }
    }

    fun changeLimit(newLimit: Int) {
        _dataLimit.value = newLimit.coerceIn(1, 10000)
        if (currentDatabase.isEmpty() || currentTable.isEmpty()) return
        // If in browse mode (SELECT * FROM ...), reload with WHERE preserved; else re-execute custom query
        if (isBrowseMode()) {
            currentOffset = 0
            _selectedRows.value = emptySet()
            _rows.value = emptyList()
        val sql = buildBrowseSql()
        _query.value = sql
        _currentQuery.value = _currentQuery.value + sql
            viewModelScope.launch {
                _isLoading.value = true
                _error.value = null
                val t0 = SystemClock.elapsedRealtime()
                try {
                    when (val result = connectionManager.executeQuery(sql)) {
                        is QueryResult.Success -> {
                            _columns.value = result.columns
                            _rows.value = result.rows
                            _hasMoreData.value = result.rows.size >= _dataLimit.value
                            _hasLoaded.value = true
                        }
                        is QueryResult.Error -> _error.value = result.message
                        else -> {}
                    }
                } catch (e: Exception) {
                    _error.value = "Failed to load data: ${e.message}"
                } finally {
                    _isLoading.value = false
                    _lastQueryDurationMs.value = SystemClock.elapsedRealtime() - t0
                }
            }
        } else {
            executeCustomQuery()
        }
    }

    /** Manual refresh — invalidate cache and reload current table */
    fun refreshData() {
        if (currentDatabase.isNotEmpty() && currentTable.isNotEmpty()) {
            _hasLoaded.value = false
            loadData(currentDatabase, currentTable, force = true)
        }
    }

    fun loadData(database: String, table: String, force: Boolean = false) {
        if (!force && _hasLoaded.value && currentDatabase == database && currentTable == table && _rows.value.isNotEmpty()) return
        currentDatabase = database
        currentTable = table
        currentOffset = 0
        _selectedRows.value = emptySet()
        _rows.value = emptyList()
        _whereInput.value = ""
        _activeWhere.value = ""
        _sortColumn.value = null
        _sortAsc.value = true

        val sql = "SELECT * FROM $database.$table LIMIT ${_dataLimit.value}"
        _query.value = sql
        _currentQuery.value = listOf(sql)

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val t0 = SystemClock.elapsedRealtime()
            try {
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        _columns.value = result.columns
                        _rows.value = result.rows
                        _hasMoreData.value = result.rows.size >= _dataLimit.value
                        _hasLoaded.value = true
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
                launch {
                    try { loadColumnInfo(database, table) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load data: ${e.message}"
            } finally {
                _isLoading.value = false
                _lastQueryDurationMs.value = SystemClock.elapsedRealtime() - t0
            }
        }
    }

    fun setQuery(newQuery: String) {
        _query.value = newQuery
    }


    fun executeCustomQuery() {
        val sql = _query.value.trim()
        if (sql.isBlank()) return

        currentOffset = 0
        _selectedRows.value = emptySet()
        _rows.value = emptyList()
        _currentQuery.value = listOf(sql)

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val t0 = SystemClock.elapsedRealtime()
            try {
                val limit = _dataLimit.value
                val limitedSql = if (!sql.uppercase().contains("LIMIT")) {
                    "$sql LIMIT $limit"
                } else {
                    sql
                }
                _currentQuery.value = listOf(limitedSql)
                executeWithLimit(limitedSql)
            } catch (e: Exception) {
                _error.value = "Query failed: ${e.message}"
            } finally {
                _isLoading.value = false
                _lastQueryDurationMs.value = SystemClock.elapsedRealtime() - t0
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMoreData.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            val t0 = SystemClock.elapsedRealtime()
            try {
                val limit = _dataLimit.value
                currentOffset += limit
                val sql = if (isBrowseMode()) {
                    buildBrowseSql(limit = limit, offset = currentOffset)
                } else {
                    val baseQuery = _query.value.trim()
                    if (baseQuery.uppercase().contains("LIMIT")) {
                        // Try to append OFFSET if not present
                        if (baseQuery.uppercase().contains("OFFSET")) baseQuery
                        else "$baseQuery OFFSET $currentOffset"
                    } else {
                        "$baseQuery LIMIT $limit OFFSET $currentOffset"
                    }
                }
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        _rows.value = _rows.value + result.rows
                        _hasMoreData.value = result.rows.size >= limit
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Load more failed: ${e.message}"
            } finally {
                _isLoadingMore.value = false
                // For loadMore we keep lastQueryDuration for status bar to reflect incremental load
                val dt = SystemClock.elapsedRealtime() - t0
                if (_lastQueryDurationMs.value == null) _lastQueryDurationMs.value = dt else _lastQueryDurationMs.value = dt
            }
        }
    }

    private suspend fun executeWithLimit(sql: String) {
        val limit = _dataLimit.value
        val limitedSql = if (!sql.uppercase().contains("LIMIT")) {
            "$sql LIMIT $limit"
        } else {
            sql
        }
        _query.value = limitedSql

        when (val result = connectionManager.executeQuery(limitedSql)) {
            is QueryResult.Success -> {
                _columns.value = result.columns
                _rows.value = result.rows
                _hasMoreData.value = result.rows.size >= limit
            }
            is QueryResult.Error -> _error.value = result.message
            else -> {}
        }
    }

    private suspend fun loadColumnInfo(database: String, table: String) {
        autoIncrementColumn = null
        pkColumnIndex = -1
        pkColumns = emptyList()

        val sql = "SHOW FULL COLUMNS FROM `$database`.`$table`"
        _currentQuery.value = _currentQuery.value + sql
        when (val result = connectionManager.executeQuery(sql)) {
            is QueryResult.Success -> {
                val pks = mutableListOf<Pair<Int, String>>()
                result.rows.forEachIndexed { index, row ->
                    val colName = row[0].toString()
                    val isAutoInc = row[5]?.toString()?.contains("auto_increment") == true
                            || row[1].toString().contains("auto_increment")
                    val isPri = row[4].toString().contains("PRI")
                    if (isPri) pks.add(index to colName)
                    if (isAutoInc && autoIncrementColumn == null) {
                        autoIncrementColumn = colName
                        if (pkColumnIndex == -1) pkColumnIndex = index
                    }
                    if (isPri && pkColumnIndex == -1) {
                        pkColumnIndex = index
                    }
                }
                pkColumns = pks
                // fallback: if no PRI but autoInc found, treat it as PK
                if (pkColumns.isEmpty() && autoIncrementColumn != null && pkColumnIndex >= 0) {
                    pkColumns = listOf(pkColumnIndex to autoIncrementColumn!!)
                }
            }
            is QueryResult.Error -> {}
            else -> {}
        }
    }


    fun insertRow(database: String, table: String, values: Map<String, String>, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        viewModelScope.launch {
            _error.value = null
            try {
                val columns = values.keys.joinToString(", ") { "`$it`" }
                val vals = values.values.joinToString(", ") { formatSqlValue(it) }
                val sql = "INSERT INTO `$database`.`$table` ($columns) VALUES ($vals)"
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.UpdateSuccess -> {
                        _operationSuccess.value = "Row inserted"
                        loadData(database, table)
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Insert failed: ${e.message}"
            }
        }
    }

    fun toggleRowSelection(index: Int) {
        if (index == -1) { selectAll(); return }
        if (index == -2) { deselectAll(); return }
        val current = _selectedRows.value.toMutableSet()
        if (current.contains(index)) current.remove(index) else current.add(index)
        _selectedRows.value = current
    }

    fun selectAll() {
        _selectedRows.value = _rows.value.indices.toSet()
    }

    fun deselectAll() {
        _selectedRows.value = emptySet()
    }

    fun clear() {
        _hasLoaded.value = false
        _columns.value = emptyList()
        _rows.value = emptyList()
        _selectedRows.value = emptySet()
        _query.value = ""
        _error.value = null
        _operationSuccess.value = null
        _whereInput.value = ""
        _activeWhere.value = ""
        _lastQueryDurationMs.value = null
        _sortColumn.value = null
        _sortAsc.value = true
        currentDatabase = ""
        currentTable = ""
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSuccess() {
        _operationSuccess.value = null
    }

    suspend fun fetchColumnsForAutocomplete(database: String, table: String): List<String>? {
        val sql = "SHOW COLUMNS FROM `$database`.`$table`"
        _currentQuery.value = _currentQuery.value + sql
        return try {
            when (val result = connectionManager.executeQueryIfFree(sql)) {
                is QueryResult.Success -> result.rows.mapNotNull { it[0]?.toString() }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    fun getPkColumn(): String? = autoIncrementColumn

    fun getPkColumnIndex(): Int = pkColumnIndex

    fun buildUpdateSql(database: String, table: String, pkColumn: String, pkValue: Any?, column: String, newValue: String): String {
        val pkStr = formatSqlValue(pkValue)
        val newValStr = formatSqlValue(newValue)
        return "UPDATE `$database`.`$table` SET `$column` = $newValStr WHERE `$pkColumn` = $pkStr"
    }
    fun buildInsertSql(database: String, table: String, values: Map<String, String>): String {
        val columns = values.keys.joinToString(", ") { "`$it`" }
        val vals = values.values.joinToString(", ") { formatSqlValue(it) }
        return "INSERT INTO `$database`.`$table` ($columns) VALUES ($vals)"
    }
    fun buildDeleteSql(database: String, table: String, pkColumn: String, pkValue: Any?): String {
        val pkStr = formatSqlValue(pkValue)
        return "DELETE FROM `$database`.`$table` WHERE `$pkColumn` = $pkStr"
    }
    fun buildDeleteSelectedSql(database: String, table: String, pkColumn: String): String? {
        val selected = _selectedRows.value
        if (selected.isEmpty()) return null
        val pkValues = selected.mapNotNull { idx -> _rows.value.getOrNull(idx)?.getOrNull(pkColumnIndex)?.let { formatSqlValue(it) } }
        if (pkValues.isEmpty()) return null
        return "DELETE FROM `$database`.`$table` WHERE `$pkColumn` IN (${pkValues.joinToString(", ")})"
    }

    fun isWriteQuery(): Boolean {
        val q = _query.value.trim().uppercase()
        return q.startsWith("INSERT") || q.startsWith("UPDATE") || q.startsWith("DELETE") ||
                q.startsWith("ALTER") || q.startsWith("DROP") || q.startsWith("CREATE") ||
                q.startsWith("TRUNCATE") || q.startsWith("RENAME") || q.startsWith("GRANT") ||
                q.startsWith("REVOKE")
    }

    private fun formatSqlValue(value: Any?): String {
        if (value == null) return "NULL"
        return when (value) {
            is Number -> value.toString()
            is Boolean -> if (value) "1" else "0"
            else -> "'${value.toString().replace("'", "''")}'"
        }
    }
}
