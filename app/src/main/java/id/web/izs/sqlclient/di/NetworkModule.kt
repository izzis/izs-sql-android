package id.web.izs.sqlclient.di

import id.web.izs.sqlclient.data.remote.MariaDbConnectionManager
import id.web.izs.sqlclient.data.remote.SshTunnelManager
import id.web.izs.sqlclient.util.CredentialStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSshTunnelManager(): SshTunnelManager {
        return SshTunnelManager()
    }

    @Provides
    @Singleton
    fun provideMariaDbConnectionManager(
        credentialStore: CredentialStore,
        sshTunnelManager: SshTunnelManager
    ): MariaDbConnectionManager {
        return MariaDbConnectionManager(credentialStore, sshTunnelManager)
    }
}
