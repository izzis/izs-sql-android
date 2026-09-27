package id.web.izs.sqlclient.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.web.izs.sqlclient.data.local.entity.ConnectionProfileEntity
import id.web.izs.sqlclient.ui.screens.browser.DatabaseBrowserScreen
import id.web.izs.sqlclient.ui.screens.browser.DbStructureScreen
import id.web.izs.sqlclient.ui.screens.connection.ConnectionEditorScreen
import id.web.izs.sqlclient.ui.screens.connection.ConnectionListScreen
import id.web.izs.sqlclient.ui.screens.dataeditor.InlineDataEditorScreen
import id.web.izs.sqlclient.ui.screens.query.ManageSavedQueriesScreen
import id.web.izs.sqlclient.ui.screens.query.SQLEditorScreen
import id.web.izs.sqlclient.ui.screens.table.TableStructureScreen
import id.web.izs.sqlclient.ui.screens.user.UserPrivilegeDetailScreen
import id.web.izs.sqlclient.ui.viewmodel.BrowserViewModel
import id.web.izs.sqlclient.ui.viewmodel.ConnectionViewModel
import id.web.izs.sqlclient.ui.viewmodel.DataEditorViewModel
import id.web.izs.sqlclient.ui.viewmodel.DbStructureViewModel
import id.web.izs.sqlclient.ui.viewmodel.IndexManagementViewModel
import id.web.izs.sqlclient.ui.viewmodel.QueryViewModel
import id.web.izs.sqlclient.ui.viewmodel.TableStructureViewModel
import id.web.izs.sqlclient.ui.viewmodel.UserPermissionViewModel
import id.web.izs.sqlclient.util.ThemeManager

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
            val currentProfile = (connectionState as? id.web.izs.sqlclient.ui.viewmodel.ConnectionState.Connected)?.profile
            val connectionError = (connectionState as? id.web.izs.sqlclient.ui.viewmodel.ConnectionState.Error)?.message
            val userPermissionViewModel: UserPermissionViewModel = hiltViewModel()

            if (currentProfile != null) {
                DatabaseBrowserScreen(
                    viewModel = browserViewModel,
                    userViewModel = userPermissionViewModel,
                    connectionViewModel = connectionViewModel,
                    queryViewModel = queryViewModel,
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
                    onOpenDbStructure = { db ->
                        navController.navigate("db_structure/$db")
                    },
                    onOpenUserDetail = { user, host -> navController.navigate("user_detail/$user/${java.net.URLEncoder.encode(host, "UTF-8")}") },
                    onNavigateToSavedQueries = { navController.navigate("manage_saved_queries") },
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
            val currentProfile = (connectionState as? id.web.izs.sqlclient.ui.viewmodel.ConnectionState.Connected)?.profile

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
            val connectionState by connectionViewModel.connectionState.collectAsState()
            val currentProfile = (connectionState as? id.web.izs.sqlclient.ui.viewmodel.ConnectionState.Connected)?.profile
            val topBarColor = profileTopBarColor(currentProfile?.color)
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
            "db_structure/{database}",
            arguments = listOf(
                navArgument("database") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val database = backStackEntry.arguments?.getString("database") ?: return@composable
            val dbStructureViewModel: DbStructureViewModel = hiltViewModel()
            val connectionState by connectionViewModel.connectionState.collectAsState()
            val currentProfile = (connectionState as? id.web.izs.sqlclient.ui.viewmodel.ConnectionState.Connected)?.profile
            val topBarColor = profileTopBarColor(currentProfile?.color)
            DbStructureScreen(
                viewModel = dbStructureViewModel,
                connectionViewModel = connectionViewModel,
                database = database,
                isLocked = connectionViewModel.sessionLocked.collectAsState().value,
                onToggleLock = { connectionViewModel.setSessionLocked(!connectionViewModel.sessionLocked.value) },
                onBack = { navController.popBackStack() },
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
            val currentProfile = (connectionState as? id.web.izs.sqlclient.ui.viewmodel.ConnectionState.Connected)?.profile

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
            val connectionState by connectionViewModel.connectionState.collectAsState()
            val currentProfile = (connectionState as? id.web.izs.sqlclient.ui.viewmodel.ConnectionState.Connected)?.profile
            val topBarColor = profileTopBarColor(currentProfile?.color)
            UserPrivilegeDetailScreen(
                viewModel = vm,
                connectionViewModel = connectionViewModel,
                user = user,
                host = host,
                isLocked = connectionViewModel.sessionLocked.collectAsState().value,
                onToggleLock = { connectionViewModel.setSessionLocked(!connectionViewModel.sessionLocked.value) },
                onBack = { navController.popBackStack() },
                onReconnect = { connectionViewModel.reconnect() },
                isReconnecting = connectionViewModel.isReconnecting.collectAsState().value,
                topBarColor = topBarColor
            )
        }

        composable("manage_saved_queries") {
            val connectionState by connectionViewModel.connectionState.collectAsState()
            val currentProfile = (connectionState as? id.web.izs.sqlclient.ui.viewmodel.ConnectionState.Connected)?.profile
            if (currentProfile != null) {
                ManageSavedQueriesScreen(
                    queryViewModel = queryViewModel,
                    connectionViewModel = connectionViewModel,
                    profile = currentProfile,
                    isLocked = connectionViewModel.sessionLocked.collectAsState().value,
                    onToggleLock = { connectionViewModel.setSessionLocked(!connectionViewModel.sessionLocked.value) },
                    onBack = { navController.popBackStack() },
                    onOpenQuery = { db, table -> navController.navigate("query/$db/$table") },
                    onReconnect = { connectionViewModel.reconnect() },
                    isReconnecting = connectionViewModel.isReconnecting.collectAsState().value
                )
            }
        }
    }
}

private fun profileTopBarColor(hex: String?): androidx.compose.ui.graphics.Color = try {
    androidx.compose.ui.graphics.Color((hex ?: "#6200EE").toColorInt())
} catch (_: Exception) {
    androidx.compose.ui.graphics.Color(0xFF6200EE)
}
