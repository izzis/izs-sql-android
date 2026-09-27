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

    @Test fun newEntryIsNotStagedByDefault() {
        assertEquals(false, QueryLogEntry("SELECT 1").staged)
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

    @Test fun withStaged_keepsExecutedHistoryAndAppendsStaged() {
        val updated = log("SELECT 0").withStaged(listOf("UPDATE t SET a = 1"))
        assertEquals(listOf("SELECT 0", "UPDATE t SET a = 1"), updated.map { it.sql })
        assertEquals(false, updated[0].staged)
        assertEquals(true, updated[1].staged)
    }

    @Test fun withStaged_replacesPreviousStagedLines() {
        val once = log("SELECT 0").withStaged(listOf("UPDATE t SET a = 1"))
        val twice = once.withStaged(listOf("UPDATE t SET a = 2", "DELETE FROM t"))
        assertEquals(listOf("SELECT 0", "UPDATE t SET a = 2", "DELETE FROM t"), twice.map { it.sql })
        assertEquals(true, twice[1].staged)
        assertEquals(true, twice[2].staged)
    }

    @Test fun withStaged_emptySqlsDropsStagedLines() {
        val updated = log("SELECT 0").withStaged(listOf("UPDATE t SET a = 1")).withStaged(emptyList())
        assertEquals(listOf("SELECT 0"), updated.map { it.sql })
    }

    @Test fun withStaged_sameStagedSqlsIsNoop() {
        val original = log("SELECT 0").withStaged(listOf("UPDATE t SET a = 1"))
        assertSame(original, original.withStaged(listOf("UPDATE t SET a = 1")))
    }

    @Test fun withoutStaged_dropsStagedOnly() {
        val updated = log("SELECT 0", "SELECT 1")
            .withStaged(listOf("UPDATE t SET a = 1"))
            .withoutStaged()
        assertEquals(listOf("SELECT 0", "SELECT 1"), updated.map { it.sql })
    }

    @Test fun withoutStaged_withoutStagedEntriesIsNoop() {
        val original = log("SELECT 1")
        assertSame(original, original.withoutStaged())
    }

    @Test fun markExecuted_flipsStagedLineInPlace() {
        val staged = log("SELECT 0").withStaged(listOf("UPDATE t SET a = 1"))
        val executed = staged.markExecuted("UPDATE t SET a = 1")
        assertEquals(2, executed.size)
        assertEquals(false, executed[1].staged)
        assertNull(executed[1].error)
    }

    @Test fun markExecuted_withoutMatchingStagedAppendsLine() {
        val executed = log("SELECT 0").markExecuted("UPDATE t SET a = 1")
        assertEquals(listOf("SELECT 0", "UPDATE t SET a = 1"), executed.map { it.sql })
        assertEquals(false, executed[1].staged)
    }

    @Test fun markExecuted_flipsOnlyTheMatchingStagedLine() {
        val staged = log().withStaged(listOf("UPDATE t SET a = 1", "DELETE FROM t"))
        val executed = staged.markExecuted("DELETE FROM t")
        assertEquals(true, executed[0].staged)
        assertEquals(false, executed[1].staged)
    }

    @Test fun withQueryError_canMarkAStagedLine() {
        val staged = log().withStaged(listOf("UPDATE t SET a = 1"))
        val marked = staged.withQueryError("UPDATE t SET a = 1", "denied")
        assertEquals("denied", marked[0].error)
        assertEquals(true, marked[0].staged)
    }
}
