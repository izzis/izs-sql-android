package com.sqlclient.android.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.data.remote.ConnectionResult
import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.SshTunnelManager
import com.sqlclient.android.data.repository.ConnectionRepository
import com.sqlclient.android.util.CredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val connectionRepository: ConnectionRepository,
    private val credentialStore: CredentialStore,
    private val sshTunnelManager: SshTunnelManager,
    private val connectionManager: MariaDbConnectionManager
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

    private val _sessionLocked = MutableStateFlow(false)
    val sessionLocked: StateFlow<Boolean> = _sessionLocked.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    private val _reconnectMessage = MutableStateFlow<String?>(null)
    val reconnectMessage: StateFlow<String?> = _reconnectMessage.asStateFlow()

    private val _connectingProfileId = MutableStateFlow<Long?>(null)
    val connectingProfileId: StateFlow<Long?> = _connectingProfileId.asStateFlow()

    private val _connectionMessage = MutableStateFlow("")
    val connectionMessage: StateFlow<String> = _connectionMessage.asStateFlow()

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
            _connectingProfileId.value = profile.id
            _connectionMessage.value = if (profile.useSshTunnel) "Opening SSH tunnel..." else "Connecting to database..."
            _connectionState.value = ConnectionState.Connecting
            when (val result = connectionRepository.connect(profile) { msg ->
                _connectionMessage.value = msg
            }) {
                is ConnectionResult.Success -> {
                    _connectionMessage.value = "Connected!"
                    _connectionState.value = ConnectionState.Connected(profile)
                    _sessionLocked.value = profile.isReadonly
                }
                is ConnectionResult.Error -> {
                    _connectionMessage.value = ""
                    _connectingProfileId.value = null
                    _connectionState.value = ConnectionState.Error(result.message)
                }
            }
        }
    }

    fun reconnect() {
        val current = _connectionState.value
        if (current !is ConnectionState.Connected) return
        val profile = current.profile
        viewModelScope.launch {
            try { connectionManager.cancelCurrentQuery() } catch (_: Exception) {}
            _isReconnecting.value = true
            when (connectionRepository.connect(profile)) {
                is ConnectionResult.Success -> {
                    _sessionLocked.value = profile.isReadonly
                    _isReconnecting.value = false
                    _reconnectMessage.value = "Reconnected"
                }
                is ConnectionResult.Error -> {
                    _isReconnecting.value = false
                    _reconnectMessage.value = "Reconnect failed"
                }
            }
        }
    }

    fun clearReconnectMessage() {
        _reconnectMessage.value = null
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
        _connectingProfileId.value = null
        _connectionMessage.value = ""
    }

    fun clearConnectingState() {
        _connectingProfileId.value = null
        _connectionMessage.value = ""
    }

    fun hasStoredPassword(profileId: Long): Boolean {
        return credentialStore.hasPassword(profileId)
    }

    fun hasStoredSshPassword(profileId: Long): Boolean {
        return credentialStore.hasSshPassword(profileId)
    }

    fun hasStoredSshPassphrase(profileId: Long): Boolean {
        return credentialStore.hasSshPassphrase(profileId)
    }

    private val _exportImportMessage = MutableStateFlow<String?>(null)
    val exportImportMessage: StateFlow<String?> = _exportImportMessage.asStateFlow()

    fun clearExportImportMessage() { _exportImportMessage.value = null }

    // Import conflict state for per-profile popup (3 buttons + checkbox All, tap outside = skip remaining == abort)
    sealed class ImportConflict {
        data object Idle : ImportConflict()
        data class Awaiting(
            val pending: List<ConnectionRepository.ParsedProfile>,
            val current: ConnectionRepository.ParsedProfile,
            val existing: ConnectionProfileEntity?,
            val index: Int,
            val total: Int
        ) : ImportConflict()
    }
    private val _importConflict = MutableStateFlow<ImportConflict>(ImportConflict.Idle)
    val importConflict: StateFlow<ImportConflict> = _importConflict.asStateFlow()

    // Pending import queue + counters (kept in VM while popping dialogs)
    private var pendingImport: List<ConnectionRepository.ParsedProfile> = emptyList()
    private var pendingIndex: Int = 0
    private var importReplaced: Int = 0
    private var importInserted: Int = 0
    private var importSkipped: Int = 0
    private var applyAll: ConnectionRepository.ImportAction? = null

    fun clearImportConflict() { _importConflict.value = ImportConflict.Idle }

    fun onImportDismiss() {
        // Tap outside = skip remaining (same as abort, no rollback). Works for first or later popup.
        val remaining = pendingImport.size - pendingIndex
        // remaining includes current if still awaiting
        if (_importConflict.value is ImportConflict.Awaiting) {
            importSkipped += remaining
            _importConflict.value = ImportConflict.Idle
            pendingImport = emptyList()
            _exportImportMessage.value = "Import stopped — $importInserted inserted, $importReplaced replaced, $importSkipped skipped (remaining skipped)"
            applyAll = null
        }
    }

    fun cancelImport() { onImportDismiss() }

    fun exportEncrypted(uri: Uri, masterPassword: String) {
        viewModelScope.launch {
            try {
                val n = connectionRepository.exportProfilesEncrypted(appContext, uri, masterPassword)
                _exportImportMessage.value = "Exported $n profiles (encrypted)"
            } catch (e: Exception) {
                _exportImportMessage.value = e.message ?: "Export failed"
            }
        }
    }

    fun importEncrypted(uri: Uri, masterPassword: String) {
        // Legacy one-shot kept for compatibility but now delegates to per-profile flow without UI (always insert)
        viewModelScope.launch {
            try {
                val n = connectionRepository.importProfilesEncrypted(appContext, uri, masterPassword)
                _exportImportMessage.value = "Imported $n profiles"
            } catch (e: Exception) {
                _exportImportMessage.value = e.message ?: "Import failed"
            }
        }
    }

    fun startImportPreview(uri: Uri, masterPassword: String) {
        viewModelScope.launch {
            try {
                val list = connectionRepository.decryptAndParseProfiles(appContext, uri, masterPassword)
                if (list.isEmpty()) {
                    _exportImportMessage.value = "No profiles in file"
                    return@launch
                }
                pendingImport = list
                pendingIndex = 0
                importReplaced = 0; importInserted = 0; importSkipped = 0
                applyAll = null
                processNextImport()
            } catch (e: Exception) {
                _exportImportMessage.value = e.message ?: "Import failed"
            }
        }
    }

    private suspend fun processNextImport() {
        while (pendingIndex < pendingImport.size) {
            val parsed = pendingImport[pendingIndex]
            val existing = if (parsed.profile.id != 0L) connectionRepository.getProfileById(parsed.profile.id) else null
            if (existing == null) {
                // No conflict → auto insert (covers file without id, id==0, or id not found)
                connectionRepository.applyImportDecision(parsed, ConnectionRepository.ImportAction.INSERT_AS_NEW)
                importInserted++
                pendingIndex++
                continue
            }
            // Conflict → apply cached All if set
            val cached = applyAll
            if (cached != null) {
                val ok = connectionRepository.applyImportDecision(parsed, cached)
                if (cached == ConnectionRepository.ImportAction.REPLACE && ok) importReplaced++
                else if (cached == ConnectionRepository.ImportAction.SKIP) importSkipped++
                else if (cached == ConnectionRepository.ImportAction.INSERT_AS_NEW && ok) importInserted++
                pendingIndex++
                continue
            }
            // Need user decision → show dialog
            _importConflict.value = ImportConflict.Awaiting(pendingImport, parsed, existing, pendingIndex, pendingImport.size)
            return
        }
        // Done
        _importConflict.value = ImportConflict.Idle
        pendingImport = emptyList()
        _exportImportMessage.value = "Import done — $importInserted inserted, $importReplaced replaced, $importSkipped skipped"
        applyAll = null
    }

    fun onImportDecision(action: ConnectionRepository.ImportAction, applyToAllChecked: Boolean) {
        viewModelScope.launch {
            if (applyToAllChecked) applyAll = action
            val parsed = pendingImport.getOrNull(pendingIndex) ?: run { _importConflict.value = ImportConflict.Idle; return@launch }
            val existing = if (parsed.profile.id != 0L) connectionRepository.getProfileById(parsed.profile.id) else null
            if (existing != null) {
                val ok = connectionRepository.applyImportDecision(parsed, action)
                if (action == ConnectionRepository.ImportAction.REPLACE && ok) importReplaced++
                else if (action == ConnectionRepository.ImportAction.SKIP) importSkipped++
                else if (action == ConnectionRepository.ImportAction.INSERT_AS_NEW && ok) importInserted++
            } else {
                // Race: no conflict anymore → treat as insert
                connectionRepository.applyImportDecision(parsed, ConnectionRepository.ImportAction.INSERT_AS_NEW)
                importInserted++
            }
            pendingIndex++
            _importConflict.value = ImportConflict.Idle
            processNextImport()
        }
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
