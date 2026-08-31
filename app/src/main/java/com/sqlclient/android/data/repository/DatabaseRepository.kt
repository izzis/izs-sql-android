package com.sqlclient.android.data.repository

import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.data.remote.QueryResult
import com.sqlclient.android.data.remote.model.DatabaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseRepository @Inject constructor(
    private val connectionManager: MariaDbConnectionManager
) {
    suspend fun showDatabases(): List<DatabaseInfo> = withContext(Dispatchers.IO) {
        when (val result = connectionManager.executeQuery("SHOW DATABASES")) {
            is QueryResult.Success -> result.rows.map { row ->
                DatabaseInfo(name = row[0].toString())
            }
            else -> emptyList()
        }
    }

    suspend fun showTables(database: String): List<String> = withContext(Dispatchers.IO) {
        when (val result = connectionManager.executeQuery("SHOW TABLES IN `$database`")) {
            is QueryResult.Success -> result.rows.map { it[0].toString() }
            else -> emptyList()
        }
    }

    suspend fun showColumns(database: String, table: String): QueryResult {
        return connectionManager.executeQuery("SHOW FULL COLUMNS FROM `$database`.`$table`")
    }

    suspend fun showCreateTable(database: String, table: String): QueryResult {
        return connectionManager.executeQuery("SHOW CREATE TABLE `$database`.`$table`")
    }

    suspend fun showIndex(database: String, table: String): QueryResult {
        return connectionManager.executeQuery("SHOW INDEX FROM `$database`.`$table`")
    }
}
