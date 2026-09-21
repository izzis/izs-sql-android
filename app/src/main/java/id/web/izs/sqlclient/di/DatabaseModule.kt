package id.web.izs.sqlclient.di

import android.content.Context
import androidx.room.Room
import id.web.izs.sqlclient.data.local.AppDatabase
import id.web.izs.sqlclient.data.local.dao.ConnectionProfileDao
import id.web.izs.sqlclient.data.local.dao.QueryHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sql_client_database"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    @Provides
    fun provideConnectionProfileDao(database: AppDatabase): ConnectionProfileDao {
        return database.connectionProfileDao()
    }

    @Provides
    fun provideQueryHistoryDao(database: AppDatabase): QueryHistoryDao {
        return database.queryHistoryDao()
    }
}
