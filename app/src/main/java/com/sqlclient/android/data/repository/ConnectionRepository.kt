package com.sqlclient.android.data.repository

import com.sqlclient.android.data.local.dao.ConnectionProfileDao
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.data.remote.ConnectionResult
import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.util.CredentialStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val profileDao: ConnectionProfileDao,
    private val connectionManager: MariaDbConnectionManager,
    private val credentialStore: CredentialStore
) {
    fun getAllProfiles(): Flow<List<ConnectionProfileEntity>> = profileDao.getAllProfiles()

    suspend fun getProfileById(id: Long): ConnectionProfileEntity? = profileDao.getProfileById(id)

    fun getProfileByIdFlow(id: Long): Flow<ConnectionProfileEntity?> = profileDao.getProfileByIdFlow(id)

    suspend fun saveProfile(
        profile: ConnectionProfileEntity,
        password: String,
        sshPassword: String? = null,
        sshPassphrase: String? = null
    ): Long {
        val id = profileDao.insertProfile(profile)
        val profileId = if (id == 0L) profile.id else id

        if (password.isNotBlank()) {
            credentialStore.savePassword(profileId, password)
        }

        if (profile.useSshTunnel) {
            sshPassword?.let { credentialStore.saveSshPassword(profileId, it) }
            sshPassphrase?.let { credentialStore.saveSshPassphrase(profileId, it) }
        }

        return profileId
    }

    suspend fun updateProfile(
        profile: ConnectionProfileEntity,
        password: String? = null,
        sshPassword: String? = null,
        sshPassphrase: String? = null
    ) {
        profileDao.updateProfile(profile)

        // Only save password if non-null and non-blank — preserves stored value when user leaves blank
        if (!password.isNullOrBlank()) {
            credentialStore.savePassword(profile.id, password)
        }

        if (profile.useSshTunnel) {
            if (!sshPassword.isNullOrBlank()) {
                credentialStore.saveSshPassword(profile.id, sshPassword)
            }
            if (!sshPassphrase.isNullOrBlank()) {
                credentialStore.saveSshPassphrase(profile.id, sshPassphrase)
            }
        }
    }

    suspend fun deleteProfile(profile: ConnectionProfileEntity) {
        credentialStore.deleteAllCredentials(profile.id)
        profileDao.deleteProfile(profile)
    }

    suspend fun testConnection(profile: ConnectionProfileEntity, password: String, sshPassword: String? = null, sshPassphrase: String? = null): ConnectionResult {
        val actualPassword = if (password.isBlank() && profile.id > 0) {
            credentialStore.getPassword(profile.id) ?: ""
        } else {
            password
        }
        if (actualPassword.isBlank()) {
            return ConnectionResult.Error("Password is required")
        }
        val testProfile = profile.copy(id = 999999)
        credentialStore.savePassword(999999, actualPassword)
        // Snapshot SSH creds for tunnel (form values take precedence over stored)
        if (profile.useSshTunnel) {
            val sshPwd = sshPassword ?: credentialStore.getSshPassword(profile.id)
            val sshPhrase = sshPassphrase ?: credentialStore.getSshPassphrase(profile.id)
            sshPwd?.let { credentialStore.saveSshPassword(999999, it) }
            sshPhrase?.let { credentialStore.saveSshPassphrase(999999, it) }
        }
        val result = connectionManager.connect(testProfile)
        credentialStore.deletePassword(999999)
        credentialStore.deleteSshPassword(999999)
        credentialStore.deleteSshPassphrase(999999)
        connectionManager.disconnect()
        return result
    }

    suspend fun connect(profile: ConnectionProfileEntity): ConnectionResult {
        // If no password stored yet, error
        val password = credentialStore.getPassword(profile.id)
            ?: return ConnectionResult.Error("Password not found. Please save the connection first.")
        return connectionManager.connect(profile)
    }

    fun disconnect() {
        connectionManager.disconnect()
    }

    fun isConnected(): Boolean = connectionManager.isConnected()

    suspend fun ping(): Boolean = connectionManager.ping()

    suspend fun getProfileCount(): Int = profileDao.getProfileCount()
}
