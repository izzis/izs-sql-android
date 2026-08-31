package com.sqlclient.android.di

import com.sqlclient.android.data.local.dao.ConnectionProfileDao
import com.sqlclient.android.data.local.dao.QueryHistoryDao
import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.SshTunnelManager
import com.sqlclient.android.data.repository.ConnectionRepository
import com.sqlclient.android.data.repository.DatabaseRepository
import com.sqlclient.android.data.repository.QueryRepository
import com.sqlclient.android.util.CredentialStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideConnectionRepository(
        profileDao: ConnectionProfileDao,
        connectionManager: MariaDbConnectionManager,
        credentialStore: CredentialStore
    ): ConnectionRepository {
        return ConnectionRepository(profileDao, connectionManager, credentialStore)
    }

    @Provides
    @Singleton
    fun provideDatabaseRepository(
        connectionManager: MariaDbConnectionManager
    ): DatabaseRepository {
        return DatabaseRepository(connectionManager)
    }

    @Provides
    @Singleton
    fun provideQueryRepository(
        connectionManager: MariaDbConnectionManager,
        historyDao: QueryHistoryDao
    ): QueryRepository {
        return QueryRepository(connectionManager, historyDao)
    }
}
