package id.web.izs.sqlclient.util

import android.content.ClipData
import android.widget.Toast
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * Returns a copy-to-clipboard function backed by the current (non-deprecated)
 * [LocalClipboard] API.
 *
 * Invoke as `copy(text)` to copy silently, or `copy(text, "Copied")` to also
 * show a toast. Must be called from a composable.
 */
@Composable
fun rememberCopyToClipboard(): (String, String?) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember(clipboard, scope, context) {
        { text: String, toastMessage: String? ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("sql-client", text)))
                if (toastMessage != null) {
                    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
