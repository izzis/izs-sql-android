package id.web.izs.sqlclient.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.web.izs.sqlclient.data.remote.MariaDbConnectionManager
import id.web.izs.sqlclient.data.remote.QueryResult
import id.web.izs.sqlclient.data.repository.QueryRepository
import id.web.izs.sqlclient.data.remote.model.EventInfo
import id.web.izs.sqlclient.data.remote.model.RoutineInfo
import id.web.izs.sqlclient.data.remote.model.TriggerInfo
import id.web.izs.sqlclient.util.DbStructureSql
import id.web.izs.sqlclient.util.QueryLogEntry
import id.web.izs.sqlclient.util.withQueryError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Database-level structure: views, triggers, events, routines.
 *
 * Every function that hits the server appends its SQL to [_currentQuery] first —
 * no hidden queries. Every write goes through a user-confirmed preview on the
 * screen side (pendingSql pattern) and re-checks [isLocked] here.
 */
@HiltViewModel
class DbStructureViewModel @Inject constructor(
    private val connectionManager: MariaDbConnectionManager,
    private val queryRepository: QueryRepository
) : ViewModel() {

    private val _views = MutableStateFlow<List<String>>(emptyList())
    val views: StateFlow<List<String>> = _views.asStateFlow()

    private val _triggers = MutableStateFlow<List<TriggerInfo>>(emptyList())
    val triggers: StateFlow<List<TriggerInfo>> = _triggers.asStateFlow()

    private val _events = MutableStateFlow<List<EventInfo>>(emptyList())
    val events: StateFlow<List<EventInfo>> = _events.asStateFlow()

    private val _routines = MutableStateFlow<List<RoutineInfo>>(emptyList())
    val routines: StateFlow<List<RoutineInfo>> = _routines.asStateFlow()

    private val _tables = MutableStateFlow<List<String>>(emptyList())
    val tables: StateFlow<List<String>> = _tables.asStateFlow()

    /** Full DDL per object, keyed "VIEW:name" / "TRIGGER:name" / "EVENT:name" / "ROUTINE:name". */
    private val _definitions = MutableStateFlow<Map<String, String>>(emptyMap())
    val definitions: StateFlow<Map<String, String>> = _definitions.asStateFlow()

    /** DO-body per event (from information_schema, for edit prefill — schedule comes from the SHOW EVENTS row). */
    private val _eventBodies = MutableStateFlow<Map<String, String>>(emptyMap())
    val eventBodies: StateFlow<Map<String, String>> = _eventBodies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _operationSuccess = MutableStateFlow<String?>(null)
    val operationSuccess: StateFlow<String?> = _operationSuccess.asStateFlow()

    private val _currentQuery = MutableStateFlow<List<QueryLogEntry>>(emptyList())
    val currentQuery: StateFlow<List<QueryLogEntry>> = _currentQuery.asStateFlow()

    init {
        // Failures come straight from the connection manager and are attached to the
        // query-log line they belong to (CurrentQueryBar draws that line in red).
        viewModelScope.launch {
            connectionManager.queryFailures.collect { failure ->
                _currentQuery.value = _currentQuery.value.withQueryError(failure.sql, failure.message)
            }
        }
    }

    // ---------- views ----------

    fun loadViews(database: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "SHOW FULL TABLES FROM `$database` WHERE TABLE_TYPE LIKE 'VIEW'"
                _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        _views.value = result.rows.map { it[0].toString() }.sorted()
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load views: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadViewDefinition(database: String, view: String) {
        if (_definitions.value["VIEW:$view"] != null) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "SHOW CREATE VIEW `$database`.`$view`"
                _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        result.rows.firstOrNull()?.getOrNull(1)?.toString()?.let { ddl ->
                            _definitions.value = _definitions.value + ("VIEW:$view" to ddl)
                        }
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load view definition: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createView(database: String, name: String, definition: String, orReplace: Boolean, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        runWrites(
            database = database,
            statements = listOf(buildCreateViewSql(database, name, definition, orReplace)),
            successMessage = if (orReplace) "View replaced" else "View created",
            onDone = {
                _definitions.value = _definitions.value - "VIEW:$name"
                loadViews(database)
            }
        )
    }

    fun dropView(database: String, view: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        runWrites(
            database = database,
            statements = listOf(buildDropViewSql(database, view)),
            successMessage = "View dropped",
            onDone = {
                _definitions.value = _definitions.value - "VIEW:$view"
                loadViews(database)
            }
        )
    }

    // ---------- triggers ----------

    fun loadTriggers(database: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "SHOW TRIGGERS FROM `$database`"
                _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        _triggers.value = result.rows.map { row ->
                            TriggerInfo(
                                name = row[0].toString(),
                                event = row[1].toString(),
                                table = row[2].toString(),
                                statement = row[3].toString(),
                                timing = row[4].toString(),
                                definer = row.getOrNull(7)?.toString()
                            )
                        }.sortedBy { it.name }
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load triggers: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadTriggerDefinition(database: String, trigger: String) {
        if (_definitions.value["TRIGGER:$trigger"] != null) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "SHOW CREATE TRIGGER `$database`.`$trigger`"
                _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        result.rows.firstOrNull()?.getOrNull(2)?.toString()?.let { ddl ->
                            _definitions.value = _definitions.value + ("TRIGGER:$trigger" to ddl)
                        }
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load trigger definition: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createTrigger(database: String, name: String, timing: String, event: String, table: String, body: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        runWrites(
            database = database,
            statements = listOf(buildCreateTriggerSql(database, name, timing, event, table, body)),
            successMessage = "Trigger created",
            onDone = { loadTriggers(database) }
        )
    }

    /** MySQL has no CREATE OR REPLACE / ALTER for triggers — edit = DROP + CREATE. */
    fun recreateTrigger(database: String, name: String, timing: String, event: String, table: String, body: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        runWrites(
            database = database,
            statements = listOf(
                buildDropTriggerSql(database, name),
                buildCreateTriggerSql(database, name, timing, event, table, body)
            ),
            successMessage = "Trigger replaced",
            onDone = {
                _definitions.value = _definitions.value - "TRIGGER:$name"
                loadTriggers(database)
            }
        )
    }

    fun dropTrigger(database: String, trigger: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        runWrites(
            database = database,
            statements = listOf(buildDropTriggerSql(database, trigger)),
            successMessage = "Trigger dropped",
            onDone = {
                _definitions.value = _definitions.value - "TRIGGER:$trigger"
                loadTriggers(database)
            }
        )
    }

    // ---------- events ----------

    fun loadEvents(database: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "SHOW EVENTS FROM `$database`"
                _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        // SHOW EVENTS columns: Db, Name, Definer, Time zone, Type,
                        // Execute at, Interval value, Interval field, Starts, Ends,
                        // Status, Originator, ...
                        _events.value = result.rows.map { row ->
                            EventInfo(
                                name = row[1].toString(),
                                status = row[10].toString(),
                                eventType = row[4].toString(),
                                executeAt = row[5]?.toString(),
                                intervalValue = row[6]?.toString(),
                                intervalField = row[7]?.toString(),
                                starts = row[8]?.toString(),
                                ends = row[9]?.toString()
                            )
                        }.sortedBy { it.name }
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load events: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadEventDefinition(database: String, event: String) {
        if (_definitions.value["EVENT:$event"] != null) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "SHOW CREATE EVENT `$database`.`$event`"
                _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        result.rows.firstOrNull()?.getOrNull(3)?.toString()?.let { ddl ->
                            _definitions.value = _definitions.value + ("EVENT:$event" to ddl)
                        }
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
                if (_eventBodies.value[event] == null) {
                    val escapedDb = database.replace("'", "''")
                    val escapedEvent = event.replace("'", "''")
                    val bodySql = "SELECT EVENT_DEFINITION FROM information_schema.EVENTS " +
                        "WHERE EVENT_SCHEMA = '$escapedDb' AND EVENT_NAME = '$escapedEvent'"
                    _currentQuery.value = _currentQuery.value + QueryLogEntry(bodySql)
                    when (val bodyResult = connectionManager.executeQuery(bodySql)) {
                        is QueryResult.Success -> {
                            bodyResult.rows.firstOrNull()?.getOrNull(0)?.toString()?.let { body ->
                                _eventBodies.value = _eventBodies.value + (event to body)
                            }
                        }
                        is QueryResult.Error -> _error.value = bodyResult.message
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load event definition: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createEvent(database: String, name: String, schedule: String, preserve: Boolean, enabled: Boolean, body: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        runWrites(
            database = database,
            statements = listOf(buildCreateEventSql(database, name, schedule, preserve, enabled, body)),
            successMessage = "Event created",
            onDone = { loadEvents(database) }
        )
    }

    fun alterEvent(database: String, name: String, schedule: String, preserve: Boolean, enabled: Boolean, body: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        runWrites(
            database = database,
            statements = listOf(buildAlterEventSql(database, name, schedule, preserve, enabled, body)),
            successMessage = "Event altered",
            onDone = {
                _definitions.value = _definitions.value - "EVENT:$name"
                _eventBodies.value = _eventBodies.value - name
                loadEvents(database)
            }
        )
    }

    fun toggleEvent(database: String, name: String, enable: Boolean, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        runWrites(
            database = database,
            statements = listOf(buildToggleEventSql(database, name, enable)),
            successMessage = if (enable) "Event enabled" else "Event disabled",
            onDone = { loadEvents(database) }
        )
    }

    fun dropEvent(database: String, event: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        runWrites(
            database = database,
            statements = listOf(buildDropEventSql(database, event)),
            successMessage = "Event dropped",
            onDone = {
                _definitions.value = _definitions.value - "EVENT:$event"
                _eventBodies.value = _eventBodies.value - event
                loadEvents(database)
            }
        )
    }

    // ---------- routines ----------

    fun loadRoutines(database: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val escaped = database.replace("'", "''")
                val sql = "SELECT ROUTINE_NAME, ROUTINE_TYPE FROM information_schema.ROUTINES " +
                    "WHERE ROUTINE_SCHEMA = '$escaped' ORDER BY 1, 2"
                _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        _routines.value = result.rows.map { row ->
                            RoutineInfo(name = row[0].toString(), kind = row[1].toString())
                        }
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load routines: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadRoutineDefinition(database: String, kind: String, name: String) {
        if (_definitions.value["ROUTINE:$name"] != null) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sql = "SHOW CREATE ${kind.uppercase()} `$database`.`$name`"
                _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        result.rows.firstOrNull()?.getOrNull(2)?.toString()?.let { ddl ->
                            _definitions.value = _definitions.value + ("ROUTINE:$name" to ddl)
                        }
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load routine definition: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Executes the raw editor content as-is (preview shows the exact same string). */
    fun createRoutine(database: String, rawSql: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        // Database is reloaded by the screen via loadRoutines on success.
        runWrites(
            database = database,
            statements = listOf(rawSql),
            successMessage = "Routine created",
            onDone = {}
        )
    }

    /** MySQL has no CREATE OR REPLACE for routines — edit = DROP + CREATE. */
    fun recreateRoutine(database: String, kind: String, name: String, rawSql: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        runWrites(
            database = database,
            statements = listOf(buildDropRoutineSql(database, kind, name), rawSql),
            successMessage = "Routine replaced",
            onDone = {
                _definitions.value = _definitions.value - "ROUTINE:$name"
                loadRoutines(database)
            }
        )
    }

    fun dropRoutine(database: String, kind: String, name: String, isLocked: Boolean = false) {
        if (isLocked) { _error.value = "Locked \u2014 unlock to write"; return }
        runWrites(
            database = database,
            statements = listOf(buildDropRoutineSql(database, kind, name)),
            successMessage = "Routine dropped",
            onDone = {
                _definitions.value = _definitions.value - "ROUTINE:$name"
                loadRoutines(database)
            }
        )
    }

    // ---------- tables (for trigger table picker) ----------

    fun loadTables(database: String) {
        viewModelScope.launch {
            try {
                val sql = "SHOW TABLES FROM `$database`"
                _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
                when (val result = connectionManager.executeQuery(sql)) {
                    is QueryResult.Success -> {
                        _tables.value = result.rows.map { it[0].toString() }.sorted()
                    }
                    is QueryResult.Error -> _error.value = result.message
                    else -> {}
                }
            } catch (e: Exception) {
                _error.value = "Failed to load tables: ${e.message}"
            }
        }
    }

    fun loadAll(database: String) {
        loadViews(database)
        loadTriggers(database)
        loadEvents(database)
        loadRoutines(database)
        loadTables(database)
    }

    // ---------- builders (pure, JVM-tested — preview == executed by construction) ----------

    fun buildCreateViewSql(database: String, name: String, definition: String, orReplace: Boolean): String =
        DbStructureSql.buildCreateViewSql(database, name, definition, orReplace)

    fun buildDropViewSql(database: String, view: String): String =
        DbStructureSql.buildDropViewSql(database, view)

    fun buildCreateTriggerSql(database: String, name: String, timing: String, event: String, table: String, body: String): String =
        DbStructureSql.buildCreateTriggerSql(database, name, timing, event, table, body)

    fun buildDropTriggerSql(database: String, trigger: String): String =
        DbStructureSql.buildDropTriggerSql(database, trigger)

    fun buildEventScheduleClause(
        mode: String,
        everyValue: String,
        everyUnit: String,
        at: String,
        starts: String,
        ends: String
    ): String = DbStructureSql.buildEventScheduleClause(mode, everyValue, everyUnit, at, starts, ends)

    fun buildCreateEventSql(database: String, name: String, schedule: String, preserve: Boolean, enabled: Boolean, body: String): String =
        DbStructureSql.buildCreateEventSql(database, name, schedule, preserve, enabled, body)

    fun buildAlterEventSql(database: String, name: String, schedule: String, preserve: Boolean, enabled: Boolean, body: String): String =
        DbStructureSql.buildAlterEventSql(database, name, schedule, preserve, enabled, body)

    fun buildToggleEventSql(database: String, name: String, enable: Boolean): String =
        DbStructureSql.buildToggleEventSql(database, name, enable)

    fun buildDropEventSql(database: String, event: String): String =
        DbStructureSql.buildDropEventSql(database, event)

    fun buildDropRoutineSql(database: String, kind: String, name: String): String =
        DbStructureSql.buildDropRoutineSql(database, kind, name)

    fun extractViewSelect(createViewDdl: String): String =
        DbStructureSql.extractViewSelect(createViewDdl)

    fun routineTemplate(kind: String, database: String, name: String): String =
        DbStructureSql.routineTemplate(kind, database, name)

    // ---------- helpers ----------

    /**
     * Executes statements sequentially, appending each to the query log.
     * Stops on the first error (no partial-apply hiding).
     */
    private fun runWrites(statements: List<String>, successMessage: String, onDone: () -> Unit, database: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val executed = mutableListOf<String>()
                for (sql in statements) {
                    _currentQuery.value = _currentQuery.value + QueryLogEntry(sql)
                    when (val result = connectionManager.executeQuery(sql)) {
                        is QueryResult.Error -> {
                            _error.value = result.message
                            return@launch
                        }
                        else -> executed.add(sql)
                    }
                }
                // Archive: one combined history entry per action (matches Confirm preview).
                if (executed.isNotEmpty()) recordWrite(executed.joinToString(";\n"), database)
                _operationSuccess.value = successMessage
                onDone()
            } catch (e: Exception) {
                _error.value = "Write failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    /** Persist a user-confirmed write to History panel (success only, like SQL editor). */
    private fun recordWrite(sql: String, database: String? = null) {
        val profileId = connectionManager.currentProfileId ?: return
        viewModelScope.launch {
            try { queryRepository.saveToHistory(profileId, sql, database) } catch (_: Exception) {}
        }
    }

    fun clearSuccess() {
        _operationSuccess.value = null
    }
}
