package com.sqlclient.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sqlclient.android.data.local.entity.QueryHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QueryHistoryDao {
    @Query("SELECT * FROM query_history WHERE connection_id = :connectionId ORDER BY executed_at DESC")
    fun getHistoryByConnection(connectionId: Long): Flow<List<QueryHistoryEntity>>

    @Query("SELECT * FROM query_history WHERE connection_id = :connectionId AND is_favorite = 1 ORDER BY executed_at DESC")
    fun getFavoritesByConnection(connectionId: Long): Flow<List<QueryHistoryEntity>>

    @Query("SELECT * FROM query_history WHERE connection_id = :connectionId AND is_favorite = 1 AND (database = :database OR database IS NULL) ORDER BY executed_at DESC")
    fun getFavoritesByConnectionAndDatabase(connectionId: Long, database: String): Flow<List<QueryHistoryEntity>>

    @Query("SELECT * FROM query_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): QueryHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: QueryHistoryEntity): Long

    @Update
    suspend fun updateHistory(history: QueryHistoryEntity)

    @Delete
    suspend fun deleteHistory(history: QueryHistoryEntity)

    @Query("DELETE FROM query_history WHERE connection_id = :connectionId")
    suspend fun deleteHistoryByConnection(connectionId: Long)

    @Query("UPDATE query_history SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE query_history SET name = :name WHERE id = :id")
    suspend fun renameHistory(id: Long, name: String)
}
