package com.sqlclient.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.QueryResult
import com.sqlclient.android.data.repository.QueryRepository
import com.sqlclient.android.data.remote.model.UserInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserPermissionViewModel @Inject constructor(
    private val connectionManager: MariaDbConnectionManager,
    private val queryRepository: QueryRepository
) : ViewModel() {

    private val _users = MutableStateFlow<List<UserInfo>>(emptyList())
    val users: StateFlow<List<UserInfo>> = _users

    private val _grants = MutableStateFlow<List<String>>(emptyList())
    val grants: StateFlow<List<String>> = _grants

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    private val _allDatabases = MutableStateFlow<List<String>>(emptyList())
    val allDatabases: StateFlow<List<String>> = _allDatabases
    private val _dbTables = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val dbTables: StateFlow<Map<String, List<String>>> = _dbTables

    fun loadAllDatabases() {
        viewModelScope.launch {
            try {
                _currentQuery.value = _currentQuery.value + "SHOW DATABASES"
                when (val r = connectionManager.executeQuery("SHOW DATABASES")) {
                    is QueryResult.Success -> _allDatabases.value = r.rows.map { it[0].toString() }.filterNot { it.lowercase() in setOf("information_schema","performance_schema","sys") }
                    else -> {}
                }
            } catch (_: Exception) {}
        }
    }
    fun loadTablesForDb(database: String) {
        if (_dbTables.value.containsKey(database)) return
        viewModelScope.launch {
            try {
                _currentQuery.value = _currentQuery.value + "SHOW TABLES IN `$database`"
                when (val r = connectionManager.executeQuery("SHOW TABLES IN `$database`")) {
                    is QueryResult.Success -> _dbTables.value = _dbTables.value + (database to r.rows.map { it[0].toString() })
                    else -> _dbTables.value = _dbTables.value + (database to emptyList())
                }
            } catch (_: Exception) { _dbTables.value = _dbTables.value + (database to emptyList()) }
        }
    }

    // --- Batched privilege edits: stage locally -> Save -> Confirm -> Execute (like DataEditor) ---
    private val _pendingPrivChanges = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val pendingPrivChanges: StateFlow<Map<String, Boolean>> = _pendingPrivChanges

    private fun privChangeKey(priv: String, onKey: String) = "${priv.uppercase()}@${onKey.lowercase()}"

    fun hasPendingPrivChanges(): Boolean = _pendingPrivChanges.value.isNotEmpty()

    fun buildPendingPrivSqls(user: String, host: String): List<String> {
        return _pendingPrivChanges.value.map { (key, wantGrant) ->
            val parts = key.split("@", limit = 2)
            val priv = parts[0]
            val onKey = if (parts.size > 1) parts[1] else "*.*"
            val (db, tbl) = when (onKey) {
                "*.*" -> "*" to "*"
                else -> {
                    val pp = onKey.split(".")
                    (pp.getOrNull(0) ?: "*") to (pp.getOrNull(1) ?: "*")
                }
            }
            if (wantGrant) buildGrantSql(user, host, priv, db, tbl) else buildRevokeSql(user, host, priv, db, tbl)
        }
    }

    fun stagePrivToggle(user: String, host: String, priv: String, onKey: String, wantChecked: Boolean, currentlyGranted: Boolean) {
        val key = privChangeKey(priv, onKey)
        if (wantChecked == currentlyGranted) {
            _pendingPrivChanges.value = _pendingPrivChanges.value - key
        } else {
            _pendingPrivChanges.value = _pendingPrivChanges.value + (key to wantChecked)
        }
        val sqls = buildPendingPrivSqls(user, host)
        _currentQuery.value = if (sqls.isNotEmpty()) sqls else listOf("SHOW GRANTS FOR `$user`@`$host`")
    }

    fun clearPendingPrivs(user: String, host: String) {
        _pendingPrivChanges.value = emptyMap()
        // Discard executes no query — leave the query log untouched.
    }

    fun commitPendingPrivs(user: String, host: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        val sqls = buildPendingPrivSqls(user, host)
        if (sqls.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                for (sql in sqls) {
                    _currentQuery.value = _currentQuery.value + sql
                    when (val r = connectionManager.executeQuery(sql)) {
                        is QueryResult.Error -> { _error.value = r.message; return@launch }
                        else -> recordWrite(sql)
                    }
                }
                flushPrivileges()
                _pendingPrivChanges.value = emptyMap()
                loadGrants(user, host)
            } catch (e: Exception) {
                _error.value = "Save failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _currentQuery = MutableStateFlow<List<String>>(emptyList())
    val currentQuery: StateFlow<List<String>> = _currentQuery

    /** Full page (re)entry — clears the query log. Loaders below only append. */
    fun resetQueryLog() {
        _currentQuery.value = emptyList()
    }

    /** Manual refresh — cache-first. Call from TopBar Refresh. Back uses cached users. */
    fun refreshUsers() {
        _hasLoaded.value = false
        resetQueryLog()
        loadUsers(force = true)
    }

    fun loadUsers(force: Boolean = false) {
        _currentQuery.value = _currentQuery.value + "SELECT user, host FROM mysql.user ORDER BY user, host"
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                when (val result = connectionManager.executeQuery("SELECT user, host FROM mysql.user ORDER BY user, host")) {
                    is QueryResult.Success -> {
                        // Fast path: load user list without per-user SHOW GRANTS (which would be N queries and slow on large user tables).
                        // Privileges are loaded lazily via loadGrants when a user is selected.
                        val userList = result.rows.map { row ->
                            UserInfo(user = row[0].toString(), host = row[1].toString())
                        }
                        _users.value = userList
                        _hasLoaded.value = true
                    }
                    is QueryResult.Error -> {
                        val msg = result.message
                        if (msg.contains("Access denied", ignoreCase = true)) {
                            _error.value = "Access denied to mysql.user"
                            _users.value = emptyList()
                        } else _error.value = msg
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load users: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadGrants(user: String, host: String) {
        _currentQuery.value = _currentQuery.value + "SHOW GRANTS FOR `$user`@`$host`"
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                when (val result = connectionManager.executeQuery("SHOW GRANTS FOR `$user`@`$host`")) {
                    is QueryResult.Success -> {
                        _grants.value = result.rows.map { it[0].toString() }
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load grants: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun grantPrivilege(user: String, host: String, privilege: String, database: String, table: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = buildGrantSql(user, host, privilege, database, table)
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Error -> _error.value = result.message
                    else -> {
                        recordWrite(sql)
                        flushPrivileges()
                        loadGrants(user, host)
                        loadUsers()
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to grant privilege: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun revokePrivilege(user: String, host: String, privilege: String, database: String, table: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = buildRevokeSql(user, host, privilege, database, table)
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Error -> _error.value = result.message
                    else -> {
                        recordWrite(sql)
                        flushPrivileges()
                        loadGrants(user, host)
                        loadUsers()
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to revoke privilege: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun sqlTarget(database: String, table: String): String = when {
        database == "*" && table == "*" -> "*.*"
        table == "*" -> "`$database`.*"
        else -> "`$database`.`$table`"
    }
    fun buildGrantSql(user: String, host: String, privilege: String, database: String, table: String): String = "GRANT $privilege ON ${sqlTarget(database, table)} TO `$user`@`$host`"
    fun buildRevokeSql(user: String, host: String, privilege: String, database: String, table: String): String = "REVOKE $privilege ON ${sqlTarget(database, table)} FROM `$user`@`$host`"
    fun buildCreateUserSql(user: String, host: String, password: String): String = "CREATE USER `$user`@`$host` IDENTIFIED BY '${password.replace("'","''")}'"
    fun buildDropUserSql(user: String, host: String): String = "DROP USER `$user`@`$host`"
    fun buildRenameUserSql(oldUser: String, oldHost: String, newUser: String, newHost: String): String = "RENAME USER `$oldUser`@`$oldHost` TO `$newUser`@`$newHost`"
    fun buildChangePasswordSql(user: String, host: String, newPassword: String): String = "ALTER USER `$user`@`$host` IDENTIFIED BY '${newPassword.replace("'","''")}'"

    fun createUser(user: String, host: String, password: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "CREATE USER `$user`@`$host` IDENTIFIED BY '${password.replace("'","''")}'"
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Error -> _error.value = result.message
                    else -> {
                        recordWrite(sql)
                        flushPrivileges()
                        loadUsers()
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to create user: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dropUser(user: String, host: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "DROP USER `$user`@`$host`"
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Error -> _error.value = result.message
                    else -> {
                        recordWrite(sql)
                        flushPrivileges()
                        loadUsers()
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to drop user: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun renameUser(oldUser: String, oldHost: String, newUser: String, newHost: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try {
                val sql = "RENAME USER `$oldUser`@`$oldHost` TO `$newUser`@`$newHost`"
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) { is QueryResult.Error -> _error.value = result.message else -> { recordWrite(sql); flushPrivileges(); loadUsers() } }
            } catch (e: Exception) { _error.value = "Failed to rename user: ${e.message}" } finally { _isLoading.value = false }
        }
    }
    fun changePassword(user: String, host: String, newPassword: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try {
                val esc = newPassword.replace("'","''")
                val sql = "ALTER USER `$user`@`$host` IDENTIFIED BY '$esc'"
                _currentQuery.value = _currentQuery.value + sql
                when (val result = connectionManager.executeQuery(sql)) { is QueryResult.Error -> _error.value = result.message else -> { recordWrite(sql); flushPrivileges(); loadUsers() } }
            } catch (e: Exception) { _error.value = "Failed to change password: ${e.message}" } finally { _isLoading.value = false }
        }
    }

    private fun flushPrivileges() {
        viewModelScope.launch {
            try {
                _currentQuery.value = _currentQuery.value + "FLUSH PRIVILEGES"
                connectionManager.executeQuery("FLUSH PRIVILEGES")
            } catch (_: Exception) {}
        }
    }

    /** Persist a user-confirmed write to History panel (success only, like SQL editor). */
    private fun recordWrite(sql: String) {
        val profileId = connectionManager.currentProfileId ?: return
        viewModelScope.launch {
            try { queryRepository.saveToHistory(profileId, sql, null) } catch (_: Exception) {}
        }
    }

    fun clearAll() {
        _hasLoaded.value = false
        _searchQuery.value = ""
        _users.value = emptyList()
        _grants.value = emptyList()
        _isLoading.value = false
        _error.value = null
    }

    fun clearError() {
        _error.value = null
    }
}
