package id.web.izs.sqlclient.data.remote

import android.util.Log
import id.web.izs.sqlclient.data.local.entity.ConnectionProfileEntity
import id.web.izs.sqlclient.util.CredentialStore
import id.web.izs.sqlclient.util.SqlUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
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
    /**
     * Endpoint of the live session — through the SSH tunnel's local port when one is open,
     * otherwise the profile host. Lets a probe reach exactly where the JDBC socket points.
     */
    @Volatile private var probeHost: String? = null
    @Volatile private var probePort: Int = 0
    /** Wall-clock of the last completed round trip; drives the stale-session pre-flight. */
    @Volatile private var lastQueryAtMs: Long = 0L

    companion object {
        private const val TAG = "MariaDbConn"
        /** Hard bound on a single reachability probe — see [isRouteReachable]. */
        private const val PROBE_TIMEOUT_MS = 500
        /** Probes before declaring the route dead; a lone lost SYN must not cost the session. */
        private const val PROBE_ATTEMPTS = 2
        private const val PROBE_RETRY_GAP_MS = 150L
        /** Idle time after which the next statement re-checks the route before running. */
        private const val STALE_MS = 5_000L
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
            // Probe endpoint: the tunnel's local port when one is open, else the real host.
            probeHost = actualHost
            probePort = actualPort
            // The handshake just proved the route works — don't probe again immediately.
            lastQueryAtMs = System.currentTimeMillis()
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
            // Two-line: short summary for quick read + raw server message verbatim.
            val raw = e.message ?: "Unknown SQL error"
            val short = when {
                raw.contains("authentication protocol", true) || raw.contains("sha256", true) ->
                    "Auth failed: server requires SSL for this method"
                (e.sqlState ?: "") == "28000" -> "Access denied: invalid username or password"
                raw.contains("Unknown database", true) -> "Database '${profile.database}' not found on server"
                raw.contains("connect timed out", true) || raw.contains("Connection refused", true) ->
                    "Cannot reach server at $actualHost:$actualPort"
                else -> "Connection failed"
            }
            ConnectionResult.Error("$short\n$raw")
        } catch (e: Exception) {
            closeTunnelIfOpened()
            val raw = e.message ?: ""
            val simple = e.javaClass.simpleName
            Log.e(TAG, "Unexpected error: $simple: $raw", e)
            // Two-line: short summary + raw message verbatim (incl. SSH/JSch).
            val short = when {
                simple.contains("JSch", true) || raw.contains("Auth fail", true) || raw.contains("Auth cancel", true) ->
                    "SSH authentication failed"
                raw.contains("key", true) && (raw.contains("invalid", true) || raw.contains("unknown", true)) ->
                    "SSH key error"
                raw.contains("timeout", true) || simple.contains("SocketTimeout", true) ->
                    "SSH connection timed out"
                else -> "Unexpected error: $simple"
            }
            ConnectionResult.Error(if (raw.isBlank()) short else "$short\n$raw")
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

    /**
     * Every failed statement, published once per execute call with the exact SQL that was sent
     * and the verbatim driver/server message. Each ViewModel collects this and attaches the
     * message to its own query-log line ([id.web.izs.sqlclient.ui.components.CurrentQueryBar]
     * draws it red) — the message is never rewritten here or there.
     */
    private val _queryFailures = MutableSharedFlow<QueryFailure>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val queryFailures: SharedFlow<QueryFailure> = _queryFailures.asSharedFlow()

    private fun reportFailure(sql: String, result: QueryResult) {
        if (result is QueryResult.Error) _queryFailures.tryEmit(QueryFailure(sql, result.message))
    }

    /** Low-priority query that never blocks UI-critical queries: skips if mutex is busy. */
    suspend fun executeQueryIfFree(sql: String): QueryResult? = withContext(Dispatchers.IO) {
        healIfStale()
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
    }.also { result ->
        if (result != null) {
            if (result !is QueryResult.Error) lastQueryAtMs = System.currentTimeMillis()
            reportFailure(sql, result)
        }
    }

    /**
     * Executes a query, self-healing the session when the connection died underneath us
     * (timeout/cancel drops the socket in driver 2.x, server wait_timeout, killed tunnel).
     * A dead session is reconnected silently; the statement itself is retried ONCE and
     * only for reads — re-running a write could double-apply it.
     */
    suspend fun executeQuery(sql: String): QueryResult = withContext(Dispatchers.IO) {
        healIfStale()
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
    }.also { result ->
        // A completed round trip proves the session was live — restart the staleness window.
        if (result !is QueryResult.Error) lastQueryAtMs = System.currentTimeMillis()
        reportFailure(sql, result)
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
    }.also { reportFailure(sql, it) }

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

    /**
     * True when a fresh TCP connect to the current endpoint answers — through the SSH tunnel's
     * local port when one is open, otherwise straight to the profile host.
     *
     * Bounded: worst case [PROBE_ATTEMPTS] × [PROBE_TIMEOUT_MS] plus one gap, far below the 30s
     * the driver would burn. Deliberately not `Connection.isValid`: with a configured
     * socketTimeout, driver 2.4.4 ignores isValid's own timeout argument (`AbstractQueryProtocol
     * .isValid` only lowers SO_TIMEOUT when the field is 0), so `isValid(1)` would block for the
     * full 30s on a blackholed socket. A live route answers in one RTT; the timeout is only the
     * ceiling for a dead one.
     *
     * Two attempts because callers act on a false negative by tearing the session down — a lone
     * lost SYN must not cost a working connection.
     */
    private fun isRouteReachable(): Boolean {
        val host = probeHost ?: return false
        val port = probePort
        if (port !in 1..65535) return false
        repeat(PROBE_ATTEMPTS) { attempt ->
            val reachable = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
                }
                true
            } catch (_: Exception) {
                false
            }
            if (reachable) return true
            if (attempt == 0) Thread.sleep(PROBE_RETRY_GAP_MS)
        }
        return false
    }

    /**
     * Reconnects BEFORE the next statement when the session has sat idle long enough that the
     * route may have silently died (NAT idle drop, wifi→cellular handoff, dead tunnel). Without
     * this the statement itself discovers the dead socket and blocks on socketTimeout first.
     *
     * Never runs while a statement is in flight — that statement's own error path heals it.
     */
    private suspend fun healIfStale() {
        if (activeConnection == null) return
        if (queryMutex.isLocked) return
        if (System.currentTimeMillis() - lastQueryAtMs < STALE_MS) return
        if (isRouteReachable()) return
        Log.d(TAG, "Stale session unreachable — reconnecting before the next statement")
        reconnectSilently()
    }

    /**
     * App-resume hook: heal the session if it or its route died while backgrounded, so the
     * first statement after returning doesn't have to discover the failure itself. No-op when
     * there is no session to heal or one is mid-flight.
     */
    suspend fun ensureConnected() = withContext(Dispatchers.IO) {
        if (activeConnection == null) return@withContext
        if (queryMutex.isLocked) return@withContext
        if (isConnected()) return@withContext
        Log.d(TAG, "Session unreachable on resume — reconnecting")
        reconnectSilently()
    }

    /**
     * True when a session object is open AND its route still answers. Blocking (one bounded
     * probe) — call from a background dispatcher.
     */
    fun isConnected(): Boolean {
        val conn = activeConnection ?: return false
        return try {
            !conn.isClosed && isRouteReachable()
        } catch (_: Exception) {
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
     * timeout), confirmed against the driver's closed flag and the route — a slow query can
     * trip SO_TIMEOUT while the session itself is still usable, and then no reconnect is needed.
     *
     * The closed flag is free and definitive for reset/broken pipe; the route probe covers the
     * blackholed case. Never `Connection.isValid` — see [isRouteReachable] for why.
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
        val conn = activeConnection
        if (conn == null) return true
        return try {
            if (conn.isClosed) true else !isRouteReachable()
        } catch (_: Exception) {
            true
        }
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
        // No session → no endpoint to probe and nothing that counts as a live round trip.
        probeHost = null
        probePort = 0
        lastQueryAtMs = 0L
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

/** A failed statement: [message] is the verbatim driver/server error for exactly [sql]. */
data class QueryFailure(val sql: String, val message: String)
