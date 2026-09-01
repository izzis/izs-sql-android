package com.sqlclient.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sqlclient.android.data.local.entity.QueryHistoryEntity
import com.sqlclient.android.data.remote.ColumnMetadata
import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.QueryResult
import com.sqlclient.android.data.repository.QueryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueryViewModel @Inject constructor(
    private val connectionManager: MariaDbConnectionManager,
    private val queryRepository: QueryRepository
) : ViewModel() {

    private val _queryTabs = MutableStateFlow<List<QueryTab>>(listOf(QueryTab(id = 1, query = "", database = null)))
    val queryTabs: StateFlow<List<QueryTab>> = _queryTabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(1L)
    val activeTabId: StateFlow<Long> = _activeTabId.asStateFlow()

    fun getTabsForDatabase(database: String): List<QueryTab> = _queryTabs.value.filter { it.database == database }
    fun ensureTabForDatabase(database: String) {
        if (_queryTabs.value.none { it.database == database }) {
            val usedNumbers = _queryTabs.value.mapNotNull { tab ->
                Regex("^Query (\\d+)$").find(tab.title)?.groupValues?.get(1)?.toIntOrNull()
            }.toSet()
            val nextNumber = (1..usedNumbers.size + 1).firstOrNull { it !in usedNumbers } ?: usedNumbers.size + 1
            val newTab = QueryTab(id = nextTabId++, query = "", title = "Query $nextNumber", database = database)
            _queryTabs.value = _queryTabs.value + newTab
            _activeTabId.value = newTab.id
        } else if (_queryTabs.value.find { it.id == _activeTabId.value }?.database != database) {
            _queryTabs.value.find { it.database == database }?.let { _activeTabId.value = it.id }
        }
    }

    private val _queryResult = MutableStateFlow<QueryResultState>(QueryResultState.Idle)
    val queryResult: StateFlow<QueryResultState> = _queryResult.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _currentProfileId = MutableStateFlow<Long?>(null)
    val currentProfileId: StateFlow<Long?> = _currentProfileId.asStateFlow()

    private var _currentDatabase: String? = null

    private var nextTabId = 2L

    private val _currentQuery = MutableStateFlow<List<String>>(emptyList())
    val currentQuery: StateFlow<List<String>> = _currentQuery.asStateFlow()

    private val _savedQueries = MutableStateFlow<List<QueryHistoryEntity>>(emptyList())
    val savedQueries: StateFlow<List<QueryHistoryEntity>> = _savedQueries.asStateFlow()

    private var _favoritesJob: Job? = null

    private val _allFavorites = MutableStateFlow<List<QueryHistoryEntity>>(emptyList())
    val allFavorites: StateFlow<List<QueryHistoryEntity>> = _allFavorites.asStateFlow()
    private var _allFavoritesJob: Job? = null

    fun setCurrentProfileId(profileId: Long) {
        val prev = _currentProfileId.value
        _currentProfileId.value = profileId
        if (prev != profileId) {
            loadFavorites(profileId)
        }
    }

    fun addTab(database: String? = _currentDatabase) {
        val usedNumbers = _queryTabs.value.filter { it.database == database }.mapNotNull { tab ->
            Regex("^Query (\\d+)$").find(tab.title)?.groupValues?.get(1)?.toIntOrNull()
        }.toSet()
        val nextNumber = (1..usedNumbers.size + 1).firstOrNull { it !in usedNumbers } ?: usedNumbers.size + 1
        val newTab = QueryTab(id = nextTabId++, query = "", title = "Query $nextNumber", database = database)
        _queryTabs.value = _queryTabs.value + newTab
        _activeTabId.value = newTab.id
    }

    fun closeTab(tabId: Long) {
        val tabs = _queryTabs.value.toMutableList()
        val closing = tabs.find { it.id == tabId } ?: return
        val dbTabs = tabs.filter { it.database == closing.database }
        if (dbTabs.size <= 1 && tabs.size <= 1) return
        // if closing last tab for its database, keep at least one for that db
        val allowClose = dbTabs.size > 1 || tabs.size > dbTabs.size
        if (!allowClose) return

        val index = tabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            tabs.removeAt(index)
            _queryTabs.value = tabs

            if (_activeTabId.value == tabId) {
                // prefer tab from same database
                val sameDb = tabs.filter { it.database == closing.database }
                _activeTabId.value = if (sameDb.isNotEmpty()) {
                    sameDb.getOrElse(index.coerceAtMost(sameDb.lastIndex)) { sameDb.first() }.id
                } else {
                    tabs.getOrElse(index.coerceAtMost(tabs.lastIndex)) { tabs.first() }.id
                }
            }
        }
    }

    fun setActiveTab(tabId: Long) {
        _activeTabId.value = tabId
    }

    fun updateQuery(tabId: Long, query: String) {
        _queryTabs.value = _queryTabs.value.map { tab ->
            if (tab.id == tabId) tab.copy(query = query) else tab
        }
    }

    fun updateTabTitle(tabId: Long, title: String) {
        _queryTabs.value = _queryTabs.value.map { tab ->
            if (tab.id == tabId) tab.copy(title = title) else tab
        }
    }

    fun useDatabase(database: String) {
        _currentDatabase = database
        val sql = "USE `$database`"
        _currentQuery.value = listOf(sql)
        viewModelScope.launch {
            try { connectionManager.executeQueryIfFree(sql) } catch (_: Exception) {}
        }
    }

    fun executeQuery(isLocked: Boolean = false) {
        val activeTab = _queryTabs.value.find { it.id == _activeTabId.value } ?: return
        val query = activeTab.query.trim()

        if (query.isBlank()) return
        if (isLocked && isWriteQuery()) { _queryResult.value = QueryResultState.Error("Locked \u2014 unlock to write"); return }

        _currentQuery.value = listOf(query)
        viewModelScope.launch {
            _isExecuting.value = true
            _queryResult.value = QueryResultState.Loading

            try {
                when (val result = connectionManager.executeQuery(query)) {
                    is QueryResult.Success -> {
                        _queryResult.value = QueryResultState.Success(
                            columns = result.columns,
                            rows = result.rows,
                            rowCount = result.rowCount
                        )
                        saveToHistory(query)
                    }
                    is QueryResult.UpdateSuccess -> {
                        _queryResult.value = QueryResultState.UpdateSuccess(result.affectedRows)
                        saveToHistory(query)
                    }
                    is QueryResult.Error -> {
                        _queryResult.value = QueryResultState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _queryResult.value = QueryResultState.Error("Execution failed: ${e.message}")
            } finally {
                _isExecuting.value = false
            }
        }
    }

    private fun saveToHistory(query: String) {
        val profileId = _currentProfileId.value ?: return
        viewModelScope.launch {
            queryRepository.saveToHistory(profileId, query)
        }
    }

    fun saveFavorite(query: String, name: String? = null, database: String? = null) {
        val profileId = _currentProfileId.value ?: return
        viewModelScope.launch {
            val id = queryRepository.saveToHistory(profileId, query, database)
            queryRepository.toggleFavorite(id, true)
            if (!name.isNullOrBlank()) {
                queryRepository.renameHistory(id, name)
            }
            loadFavorites(profileId, database)
            loadAllFavorites(profileId)
        }
    }

    fun saveFavoriteForActiveTab(name: String?, database: String?) {
        val tab = _queryTabs.value.find { it.id == _activeTabId.value } ?: return
        val query = tab.query
        if (query.isBlank()) return
        val savedId = tab.savedQueryId
        if (savedId != null) {
            updateSavedQuery(savedId, query, name, database)
        } else {
            // create new and bind to tab
            val profileId = _currentProfileId.value ?: return
            viewModelScope.launch {
                val id = queryRepository.saveToHistory(profileId, query, database)
                queryRepository.toggleFavorite(id, true)
                if (!name.isNullOrBlank()) {
                    queryRepository.renameHistory(id, name)
                }
                _queryTabs.value = _queryTabs.value.map {
                    if (it.id == tab.id) it.copy(savedQueryId = id, title = name?.takeIf { n -> n.isNotBlank() } ?: it.title) else it
                }
                loadFavorites(profileId, database)
                loadAllFavorites(profileId)
            }
        }
    }

    fun loadFavorites(profileId: Long? = null, database: String? = null) {
        val id = profileId ?: _currentProfileId.value ?: return
        _favoritesJob?.cancel()
        _favoritesJob = viewModelScope.launch {
            val flow = if (database != null) {
                queryRepository.getFavoritesByConnectionAndDatabase(id, database)
            } else {
                queryRepository.getFavoritesByConnection(id)
            }
            flow.collect { favorites ->
                _savedQueries.value = favorites
            }
        }
    }

    fun loadAllFavorites(profileId: Long) {
        _allFavoritesJob?.cancel()
        _allFavoritesJob = viewModelScope.launch {
            queryRepository.getFavoritesByConnection(profileId).collect {
                _allFavorites.value = it
            }
        }
    }

    fun openSavedQuery(entity: QueryHistoryEntity) {
        val title = entity.name ?: "Query"
        val db = entity.database ?: _currentDatabase
        val newTab = QueryTab(id = nextTabId++, query = entity.queryText, title = title, savedQueryId = entity.id, database = db)
        _queryTabs.value = _queryTabs.value + newTab
        _activeTabId.value = newTab.id
    }

    fun updateSavedQuery(savedQueryId: Long, query: String, name: String?, database: String? = null) {
        viewModelScope.launch {
            val finalName = name?.takeIf { it.isNotBlank() }
            queryRepository.updateSavedQuery(savedQueryId, query, database, finalName)
            val pid = _currentProfileId.value ?: return@launch
            loadFavorites(pid, _currentDatabase)
            loadAllFavorites(pid)
            // keep tab title in sync
            val tab = _queryTabs.value.find { it.savedQueryId == savedQueryId }
            if (tab != null) {
                val newTitle = finalName ?: "Query"
                updateTabTitle(tab.id, newTitle)
            }
        }
    }

    fun deleteSavedQuery(entity: QueryHistoryEntity) {
        viewModelScope.launch {
            queryRepository.deleteHistory(entity)
            // unlink any tabs that were opened from this saved query so next save creates new
            _queryTabs.value = _queryTabs.value.map { tab ->
                if (tab.savedQueryId == entity.id) tab.copy(savedQueryId = null) else tab
            }
            val profileId = _currentProfileId.value ?: return@launch
            loadFavorites(profileId, _currentDatabase)
            loadAllFavorites(profileId)
        }
    }

    fun renameSavedQuery(entity: QueryHistoryEntity, newName: String) {
        viewModelScope.launch {
            queryRepository.renameHistory(entity.id, newName)
            val profileId = _currentProfileId.value ?: return@launch
            loadFavorites(profileId, _currentDatabase)
            loadAllFavorites(profileId)
        }
    }

    suspend fun saveFavoriteDirect(profileId: Long, query: String, database: String?, name: String?) {
        val id = queryRepository.saveToHistory(profileId, query, database)
        queryRepository.toggleFavorite(id, true)
        if (!name.isNullOrBlank()) queryRepository.renameHistory(id, name)
    }

    fun clearAll() {
        _queryTabs.value = listOf(QueryTab(id = 1, query = ""))
        _activeTabId.value = 1
        _queryResult.value = QueryResultState.Idle
        _isExecuting.value = false
        _currentProfileId.value = null
    }

    fun isWriteQuery(): Boolean {
        val activeTab = _queryTabs.value.find { it.id == _activeTabId.value } ?: return false
        val query = activeTab.query.trim().uppercase()
        return query.startsWith("INSERT") || query.startsWith("UPDATE") || query.startsWith("DELETE") ||
                query.startsWith("ALTER") || query.startsWith("DROP") || query.startsWith("CREATE") ||
                query.startsWith("TRUNCATE") || query.startsWith("RENAME") || query.startsWith("GRANT") ||
                query.startsWith("REVOKE")
    }

    fun getActiveQueryText(): String {
        val activeTab = _queryTabs.value.find { it.id == _activeTabId.value } ?: return ""
        return activeTab.query.trim()
    }

    suspend fun fetchColumns(database: String, table: String): List<String>? {
        val sql = "SHOW COLUMNS FROM `$database`.`$table`"
        _currentQuery.value = _currentQuery.value + sql
        return try {
            when (val result = connectionManager.executeQueryIfFree(sql)) {
                is QueryResult.Success -> result.rows.mapNotNull { it[0]?.toString() }
                else -> null
            }
        } catch (_: Exception) { null }
    }
}

data class QueryTab(
    val id: Long,
    val query: String,
    val title: String = "Query ${id}",
    val savedQueryId: Long? = null,
    val database: String? = null
)

sealed class QueryResultState {
    data object Idle : QueryResultState()
    data object Loading : QueryResultState()
    data class Success(
        val columns: List<ColumnMetadata>,
        val rows: List<List<Any?>>,
        val rowCount: Int
    ) : QueryResultState()

    data class UpdateSuccess(val affectedRows: Int) : QueryResultState()
    data class Error(val message: String) : QueryResultState()
}
