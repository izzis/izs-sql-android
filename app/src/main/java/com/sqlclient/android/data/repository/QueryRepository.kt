package com.sqlclient.android.data.repository

import com.sqlclient.android.data.local.dao.QueryHistoryDao
import com.sqlclient.android.data.local.entity.QueryHistoryEntity
import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.QueryResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueryRepository @Inject constructor(
    private val connectionManager: MariaDbConnectionManager,
    private val historyDao: QueryHistoryDao
) {
    suspend fun executeQuery(sql: String): QueryResult {
        return connectionManager.executeQuery(sql)
    }

    suspend fun executeUpdate(sql: String): QueryResult {
        return connectionManager.executeUpdate(sql)
    }

    fun getHistoryByConnection(connectionId: Long): Flow<List<QueryHistoryEntity>> {
        return historyDao.getHistoryByConnection(connectionId)
    }

    fun getHistoryLimited(connectionId: Long, limit: Int = 200): Flow<List<QueryHistoryEntity>> {
        return historyDao.getHistoryByConnectionLimited(connectionId, limit)
    }

    fun searchHistory(connectionId: Long, search: String): Flow<List<QueryHistoryEntity>> {
        return historyDao.searchHistory(connectionId, search)
    }

    fun getFavoritesByConnection(connectionId: Long): Flow<List<QueryHistoryEntity>> {
        return historyDao.getFavoritesByConnection(connectionId)
    }

    fun getFavoritesByConnectionAndDatabase(connectionId: Long, database: String): Flow<List<QueryHistoryEntity>> {
        return historyDao.getFavoritesByConnectionAndDatabase(connectionId, database)
    }

    suspend fun saveToHistory(connectionId: Long, query: String, database: String? = null): Long {
        val id = historyDao.insertHistory(
            QueryHistoryEntity(
                connectionId = connectionId,
                queryText = query,
                database = database
            )
        )
        // prune to keep last 200 history entries (is_favorite=0 only)
        try { historyDao.pruneHistory(connectionId, 200) } catch (_: Exception) {}
        return id
    }

    suspend fun clearHistory(connectionId: Long) {
        historyDao.clearHistory(connectionId)
    }

    suspend fun pruneHistory(connectionId: Long, keep: Int = 200) {
        historyDao.pruneHistory(connectionId, keep)
    }

    suspend fun toggleFavorite(historyId: Long, isFavorite: Boolean) {
        historyDao.toggleFavorite(historyId, isFavorite)
    }

    suspend fun renameHistory(historyId: Long, name: String) {
        historyDao.renameHistory(historyId, name)
    }

    suspend fun updateSavedQuery(id: Long, queryText: String, database: String?, name: String?) {
        historyDao.updateSavedQuery(id, queryText, database, name)
    }

    suspend fun getHistoryById(id: Long): QueryHistoryEntity? {
        return historyDao.getHistoryById(id)
    }

    suspend fun deleteHistory(history: QueryHistoryEntity) {
        historyDao.deleteHistory(history)
    }

    suspend fun deleteHistoryByConnection(connectionId: Long) {
        historyDao.deleteHistoryByConnection(connectionId)
    }
}
