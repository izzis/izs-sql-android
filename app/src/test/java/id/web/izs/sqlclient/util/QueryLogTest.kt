package id.web.izs.sqlclient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class QueryLogTest {

    private fun log(vararg sql: String) = sql.map { QueryLogEntry(it) }

    @Test fun newEntryHasNoError() {
        assertNull(QueryLogEntry("SELECT 1").error)
    }

    @Test fun withQueryError_marksMatchingEntry() {
        val marked = log("SELECT 1", "SELECT 2").withQueryError("SELECT 2", "boom")
        assertNull(marked[0].error)
        assertEquals("boom", marked[1].error)
        assertEquals(listOf("SELECT 1", "SELECT 2"), marked.map { it.sql })
    }

    @Test fun withQueryError_unknownSqlLeavesLogUntouched() {
        val original = log("SELECT 1")
        assertSame(original, original.withQueryError("SELECT 9", "boom"))
    }

    @Test fun withQueryError_repeatedSqlMarksTheLastOne() {
        val marked = log("SELECT 1", "SELECT 1").withQueryError("SELECT 1", "boom")
        assertNull(marked[0].error)
        assertEquals("boom", marked[1].error)
    }

    @Test fun withQueryError_overwritesPreviousMessage() {
        val marked = log("SELECT 1")
            .withQueryError("SELECT 1", "first")
            .withQueryError("SELECT 1", "second")
        assertEquals("second", marked[0].error)
    }

    @Test fun withQueryError_emptyLogIsNoop() {
        val empty = emptyList<QueryLogEntry>()
        assertSame(empty, empty.withQueryError("SELECT 1", "boom"))
    }

    @Test fun withQueryError_doesNotMutateOriginalList() {
        val original = log("SELECT 1")
        original.withQueryError("SELECT 1", "boom")
        assertNull(original[0].error)
    }
}
