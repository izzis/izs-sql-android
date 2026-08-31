package com.sqlclient.android.ui.screens.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.ui.theme.ConnectionColors
import com.sqlclient.android.ui.viewmodel.ConnectionViewModel
import com.sqlclient.android.ui.viewmodel.SshTestResult
import com.sqlclient.android.ui.viewmodel.TestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionEditorScreen(
    viewModel: ConnectionViewModel,
    existingProfile: ConnectionProfileEntity? = null,
    onSave: (ConnectionProfileEntity, String, String?, String?) -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existingProfile != null) "Edit Connection" else "New Connection",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Connection Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.width(100.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = database,
                onValueChange = { database = it },
                label = { Text("Database (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                supportingText = {
                    if (existingProfile != null && hasStoredPassword && password.isEmpty()) {
                        Text(
                            text = "Password is saved. Leave blank to keep current, or type new password.",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isReadonly,
                    onCheckedChange = { isReadonly = it }
                )
                Text("Read Only Mode", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = useSsl,
                    onCheckedChange = { useSsl = it }
                )
                Text("Use SSL/TLS", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = useSshTunnel,
                    onCheckedChange = {
                        useSshTunnel = it
                        viewModel.clearSshTestResult()
                    }
                )
                Text("Use SSH Tunnel", style = MaterialTheme.typography.bodyMedium)
            }

            AnimatedVisibility(visible = useSshTunnel) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = sshHost,
                        onValueChange = { sshHost = it },
                        label = { Text("SSH Host") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sshPort,
                            onValueChange = { sshPort = it },
                            label = { Text("SSH Port") },
                            modifier = Modifier.width(100.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = sshUsername,
                            onValueChange = { sshUsername = it },
                            label = { Text("SSH Username") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = sshPassword,
                        onValueChange = { sshPassword = it },
                        label = { Text("SSH Password") },
                        supportingText = {
                            if (existingProfile != null && hasStoredSshPassword && sshPassword.isEmpty()) {
                                Text(
                                    text = "Password is saved. Leave blank to keep current, or type new password.",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (sshPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { sshPasswordVisible = !sshPasswordVisible }) {
                                Icon(
                                    imageVector = if (sshPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (sshPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = sshKeyPath,
                        onValueChange = { sshKeyPath = it },
                        label = { Text("SSH Key Path (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = sshPassphrase,
                        onValueChange = { sshPassphrase = it },
                        label = { Text("SSH Key Passphrase (optional)") },
                        supportingText = {
                            if (existingProfile != null && hasStoredSshPassphrase && sshPassphrase.isEmpty()) {
                                Text(
                                    text = "Passphrase is saved. Leave blank to keep current, or type new passphrase.",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (sshPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { sshPassphraseVisible = !sshPassphraseVisible }) {
                                Icon(
                                    imageVector = if (sshPassphraseVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (sshPassphraseVisible) "Hide passphrase" else "Show passphrase"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
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
                        Text("Test SSH")
                    }

                    when (val result = sshTestResult) {
                        is SshTestResult.Loading -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing SSH connection...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        is SshTestResult.Success -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = result.message,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        is SshTestResult.Error -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = result.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Connection Color",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConnectionColors.take(5).forEachIndexed { index, color ->
                    val isSelected = selectedColorIndex == index
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedColorIndex = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(28.dp)) {
                            drawCircle(color = color)
                        }
                    }
                }
            }

            when (val result = testResult) {
                is TestResult.Loading -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing connection...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is TestResult.Success -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = result.message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                is TestResult.Error -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = result.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val profile = buildProfile(
                            existingProfile?.id ?: 0L,
                            name, host, port.toIntOrNull() ?: 3306,
                            database.ifBlank { null }, username, isReadonly,
                            String.format("#%06X", 0xFFFFFF and ConnectionColors[selectedColorIndex].hashCode()),
                            useSshTunnel, sshHost, sshPort.toIntOrNull() ?: 22,
                            sshUsername, sshKeyPath, useSsl
                        )
                        viewModel.testConnection(profile, password, sshPassword.ifBlank { null }, sshPassphrase.ifBlank { null })
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank() && host.isNotBlank() && username.isNotBlank() && testResult !is TestResult.Loading
                ) {
                    Text("Test Connection")
                }

                Button(
                    onClick = {
                        val profile = buildProfile(
                            existingProfile?.id ?: 0L,
                            name, host, port.toIntOrNull() ?: 3306,
                            database.ifBlank { null }, username, isReadonly,
                            String.format("#%06X", 0xFFFFFF and ConnectionColors[selectedColorIndex].hashCode()),
                            useSshTunnel, sshHost, sshPort.toIntOrNull() ?: 22,
                            sshUsername, sshKeyPath, useSsl
                        )
                        val passwordToSend = password.ifBlank { null }
                        onSave(profile, passwordToSend ?: "", sshPassword.ifBlank { null }, sshPassphrase.ifBlank { null })
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank() && host.isNotBlank() && username.isNotBlank()
                ) {
                    Text("Save")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
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
