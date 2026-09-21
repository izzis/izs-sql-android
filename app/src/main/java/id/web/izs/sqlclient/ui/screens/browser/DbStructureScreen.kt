package id.web.izs.sqlclient.ui.screens.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.web.izs.sqlclient.data.remote.model.EventInfo
import id.web.izs.sqlclient.data.remote.model.RoutineInfo
import id.web.izs.sqlclient.data.remote.model.TriggerInfo
import id.web.izs.sqlclient.ui.components.AppTopBar
import id.web.izs.sqlclient.ui.components.CurrentQueryBar
import id.web.izs.sqlclient.ui.components.ReconnectBanner
import id.web.izs.sqlclient.ui.viewmodel.ConnectionViewModel
import id.web.izs.sqlclient.ui.viewmodel.DbStructureViewModel
import id.web.izs.sqlclient.util.rememberCopyToClipboard
import kotlinx.coroutines.delay

@Composable
fun DbStructureScreen(
    viewModel: DbStructureViewModel,
    connectionViewModel: ConnectionViewModel,
    database: String,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onBack: () -> Unit,
    onReconnect: (() -> Unit)? = null,
    isReconnecting: Boolean = false,
    topBarColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val views by viewModel.views.collectAsState()
    val triggers by viewModel.triggers.collectAsState()
    val events by viewModel.events.collectAsState()
    val routines by viewModel.routines.collectAsState()
    val tables by viewModel.tables.collectAsState()
    val definitions by viewModel.definitions.collectAsState()
    val eventBodies by viewModel.eventBodies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val success by viewModel.operationSuccess.collectAsState()
    val currentQuery by viewModel.currentQuery.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Views", "Triggers", "Events", "Routines")

    var showCreateView by remember { mutableStateOf(false) }
    var editingView by remember { mutableStateOf<String?>(null) }
    var viewToDelete by remember { mutableStateOf<String?>(null) }

    var showCreateTrigger by remember { mutableStateOf(false) }
    var editingTrigger by remember { mutableStateOf<TriggerInfo?>(null) }
    var triggerToDelete by remember { mutableStateOf<TriggerInfo?>(null) }

    var showCreateEvent by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<EventInfo?>(null) }
    var eventToDelete by remember { mutableStateOf<EventInfo?>(null) }

    var showCreateRoutine by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<RoutineInfo?>(null) }
    var routineToDelete by remember { mutableStateOf<RoutineInfo?>(null) }

    var pendingSql by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(database) {
        viewModel.loadAll(database)
    }

    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(success) {
        success?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
            viewModel.loadAll(database)
        }
    }

    val reconnectMessage by connectionViewModel.reconnectMessage.collectAsState()
    LaunchedEffect(reconnectMessage) {
        reconnectMessage?.let {
            delay(2000)
            connectionViewModel.clearReconnectMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = database,
                subtitle = "Database Structure",
                containerColor = topBarColor,
                onRefresh = { viewModel.loadAll(database) },
                isRefreshing = isLoading,
                isLocked = isLocked,
                showLock = true,
                onToggleLock = onToggleLock,
                onBack = onBack,
                onReconnect = onReconnect,
                isReconnecting = isReconnecting
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { CurrentQueryBar(queries = currentQuery) },
        floatingActionButton = {
            when (selectedTab) {
                0 -> StructureFab("Create View") { if (!isLocked) showCreateView = true }
                1 -> StructureFab("Create Trigger") { if (!isLocked) showCreateTrigger = true }
                2 -> StructureFab("Create Event") { if (!isLocked) showCreateEvent = true }
                3 -> StructureFab("Create Routine") { if (!isLocked) showCreateRoutine = true }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            ReconnectBanner(message = reconnectMessage)
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }

            when (selectedTab) {
                0 -> ViewsTab(
                    views = views,
                    definitions = definitions,
                    isLocked = isLocked,
                    isLoading = isLoading,
                    onExpand = { viewModel.loadViewDefinition(database, it) },
                    onEdit = { editingView = it },
                    onDelete = { viewToDelete = it }
                )
                1 -> TriggersTab(
                    triggers = triggers,
                    definitions = definitions,
                    isLocked = isLocked,
                    isLoading = isLoading,
                    onExpand = { viewModel.loadTriggerDefinition(database, it) },
                    onEdit = { editingTrigger = it },
                    onDelete = { triggerToDelete = it }
                )
                2 -> EventsTab(
                    events = events,
                    definitions = definitions,
                    isLocked = isLocked,
                    isLoading = isLoading,
                    onExpand = { viewModel.loadEventDefinition(database, it) },
                    onEdit = { editingEvent = it },
                    onDelete = { eventToDelete = it },
                    onToggle = { event, enable ->
                        if (!isLocked) {
                            val sql = viewModel.buildToggleEventSql(database, event.name, enable)
                            pendingSql = sql
                            pendingAction = {
                                viewModel.toggleEvent(database, event.name, enable, isLocked = isLocked)
                            }
                        }
                    }
                )
                3 -> RoutinesTab(
                    routines = routines,
                    definitions = definitions,
                    isLocked = isLocked,
                    isLoading = isLoading,
                    onExpand = { viewModel.loadRoutineDefinition(database, it.kind, it.name) },
                    onEdit = { editingRoutine = it },
                    onDelete = { routineToDelete = it }
                )
            }
        }
    }

    // ---------- view dialogs ----------

    if (showCreateView) {
        ViewDialog(
            title = "Create View",
            initialName = "",
            initialDefinition = "",
            nameEditable = true,
            confirmLabel = "Create",
            onDismiss = { showCreateView = false },
            onConfirm = { name, definition ->
                if (isLocked) { showCreateView = false; return@ViewDialog }
                val sql = viewModel.buildCreateViewSql(database, name, definition, orReplace = false)
                pendingSql = sql
                pendingAction = {
                    viewModel.createView(database, name, definition, orReplace = false, isLocked = isLocked)
                    showCreateView = false
                }
            }
        )
    }

    editingView?.let { viewName ->
        ViewDialog(
            title = "Edit View: $viewName",
            initialName = viewName,
            initialDefinition = definitions["VIEW:$viewName"]?.let { viewModel.extractViewSelect(it) } ?: "",
            nameEditable = false,
            confirmLabel = "Replace",
            onDismiss = { editingView = null },
            onConfirm = { name, definition ->
                if (isLocked) { editingView = null; return@ViewDialog }
                val sql = viewModel.buildCreateViewSql(database, name, definition, orReplace = true)
                pendingSql = sql
                pendingAction = {
                    viewModel.createView(database, name, definition, orReplace = true, isLocked = isLocked)
                    editingView = null
                }
            }
        )
    }

    viewToDelete?.let { viewName ->
        val dropSql = viewModel.buildDropViewSql(database, viewName)
        DropConfirmDialog(
            title = "Drop View",
            message = "Drop view `$viewName` from `$database`? Objects depending on it may break.",
            sql = dropSql,
            isLocked = isLocked,
            onDismiss = { viewToDelete = null },
            onDrop = {
                pendingSql = dropSql
                pendingAction = { viewModel.dropView(database, viewName, isLocked = isLocked); viewToDelete = null }
                viewToDelete = null
            }
        )
    }

    // ---------- trigger dialogs ----------

    if (showCreateTrigger) {
        TriggerDialog(
            title = "Create Trigger",
            tables = tables,
            initial = null,
            nameEditable = true,
            confirmLabel = "Create",
            onDismiss = { showCreateTrigger = false },
            onConfirm = { name, timing, event, table, body ->
                if (isLocked) { showCreateTrigger = false; return@TriggerDialog }
                val sql = viewModel.buildCreateTriggerSql(database, name, timing, event, table, body)
                pendingSql = sql
                pendingAction = {
                    viewModel.createTrigger(database, name, timing, event, table, body, isLocked = isLocked)
                    showCreateTrigger = false
                }
            }
        )
    }

    editingTrigger?.let { trg ->
        TriggerDialog(
            title = "Edit Trigger: ${trg.name}",
            tables = tables,
            initial = TriggerInitial(trg.name, trg.timing, trg.event, trg.table, trg.statement),
            nameEditable = false,
            confirmLabel = "Replace",
            onDismiss = { editingTrigger = null },
            onConfirm = { name, timing, event, table, body ->
                if (isLocked) { editingTrigger = null; return@TriggerDialog }
                // No CREATE OR REPLACE for triggers — preview shows DROP + CREATE.
                val drop = viewModel.buildDropTriggerSql(database, name)
                val create = viewModel.buildCreateTriggerSql(database, name, timing, event, table, body)
                pendingSql = "$drop;\n$create"
                pendingAction = {
                    viewModel.recreateTrigger(database, name, timing, event, table, body, isLocked = isLocked)
                    editingTrigger = null
                }
            }
        )
    }

    triggerToDelete?.let { trg ->
        val dropSql = viewModel.buildDropTriggerSql(database, trg.name)
        DropConfirmDialog(
            title = "Drop Trigger",
            message = "Drop trigger `${trg.name}` from `$database`?",
            sql = dropSql,
            isLocked = isLocked,
            onDismiss = { triggerToDelete = null },
            onDrop = {
                pendingSql = dropSql
                pendingAction = { viewModel.dropTrigger(database, trg.name, isLocked = isLocked); triggerToDelete = null }
                triggerToDelete = null
            }
        )
    }

    // ---------- event dialogs ----------

    if (showCreateEvent) {
        EventDialog(
            title = "Create Event",
            initial = null,
            onDismiss = { showCreateEvent = false },
            onConfirm = { name, schedule, preserve, enabled, body ->
                if (isLocked) { showCreateEvent = false; return@EventDialog }
                val sql = viewModel.buildCreateEventSql(database, name, schedule, preserve, enabled, body)
                pendingSql = sql
                pendingAction = {
                    viewModel.createEvent(database, name, schedule, preserve, enabled, body, isLocked = isLocked)
                    showCreateEvent = false
                }
            }
        )
    }

    editingEvent?.let { ev ->
        val initial = EventInitial(
            name = ev.name,
            mode = if (ev.eventType == "RECURRING") "EVERY" else "AT",
            everyValue = ev.intervalValue ?: "",
            everyUnit = ev.intervalField ?: "",
            at = ev.executeAt ?: "",
            starts = ev.starts ?: "",
            ends = ev.ends ?: "",
            preserve = false,
            enabled = ev.status == "ENABLED",
            body = eventBodies[ev.name] ?: ""
        )
        EventDialog(
            title = "Edit Event: ${ev.name}",
            initial = initial,
            onDismiss = { editingEvent = null },
            onConfirm = { name, schedule, preserve, enabled, body ->
                if (isLocked) { editingEvent = null; return@EventDialog }
                val sql = viewModel.buildAlterEventSql(database, name, schedule, preserve, enabled, body)
                pendingSql = sql
                pendingAction = {
                    viewModel.alterEvent(database, name, schedule, preserve, enabled, body, isLocked = isLocked)
                    editingEvent = null
                }
            }
        )
    }

    eventToDelete?.let { ev ->
        val dropSql = viewModel.buildDropEventSql(database, ev.name)
        DropConfirmDialog(
            title = "Drop Event",
            message = "Drop event `${ev.name}` from `$database`?",
            sql = dropSql,
            isLocked = isLocked,
            onDismiss = { eventToDelete = null },
            onDrop = {
                pendingSql = dropSql
                pendingAction = { viewModel.dropEvent(database, ev.name, isLocked = isLocked); eventToDelete = null }
                eventToDelete = null
            }
        )
    }

    // ---------- routine dialogs ----------

    if (showCreateRoutine) {
        RoutineDialog(
            title = "Create Routine",
            kinds = listOf("PROCEDURE", "FUNCTION"),
            initialKind = "PROCEDURE",
            initialSql = "",
            kindEditable = true,
            confirmLabel = "Create",
            database = database,
            onDismiss = { showCreateRoutine = false },
            onConfirm = { _, sql ->
                if (isLocked) { showCreateRoutine = false; return@RoutineDialog }
                pendingSql = sql
                pendingAction = {
                    viewModel.createRoutine(database, sql, isLocked = isLocked)
                    showCreateRoutine = false
                }
            }
        )
    }

    editingRoutine?.let { routine ->
        RoutineDialog(
            title = "Edit ${routine.kind.lowercase().replaceFirstChar { it.uppercase() }}: ${routine.name}",
            kinds = listOf(routine.kind),
            initialKind = routine.kind,
            initialSql = definitions["ROUTINE:${routine.name}"] ?: "",
            kindEditable = false,
            confirmLabel = "Replace",
            database = database,
            onDismiss = { editingRoutine = null },
            onConfirm = { kind, sql ->
                if (isLocked) { editingRoutine = null; return@RoutineDialog }
                // No CREATE OR REPLACE for routines — preview shows DROP + CREATE.
                val drop = viewModel.buildDropRoutineSql(database, kind, routine.name)
                pendingSql = "$drop;\n$sql"
                pendingAction = {
                    viewModel.recreateRoutine(database, kind, routine.name, sql, isLocked = isLocked)
                    editingRoutine = null
                }
            }
        )
    }

    routineToDelete?.let { routine ->
        val dropSql = viewModel.buildDropRoutineSql(database, routine.kind, routine.name)
        DropConfirmDialog(
            title = "Drop ${routine.kind.lowercase().replaceFirstChar { it.uppercase() }}",
            message = "Drop ${routine.kind.lowercase()} `${routine.name}` from `$database`? Objects calling it may break.",
            sql = dropSql,
            isLocked = isLocked,
            onDismiss = { routineToDelete = null },
            onDrop = {
                pendingSql = dropSql
                pendingAction = { viewModel.dropRoutine(database, routine.kind, routine.name, isLocked = isLocked); routineToDelete = null }
                routineToDelete = null
            }
        )
    }

    // ---------- generic write preview (preview == executed, explicit Execute tap) ----------

    pendingSql?.let { sql ->
        val copyToClipboard = rememberCopyToClipboard()
        AlertDialog(
            onDismissRequest = { pendingSql = null; pendingAction = null },
            title = { Text("Confirm Write") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Query to be executed:", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(sql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { copyToClipboard(sql, "Copied") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("Copy")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { val a = pendingAction; pendingSql = null; pendingAction = null; a?.invoke() }) {
                    Text("Execute", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingSql = null; pendingAction = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StructureFab(label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Default.Add, contentDescription = label) },
        text = { Text(label) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
}

// ---------- tabs ----------

@Composable
private fun ViewsTab(
    views: List<String>,
    definitions: Map<String, String>,
    isLocked: Boolean,
    isLoading: Boolean,
    onExpand: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    if (views.isEmpty()) {
        EmptyObjectsBox(isLoading = isLoading, text = "No views in this database")
        return
    }
    var expanded by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(views) { view ->
            ObjectCard(
                title = view,
                badges = "VIEW",
                expanded = expanded == view,
                onToggleExpand = {
                    expanded = if (expanded == view) null else view
                    if (expanded == view) onExpand(view)
                },
                ddl = definitions["VIEW:$view"],
                isLocked = isLocked,
                onEdit = { onEdit(view) },
                onDelete = { onDelete(view) }
            )
        }
    }
}

@Composable
private fun TriggersTab(
    triggers: List<TriggerInfo>,
    definitions: Map<String, String>,
    isLocked: Boolean,
    isLoading: Boolean,
    onExpand: (String) -> Unit,
    onEdit: (TriggerInfo) -> Unit,
    onDelete: (TriggerInfo) -> Unit
) {
    if (triggers.isEmpty()) {
        EmptyObjectsBox(isLoading = isLoading, text = "No triggers in this database")
        return
    }
    var expanded by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(triggers) { trg ->
            ObjectCard(
                title = trg.name,
                badges = "${trg.timing} ${trg.event} ON ${trg.table}",
                expanded = expanded == trg.name,
                onToggleExpand = {
                    expanded = if (expanded == trg.name) null else trg.name
                    if (expanded == trg.name) onExpand(trg.name)
                },
                ddl = definitions["TRIGGER:${trg.name}"],
                isLocked = isLocked,
                onEdit = { onEdit(trg) },
                onDelete = { onDelete(trg) }
            )
        }
    }
}

@Composable
private fun EventsTab(
    events: List<EventInfo>,
    definitions: Map<String, String>,
    isLocked: Boolean,
    isLoading: Boolean,
    onExpand: (String) -> Unit,
    onEdit: (EventInfo) -> Unit,
    onDelete: (EventInfo) -> Unit,
    onToggle: (EventInfo, Boolean) -> Unit
) {
    if (events.isEmpty()) {
        EmptyObjectsBox(isLoading = isLoading, text = "No events in this database")
        return
    }
    var expanded by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(events) { ev ->
            val schedule = if (ev.eventType == "RECURRING") {
                "EVERY ${ev.intervalValue ?: "?"} ${ev.intervalField ?: ""}".trim()
            } else {
                "AT ${ev.executeAt ?: "?"}"
            }
            ObjectCard(
                title = ev.name,
                badges = "$schedule • ${ev.status}",
                expanded = expanded == ev.name,
                onToggleExpand = {
                    expanded = if (expanded == ev.name) null else ev.name
                    if (expanded == ev.name) onExpand(ev.name)
                },
                ddl = definitions["EVENT:${ev.name}"],
                isLocked = isLocked,
                onEdit = { onEdit(ev) },
                onDelete = { onDelete(ev) },
                extraActions = {
                    TextButton(
                        enabled = !isLocked,
                        onClick = { onToggle(ev, ev.status != "ENABLED") }
                    ) {
                        Text(if (ev.status == "ENABLED") "Disable" else "Enable")
                    }
                }
            )
        }
    }
}

@Composable
private fun RoutinesTab(
    routines: List<RoutineInfo>,
    definitions: Map<String, String>,
    isLocked: Boolean,
    isLoading: Boolean,
    onExpand: (RoutineInfo) -> Unit,
    onEdit: (RoutineInfo) -> Unit,
    onDelete: (RoutineInfo) -> Unit
) {
    if (routines.isEmpty()) {
        EmptyObjectsBox(isLoading = isLoading, text = "No routines in this database")
        return
    }
    var expanded by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(routines) { routine ->
            ObjectCard(
                title = routine.name,
                badges = routine.kind,
                expanded = expanded == routine.name,
                onToggleExpand = {
                    expanded = if (expanded == routine.name) null else routine.name
                    if (expanded == routine.name) onExpand(routine)
                },
                ddl = definitions["ROUTINE:${routine.name}"],
                isLocked = isLocked,
                onEdit = { onEdit(routine) },
                onDelete = { onDelete(routine) }
            )
        }
    }
}

@Composable
private fun EmptyObjectsBox(isLoading: Boolean, text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        } else {
            Text(text = text, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shared expandable card: title + badges, lazy DDL with copy, lock-gated Edit/Delete. */
@Composable
private fun ObjectCard(
    title: String,
    badges: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    ddl: String?,
    isLocked: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    extraActions: @Composable RowScope.() -> Unit = {}
) {
    val copyToClipboard = rememberCopyToClipboard()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClickLabel = "Show definition", onClick = onToggleExpand),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            Text(text = badges, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (ddl != null) {
                    SelectionContainer {
                        Text(text = ddl, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            copyToClipboard(ddl, "Copied")
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy")
                        }
                        TextButton(enabled = !isLocked, onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit")
                        }
                        TextButton(enabled = !isLocked, onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                        extraActions()
                    }
                } else {
                    Text(text = "Loading definition…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DropConfirmDialog(
    title: String,
    message: String,
    sql: String,
    isLocked: Boolean,
    onDismiss: () -> Unit,
    onDrop: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Text(sql, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isLocked, onClick = onDrop) {
                Text("Drop", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- dialogs ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {},
            readOnly = true, label = { Text(label) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun SqlEditorField(label: String, value: String, onChange: (String) -> Unit, minLines: Int = 6) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, minLines = minLines,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ViewDialog(
    title: String,
    initialName: String,
    initialDefinition: String,
    nameEditable: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, definition: String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var definition by remember(initialDefinition) { mutableStateOf(initialDefinition) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("View Name") }, singleLine = true, enabled = nameEditable,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                SqlEditorField(label = "SELECT statement", value = definition, onChange = { definition = it })
                Spacer(modifier = Modifier.height(4.dp))
                Text("Executed as CREATE [OR REPLACE] VIEW `db`.`name` AS … — the preview shows the exact SQL.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), definition.trim()) },
                enabled = name.isNotBlank() && definition.isNotBlank()
            ) {
                Icon(
                    if (confirmLabel == "Create") Icons.Default.Add else Icons.Default.Edit,
                    contentDescription = null, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel")
            }
        }
    )
}

private data class TriggerInitial(
    val name: String,
    val timing: String,
    val event: String,
    val table: String,
    val body: String
)

@Composable
private fun TriggerDialog(
    title: String,
    tables: List<String>,
    initial: TriggerInitial?,
    nameEditable: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, timing: String, event: String, table: String, body: String) -> Unit
) {
    var name by remember(initial?.name ?: "") { mutableStateOf(initial?.name ?: "") }
    var timing by remember(initial?.timing ?: "BEFORE") { mutableStateOf(initial?.timing ?: "BEFORE") }
    var event by remember(initial?.event ?: "INSERT") { mutableStateOf(initial?.event ?: "INSERT") }
    var table by remember(initial?.table ?: tables.firstOrNull().orEmpty()) {
        mutableStateOf(initial?.table ?: tables.firstOrNull().orEmpty())
    }
    var body by remember(initial?.body ?: "") { mutableStateOf(initial?.body ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Trigger Name") }, singleLine = true, enabled = nameEditable,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OptionsDropdown(label = "Timing", options = listOf("BEFORE", "AFTER"), selected = timing, onSelect = { timing = it })
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OptionsDropdown(label = "Event", options = listOf("INSERT", "UPDATE", "DELETE"), selected = event, onSelect = { event = it })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (tables.isNotEmpty()) {
                    OptionsDropdown(label = "Table", options = tables, selected = table, onSelect = { table = it })
                } else {
                    OutlinedTextField(
                        value = table, onValueChange = { table = it },
                        label = { Text("Table") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                SqlEditorField(label = "Body (FOR EACH ROW …)", value = body, onChange = { body = it })
                Spacer(modifier = Modifier.height(4.dp))
                Text("Multi-statement bodies (BEGIN … END) are sent as one statement.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), timing, event, table.trim(), body.trim()) },
                enabled = name.isNotBlank() && table.isNotBlank() && body.isNotBlank()
            ) {
                Icon(
                    if (confirmLabel == "Create") Icons.Default.Add else Icons.Default.Edit,
                    contentDescription = null, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel")
            }
        }
    )
}

private data class EventInitial(
    val name: String,
    val mode: String,
    val everyValue: String,
    val everyUnit: String,
    val at: String,
    val starts: String,
    val ends: String,
    val preserve: Boolean,
    val enabled: Boolean,
    val body: String
)

private val EVENT_UNITS = listOf(
    "YEAR", "QUARTER", "MONTH", "WEEK", "DAY", "HOUR", "MINUTE", "SECOND",
    "YEAR_MONTH", "DAY_HOUR", "DAY_MINUTE", "DAY_SECOND", "HOUR_MINUTE", "HOUR_SECOND", "MINUTE_SECOND"
)

@Composable
private fun EventDialog(
    title: String,
    initial: EventInitial?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, schedule: String, preserve: Boolean, enabled: Boolean, body: String) -> Unit
) {
    var name by remember(initial?.name ?: "") { mutableStateOf(initial?.name ?: "") }
    var mode by remember(initial?.mode ?: "EVERY") { mutableStateOf(initial?.mode ?: "EVERY") }
    var everyValue by remember(initial?.everyValue ?: "1") { mutableStateOf(initial?.everyValue ?: "1") }
    var everyUnit by remember(initial?.everyUnit ?: "DAY") { mutableStateOf(initial?.everyUnit ?: "DAY") }
    var at by remember(initial?.at ?: "") { mutableStateOf(initial?.at ?: "") }
    var starts by remember(initial?.starts ?: "") { mutableStateOf(initial?.starts ?: "") }
    var ends by remember(initial?.ends ?: "") { mutableStateOf(initial?.ends ?: "") }
    var preserve by remember(initial?.preserve ?: false) { mutableStateOf(initial?.preserve ?: false) }
    var enabled by remember(initial?.enabled ?: true) { mutableStateOf(initial?.enabled ?: true) }
    var body by remember(initial?.body ?: "") { mutableStateOf(initial?.body ?: "") }
    val nameEditable = initial == null

    // NOTE: the schedule fragment is assembled here for live preview purposes only;
    // the executed SQL is rebuilt by the ViewModel builder from the same parts.
    val scheduleValid = if (mode == "AT") at.isNotBlank() else everyValue.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Event Name") }, singleLine = true, enabled = nameEditable,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OptionsDropdown(label = "Schedule", options = listOf("EVERY", "AT"), selected = mode, onSelect = { mode = it })
                Spacer(modifier = Modifier.height(8.dp))
                if (mode == "EVERY") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = everyValue, onValueChange = { everyValue = it },
                            label = { Text("Every") }, singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            OptionsDropdown(label = "Unit", options = EVENT_UNITS, selected = everyUnit, onSelect = { everyUnit = it })
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = at, onValueChange = { at = it },
                        label = { Text("At (YYYY-MM-DD HH:MM:SS)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = starts, onValueChange = { starts = it },
                    label = { Text("Starts (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ends, onValueChange = { ends = it },
                    label = { Text("Ends (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = preserve, onCheckedChange = { preserve = it })
                    Text("On completion preserve", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                    Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                SqlEditorField(label = "Body (DO …)", value = body, onChange = { body = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val schedule = id.web.izs.sqlclient.util.DbStructureSql.buildEventScheduleClause(
                        mode, everyValue, everyUnit, at, starts, ends
                    )
                    onConfirm(name.trim(), schedule, preserve, enabled, body.trim())
                },
                enabled = name.isNotBlank() && scheduleValid && body.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (initial == null) "Create" else "Alter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RoutineDialog(
    title: String,
    kinds: List<String>,
    initialKind: String,
    initialSql: String,
    kindEditable: Boolean,
    confirmLabel: String,
    database: String,
    onDismiss: () -> Unit,
    onConfirm: (kind: String, sql: String) -> Unit
) {
    var kind by remember(initialKind) { mutableStateOf(initialKind) }
    var sql by remember(initialKind, initialSql) {
        mutableStateOf(
            initialSql.ifBlank {
                id.web.izs.sqlclient.util.DbStructureSql.routineTemplate(initialKind, database, "new_routine")
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (kindEditable) {
                    OptionsDropdown(label = "Kind", options = kinds, selected = kind, onSelect = {
                        kind = it
                        if (sql.isBlank()) {
                            sql = id.web.izs.sqlclient.util.DbStructureSql.routineTemplate(it, database, "new_routine")
                        }
                    })
                    Spacer(modifier = Modifier.height(8.dp))
                }
                SqlEditorField(label = "Routine SQL (executed as-is)", value = sql, onChange = { sql = it }, minLines = 10)
                Spacer(modifier = Modifier.height(4.dp))
                Text("No builder here on purpose: routine syntax is free-form. The preview shows exactly what runs.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(kind, sql.trim()) },
                enabled = sql.trim().startsWith("CREATE", ignoreCase = true)
            ) {
                Icon(
                    if (confirmLabel == "Create") Icons.Default.Add else Icons.Default.Edit,
                    contentDescription = null, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel")
            }
        }
    )
}
