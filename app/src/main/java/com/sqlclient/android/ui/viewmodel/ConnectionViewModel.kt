package com.sqlclient.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.data.remote.ConnectionResult
import com.sqlclient.android.data.remote.SshTunnelManager
import com.sqlclient.android.data.repository.ConnectionRepository
import com.sqlclient.android.util.CredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val credentialStore: CredentialStore,
    private val sshTunnelManager: SshTunnelManager
) : ViewModel() {

    val profiles: StateFlow<List<ConnectionProfileEntity>> = connectionRepository.getAllProfiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _testResult = MutableStateFlow<TestResult>(TestResult.Idle)
    val testResult: StateFlow<TestResult> = _testResult

    private val _sshTestResult = MutableStateFlow<SshTestResult>(SshTestResult.Idle)
    val sshTestResult: StateFlow<SshTestResult> = _sshTestResult

    /** Session lock — controls whether writes are blocked in the current session.
     *  Initialized from profile.isReadonly on connect; toggling does NOT persist to DB.
     *  profile.isReadonly is only the default for next connect. */
    private val _sessionLocked = MutableStateFlow(false)
    val sessionLocked: StateFlow<Boolean> = _sessionLocked.asStateFlow()

    fun setSessionLocked(locked: Boolean) {
        _sessionLocked.value = locked
    }

    fun saveProfile(
        profile: ConnectionProfileEntity,
        password: String,
        sshPassword: String?,
        sshPassphrase: String?
    ) {
        viewModelScope.launch {
            connectionRepository.saveProfile(profile, password, sshPassword, sshPassphrase)
        }
    }

    fun updateProfile(
        profile: ConnectionProfileEntity,
        password: String?,
        sshPassword: String?,
        sshPassphrase: String?
    ) {
        viewModelScope.launch {
            connectionRepository.updateProfile(profile, password, sshPassword, sshPassphrase)
        }
    }

    fun deleteProfile(profile: ConnectionProfileEntity) {
        viewModelScope.launch {
            connectionRepository.deleteProfile(profile)
        }
    }

    suspend fun getProfileById(id: Long): ConnectionProfileEntity? {
        return connectionRepository.getProfileById(id)
    }

    fun testConnection(profile: ConnectionProfileEntity, password: String, sshPassword: String? = null, sshPassphrase: String? = null) {
        viewModelScope.launch {
            _testResult.value = TestResult.Loading
            when (val result = connectionRepository.testConnection(profile, password, sshPassword, sshPassphrase)) {
                is ConnectionResult.Success -> {
                    _testResult.value = TestResult.Success("Connection successful!")
                    try { result.connection.close() } catch (_: Exception) {}
                }
                is ConnectionResult.Error -> {
                    _testResult.value = TestResult.Error(result.message)
                }
            }
        }
    }

    fun testSshConnection(
        sshHost: String,
        sshPort: Int,
        sshUsername: String,
        sshPassword: String?,
        sshKeyPath: String?,
        sshPassphrase: String? = null
    ) {
        viewModelScope.launch {
            _sshTestResult.value = SshTestResult.Loading
            when (val result = sshTunnelManager.testSshConnection(
                sshHost = sshHost,
                sshPort = sshPort,
                sshUsername = sshUsername,
                sshPassword = sshPassword,
                sshKeyPath = sshKeyPath,
                sshPassphrase = sshPassphrase
            )) {
                is SshTunnelManager.SshTestResult.Success -> {
                    _sshTestResult.value = SshTestResult.Success("SSH connection successful!")
                }
                is SshTunnelManager.SshTestResult.Error -> {
                    _sshTestResult.value = SshTestResult.Error(result.message)
                }
            }
        }
    }

    fun connect(profile: ConnectionProfileEntity) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Connecting
            when (val result = connectionRepository.connect(profile)) {
                is ConnectionResult.Success -> {
                    _connectionState.value = ConnectionState.Connected(profile)
                    _sessionLocked.value = profile.isReadonly
                }
                is ConnectionResult.Error -> {
                    _connectionState.value = ConnectionState.Error(result.message)
                }
            }
        }
    }

    fun disconnect() {
        connectionRepository.disconnect()
        _connectionState.value = ConnectionState.Disconnected
        _sessionLocked.value = false
    }

    fun clearTestResult() {
        _testResult.value = TestResult.Idle
    }

    fun clearSshTestResult() {
        _sshTestResult.value = SshTestResult.Idle
    }

    fun clearConnectionError() {
        if (_connectionState.value is ConnectionState.Error) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun hasStoredPassword(profileId: Long): Boolean {
        return credentialStore.hasPassword(profileId)
    }
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val profile: ConnectionProfileEntity) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

sealed class TestResult {
    data object Idle : TestResult()
    data object Loading : TestResult()
    data class Success(val message: String) : TestResult()
    data class Error(val message: String) : TestResult()
}

sealed class SshTestResult {
    data object Idle : SshTestResult()
    data object Loading : SshTestResult()
    data class Success(val message: String) : SshTestResult()
    data class Error(val message: String) : SshTestResult()
}
