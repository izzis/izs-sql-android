package id.web.izs.sqlclient.util

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_FOLLOW_SYSTEM = "follow_system"
    }

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _followSystem = MutableStateFlow(prefs.getBoolean(KEY_FOLLOW_SYSTEM, false))
    val followSystem: StateFlow<Boolean> = _followSystem.asStateFlow()

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean(KEY_DARK_MODE, newValue).apply()
    }

    fun setDarkMode(dark: Boolean) {
        _isDarkMode.value = dark
        prefs.edit().putBoolean(KEY_DARK_MODE, dark).apply()
    }

    fun setFollowSystem(follow: Boolean) {
        _followSystem.value = follow
        prefs.edit().putBoolean(KEY_FOLLOW_SYSTEM, follow).apply()
    }
}
