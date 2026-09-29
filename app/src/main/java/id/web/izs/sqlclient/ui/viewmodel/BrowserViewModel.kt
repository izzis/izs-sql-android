package id.web.izs.sqlclient.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.web.izs.sqlclient.data.PrivilegeResolver
import id.web.izs.sqlclient.data.local.entity.QueryHistoryEntity
import id.web.izs.sqlclient.data.remote.MariaDbConnectionManager
import id.web.izs.sqlclient.data.remote.QueryResult
import id.web.izs.sqlclient.data.remote.model.ColumnInfo
import id.web.izs.sqlclient.data.remote.model.DatabaseInfo
import id.web.izs.sqlclient.data.remote.model.IndexInfo
import id.web.izs.sqlclient.data.remote.model.PrivilegeSet
import id.web.izs.sqlclient.data.repository.QueryRepository
import id.web.izs.sqlclient.util.QueryLogEntry
import id.web.izs.sqlclient.util.SqlUtil
import id.web.izs.sqlclient.util.TableSql
import id.web.izs.sqlclient.util.withStatement
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

    // Databases whose last SHOW TABLES failed. Kept separate from _tables so an empty
    // list still means "really no tables" while these stay retryable on expand/refresh.
    private val _tableLoadFailed = MutableStateFlow<Set<String>>(emptySet())
    val tableLoadFailed: StateFlow<Set<String>> = _tableLoadFailed

    // Tables whose last metadata query failed. _columns/_indexes stay null on failure so the
    // next expand retries them — these markers only tell the UI to stop spinning forever.
    private val _columnsFailed = MutableStateFlow<Set<String>>(emptySet())
    val columnsFailed: StateFlow<Set<String>> = _columnsFailed

    private val _indexesFailed = MutableStateFlow<Set<String>>(emptySet())
    val indexesFailed: StateFlow<Set<String>> = _indexesFailed

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

    private val _currentQuery = MutableStateFlow<List<QueryLogEntry>>(emptyList())
    val currentQuery: StateFlow<List<QueryLogEntry>> = _currentQuery

    /** Drops the whole log — page entry and the bar's clear button. Refresh keeps it. */
    fun resetQueryLog() {
        _currentQuery.value = emptyList()
    }

    init {
        viewModelScope.launch {
            // One collector, one flow: a statement this layer composed gets its own line, a
            // failure marks the line the caller already wrote. The order between them is the
            // log (a 1046 before the re-select and the retry it caused), so it is the flow's
            // to promise — two flows would leave two collectors nothing to order by.
            connectionManager.queryStatements.collect { statement ->
                _currentQuery.value = _currentQuery.value.withStatement(statement)
            }
        }
    }

    private val _operationSuccess = MutableStateFlow<String?>(null)
    val operationSuccess: StateFlow<String?> = _operationSuccess
    fun clearSuccess() { _operationSuccess.value = null }

    /** Persist a user-confirmed write to History panel (success only, like SQL editor). */
    private fun recordWrite(sql: String, database: String) {
        val profileId = connectionManager.currentProfileId ?: return
        viewModelScope.launch {
            try { queryRepository.saveToHistory(profileId, sql, database) } catch (_: Exception) {}
        }
    }

    /**
     * Executes one table-DDL statement. Contract: [sql] must classify as a write
     * ([SqlUtil.isWriteQuery]) so callers always route through preview + confirm + lock.
     */
    private fun runTableWrite(
        database: String,
        sql: String,
        successMessage: String,
        isLocked: Boolean
    ) {
        if (isLocked) { _error.value = "Locked — unlock to write"; return }
        if (!SqlUtil.isWriteQuery(sql)) { _error.value = "Refused: not a write query"; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Error -> _error.value = result.message
                    else -> {
                        recordWrite(sql, database)
                        _operationSuccess.value = successMessage
                        refreshTables(database)
                    }
                }
            } catch (e: Exception) {
                _error.value = "Table operation failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createTable(database: String, table: String, columns: List<TableSql.NewColumnSpec>, engine: String, isLocked: Boolean = false) {
        val sql = try {
            TableSql.buildCreateTableSql(database, table, columns, engine)
        } catch (e: IllegalArgumentException) { _error.value = e.message; return }
        runTableWrite(database, sql, "Table '$table' created", isLocked)
    }

    fun dropTable(database: String, table: String, isLocked: Boolean = false) {
        runTableWrite(database, TableSql.buildDropTableSql(database, table), "Table '$table' dropped", isLocked)
    }

    fun renameTable(database: String, oldTable: String, newTable: String, isLocked: Boolean = false) {
        val sql = try {
            TableSql.buildRenameTableSql(database, oldTable, newTable)
        } catch (e: IllegalArgumentException) { _error.value = e.message; return }
        runTableWrite(database, sql, "Table renamed to '$newTable'", isLocked)
    }

    fun truncateTable(database: String, table: String, isLocked: Boolean = false) {
        runTableWrite(database, TableSql.buildTruncateTableSql(database, table), "Table '$table' truncated", isLocked)
    }

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
                _currentQuery.value = _currentQuery.value + QueryLogEntry("SHOW DATABASES")
                when (val result = connectionManager.executeQuery("SHOW DATABASES")) {
                    is QueryResult.Success -> {
                        val all = result.rows.map { DatabaseInfo(name = it[0].toString()) }
                        val filtered = all.filterNot { it.name.lowercase() in systemSchemas }
                        _databases.value = filtered
                        _hasLoadedDatabases.value = true
                        // Try to load privileges (best-effort, no error spam). The resolver only
                        // reaches the server while its cache is cold — log the query it will run.
                        if (force || privilegeResolver.getCached() == null) {
                            _currentQuery.value = _currentQuery.value + QueryLogEntry("SHOW GRANTS")
                        }
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
        // Re-run SHOW TABLES for the expanded databases; ones already cached are skipped
        // by the guard in requestTables, so only the failed (or never fetched) ones run.
        _expandedDatabases.value.forEach { requestTables(it) }
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
        val failed = _tableLoadFailed.value.contains(database)
        if (expanding && (!_tables.value.containsKey(database) || failed) && !_loadingDatabases.value.contains(database)) {
            requestTables(database)
        }
    }

    fun selectDatabase(database: String) {
        _selectedDatabase.value = database
        _tableSearchQuery.value = ""
        if (!_tables.value.containsKey(database) || _tableLoadFailed.value.contains(database)) {
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
        val failed = _tableLoadFailed.value.contains(database)
        if ((_tables.value.containsKey(database) && !failed) || _loadingDatabases.value.contains(database)) return
        _loadingDatabases.value = _loadingDatabases.value + database
        _isRefreshingTables.value = true
        viewModelScope.launch {
            _currentQuery.value = _currentQuery.value + QueryLogEntry("SHOW TABLES IN `$database`")
        try {
                when (val result = connectionManager.executeQuery("SHOW TABLES IN `$database`")) {
                    is QueryResult.Success -> {
                        var tableNames = result.rows.map { it[0].toString() }
                        tableNames = privilegeResolver.filterTables(database, tableNames, _privilegeSet.value)
                        _tables.value = _tables.value + (database to tableNames)
                        _tableLoadFailed.value = _tableLoadFailed.value - database
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
                            _tableLoadFailed.value = _tableLoadFailed.value - database
                        } else {
                            _error.value = msg
                            _tables.value = _tables.value + (database to emptyList())
                            _tableLoadFailed.value = _tableLoadFailed.value + database
                        }
                    }
                    else -> {
                        _tables.value = _tables.value + (database to emptyList())
                        _tableLoadFailed.value = _tableLoadFailed.value + database
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load tables: ${e.message}"
                _tables.value = _tables.value + (database to emptyList())
                _tableLoadFailed.value = _tableLoadFailed.value + database
            } finally {
                _loadingDatabases.value = _loadingDatabases.value - database
                _isRefreshingTables.value = false
            }
        }
    }

    /**
     * Drops a database's cached sizes/columns/indexes and immediately reloads the metadata for
     * the tables still expanded. Without the reload those rows keep a null cache and show a
     * spinner forever, until the user collapses and re-expands each table by hand.
     */
    private fun refreshTableMetadata(database: String) {
        val prefix = "$database."
        _tableSizes.value = _tableSizes.value.filterKeys { !it.startsWith(prefix) }
        _columns.value = _columns.value.filterKeys { !it.startsWith(prefix) }
        _indexes.value = _indexes.value.filterKeys { !it.startsWith(prefix) }
        _columnsFailed.value = _columnsFailed.value.filterNot { it.startsWith(prefix) }.toSet()
        _indexesFailed.value = _indexesFailed.value.filterNot { it.startsWith(prefix) }.toSet()
        _expandedTables.value
            .filter { it.startsWith(prefix) }
            .forEach { key ->
                val table = key.removePrefix(prefix)
                loadColumns(database, table)
                loadIndexes(database, table)
            }
    }

    fun refreshTables(database: String) {
        _tables.value = _tables.value - database
        refreshTableMetadata(database)
        requestTables(database)
    }

    fun getCachedTables(database: String): List<String>? {
        val tables = _tables.value[database]
        return if (_tables.value.containsKey(database)) tables else null
    }

    suspend fun requestTablesSync(database: String): List<String>? {
        if (_tables.value.containsKey(database)) return _tables.value[database]
        val sql = "SHOW TABLES IN `$database`"
        _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
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
        val key = "$database.$table"
        viewModelScope.launch {
            try {
                if (_columns.value[key] != null) return@launch
                _columnsFailed.value = _columnsFailed.value - key
                _currentQuery.value = _currentQuery.value + QueryLogEntry("SHOW FULL COLUMNS FROM `$database`.`$table`")
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
                        _columnsFailed.value = _columnsFailed.value - key
                    }
                    is QueryResult.Error -> {
                        _error.value = result.message
                        _columnsFailed.value = _columnsFailed.value + key
                    }
                    else -> {
                        _columnsFailed.value = _columnsFailed.value + key
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load columns: ${e.message}"
                _columnsFailed.value = _columnsFailed.value + key
            }
        }
    }

    private fun loadIndexes(database: String, table: String) {
        val key = "$database.$table"
        viewModelScope.launch {
            try {
                if (_indexes.value[key] != null) return@launch
                _indexesFailed.value = _indexesFailed.value - key
                _currentQuery.value = _currentQuery.value + QueryLogEntry("SHOW INDEX FROM `$database`.`$table`")
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
                        _indexesFailed.value = _indexesFailed.value - key
                    }
                    is QueryResult.Error -> {
                        _error.value = result.message
                        _indexesFailed.value = _indexesFailed.value + key
                    }
                    else -> {
                        _indexesFailed.value = _indexesFailed.value + key
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load indexes: ${e.message}"
                _indexesFailed.value = _indexesFailed.value + key
            }
        }
    }

    fun refreshAllDatabases() {
        _databases.value = emptyList()
        _visibleDatabases.value = emptyList()
        _tables.value = emptyMap()
        _tableSizes.value = emptyMap()
        _hasLoadedDatabases.value = false
        loadDatabases(force = true)
    }

    fun refreshDatabase(database: String) {
        if (database == "__ALL__") { refreshAllDatabases(); return }
        _tables.value = _tables.value - database
        refreshTableMetadata(database)
        if (_expandedDatabases.value.contains(database)) {
            requestTables(database)
        }
    }

    fun refreshTableSizes(database: String) {
        viewModelScope.launch {
            try {
                if (!_tables.value.containsKey(database)) return@launch
                val sql = "SELECT table_name, data_length + index_length AS total_bytes FROM information_schema.TABLES WHERE table_schema = '$database' ORDER BY table_name"
                _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
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
        _tableLoadFailed.value = emptySet()
        _columnsFailed.value = emptySet()
        _indexesFailed.value = emptySet()
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
