package id.web.izs.sqlclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import id.web.izs.sqlclient.data.remote.MariaDbConnectionManager
import id.web.izs.sqlclient.navigation.NavGraph
import id.web.izs.sqlclient.ui.theme.SqlClientTheme
import id.web.izs.sqlclient.util.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var connectionManager: MariaDbConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A session can die while backgrounded (NAT idle drop, wifi→cellular handoff) without
        // the driver noticing. Heal it on resume so the first statement doesn't discover the
        // failure itself. Registered before setContent so it fires for this first resume too.
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lifecycleScope.launch { connectionManager.ensureConnected() }
            }
        })
        setContent {
            val isDarkMode by themeManager.isDarkMode.collectAsState()
            val followSystem by themeManager.followSystem.collectAsState()
            val systemDark = isSystemInDarkTheme()

            val useDarkTheme = when {
                followSystem -> systemDark
                else -> isDarkMode
            }

            SqlClientTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(themeManager = themeManager)
                }
            }
        }
    }
}
