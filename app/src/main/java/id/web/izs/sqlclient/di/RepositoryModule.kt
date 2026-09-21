package id.web.izs.sqlclient.di

import id.web.izs.sqlclient.data.local.dao.ConnectionProfileDao
import id.web.izs.sqlclient.data.local.dao.QueryHistoryDao
import id.web.izs.sqlclient.data.remote.MariaDbConnectionManager
import id.web.izs.sqlclient.data.remote.SshTunnelManager
import id.web.izs.sqlclient.data.repository.ConnectionRepository
import id.web.izs.sqlclient.data.repository.DatabaseRepository
import id.web.izs.sqlclient.data.repository.QueryRepository
import id.web.izs.sqlclient.util.CredentialStore
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
