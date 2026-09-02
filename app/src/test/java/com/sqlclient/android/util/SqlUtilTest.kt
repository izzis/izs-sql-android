package com.sqlclient.android.util

import org.junit.Assert.*
import org.junit.Test

class SqlUtilTest {

    // stripLeading
    @Test fun stripLeading_plain() {
        assertEquals("SELECT 1", SqlUtil.stripLeading("SELECT 1"))
        assertEquals("SELECT 1", SqlUtil.stripLeading("  SELECT 1"))
        assertEquals("SELECT 1", SqlUtil.stripLeading("\n\t SELECT 1"))
    }

    @Test fun stripLeading_lineComments() {
        assertEquals("SELECT 1", SqlUtil.stripLeading("-- comment\nSELECT 1"))
        assertEquals("SELECT 1", SqlUtil.stripLeading("# comment\nSELECT 1"))
        assertEquals("", SqlUtil.stripLeading("-- only comment"))
        assertEquals("", SqlUtil.stripLeading("# only"))
        assertEquals("SELECT 1", SqlUtil.stripLeading("-- a\n# b\nSELECT 1"))
    }

    @Test fun stripLeading_blockComment() {
        assertEquals("SELECT 1", SqlUtil.stripLeading("/* comment */SELECT 1"))
        assertEquals("SELECT 1", SqlUtil.stripLeading("/* c */  SELECT 1"))
        assertEquals("SELECT 1", SqlUtil.stripLeading("/* a */ /* b */ SELECT 1"))
        assertEquals("", SqlUtil.stripLeading("/* unclosed"))
        assertEquals("", SqlUtil.stripLeading("/* only */"))
    }

    @Test fun stripLeading_mixed() {
        assertEquals("SELECT 1", SqlUtil.stripLeading("  -- a\n  # b\n  /*c*/ SELECT 1"))
    }

    // isWriteQuery - single statement
    @Test fun isWriteQuery_singles() {
        assertTrue(SqlUtil.isWriteQuery("INSERT INTO t VALUES (1)"))
        assertTrue(SqlUtil.isWriteQuery("insert into t values (1)"))
        assertTrue(SqlUtil.isWriteQuery("  UPDATE t SET x=1"))
        assertTrue(SqlUtil.isWriteQuery("DELETE FROM t"))
        assertTrue(SqlUtil.isWriteQuery("ALTER TABLE t ADD COLUMN x INT"))
        assertTrue(SqlUtil.isWriteQuery("DROP TABLE t"))
        assertTrue(SqlUtil.isWriteQuery("CREATE TABLE t (id INT)"))
        assertTrue(SqlUtil.isWriteQuery("TRUNCATE TABLE t"))
        assertTrue(SqlUtil.isWriteQuery("RENAME TABLE a TO b"))
        assertTrue(SqlUtil.isWriteQuery("GRANT SELECT ON *.* TO 'u'@'%'"))
        assertTrue(SqlUtil.isWriteQuery("REVOKE SELECT ON *.* FROM 'u'@'%'"))
        // with paren / tab / newline after prefix
        assertTrue(SqlUtil.isWriteQuery("INSERT(x) VALUES (1)"))
        assertTrue(SqlUtil.isWriteQuery("DELETE\tFROM t"))
        assertTrue(SqlUtil.isWriteQuery("UPDATE\nt SET x=1"))
    }

    @Test fun isWriteQuery_reads() {
        assertFalse(SqlUtil.isWriteQuery("SELECT * FROM t"))
        assertFalse(SqlUtil.isWriteQuery("select * from t"))
        assertFalse(SqlUtil.isWriteQuery("SHOW DATABASES"))
        assertFalse(SqlUtil.isWriteQuery("DESCRIBE t"))
        assertFalse(SqlUtil.isWriteQuery("EXPLAIN SELECT * FROM t"))
        assertFalse(SqlUtil.isWriteQuery(""))
        assertFalse(SqlUtil.isWriteQuery("   "))
        assertFalse(SqlUtil.isWriteQuery("-- only\n# only"))
    }

    @Test fun isWriteQuery_wordBoundary() {
        // INSERTINTO is not a write (no boundary) - but INSERT INTO is
        assertFalse(SqlUtil.isWriteQuery("INSERTINTO t VALUES (1)"))
        assertFalse(SqlUtil.isWriteQuery("UPDATEX t SET x=1"))
        // with non-alnum after prefix counts as write: INSERT;  INSERT-
        assertTrue(SqlUtil.isWriteQuery("INSERT;"))
        assertTrue(SqlUtil.isWriteQuery("DROP;"))
    }

    @Test fun isWriteQuery_commentsLeading() {
        assertTrue(SqlUtil.isWriteQuery("/* c */ UPDATE t SET x=1"))
        assertTrue(SqlUtil.isWriteQuery("-- c\nUPDATE t SET x=1"))
        assertFalse(SqlUtil.isWriteQuery("/* c */ SELECT * FROM t"))
    }

    @Test fun isWriteQuery_multiStatement() {
        assertTrue(SqlUtil.isWriteQuery("SELECT 1; DROP TABLE t"))
        assertTrue(SqlUtil.isWriteQuery("SELECT * FROM t; UPDATE t SET x=1"))
        assertTrue(SqlUtil.isWriteQuery("SELECT 1; SELECT 2; DELETE FROM t"))
        assertFalse(SqlUtil.isWriteQuery("SELECT 1; SELECT 2"))
        assertTrue(SqlUtil.isWriteQuery("SELECT 1; -- c\nDROP TABLE t"))
        // trailing semicolon with empty
        assertFalse(SqlUtil.isWriteQuery("SELECT 1;"))
        assertTrue(SqlUtil.isWriteQuery("SELECT 1; ; UPDATE t SET x=1"))
    }

    @Test fun isWriteQuery_caseInsensitive() {
        assertTrue(SqlUtil.isWriteQuery("  update t set x=1 where id=1"))
        assertTrue(SqlUtil.isWriteQuery("  GrAnT SELECT ON *.* TO 'a'@'%'"))
    }

    // shouldApplyLimit - only SELECT
    @Test fun shouldApplyLimit_select() {
        assertTrue(SqlUtil.shouldApplyLimit("SELECT * FROM t"))
        assertTrue(SqlUtil.shouldApplyLimit("  SELECT * FROM t"))
        assertTrue(SqlUtil.shouldApplyLimit("select * from t"))
        assertTrue(SqlUtil.shouldApplyLimit("/* c */ SELECT * FROM t"))
        assertTrue(SqlUtil.shouldApplyLimit("-- c\nSELECT * FROM t"))
    }

    @Test fun shouldApplyLimit_notSelect() {
        assertFalse(SqlUtil.shouldApplyLimit("UPDATE t SET x=1"))
        assertFalse(SqlUtil.shouldApplyLimit("INSERT INTO t VALUES (1)"))
        assertFalse(SqlUtil.shouldApplyLimit("DELETE FROM t"))
        assertFalse(SqlUtil.shouldApplyLimit("SHOW DATABASES"))
        assertFalse(SqlUtil.shouldApplyLimit("DESCRIBE t"))
        assertFalse(SqlUtil.shouldApplyLimit("EXPLAIN SELECT * FROM t"))
        assertFalse(SqlUtil.shouldApplyLimit(""))
        assertFalse(SqlUtil.shouldApplyLimit("  "))
    }

    @Test fun shouldApplyLimit_writeOverridesSelectInMulti() {
        // multi with write inside -> should not limit (safe)
        assertFalse(SqlUtil.shouldApplyLimit("SELECT 1; DROP TABLE t"))
        assertFalse(SqlUtil.shouldApplyLimit("SELECT * FROM t; UPDATE t SET x=1"))
    }

    // buildLimitedSql
    @Test fun buildLimited_selectWithoutLimit() {
        assertEquals("SELECT * FROM t LIMIT 200", SqlUtil.buildLimitedSql("SELECT * FROM t", 200))
        assertEquals("SELECT * FROM t LIMIT 200", SqlUtil.buildLimitedSql("  SELECT * FROM t  ", 200))
    }

    @Test fun buildLimited_selectWithLimit() {
        assertEquals("SELECT * FROM t LIMIT 10", SqlUtil.buildLimitedSql("SELECT * FROM t LIMIT 10", 200))
        assertEquals("SELECT * FROM t LIMIT 10 OFFSET 5", SqlUtil.buildLimitedSql("SELECT * FROM t LIMIT 10 OFFSET 5", 200))
        // contains LIMIT substring already -> no double
        assertEquals("SELECT * FROM t LIMIT 10", SqlUtil.buildLimitedSql("SELECT * FROM t LIMIT 10", 500))
    }

    @Test fun buildLimited_writeNeverLimited() {
        assertEquals("UPDATE t SET x=1", SqlUtil.buildLimitedSql("UPDATE t SET x=1", 200))
        assertEquals("UPDATE t SET x=1 WHERE id=1", SqlUtil.buildLimitedSql("UPDATE t SET x=1 WHERE id=1", 200))
        assertEquals("DELETE FROM t", SqlUtil.buildLimitedSql("DELETE FROM t", 200))
        assertEquals("INSERT INTO t VALUES (1)", SqlUtil.buildLimitedSql("INSERT INTO t VALUES (1)", 200))
        assertEquals("DROP TABLE t", SqlUtil.buildLimitedSql("DROP TABLE t", 200))
        // bulk UPDATE 10k rows must stay unlimited
        assertEquals("UPDATE users SET active=1 WHERE status='old'", SqlUtil.buildLimitedSql("UPDATE users SET active=1 WHERE status='old'", 200))
    }

    @Test fun buildLimited_showNeverLimited() {
        assertEquals("SHOW DATABASES", SqlUtil.buildLimitedSql("SHOW DATABASES", 200))
        assertEquals("SHOW TABLES IN db", SqlUtil.buildLimitedSql("SHOW TABLES IN db", 200))
    }

    @Test fun buildLimited_previewEqualsGeneralLog() {
        // critical invariant: preview == what will be sent for DataEditor
        val cases = listOf(
            "SELECT * FROM big_table" to "SELECT * FROM big_table LIMIT 200",
            "SELECT * FROM t LIMIT 10" to "SELECT * FROM t LIMIT 10",
            "UPDATE t SET x=1" to "UPDATE t SET x=1",
            "  SELECT * FROM t  " to "SELECT * FROM t LIMIT 200",
            "-- c\nSELECT * FROM t" to "-- c\nSELECT * FROM t LIMIT 200"
        )
        for ((input, expected) in cases) {
            // buildLimited is used for both preview and execution
            assertEquals(expected, SqlUtil.buildLimitedSql(input.trim(), 200).let {
                // note: original buildLimited trims input, so leading -- case keeps prefix
                SqlUtil.buildLimitedSql(input, 200)
            })
        }
    }

    @Test fun buildLimited_empty() {
        assertEquals("", SqlUtil.buildLimitedSql("", 200))
        assertEquals("", SqlUtil.buildLimitedSql("   ", 200))
    }
}
