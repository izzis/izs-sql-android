package com.sqlclient.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sqlclient.android.data.local.dao.ConnectionProfileDao
import com.sqlclient.android.data.local.dao.QueryHistoryDao
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.data.local.entity.QueryHistoryEntity

@Database(
    entities = [
        ConnectionProfileEntity::class,
        QueryHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionProfileDao(): ConnectionProfileDao
    abstract fun queryHistoryDao(): QueryHistoryDao
}
