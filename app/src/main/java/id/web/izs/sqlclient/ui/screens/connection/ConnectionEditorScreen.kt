package id.web.izs.sqlclient.ui.screens.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import id.web.izs.sqlclient.data.local.entity.ConnectionProfileEntity
import id.web.izs.sqlclient.ui.theme.ConnectionColors
import id.web.izs.sqlclient.ui.viewmodel.ConnectionViewModel
import id.web.izs.sqlclient.ui.viewmodel.SshTestResult
import id.web.izs.sqlclient.ui.viewmodel.TestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionEditorScreen(
    viewModel: ConnectionViewModel,
    existingProfile: ConnectionProfileEntity? = null,
    onSave: (ConnectionProfileEntity, String?, String?, String?) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(existingProfile?.name ?: "") }
    var host by remember { mutableStateOf(existingProfile?.host ?: "localhost") }
    var port by remember { mutableStateOf(existingProfile?.port?.toString() ?: "3306") }
    var database by remember { mutableStateOf(existingProfile?.database ?: "") }
    var username by remember { mutableStateOf(existingProfile?.username ?: "root") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isReadonly by remember(existingProfile?.id) { mutableStateOf(existingProfile?.isReadonly ?: false) }

    val hasStoredPassword = remember(existingProfile?.id) {
        existingProfile?.id?.let { viewModel.hasStoredPassword(it) } ?: false
    }

    val initialColorIndex = remember {
        val color = existingProfile?.color ?: "#2196F3"
        ConnectionColors.indexOfFirst { c ->
            val hex = String.format("#%06X", 0xFFFFFF and c.hashCode())
            hex.equals(color, ignoreCase = true)
        }.coerceAtLeast(0)
    }
    var selectedColorIndex by remember { mutableIntStateOf(initialColorIndex) }

    var useSshTunnel by remember { mutableStateOf(existingProfile?.useSshTunnel ?: false) }
    var sshHost by remember { mutableStateOf(existingProfile?.sshHost ?: "") }
    var sshPort by remember { mutableStateOf(existingProfile?.sshPort?.toString() ?: "22") }
    var sshUsername by remember { mutableStateOf(existingProfile?.sshUsername ?: "") }
    var sshPassword by remember { mutableStateOf("") }
    var sshPasswordVisible by remember { mutableStateOf(false) }
    var sshKeyPath by remember { mutableStateOf(existingProfile?.sshKeyPath ?: "") }
    var sshPassphrase by remember { mutableStateOf("") }
    var sshPassphraseVisible by remember { mutableStateOf(false) }

    val hasStoredSshPassword = remember(existingProfile?.id) {
        existingProfile?.id?.let { viewModel.hasStoredSshPassword(it) } ?: false
    }
    val hasStoredSshPassphrase = remember(existingProfile?.id) {
        existingProfile?.id?.let { viewModel.hasStoredSshPassphrase(it) } ?: false
    }

    var useSsl by remember { mutableStateOf(existingProfile?.useSsl ?: false) }

    val testResult by viewModel.testResult.collectAsState()
    val sshTestResult by viewModel.sshTestResult.collectAsState()

    fun currentProfile() = buildProfile(
        existingProfile?.id ?: 0L,
        name, host, port.toIntOrNull() ?: 3306,
        database.ifBlank { null }, username, isReadonly,
        String.format("#%06X", 0xFFFFFF and ConnectionColors[selectedColorIndex].hashCode()),
        useSshTunnel, sshHost, sshPort.toIntOrNull() ?: 22,
        sshUsername, sshKeyPath, useSsl
    )
    val canSubmit = name.isNotBlank() && host.isNotBlank() && username.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (existingProfile != null) "Edit Connection" else "New Connection",
                            fontWeight = FontWeight.SemiBold
                        )
                        if (name.isNotBlank()) {
                            Text(
                                text = "$username@$host:$port",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ConnectionColors[selectedColorIndex],
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---- Server ----
            SectionCard(title = "Server", icon = Icons.Default.Dns) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Connection Name") },
                    placeholder = { DimPlaceholder("Production DB") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = host, onValueChange = { host = it },
                        label = { Text("Host") },
                        placeholder = { DimPlaceholder("192.168.1.10") },
                        modifier = Modifier.weight(1f), singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = port, onValueChange = { port = it },
                        label = { Text("Port") },
                        modifier = Modifier.width(96.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, shape = RoundedCornerShape(14.dp)
                    )
                }
                OutlinedTextField(
                    value = database, onValueChange = { database = it },
                    label = { Text("Database (opt.)") },
                    placeholder = { DimPlaceholder("mydb") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // ---- Credentials ----
            SectionCard(title = "Credentials", icon = Icons.Default.VpnKey) {
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                PasswordField(
                    value = password, onValueChange = { password = it },
                    label = "Password", visible = passwordVisible,
                    onToggleVisible = { passwordVisible = !passwordVisible },
                    hint = if (existingProfile != null && hasStoredPassword && password.isEmpty())
                        "Saved — leave blank to keep, or type a new one" else null
                )
            }

            // ---- Options ----
            SectionCard(title = "Options", icon = Icons.Default.Tune) {
                SwitchRow(
                    title = "Read-only session",
                    subtitle = "Lock writes by default on connect",
                    checked = isReadonly, onCheckedChange = { isReadonly = it }
                )
                SwitchRow(
                    title = "Use SSL/TLS",
                    subtitle = "Encrypted connection to the server",
                    checked = useSsl, onCheckedChange = { useSsl = it }
                )
                SwitchRow(
                    title = "Use SSH Tunnel",
                    subtitle = "Connect via an SSH bastion host",
                    checked = useSshTunnel,
                    onCheckedChange = { useSshTunnel = it; viewModel.clearSshTestResult() }
                )
            }

            // ---- SSH ----
            AnimatedVisibility(visible = useSshTunnel) {
                SectionCard(title = "SSH Tunnel", icon = Icons.Default.Terminal) {
                    OutlinedTextField(
                        value = sshHost, onValueChange = { sshHost = it },
                        label = { Text("SSH Host") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sshPort, onValueChange = { sshPort = it },
                            label = { Text("Port") },
                            modifier = Modifier.width(96.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, shape = RoundedCornerShape(14.dp)
                        )
                        OutlinedTextField(
                            value = sshUsername, onValueChange = { sshUsername = it },
                            label = { Text("SSH Username") },
                            modifier = Modifier.weight(1f), singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                    PasswordField(
                        value = sshPassword, onValueChange = { sshPassword = it },
                        label = "SSH Password", visible = sshPasswordVisible,
                        onToggleVisible = { sshPasswordVisible = !sshPasswordVisible },
                        hint = if (existingProfile != null && hasStoredSshPassword && sshPassword.isEmpty())
                            "Saved — leave blank to keep, or type a new one" else null
                    )
                    OutlinedTextField(
                        value = sshKeyPath, onValueChange = { sshKeyPath = it },
                        label = { Text("Key Path (opt.)") },
                        placeholder = { DimPlaceholder("/sdcard/keys/id_rsa") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    // Full width: half-width clips the value next to the visibility icon.
                    PasswordField(
                        value = sshPassphrase, onValueChange = { sshPassphrase = it },
                        label = "Key Passphrase (opt.)", visible = sshPassphraseVisible,
                        onToggleVisible = { sshPassphraseVisible = !sshPassphraseVisible },
                        hint = if (existingProfile != null && hasStoredSshPassphrase && sshPassphrase.isEmpty())
                            "Saved — blank keeps current" else null
                    )
                    FilledTonalButton(
                        onClick = {
                            viewModel.clearSshTestResult()
                            viewModel.testSshConnection(
                                sshHost = sshHost,
                                sshPort = sshPort.toIntOrNull() ?: 22,
                                sshUsername = sshUsername,
                                sshPassword = sshPassword.ifBlank { null },
                                sshKeyPath = sshKeyPath.ifBlank { null },
                                sshPassphrase = sshPassphrase.ifBlank { null }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = sshHost.isNotBlank() && sshUsername.isNotBlank() && sshTestResult !is SshTestResult.Loading
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Test SSH")
                    }
                    when (val result = sshTestResult) {
                        is SshTestResult.Loading -> ResultBanner(loading = "Testing SSH connection…")
                        is SshTestResult.Success -> ResultBanner(success = result.message)
                        is SshTestResult.Error -> ResultBanner(error = result.message)
                        else -> {}
                    }
                }
            }

            // ---- Appearance ----
            SectionCard(title = "Appearance", icon = Icons.Default.Palette) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConnectionColors.take(5).forEachIndexed { index, color ->
                        val isSelected = selectedColorIndex == index
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(30.dp)) { drawCircle(color = color) }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Top bar follows this color",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- DB test result ----
            when (val result = testResult) {
                is TestResult.Loading -> ResultBanner(loading = "Testing connection…")
                is TestResult.Success -> ResultBanner(success = result.message)
                is TestResult.Error -> ResultBanner(error = result.message)
                else -> {}
            }

            // ---- Actions ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        viewModel.testConnection(
                            currentProfile(), password,
                            sshPassword.ifBlank { null }, sshPassphrase.ifBlank { null }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canSubmit && testResult !is TestResult.Loading
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Test")
                }
                Button(
                    onClick = {
                        onSave(
                            currentProfile(),
                            password.ifBlank { null },
                            sshPassword.ifBlank { null },
                            sshPassphrase.ifBlank { null }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canSubmit
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** Dim example-hint inside fields — clearly a placeholder, never mistaken for real input. */
@Composable
private fun DimPlaceholder(text: String) {
    Text(
        text, maxLines = 1,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    )
}

/** Tonal section card with an icon + title header. */
@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

/** Single-line password field with visibility toggle and optional saved-hint. */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    hint: String? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, maxLines = 1) },
        placeholder = { if (placeholder != null) DimPlaceholder(placeholder) },
        supportingText = { if (hint != null) Text(hint, color = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (visible) "Hide" else "Show"
                )
            }
        }
    )
}

/** Title + subtitle row with a trailing Switch. */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Status banner: loading spinner, success (green container), or error.
 * Error text is two-line "short\nraw": short bold on top, raw monospace below.
 */
@Composable
private fun ResultBanner(
    loading: String? = null,
    success: String? = null,
    error: String? = null
) {
    when {
        loading != null -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(loading, style = MaterialTheme.typography.bodyMedium)
            }
        }
        success != null -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    success,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        error != null -> {
            val short = error.substringBefore("\n")
            val raw = error.substringAfter("\n", "")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Error, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        short,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (raw.isNotEmpty()) {
                    Text(
                        raw,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

private fun buildProfile(
    id: Long,
    name: String,
    host: String,
    port: Int,
    database: String?,
    username: String,
    isReadonly: Boolean,
    color: String,
    useSshTunnel: Boolean,
    sshHost: String,
    sshPort: Int,
    sshUsername: String,
    sshKeyPath: String,
    useSsl: Boolean
): ConnectionProfileEntity {
    return ConnectionProfileEntity(
        id = id,
        name = name,
        host = host,
        port = port,
        database = database,
        username = username,
        isReadonly = isReadonly,
        color = color,
        useSshTunnel = useSshTunnel,
        sshHost = sshHost.ifBlank { null },
        sshPort = sshPort,
        sshUsername = sshUsername.ifBlank { null },
        sshKeyPath = sshKeyPath.ifBlank { null },
        useSsl = useSsl
    )
}
