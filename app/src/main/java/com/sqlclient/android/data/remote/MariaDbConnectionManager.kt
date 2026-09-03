package com.sqlclient.android.data.remote

import android.util.Log
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.util.CredentialStore
import com.sqlclient.android.util.SqlUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MariaDbConnectionManager @Inject constructor(
    private val credentialStore: CredentialStore,
    private val sshTunnelManager: SshTunnelManager
) {
    private var activeConnection: Connection? = null
    private var activeProfileId: Long? = null
    /** Profile id of the live session — used to attribute persistent query history. */
    val currentProfileId: Long? get() = activeProfileId
    /**
     * Last profile with a successful connect — used by silent auto-reconnect after
     * timeout/cancel kills the socket (driver 2.x drops the connection on cancel).
     * Cleared on manual disconnect so we never reconnect behind the user's back.
     */
    private var lastProfile: ConnectionProfileEntity? = null
    /** Serializes auto-reconnects so concurrent failing queries heal the session once. */
    private val reconnectMutex = Mutex()

    companion object {
        private const val TAG = "MariaDbConn"
        init {
            try {
                Class.forName("org.mariadb.jdbc.Driver")
                Log.d(TAG, "MariaDB driver loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load MariaDB driver", e)
            }
        }
    }

    suspend fun connect(profile: ConnectionProfileEntity, onStatus: ((String) -> Unit)? = null): ConnectionResult = withContext(Dispatchers.IO) {
        var actualHost = profile.host
        var actualPort = profile.port
        // Tracks whether THIS call opened an SSH tunnel, so failures below can close it
        // instead of leaking an orphan session (activeProfileId is null on failed connect).
        var tunnelOpened = false
        fun closeTunnelIfOpened() {
            if (tunnelOpened) {
                try { sshTunnelManager.closeTunnel() } catch (_: Exception) {}
                tunnelOpened = false
            }
        }

        try {
            // disconnect() clears lastProfile (manual-disconnect semantics) — restore it:
            // a FAILED connect must keep auto-reconnect armed with the previous profile.
            val prevProfile = lastProfile
            disconnect()
            lastProfile = prevProfile

            if (profile.useSshTunnel) {
                val tunnel = sshTunnelManager.openTunnel(
                    sshHost = profile.sshHost ?: return@withContext ConnectionResult.Error("SSH host is required"),
                    sshPort = profile.sshPort,
                    sshUsername = profile.sshUsername ?: return@withContext ConnectionResult.Error("SSH username is required"),
                    sshPassword = credentialStore.getSshPassword(profile.id),
                    sshKeyPath = profile.sshKeyPath,
                    sshPassphrase = credentialStore.getSshPassphrase(profile.id),
                    remoteHost = profile.host,
                    remotePort = profile.port
                )
                actualHost = "127.0.0.1"
                actualPort = tunnel.localPort
                tunnelOpened = true
            }

            onStatus?.invoke("Connecting to database...")

            val password = credentialStore.getPassword(profile.id)
                ?: run {
                    closeTunnelIfOpened()
                    return@withContext ConnectionResult.Error("Password not found. Save the connection first.")
                }

            Log.d(TAG, "Connecting to $actualHost:$actualPort user=${profile.username} ssl=${profile.useSsl} db=${profile.database}")

            val props = Properties().apply {
                put("user", profile.username)
                put("password", password)
                put("connectTimeout", "8000")
                put("socketTimeout", "30000")

                if (profile.useSsl) {
                    put("useSSL", "true")
                    put("trustServerCertificate", "true")
                    put("allowPublicKeyRetrieval", "true")
                } else {
                    put("useSSL", "false")
                    put("allowPublicKeyRetrieval", "true")
                }

                if (!profile.database.isNullOrBlank()) {
                    put("database", profile.database)
                }
            }

            val url = "jdbc:mariadb://$actualHost:$actualPort/"

            Log.d(TAG, "Connecting with URL: $url")
            val connection = DriverManager.getConnection(url, props)
            Log.d(TAG, "Connected! isValid=${connection.isValid(3)}")

            activeConnection = connection
            activeProfileId = profile.id
            // Remember for silent auto-reconnect. Skip the ephemeral test profile
            // (ConnectionRepository.testConnection id 999999) so a Test never hijacks it.
            if (profile.id != 999999L) lastProfile = profile

            ConnectionResult.Success(connection)
        } catch (e: Error) {
            closeTunnelIfOpened()
            Log.e(TAG, "Driver Error: ${e.javaClass.simpleName}: ${e.message}", e)
            ConnectionResult.Error("Driver error: ${e.javaClass.simpleName}: ${e.message}")
        } catch (e: SQLException) {
            closeTunnelIfOpened()
            Log.e(TAG, "SQL Error: ${e.sqlState} - ${e.message}", e)
            val msg = e.message ?: "Unknown SQL error"
            val sqlState = e.sqlState ?: ""
            when {
                msg.contains("authentication protocol") || msg.contains("sha256") -> {
                    ConnectionResult.Error("Auth failed: Server requires SSL for this authentication method. Enable 'Use SSL/TLS' in connection settings.")
                }
                sqlState == "28000" -> {
                    ConnectionResult.Error("Access denied: Invalid username or password")
                }
                msg.contains("Unknown database") -> {
                    ConnectionResult.Error("Database '${profile.database}' not found on server")
                }
                msg.contains("connect timed out") || msg.contains("Connection refused") -> {
                    ConnectionResult.Error("Cannot reach server at $actualHost:$actualPort")
                }
                else -> {
                    ConnectionResult.Error("Connection failed: $msg")
                }
            }
        } catch (e: Exception) {
            closeTunnelIfOpened()
            val raw = e.message ?: ""
            val simple = e.javaClass.simpleName
            Log.e(TAG, "Unexpected error: $simple: $raw", e)
            // Map SSH auth failures to friendly message instead of "Unexpected error: JSchException: Auth fail"
            if (simple.contains("JSch") || raw.contains("Auth fail", true) || raw.contains("Auth cancel", true)) {
                ConnectionResult.Error("SSH authentication failed — check SSH username/password, key path & passphrase. ($raw)")
            } else if (raw.contains("key", true) && (raw.contains("invalid", true) || raw.contains("unknown", true))) {
                ConnectionResult.Error("SSH key error: $raw")
            } else if (raw.contains("timeout", true) || simple.contains("SocketTimeout")) {
                ConnectionResult.Error("SSH connection timed out. Check SSH host/port and network. ($raw)")
            } else {
                ConnectionResult.Error("Unexpected error: $simple: $raw")
            }
        }
    }

    @Volatile private var activeStatement: java.sql.Statement? = null
    /**
     * Set by [cancelCurrentQuery]; consulted right after a new statement is published.
     * Closes the lost-cancel race where cancel reads [activeStatement] as null a moment
     * before the runner assigns it. Cleared in every query `finally` and on idle cancel.
     */
    private val cancelRequested = java.util.concurrent.atomic.AtomicBoolean(false)

    fun cancelCurrentQuery() {
        cancelRequested.set(true)
        try { activeStatement?.cancel() } catch (_: Exception) {}
        // No query actually running: drop the flag so the NEXT query isn't wrongly cancelled.
        if (queryMutex.tryLock()) {
            cancelRequested.set(false)
            queryMutex.unlock()
        }
    }

    /**
     * Publishes [stmt] as cancellable and honors a pending cancel.
     * @return false if a pending cancel was honored — caller must abort without executing.
     */
    private fun publishStatement(stmt: java.sql.Statement): Boolean {
        activeStatement = stmt
        if (cancelRequested.get()) {
            try { stmt.cancel() } catch (_: Exception) {}
            return false
        }
        return true
    }

    private val queryMutex = kotlinx.coroutines.sync.Mutex()

    /** Low-priority query that never blocks UI-critical queries: skips if mutex is busy. */
    suspend fun executeQueryIfFree(sql: String): QueryResult? = withContext(Dispatchers.IO) {
        if (!queryMutex.tryLock()) return@withContext null
        var stmt: java.sql.Statement? = null
        var rs: java.sql.ResultSet? = null
        try {
            val conn = activeConnection ?: return@withContext QueryResult.Error("No active connection")
            stmt = conn.createStatement()
            try { stmt.queryTimeout = 6 } catch (_: Exception) {}
            try { stmt.maxRows = 1001 } catch (_: Exception) {}
            if (!publishStatement(stmt)) return@withContext QueryResult.Error("Cancelled")
            val hasResultSet = stmt.execute(sql)
            if (hasResultSet) {
                rs = stmt.resultSet
                val metaData = rs.metaData
                val columnCount = metaData.columnCount
                val columns = (1..columnCount).map { i ->
                    ColumnMetadata(name = metaData.getColumnLabel(i), type = metaData.getColumnTypeName(i))
                }
                val rows = mutableListOf<List<Any?>>()
                while (rs.next()) {
                    val row = (1..columnCount).map { i -> rs.getObject(i) }
                    rows.add(row)
                }
                val truncated = rows.size == 1001
                val resultRows = if (truncated) rows.dropLast(1) else rows
                QueryResult.Success(columns = columns, rows = resultRows, rowCount = resultRows.size, truncated = truncated)
            } else {
                QueryResult.UpdateSuccess(stmt.updateCount)
            }
        } catch (e: SQLException) {
            QueryResult.Error("Query failed: ${e.message}")
        } finally {
            activeStatement = null
            try { rs?.close() } catch (_: Exception) {}
            try { stmt?.close() } catch (_: Exception) {}
            cancelRequested.set(false)
            queryMutex.unlock()
        }
    }

    /**
     * Executes a query, self-healing the session when the connection died underneath us
     * (timeout/cancel drops the socket in driver 2.x, server wait_timeout, killed tunnel).
     * A dead session is reconnected silently; the statement itself is retried ONCE and
     * only for reads — re-running a write could double-apply it.
     */
    suspend fun executeQuery(sql: String): QueryResult = withContext(Dispatchers.IO) {
        val first = executeQueryOnce(sql)
        if (first is QueryResult.Error) {
            val dead = isDeadConnectionError(first.message)
            if (first.message == "Cancelled" || dead) {
                // cancel()/timeout drops the socket in driver 2.x — heal for the next query.
                val healed = reconnectSilently()
                if (dead && healed && !SqlUtil.isWriteQuery(sql)) {
                    return@withContext executeQueryOnce(sql)
                }
            }
        }
        first
    }

    private suspend fun executeQueryOnce(sql: String): QueryResult = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext QueryResult.Error("No active connection")
        queryMutex.lock()
        var stmt: java.sql.Statement? = null
        var rs: java.sql.ResultSet? = null
        try {
            stmt = conn.createStatement()
            try { stmt.queryTimeout = if (sql.contains("information_schema", true)) 6 else 30 } catch (_: Exception) {}
            try { stmt.maxRows = 1001 } catch (_: Exception) {}
            if (!publishStatement(stmt)) return@withContext QueryResult.Error("Cancelled")
            val hasResultSet = stmt.execute(sql)
            if (hasResultSet) {
                rs = stmt.resultSet
                val metaData = rs.metaData
                val columnCount = metaData.columnCount
                val columns = (1..columnCount).map { i ->
                    ColumnMetadata(name = metaData.getColumnLabel(i), type = metaData.getColumnTypeName(i))
                }
                val rows = mutableListOf<List<Any?>>()
                while (rs.next()) {
                    val row = (1..columnCount).map { i -> rs.getObject(i) }
                    rows.add(row)
                }
                val truncated = rows.size == 1001
                val resultRows = if (truncated) rows.dropLast(1) else rows
                QueryResult.Success(columns = columns, rows = resultRows, rowCount = resultRows.size, truncated = truncated)
            } else {
                QueryResult.UpdateSuccess(stmt.updateCount)
            }
        } catch (e: SQLException) {
            if (e.message?.contains("cancel", true) == true || e.message?.contains("KILL", true) == true) {
                QueryResult.Error("Cancelled")
            } else {
                QueryResult.Error("Query failed: ${e.message}")
            }
        } finally {
            activeStatement = null
            try { rs?.close() } catch (_: Exception) {}
            try { stmt?.close() } catch (_: Exception) {}
            cancelRequested.set(false)
            queryMutex.unlock()
        }
    }

    /**
     * Like [executeQuery] but NEVER retries the statement: writes must not double-apply.
     * Only heals the session so the user's next action works.
     */
    suspend fun executeUpdate(sql: String): QueryResult = withContext(Dispatchers.IO) {
        val first = executeUpdateOnce(sql)
        if (first is QueryResult.Error && isDeadConnectionError(first.message)) {
            reconnectSilently()
        }
        first
    }

    private suspend fun executeUpdateOnce(sql: String): QueryResult = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext QueryResult.Error("No active connection")
        queryMutex.lock()
        var stmt: java.sql.Statement? = null
        try {
            stmt = conn.createStatement()
            try { stmt.queryTimeout = 30 } catch (_: Exception) {}
            if (!publishStatement(stmt)) return@withContext QueryResult.Error("Cancelled")
            val updateCount = stmt.executeUpdate(sql)
            QueryResult.UpdateSuccess(updateCount)
        } catch (e: SQLException) {
            QueryResult.Error("Update failed: ${e.message}")
        } finally {
            activeStatement = null
            try { stmt?.close() } catch (_: Exception) {}
            cancelRequested.set(false)
            queryMutex.unlock()
        }
    }

    fun isConnected(): Boolean {
        return try {
            activeConnection?.isValid(3) == true
        } catch (e: Exception) {
            false
        }
    }

    /** Verify connection is actually alive by running SELECT 1. */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        val result = executeQueryIfFree("SELECT 1")
        result is QueryResult.Success
    }

    /**
     * True when [message] looks like a dead session (closed/reset/link failure/socket
     * timeout), confirmed with a live ping — a slow query can trip SO_TIMEOUT while the
     * session itself is still usable, and then no reconnect is needed.
     */
    private fun isDeadConnectionError(message: String?): Boolean {
        if (message == null) return false
        val m = message.lowercase()
        val sniff = m.contains("connection is closed")
            || m.contains("connection reset")
            || m.contains("communications link failure")
            || m.contains("socket closed")
            || m.contains("broken pipe")
            || m.contains("lost connection")
            || m.contains("read timed out")
            || m.contains("socket timeout")
        if (!sniff) return false
        return try { activeConnection?.isValid(2) != true } catch (_: Exception) { true }
    }

    /**
     * Reconnects using [lastProfile] without touching UI state or re-running any
     * statement. Safe to call from query error paths: serialized by [reconnectMutex],
     * never while holding [queryMutex] (attempts run sequentially, lock released first).
     */
    private suspend fun reconnectSilently(): Boolean = reconnectMutex.withLock {
        try { if (isConnected()) return true } catch (_: Exception) {}
        val profile = lastProfile ?: return false
        Log.d(TAG, "Session dead — reconnecting silently as ${profile.username}")
        return when (connect(profile)) {
            is ConnectionResult.Success -> true
            is ConnectionResult.Error -> false
        }
    }

    fun disconnect() {
        // Ask the in-flight query (if any) to stop BEFORE close() hits the driver
        // mid-execute. Inline snapshot-cancel only — deliberately NOT cancelCurrentQuery(),
        // which would set cancelRequested and wrongly cancel the next connect's first query.
        try { activeStatement?.cancel() } catch (_: Exception) {}
        try {
            activeConnection?.close()
        } catch (_: Exception) {}
        activeConnection = null

        activeProfileId?.let {
            sshTunnelManager.closeTunnel()
        }
        activeProfileId = null
        // Manual disconnect: forget the profile so no auto-reconnect fires afterwards.
        lastProfile = null
    }
}

sealed class ConnectionResult {
    data class Success(val connection: Connection) : ConnectionResult()
    data class Error(val message: String) : ConnectionResult()
}

data class ColumnMetadata(
    val name: String,
    val type: String
)

sealed class QueryResult {
    data class Success(
        val columns: List<ColumnMetadata>,
        val rows: List<List<Any?>>,
        val rowCount: Int,
        val truncated: Boolean = false
    ) : QueryResult()

    data class UpdateSuccess(val affectedRows: Int) : QueryResult()
    data class Error(val message: String) : QueryResult()
}
