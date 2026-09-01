package com.sqlclient.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sqlclient.android.data.local.entity.QueryHistoryEntity
import com.sqlclient.android.data.remote.ColumnMetadata
import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.QueryResult
import com.sqlclient.android.data.repository.QueryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    private val _queryTabs = MutableStateFlow<List<QueryTab>>(listOf(QueryTab(id = 1, query = "SELECT * FROM ")))
    val queryTabs: StateFlow<List<QueryTab>> = _queryTabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(1L)
    val activeTabId: StateFlow<Long> = _activeTabId.asStateFlow()

    private val _queryResult = MutableStateFlow<QueryResultState>(QueryResultState.Idle)
    val queryResult: StateFlow<QueryResultState> = _queryResult.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _currentProfileId = MutableStateFlow<Long?>(null)
    val currentProfileId: StateFlow<Long?> = _currentProfileId.asStateFlow()

    private var nextTabId = 2L

    private val _currentQuery = MutableStateFlow<List<String>>(emptyList())
    val currentQuery: StateFlow<List<String>> = _currentQuery.asStateFlow()

    private val _savedQueries = MutableStateFlow<List<QueryHistoryEntity>>(emptyList())
    val savedQueries: StateFlow<List<QueryHistoryEntity>> = _savedQueries.asStateFlow()

    private var initializedForProfile: Long? = null

    fun setCurrentProfileId(profileId: Long) {
        _currentProfileId.value = profileId
        if (initializedForProfile != profileId) {
            loadFavorites(profileId)
        }
    }

    fun addTab() {
        val newTab = QueryTab(id = nextTabId++, query = "")
        _queryTabs.value = _queryTabs.value + newTab
        _activeTabId.value = newTab.id
    }

    fun closeTab(tabId: Long) {
        val tabs = _queryTabs.value.toMutableList()
        if (tabs.size <= 1) return

        val index = tabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            tabs.removeAt(index)
            _queryTabs.value = tabs

            if (_activeTabId.value == tabId) {
                _activeTabId.value = tabs.getOrElse(index.coerceAtMost(tabs.lastIndex)) { tabs.first() }.id
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

    fun saveFavorite(query: String) {
        val profileId = _currentProfileId.value ?: return
        viewModelScope.launch {
            val id = queryRepository.saveToHistory(profileId, query)
            queryRepository.toggleFavorite(id, true)
            loadFavorites(profileId)
        }
    }

    fun loadFavorites(profileId: Long? = null) {
        val id = profileId ?: _currentProfileId.value ?: return
        viewModelScope.launch {
            queryRepository.getFavoritesByConnection(id).collect { favorites ->
                _savedQueries.value = favorites
            }
        }
    }

    fun openSavedQuery(entity: QueryHistoryEntity) {
        val newTab = QueryTab(id = nextTabId++, query = entity.queryText)
        _queryTabs.value = _queryTabs.value + newTab
        _activeTabId.value = newTab.id
    }

    fun deleteSavedQuery(entity: QueryHistoryEntity) {
        viewModelScope.launch {
            queryRepository.deleteHistory(entity)
            val profileId = _currentProfileId.value ?: return@launch
            loadFavorites(profileId)
        }
    }

    fun clearAll() {
        _queryTabs.value = listOf(QueryTab(id = 1, query = "SELECT * FROM "))
        _activeTabId.value = 1
        _queryResult.value = QueryResultState.Idle
        _isExecuting.value = false
        _currentProfileId.value = null
        initializedForProfile = null
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

    fun setInitialQuery(database: String, table: String) {
        val profileId = _currentProfileId.value
        if (initializedForProfile == profileId && _queryTabs.value.isNotEmpty()) return
        val query = if (table == "_") "SELECT * FROM `$database`." else "SELECT * FROM `$database`.`$table`"
        _currentQuery.value = listOf(query)
        _queryTabs.value = listOf(QueryTab(id = 1, query = query))
        _activeTabId.value = 1
        nextTabId = 2
        initializedForProfile = profileId
    }
}

data class QueryTab(
    val id: Long,
    val query: String,
    val title: String = "Query ${id}"
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
