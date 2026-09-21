package id.web.izs.sqlclient.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import id.web.izs.sqlclient.ui.viewmodel.BrowserViewModel
import id.web.izs.sqlclient.ui.viewmodel.ConnectionViewModel
import id.web.izs.sqlclient.ui.viewmodel.QueryViewModel
import kotlinx.coroutines.launch

@Composable
fun DrawerScaffold(
    content: @Composable () -> Unit
) {
    val connectionViewModel: ConnectionViewModel = hiltViewModel()
    val browserViewModel: BrowserViewModel = hiltViewModel()
    val queryViewModel: QueryViewModel = hiltViewModel()
    val connectionState by connectionViewModel.connectionState.collectAsState()
    val profile = (connectionState as? id.web.izs.sqlclient.ui.viewmodel.ConnectionState.Connected)?.profile
    if (profile == null) {
        content()
        return
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val databases by browserViewModel.databases.collectAsState()
    val visibleDatabases by browserViewModel.visibleDatabases.collectAsState()
    val searchQuery by browserViewModel.searchQuery.collectAsState()
    val isLoading by browserViewModel.isLoading.collectAsState()
    val hasLoaded by browserViewModel.hasLoadedDatabases.collectAsState()
    val selectedDatabase by browserViewModel.selectedDatabase.collectAsState()
    val allFav by queryViewModel.allFavorites.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                AppSidebar(
                    profile = profile,
                    databases = databases,
                    visibleDatabases = if (visibleDatabases.isNotEmpty()) visibleDatabases else databases,
                    searchQuery = searchQuery,
                    onSearchChange = { browserViewModel.setSearchQuery(it) },
                    users = emptyList(),
                    isLoading = isLoading && !hasLoaded,
                    onRefreshDatabases = { browserViewModel.refreshDatabases() },
                    onDatabaseClick = { scope.launch { drawerState.close() } },
                    onUsersClick = { scope.launch { drawerState.close() } },
                    onHistoryClick = { scope.launch { drawerState.close() } },
                    onSavedQueriesClick = { scope.launch { drawerState.close() } },
                    savedQueryCount = allFav.size,
                    selectedDatabase = selectedDatabase
                )
            }
        }
    ) {
        content()
    }
}
