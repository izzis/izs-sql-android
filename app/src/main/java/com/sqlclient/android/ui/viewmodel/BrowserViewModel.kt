package com.sqlclient.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sqlclient.android.data.PrivilegeResolver
import com.sqlclient.android.data.local.entity.QueryHistoryEntity
import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.QueryResult
import com.sqlclient.android.data.remote.model.ColumnInfo
import com.sqlclient.android.data.remote.model.DatabaseInfo
import com.sqlclient.android.data.remote.model.IndexInfo
import com.sqlclient.android.data.remote.model.PrivilegeSet
import com.sqlclient.android.data.repository.QueryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val connectionManager: MariaDbConnectionManager,
    private val privilegeResolver: PrivilegeResolver,
    private val queryRepository: QueryRepository
) : ViewModel() {

    private val _databases = MutableStateFlow<List<DatabaseInfo>>(emptyList())
    val databases: StateFlow<List<DatabaseInfo>> = _databases

    // Filtered by privilege — what UI should show (sidebar + main). Falls back to _databases if no grants loaded.
    private val _visibleDatabases = MutableStateFlow<List<DatabaseInfo>>(emptyList())
    val visibleDatabases: StateFlow<List<DatabaseInfo>> = _visibleDatabases

    private val _tables = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val tables: StateFlow<Map<String, List<String>>> = _tables

    private val _columns = MutableStateFlow<Map<String, List<ColumnInfo>>>(emptyMap())
    val columns: StateFlow<Map<String, List<ColumnInfo>>> = _columns

    private val _indexes = MutableStateFlow<Map<String, List<IndexInfo>>>(emptyMap())
    val indexes: StateFlow<Map<String, List<IndexInfo>>> = _indexes

    private val _tableSizes = MutableStateFlow<Map<String, String>>(emptyMap())
    val tableSizes: StateFlow<Map<String, String>> = _tableSizes

    private val _expandedDatabases = MutableStateFlow<Set<String>>(emptySet())
    val expandedDatabases: StateFlow<Set<String>> = _expandedDatabases

    private val _expandedTables = MutableStateFlow<Set<String>>(emptySet())
    val expandedTables: StateFlow<Set<String>> = _expandedTables

    private val _selectedTable = MutableStateFlow<Pair<String, String>?>(null)
    val selectedTable: StateFlow<Pair<String, String>?> = _selectedTable

    // Keep selected database for TableList main view (manual refresh per DB)
    private val _selectedDatabase = MutableStateFlow<String?>(null)
    val selectedDatabase: StateFlow<String?> = _selectedDatabase

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshingTables = MutableStateFlow(false)
    val isRefreshingTables: StateFlow<Boolean> = _isRefreshingTables

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _loadingDatabases = MutableStateFlow<Set<String>>(emptySet())
    val loadingDatabases: StateFlow<Set<String>> = _loadingDatabases

    private val _privilegeSet = MutableStateFlow<PrivilegeSet?>(null)
    val privilegeSet: StateFlow<PrivilegeSet?> = _privilegeSet

    private val _hasLoadedDatabases = MutableStateFlow(false)
    val hasLoadedDatabases: StateFlow<Boolean> = _hasLoadedDatabases

    private val _currentQuery = MutableStateFlow<List<String>>(emptyList())
    val currentQuery: StateFlow<List<String>> = _currentQuery

    // --- Query history (persistent, is_favorite=0, limit 200) ---
    private val _history = MutableStateFlow<List<QueryHistoryEntity>>(emptyList())
    val history: StateFlow<List<QueryHistoryEntity>> = _history

    private val _historySearch = MutableStateFlow("")
    val historySearch: StateFlow<String> = _historySearch

    private var historyJob: Job? = null

    fun setHistorySearch(q: String) {
        _historySearch.value = q
        val cid = _historyConnectionId ?: return
        loadHistory(cid)
    }

    private var _historyConnectionId: Long? = null

    fun loadHistory(connectionId: Long) {
        _historyConnectionId = connectionId
        historyJob?.cancel()
        val search = _historySearch.value.trim()
        historyJob = viewModelScope.launch {
            val flow = if (search.isBlank()) {
                queryRepository.getHistoryLimited(connectionId, 200)
            } else {
                queryRepository.searchHistory(connectionId, search)
            }
            flow.collect { _history.value = it }
        }
    }

    fun clearHistory(connectionId: Long) {
        viewModelScope.launch {
            queryRepository.clearHistory(connectionId)
        }
    }

    fun deleteHistoryItem(entity: QueryHistoryEntity) {
        viewModelScope.launch {
            queryRepository.deleteHistory(entity)
        }
    }

    // Keep active panel in VM so it survives navigation to user_detail and back
    enum class BrowserPanel { TABLE_INFO, USERS, HISTORY }
    private val _activePanel = MutableStateFlow(BrowserPanel.TABLE_INFO)
    val activePanel: StateFlow<BrowserPanel> = _activePanel
    fun setActivePanel(panel: BrowserPanel) { _activePanel.value = panel }

    private val systemSchemas = setOf("information_schema", "performance_schema", "sys")

    // Keep filtered flag for table search on main screen
    private val _tableSearchQuery = MutableStateFlow("")
    val tableSearchQuery: StateFlow<String> = _tableSearchQuery

    fun setTableSearchQuery(q: String) { _tableSearchQuery.value = q }

    /**
     * Manual refresh only. Call from TopBar Refresh or first open when cache empty.
     * Does NOT auto-run on init; caller must invoke.
     */
    fun loadDatabases(force: Boolean = false) {
        if (!force && _hasLoadedDatabases.value && _databases.value.isNotEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _currentQuery.value = listOf("SHOW GRANTS FOR CURRENT_USER()", "SHOW DATABASES")
                when (val result = connectionManager.executeQuery("SHOW DATABASES")) {
                    is QueryResult.Success -> {
                        val all = result.rows.map { DatabaseInfo(name = it[0].toString()) }
                        val filtered = all.filterNot { it.name.lowercase() in systemSchemas }
                        _databases.value = filtered
                        _hasLoadedDatabases.value = true
                        // Try to load privileges (best-effort, no error spam)
                        val grants = privilegeResolver.loadGrants(force = force)
                        _privilegeSet.value = grants
                        applyVisibleDatabases()
                    }
                    is QueryResult.Error -> {
                        val msg = result.message
                        if (msg.contains("Access denied", ignoreCase = true)) {
                            _databases.value = emptyList()
                            _visibleDatabases.value = emptyList()
                        }
                        _error.value = msg
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load databases: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshDatabases() {
        privilegeResolver.invalidate()
        _hasLoadedDatabases.value = false
        loadDatabases(force = true)
    }

    private fun applyVisibleDatabases() {
        val all = _databases.value.map { it.name }
        val visible = privilegeResolver.filterDatabases(all, _privilegeSet.value)
        _visibleDatabases.value = visible.map { DatabaseInfo(it) }
    }

    /**
     * Expand/collapse. On expand, lazy-load tables if not yet cached (fixes empty expand on first tap).
     * Manual refresh still via [refreshTables] / [refreshDatabases].
     */
    fun toggleDatabase(database: String) {
        val current = _expandedDatabases.value.toMutableSet()
        val expanding = !current.contains(database)
        if (current.contains(database)) {
            current.remove(database)
        } else {
            current.add(database)
        }
        _expandedDatabases.value = current
        if (expanding && !_tables.value.containsKey(database) && !_loadingDatabases.value.contains(database)) {
            requestTables(database)
        }
    }

    fun selectDatabase(database: String) {
        _selectedDatabase.value = database
        _tableSearchQuery.value = ""
        if (!_tables.value.containsKey(database)) {
            requestTables(database)
        } else {
            // Tables cached — lazily populate sizes once per DB, then keep in cache for back
            val keys = _tables.value[database] ?: emptyList()
            if (keys.isNotEmpty() && keys.none { _tableSizes.value.containsKey("$database.$it") }) {
                refreshTableSizes(database)
            }
        }
    }

    fun requestTables(database: String) {
        if (_tables.value.containsKey(database) || _loadingDatabases.value.contains(database)) return
        _loadingDatabases.value = _loadingDatabases.value + database
        _isRefreshingTables.value = true
        viewModelScope.launch {
            _currentQuery.value = _currentQuery.value + "SHOW TABLES IN `$database`"
        try {
                when (val result = connectionManager.executeQuery("SHOW TABLES IN `$database`")) {
                    is QueryResult.Success -> {
                        var tableNames = result.rows.map { it[0].toString() }
                        tableNames = privilegeResolver.filterTables(database, tableNames, _privilegeSet.value)
                        _tables.value = _tables.value + (database to tableNames)
                        // Best-effort: populate sizes once per DB, then reuse cache on back
                        if (tableNames.isNotEmpty() && tableNames.none { _tableSizes.value.containsKey("$database.$it") }) {
                            refreshTableSizes(database)
                        }
                    }
                    is QueryResult.Error -> {
                        val msg = result.message
                        if (msg.contains("Access denied", ignoreCase = true) || msg.contains("denied", ignoreCase = true)) {
                            _databases.value = _databases.value.filterNot { it.name == database }
                            applyVisibleDatabases()
                            _tables.value = _tables.value - database
                        } else {
                            _error.value = msg
                            _tables.value = _tables.value + (database to emptyList())
                        }
                    }
                    else -> {
                        _tables.value = _tables.value + (database to emptyList())
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load tables: ${e.message}"
                _tables.value = _tables.value + (database to emptyList())
            } finally {
                _loadingDatabases.value = _loadingDatabases.value - database
                _isRefreshingTables.value = false
            }
        }
    }

    fun refreshTables(database: String) {
        _tables.value = _tables.value - database
        _tableSizes.value = _tableSizes.value.filterKeys { !it.startsWith("$database.") }
        _columns.value = _columns.value.filterKeys { !it.startsWith("$database.") }
        _indexes.value = _indexes.value.filterKeys { !it.startsWith("$database.") }
        requestTables(database)
    }

    fun getCachedTables(database: String): List<String>? {
        val tables = _tables.value[database]
        return if (_tables.value.containsKey(database)) tables else null
    }

    suspend fun requestTablesSync(database: String): List<String>? {
        if (_tables.value.containsKey(database)) return _tables.value[database]
        val sql = "SHOW TABLES IN `$database`"
        _currentQuery.value = _currentQuery.value + sql
        return try {
            when (val result = connectionManager.executeQueryIfFree(sql)) {
                is QueryResult.Success -> {
                    val names = result.rows.map { it[0].toString() }
                    _tables.value = _tables.value + (database to names)
                    names
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    /**
     * Table expand — lazy load columns/indexes only on first expand (tap arrow).
     * Cached per db.table; subsequent expands use cache. Back preserves cache.
     */
    fun toggleTable(database: String, table: String) {
        val key = "$database.$table"
        val current = _expandedTables.value.toMutableSet()
        if (current.contains(key)) {
            current.remove(key)
        } else {
            current.add(key)
            if (_columns.value[key] == null) {
                loadColumns(database, table)
            }
            if (_indexes.value[key] == null) {
                loadIndexes(database, table)
            }
        }
        _expandedTables.value = current
    }

    fun selectTable(database: String, table: String) {
        _selectedTable.value = Pair(database, table)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else -> "${bytes / (1024 * 1024 * 1024)}GB"
        }
    }

    private fun loadColumns(database: String, table: String) {
        viewModelScope.launch {
            try {
                val key = "$database.$table"
                if (_columns.value[key] != null) return@launch
                _currentQuery.value = _currentQuery.value + "SHOW FULL COLUMNS FROM `$database`.`$table`"
                when (val result = connectionManager.executeQuery("SHOW FULL COLUMNS FROM `$database`.`$table`")) {
                    is QueryResult.Success -> {
                        val columnList = result.rows.map { row ->
                            ColumnInfo(
                                name = row[0].toString(),
                                type = row[1].toString(),
                                nullable = row[3].toString() == "YES",
                                defaultValue = row[5]?.toString(),
                                isPrimaryKey = row[4].toString().contains("PRI"),
                                isAutoIncrement = row[5]?.toString()?.contains("auto_increment") == true || row[1].toString().contains("auto_increment"),
                                comment = row[8]?.toString(),
                                maxLength = null,
                                keyType = row[4].toString()
                            )
                        }
                        _columns.value = _columns.value + (key to columnList)
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load columns: ${e.message}"
            }
        }
    }

    private fun loadIndexes(database: String, table: String) {
        viewModelScope.launch {
            try {
                val key = "$database.$table"
                if (_indexes.value[key] != null) return@launch
                _currentQuery.value = _currentQuery.value + "SHOW INDEX FROM `$database`.`$table`"
                when (val result = connectionManager.executeQuery("SHOW INDEX FROM `$database`.`$table`")) {
                    is QueryResult.Success -> {
                        val indexMap = mutableMapOf<String, MutableList<Pair<String, Int>>>()
                        val indexTypes = mutableMapOf<String, String>()
                        result.rows.forEach { row ->
                            val keyName = row[2].toString()
                            val columnName = row[4].toString()
                            val nonUnique = (row[1] as? Number)?.toInt() ?: row[1].toString().toIntOrNull() ?: 0
                            val indexType = row.getOrNull(10)?.toString() ?: "BTREE"
                            indexMap.getOrPut(keyName) { mutableListOf() }.add(columnName to nonUnique)
                            indexTypes[keyName] = indexType
                        }
                        val indexList = indexMap.map { (name, cols) ->
                            IndexInfo(
                                name = name,
                                columns = cols.map { it.first },
                                isUnique = cols.first().second == 0,
                                type = indexTypes[name] ?: "BTREE"
                            )
                        }
                        _indexes.value = _indexes.value + (key to indexList)
                    }
                    else -> {}
                }
            } catch (_: Exception) {}
        }
    }

    fun refreshAllDatabases() {
        _databases.value = emptyList()
        _visibleDatabases.value = emptyList()
        _tables.value = emptyMap()
        _tableSizes.value = emptyMap()
        _hasLoadedDatabases.value = false
        _currentQuery.value = emptyList()
        loadDatabases(force = true)
    }

    fun refreshDatabase(database: String) {
        if (database == "__ALL__") { refreshAllDatabases(); return }
        _tables.value = _tables.value - database
        _tableSizes.value = _tableSizes.value.filterKeys { !it.startsWith("$database.") }
        _columns.value = _columns.value.filterKeys { !it.startsWith("$database.") }
        _indexes.value = _indexes.value.filterKeys { !it.startsWith("$database.") }
        if (_expandedDatabases.value.contains(database)) {
            requestTables(database)
        }
    }

    fun refreshTableSizes(database: String) {
        viewModelScope.launch {
            try {
                if (!_tables.value.containsKey(database)) return@launch
                val sql = "SELECT table_name, data_length + index_length AS total_bytes FROM information_schema.TABLES WHERE table_schema = '$database' ORDER BY table_name"
                _currentQuery.value = _currentQuery.value + sql
                val result = connectionManager.executeQueryIfFree(sql) ?: connectionManager.executeQuery(sql)
                when (result) {
                    is QueryResult.Success -> {
                        val sizes = mutableMapOf<String, String>()
                        result.rows.forEach { row ->
                            val tableName = row[0].toString()
                            val totalBytes = (row[1] as? Number)?.toLong() ?: 0L
                            sizes["$database.$tableName"] = formatSize(totalBytes)
                        }
                        _tableSizes.value = _tableSizes.value + sizes
                    }
                    else -> {}
                }
            } catch (_: Exception) {}
        }
    }

    fun clearAll() {
        _databases.value = emptyList()
        _visibleDatabases.value = emptyList()
        _tables.value = emptyMap()
        _columns.value = emptyMap()
        _indexes.value = emptyMap()
        _tableSizes.value = emptyMap()
        _expandedDatabases.value = emptySet()
        _expandedTables.value = emptySet()
        _selectedTable.value = null
        _selectedDatabase.value = null
        _activePanel.value = BrowserPanel.TABLE_INFO
        _loadingDatabases.value = emptySet()
        _isLoading.value = false
        _isRefreshingTables.value = false
        _error.value = null
        _searchQuery.value = ""
        _tableSearchQuery.value = ""
        _hasLoadedDatabases.value = false
        _privilegeSet.value = null
        privilegeResolver.invalidate()
    }

    fun clearSelectedDatabase() {
        _selectedDatabase.value = null
        _selectedTable.value = null
        _tableSearchQuery.value = ""
    }

    fun clearSelectedTable() {
        _selectedTable.value = null
    }

    fun clearError() {
        _error.value = null
    }
}
