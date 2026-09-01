package com.sqlclient.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.ui.screens.browser.DatabaseBrowserScreen
import com.sqlclient.android.ui.screens.connection.ConnectionEditorScreen
import com.sqlclient.android.ui.screens.connection.ConnectionListScreen
import com.sqlclient.android.ui.screens.dataeditor.InlineDataEditorScreen
import com.sqlclient.android.ui.screens.query.SQLEditorScreen
import com.sqlclient.android.ui.screens.table.IndexManagementScreen
import com.sqlclient.android.ui.screens.table.TableStructureScreen
import com.sqlclient.android.ui.screens.user.UserManagementScreen
import com.sqlclient.android.ui.screens.user.UserPrivilegeDetailScreen
import com.sqlclient.android.ui.viewmodel.BrowserViewModel
import com.sqlclient.android.ui.viewmodel.ConnectionViewModel
import com.sqlclient.android.ui.viewmodel.DataEditorViewModel
import com.sqlclient.android.ui.viewmodel.IndexManagementViewModel
import com.sqlclient.android.ui.viewmodel.QueryViewModel
import com.sqlclient.android.ui.viewmodel.TableStructureViewModel
import com.sqlclient.android.ui.viewmodel.UserPermissionViewModel
import com.sqlclient.android.util.ThemeManager

@Composable
fun NavGraph(themeManager: ThemeManager) {
    val navController = rememberNavController()
    val connectionViewModel: ConnectionViewModel = hiltViewModel()
    val browserViewModel: BrowserViewModel = hiltViewModel()
    val queryViewModel: QueryViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = "connections"
    ) {
        composable("connections") {
            ConnectionListScreen(
                viewModel = connectionViewModel,
                themeManager = themeManager,
                onAddConnection = {
                    connectionViewModel.clearTestResult()
                    navController.navigate("connection/new")
                },
                onEditConnection = { profileId ->
                    connectionViewModel.clearTestResult()
                    navController.navigate("connection/edit/$profileId")
                },
                onConnect = { profile ->
                    connectionViewModel.connect(profile)
                },
                onNavigateToBrowser = {
                    navController.navigate("browser") {
                        popUpTo("connections")
                    }
                }
            )
        }

        composable("connection/new") {
            ConnectionEditorScreen(
                viewModel = connectionViewModel,
                onSave = { profile, password, sshPassword, sshPassphrase ->
                    if (password != null) {
                        connectionViewModel.saveProfile(profile, password, sshPassword, sshPassphrase)
                    }
                    navController.popBackStack()
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            "connection/edit/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: return@composable
            var profile by remember { mutableStateOf<ConnectionProfileEntity?>(null) }

            LaunchedEffect(profileId) {
                profile = connectionViewModel.getProfileById(profileId)
            }

            profile?.let { currentProfile ->
                ConnectionEditorScreen(
                    viewModel = connectionViewModel,
                    existingProfile = currentProfile,
                    onSave = { updatedProfile, password, sshPassword, sshPassphrase ->
                        connectionViewModel.updateProfile(updatedProfile, password, sshPassword, sshPassphrase)
                        navController.popBackStack()
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable("browser") {
            val connectionState by connectionViewModel.connectionState.collectAsState()
            val currentProfile = (connectionState as? com.sqlclient.android.ui.viewmodel.ConnectionState.Connected)?.profile
            val connectionError = (connectionState as? com.sqlclient.android.ui.viewmodel.ConnectionState.Error)?.message
            val userPermissionViewModel: UserPermissionViewModel = hiltViewModel()

            if (currentProfile != null) {
                DatabaseBrowserScreen(
                    viewModel = browserViewModel,
                    userViewModel = userPermissionViewModel,
                    connectionViewModel = connectionViewModel,
                    profile = currentProfile,
                    onDisconnect = {
                        connectionViewModel.disconnect()
                        browserViewModel.clearAll()
                        userPermissionViewModel.clearAll()
                        navController.popBackStack("connections", inclusive = false)
                    },
                    isLocked = connectionViewModel.sessionLocked.collectAsState().value,
                    onToggleLock = { connectionViewModel.setSessionLocked(!connectionViewModel.sessionLocked.value) },
                    onOpenQuery = { db, table ->
                        navController.navigate("query/$db/$table")
                    },
                    onOpenTableStructure = { db, table ->
                        navController.navigate("structure/$db/$table")
                    },
                    onOpenDataEditor = { db, table ->
                        navController.navigate("data_editor/$db/$table")
                    },
                    onOpenUserDetail = { user, host -> navController.navigate("user_detail/$user/${java.net.URLEncoder.encode(host, "UTF-8")}") },
                    onReconnect = { connectionViewModel.reconnect() },
                    isReconnecting = connectionViewModel.isReconnecting.collectAsState().value
                )
            } else if (connectionError != null) {
                LaunchedEffect(Unit) {
                    connectionViewModel.disconnect()
                    navController.popBackStack()
                }
            }
        }

        composable(
            "query/{database}/{table}",
            arguments = listOf(
                navArgument("database") { type = NavType.StringType },
                navArgument("table") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val database = backStackEntry.arguments?.getString("database") ?: return@composable
            val table = backStackEntry.arguments?.getString("table") ?: return@composable
            val connectionState by connectionViewModel.connectionState.collectAsState()
            val currentProfile = (connectionState as? com.sqlclient.android.ui.viewmodel.ConnectionState.Connected)?.profile

            if (currentProfile != null) {
                SQLEditorScreen(
                    viewModel = queryViewModel,
                    connectionViewModel = connectionViewModel,
                    browserViewModel = browserViewModel,
                    profile = currentProfile,
                    database = database,
                    table = table,
                    isLocked = connectionViewModel.sessionLocked.collectAsState().value,
                    onToggleLock = { connectionViewModel.setSessionLocked(!connectionViewModel.sessionLocked.value) },
                    onBack = { navController.popBackStack() },
                    onReconnect = { connectionViewModel.reconnect() },
                    isReconnecting = connectionViewModel.isReconnecting.collectAsState().value
                )
            }
        }

        composable(
            "structure/{database}/{table}",
            arguments = listOf(
                navArgument("database") { type = NavType.StringType },
                navArgument("table") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val database = backStackEntry.arguments?.getString("database") ?: return@composable
            val table = backStackEntry.arguments?.getString("table") ?: return@composable
            val tableStructureViewModel: TableStructureViewModel = hiltViewModel()
            val structureIndexViewModel: IndexManagementViewModel = hiltViewModel()
            val currentProfile = (connectionViewModel.connectionState.value as? com.sqlclient.android.ui.viewmodel.ConnectionState.Connected)?.profile
            val topBarColor = try {
                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(currentProfile?.color ?: "#6200EE"))
            } catch (_: Exception) {
                androidx.compose.ui.graphics.Color(0xFF6200EE)
            }
            TableStructureScreen(
                viewModel = tableStructureViewModel,
                indexViewModel = structureIndexViewModel,
                connectionViewModel = connectionViewModel,
                database = database,
                table = table,
                isLocked = connectionViewModel.sessionLocked.collectAsState().value,
                onToggleLock = { connectionViewModel.setSessionLocked(!connectionViewModel.sessionLocked.value) },
                onBack = { navController.popBackStack() },
                onData = { navController.navigate("data_editor/$database/$table") },
                onReconnect = { connectionViewModel.reconnect() },
                isReconnecting = connectionViewModel.isReconnecting.collectAsState().value,
                topBarColor = topBarColor
            )
        }

        composable(
            "data_editor/{database}/{table}",
            arguments = listOf(
                navArgument("database") { type = NavType.StringType },
                navArgument("table") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val database = backStackEntry.arguments?.getString("database") ?: return@composable
            val table = backStackEntry.arguments?.getString("table") ?: return@composable
            val dataEditorViewModel: DataEditorViewModel = hiltViewModel()
            val connectionState by connectionViewModel.connectionState.collectAsState()
            val currentProfile = (connectionState as? com.sqlclient.android.ui.viewmodel.ConnectionState.Connected)?.profile

            if (currentProfile != null) {
                InlineDataEditorScreen(
                    viewModel = dataEditorViewModel,
                    connectionViewModel = connectionViewModel,
                    queryViewModel = queryViewModel,
                    browserViewModel = browserViewModel,
                    profile = currentProfile,
                    database = database,
                    table = table,
                    isLocked = connectionViewModel.sessionLocked.collectAsState().value,
                    onToggleLock = { connectionViewModel.setSessionLocked(!connectionViewModel.sessionLocked.value) },
                    onBack = { navController.popBackStack() },
                    onOpenStructure = { navController.navigate("structure/$database/$table") },
                    onReconnect = { connectionViewModel.reconnect() },
                    isReconnecting = connectionViewModel.isReconnecting.collectAsState().value
                )
            }
        }

        composable("users") {
            val userPermissionViewModel: UserPermissionViewModel = hiltViewModel()

            UserManagementScreen(
                viewModel = userPermissionViewModel,
                connectionViewModel = connectionViewModel,
                isLocked = connectionViewModel.sessionLocked.collectAsState().value,
                onToggleLock = { connectionViewModel.setSessionLocked(!connectionViewModel.sessionLocked.value) },
                onBack = { navController.popBackStack() },
                onOpenUserDetail = { user, host -> navController.navigate("user_detail/$user/${java.net.URLEncoder.encode(host, "UTF-8")}") },
                onReconnect = { connectionViewModel.reconnect() },
                isReconnecting = connectionViewModel.isReconnecting.collectAsState().value
            )
        }

        composable(
            "user_detail/{user}/{host}",
            arguments = listOf(
                navArgument("user") { type = NavType.StringType },
                navArgument("host") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val user = backStackEntry.arguments?.getString("user") ?: return@composable
            val host = backStackEntry.arguments?.getString("host") ?: return@composable
            val vm: UserPermissionViewModel = hiltViewModel()
            UserPrivilegeDetailScreen(
                viewModel = vm,
                connectionViewModel = connectionViewModel,
                user = user,
                host = host,
                isLocked = connectionViewModel.sessionLocked.collectAsState().value,
                onToggleLock = { connectionViewModel.setSessionLocked(!connectionViewModel.sessionLocked.value) },
                onBack = { navController.popBackStack() },
                onReconnect = { connectionViewModel.reconnect() },
                isReconnecting = connectionViewModel.isReconnecting.collectAsState().value
            )
        }

        composable(
            "index_management/{database}/{table}",
            arguments = listOf(
                navArgument("database") { type = NavType.StringType },
                navArgument("table") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val database = backStackEntry.arguments?.getString("database") ?: return@composable
            val table = backStackEntry.arguments?.getString("table") ?: return@composable
            val indexManagementViewModel: IndexManagementViewModel = hiltViewModel()
            val indexManagementProfile = (connectionViewModel.connectionState.value as? com.sqlclient.android.ui.viewmodel.ConnectionState.Connected)?.profile
            val indexManagementTopBarColor = try {
                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(indexManagementProfile?.color ?: "#6200EE"))
            } catch (_: Exception) {
                androidx.compose.ui.graphics.Color(0xFF6200EE)
            }
            IndexManagementScreen(
                viewModel = indexManagementViewModel,
                connectionViewModel = connectionViewModel,
                database = database,
                table = table,
                isLocked = connectionViewModel.sessionLocked.collectAsState().value,
                onToggleLock = { connectionViewModel.setSessionLocked(!connectionViewModel.sessionLocked.value) },
                onBack = { navController.popBackStack() },
                onReconnect = { connectionViewModel.reconnect() },
                isReconnecting = connectionViewModel.isReconnecting.collectAsState().value,
                topBarColor = indexManagementTopBarColor
            )
        }
    }
}
