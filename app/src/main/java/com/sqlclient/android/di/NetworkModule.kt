package com.sqlclient.android.di

import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.SshTunnelManager
import com.sqlclient.android.util.CredentialStore
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
