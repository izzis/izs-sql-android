package id.web.izs.sqlclient.ui.components

import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DbIconsTest {

    @Test
    fun mysqlIconParses() {
        // Throws IllegalArgumentException if the Simple Icons path data is malformed.
        val icon = DbIcons.Mysql
        assertEquals("Mysql", icon.name)
        var nodes = 0
        fun count(group: VectorGroup) {
            for (n in group) {
                when (n) {
                    is VectorPath -> nodes += n.pathData.size
                    is VectorGroup -> count(n)
                }
            }
        }
        count(icon.root)
        assertTrue("MySQL path must contain nodes", nodes > 100)
    }
}
