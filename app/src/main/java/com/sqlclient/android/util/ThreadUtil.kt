package com.sqlclient.android.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThreadUtil {
    suspend fun <T> runIO(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        block()
    }
}
