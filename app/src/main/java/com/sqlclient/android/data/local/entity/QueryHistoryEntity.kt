package com.sqlclient.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "query_history",
    foreignKeys = [
        ForeignKey(
            entity = ConnectionProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["connection_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("connection_id")]
)
data class QueryHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "connection_id")
    val connectionId: Long,

    @ColumnInfo(name = "database")
    val database: String? = null,

    @ColumnInfo(name = "query_text")
    val queryText: String,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "name")
    val name: String? = null,

    @ColumnInfo(name = "executed_at")
    val executedAt: Long = System.currentTimeMillis()
)
