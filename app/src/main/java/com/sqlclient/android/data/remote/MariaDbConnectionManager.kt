package com.sqlclient.android.data.remote

import android.util.Log
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.util.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
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

    suspend fun connect(profile: ConnectionProfileEntity): ConnectionResult = withContext(Dispatchers.IO) {
        var actualHost = profile.host
        var actualPort = profile.port

        try {
            disconnect()

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
            }

            val password = credentialStore.getPassword(profile.id)
                ?: return@withContext ConnectionResult.Error("Password not found. Save the connection first.")

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

            ConnectionResult.Success(connection)
        } catch (e: Error) {
            Log.e(TAG, "Driver Error: ${e.javaClass.simpleName}: ${e.message}", e)
            ConnectionResult.Error("Driver error: ${e.javaClass.simpleName}: ${e.message}")
        } catch (e: SQLException) {
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
                QueryResult.Success(columns = columns, rows = rows, rowCount = rows.size)
            } else {
                QueryResult.UpdateSuccess(stmt.updateCount)
            }
        } catch (e: SQLException) {
            QueryResult.Error("Query failed: ${e.message}")
        } finally {
            try { rs?.close() } catch (_: Exception) {}
            try { stmt?.close() } catch (_: Exception) {}
            queryMutex.unlock()
        }
    }

    suspend fun executeQuery(sql: String): QueryResult = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext QueryResult.Error("No active connection")
        queryMutex.lock()
        var stmt: java.sql.Statement? = null
        var rs: java.sql.ResultSet? = null
        try {
            stmt = conn.createStatement()
            try { stmt.queryTimeout = if (sql.contains("information_schema", true)) 6 else 30 } catch (_: Exception) {}
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
                QueryResult.Success(columns = columns, rows = rows, rowCount = rows.size)
            } else {
                QueryResult.UpdateSuccess(stmt.updateCount)
            }
        } catch (e: SQLException) {
            QueryResult.Error("Query failed: ${e.message}")
        } finally {
            try { rs?.close() } catch (_: Exception) {}
            try { stmt?.close() } catch (_: Exception) {}
            queryMutex.unlock()
        }
    }

    suspend fun executeUpdate(sql: String): QueryResult = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext QueryResult.Error("No active connection")
        queryMutex.lock()
        var stmt: java.sql.Statement? = null
        try {
            stmt = conn.createStatement()
            try { stmt.queryTimeout = 30 } catch (_: Exception) {}
            val updateCount = stmt.executeUpdate(sql)
            QueryResult.UpdateSuccess(updateCount)
        } catch (e: SQLException) {
            QueryResult.Error("Update failed: ${e.message}")
        } finally {
            try { stmt?.close() } catch (_: Exception) {}
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

    fun disconnect() {
        try {
            activeConnection?.close()
        } catch (_: Exception) {}
        activeConnection = null

        activeProfileId?.let {
            sshTunnelManager.closeTunnel()
        }
        activeProfileId = null
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
        val rowCount: Int
    ) : QueryResult()

    data class UpdateSuccess(val affectedRows: Int) : QueryResult()
    data class Error(val message: String) : QueryResult()
}
