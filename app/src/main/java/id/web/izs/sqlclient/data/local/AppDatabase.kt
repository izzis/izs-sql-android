package id.web.izs.sqlclient.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import id.web.izs.sqlclient.data.local.dao.ConnectionProfileDao
import id.web.izs.sqlclient.data.local.dao.QueryHistoryDao
import id.web.izs.sqlclient.data.local.entity.ConnectionProfileEntity
import id.web.izs.sqlclient.data.local.entity.QueryHistoryEntity

@Database(
    entities = [
        ConnectionProfileEntity::class,
        QueryHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionProfileDao(): ConnectionProfileDao
    abstract fun queryHistoryDao(): QueryHistoryDao
}
