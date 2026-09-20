package com.sqlclient.android.di

import android.content.Context
import androidx.room.Room
import com.sqlclient.android.data.local.AppDatabase
import com.sqlclient.android.data.local.dao.ConnectionProfileDao
import com.sqlclient.android.data.local.dao.QueryHistoryDao
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
