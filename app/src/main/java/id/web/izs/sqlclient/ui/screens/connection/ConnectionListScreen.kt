package id.web.izs.sqlclient.ui.screens.connection

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.web.izs.sqlclient.R
import id.web.izs.sqlclient.data.local.entity.ConnectionProfileEntity
import id.web.izs.sqlclient.data.repository.ConnectionRepository
import id.web.izs.sqlclient.ui.components.ConnectionCard
import id.web.izs.sqlclient.ui.viewmodel.ConnectionViewModel
import id.web.izs.sqlclient.util.ThemeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionListScreen(
    viewModel: ConnectionViewModel,
    themeManager: ThemeManager,
    onAddConnection: () -> Unit,
    onEditConnection: (Long) -> Unit,
    onConnect: (ConnectionProfileEntity) -> Unit,
    onNavigateToBrowser: () -> Unit = {}
) {
    val profiles by viewModel.profiles.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<ConnectionProfileEntity?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val isDarkMode by themeManager.isDarkMode.collectAsState()
    val followSystem by themeManager.followSystem.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val connectingProfileId by viewModel.connectingProfileId.collectAsState()
    val connectionMessage by viewModel.connectionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportImportMessage by viewModel.exportImportMessage.collectAsState()
    val importConflict by viewModel.importConflict.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportConfirm by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExportPassword by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.clearConnectingState()
    }

    LaunchedEffect(exportImportMessage) {
        exportImportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearExportImportMessage()
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null && pendingExportPassword.isNotEmpty()) {
            viewModel.exportEncrypted(uri, pendingExportPassword)
            pendingExportPassword = ""
        }
    }
    val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // Smart: .dbp/data-sources.json is processed directly, .enc asks for its password.
            viewModel.startSmartImport(uri)
        }
    }

    val requestEncPassword by viewModel.requestEncPassword.collectAsState()
    LaunchedEffect(requestEncPassword) {
        requestEncPassword?.let { uri ->
            viewModel.clearEncPasswordRequest()
            pendingImportUri = uri
            importPassword = ""
            showImportDialog = true
        }
    }

    LaunchedEffect(connectionState) {
        when (connectionState) {
            is id.web.izs.sqlclient.ui.viewmodel.ConnectionState.Connected -> {
                onNavigateToBrowser()
            }
            is id.web.izs.sqlclient.ui.viewmodel.ConnectionState.Error -> {
                val error = (connectionState as id.web.izs.sqlclient.ui.viewmodel.ConnectionState.Error).message
                snackbarHostState.showSnackbar(error)
                viewModel.clearConnectionError()
            }
            else -> {}
        }
    }

    val filteredProfiles = remember(profiles, searchQuery) {
        if (searchQuery.isBlank()) profiles
        else profiles.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.host.contains(searchQuery, ignoreCase = true) ||
                    (it.sshHost?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Database glyph on transparent, tinted with the theme
                        // surface color so it stays visible in both light and
                        // dark mode. Dedicated asset (glyph fills ~89% of the
                        // canvas); the launcher foreground keeps its padding
                        // for the green tile and is untouched.
                        Image(
                            painter = painterResource(R.drawable.ic_header_logo),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "izs SQL",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = {
                        exportPassword = ""
                        exportConfirm = ""
                        showExportDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "Export"
                        )
                    }
                    IconButton(onClick = {
                        openDocLauncher.launch(arrayOf("application/octet-stream", "application/zip", "application/json", "*/*"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Import (.enc / DBeaver .dbp)"
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            // Fixed filter + New (izs-ssh parity): a slim 48.dp box with a boxy
            // "+ New" button docked on its right. Always visible, never overlays.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 12.dp),
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Filter by name, host, or tunnel",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        },
                    )
                }
                Button(
                    onClick = onAddConnection,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("New", modifier = Modifier.padding(start = 4.dp))
                }
            }

        if (filteredProfiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No connections found" else "No connections yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Tap + New above to add a connection",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredProfiles, key = { it.id }) { profile ->
                    ConnectionCard(
                        profile = profile,
                        onClick = { onConnect(profile) },
                        onEdit = { onEditConnection(profile.id) },
                        onDelete = { showDeleteDialog = profile },
                        isConnecting = connectingProfileId == profile.id,
                        statusMessage = if (connectingProfileId == profile.id) connectionMessage else ""
                    )
                }
            }
        }
        }
    }

    showDeleteDialog?.let { profile ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Connection") },
            text = { Text("Are you sure you want to delete \"${profile.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProfile(profile)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            isDarkMode = isDarkMode,
            followSystem = followSystem,
            onToggleDarkMode = { themeManager.toggleTheme() },
            onToggleFollowSystem = { themeManager.setFollowSystem(it) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showExportDialog) {
        val exportValid = exportPassword.length >= 8 && exportPassword == exportConfirm
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export profiles") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The file will be encrypted with a master password. The password is not stored in the app (SSH-key-like).", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        label = { Text("Master password (min 8)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = exportConfirm,
                        onValueChange = { exportConfirm = it },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (exportPassword.isNotEmpty() && exportPassword.length < 8) Text("Min 8 characters", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (exportConfirm.isNotEmpty() && exportPassword != exportConfirm) Text("Passwords do not match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = exportValid,
                    onClick = {
                        pendingExportPassword = exportPassword
                        showExportDialog = false
                        val name = "profiles_export_${System.currentTimeMillis()}.enc"
                        createDocLauncher.launch(name)
                    }
                ) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel") } }
        )
    }

    if (showImportDialog) {
        val importValid = importPassword.length >= 8
        AlertDialog(
            onDismissRequest = { showImportDialog = false; pendingImportUri = null },
            title = { Text("Import profiles") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the master password to open the .enc file", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text("Master password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (importPassword.isNotEmpty() && importPassword.length < 8) Text("Min 8 characters", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importValid && pendingImportUri != null,
                    onClick = {
                        val uri = pendingImportUri
                        showImportDialog = false
                        pendingImportUri = null
                        val pwd = importPassword
                        importPassword = ""
                        if (uri != null) viewModel.startImportPreview(uri, pwd)
                    }
                ) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImportDialog = false; pendingImportUri = null }) { Text("Cancel") } }
        )
    }

    // Per-profile conflict dialog: 3 buttons + checkbox All, tap outside = skip remaining
    val conflict = importConflict
    if (conflict is ConnectionViewModel.ImportConflict.Awaiting) {
        var applyAllChecked by remember(conflict.index) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { viewModel.onImportDismiss() },
            title = { Text("Profile already exists (${conflict.index + 1}/${conflict.total})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Incoming: \"${conflict.current.profile.name}\" @ ${conflict.current.profile.host}:${conflict.current.profile.port} (${conflict.current.profile.username})", style = MaterialTheme.typography.bodySmall)
                    conflict.existing?.let {
                        Text("Existing id=${it.id}: \"${it.name}\" @ ${it.host}:${it.port} (${it.username})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Choose action for this profile:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = applyAllChecked,
                            onCheckedChange = { applyAllChecked = it }
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text("Apply to all remaining duplicates", style = MaterialTheme.typography.bodySmall)
                            Text("(${conflict.total - conflict.index - 1} left)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { viewModel.onImportDecision(ConnectionRepository.ImportAction.REPLACE, applyAllChecked) }) { Text("Replace") }
                    TextButton(onClick = { viewModel.onImportDecision(ConnectionRepository.ImportAction.SKIP, applyAllChecked) }) { Text("Skip") }
                    TextButton(onClick = { viewModel.onImportDecision(ConnectionRepository.ImportAction.INSERT_AS_NEW, applyAllChecked) }) { Text("Insert") }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onImportDismiss() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsDialog(
    isDarkMode: Boolean,
    followSystem: Boolean,
    onToggleDarkMode: () -> Unit,
    onToggleFollowSystem: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Follow system theme", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = followSystem,
                        onCheckedChange = onToggleFollowSystem
                    )
                }

                if (!followSystem) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark mode", style = MaterialTheme.typography.bodyMedium)
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = if (isDarkMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                        )
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { onToggleDarkMode() }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
