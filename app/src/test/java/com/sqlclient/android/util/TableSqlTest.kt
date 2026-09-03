package com.sqlclient.android.util

import org.junit.Assert.*
import org.junit.Test

class TableSqlTest {

    @Test fun createTable_basic() {
        val sql = TableSql.buildCreateTableSql(
            "db", "users",
            listOf(
                TableSql.NewColumnSpec("id", "INT", nullable = false, primaryKey = true, autoIncrement = true),
                TableSql.NewColumnSpec("name", "VARCHAR", length = "100")
            )
        )
        assertEquals(
            "CREATE TABLE `db`.`users` (\n" +
                "  `id` INT NOT NULL AUTO_INCREMENT,\n" +
                "  `name` VARCHAR(100) NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB",
            sql
        )
    }

    @Test fun createTable_defaultStringEscaped() {
        val sql = TableSql.buildCreateTableSql(
            "db", "t",
            listOf(TableSql.NewColumnSpec("nick", "VARCHAR", length = "50", default = "o'brien"))
        )
        assertTrue(sql.contains("`nick` VARCHAR(50) NULL DEFAULT 'o''brien'"))
    }

    @Test fun createTable_defaultBareValues() {
        val sql = TableSql.buildCreateTableSql(
            "db", "t",
            listOf(
                TableSql.NewColumnSpec("n", "INT", default = "0"),
                TableSql.NewColumnSpec("ts", "TIMESTAMP", default = "CURRENT_TIMESTAMP")
            )
        )
        assertTrue(sql.contains("`n` INT NULL DEFAULT 0"))
        assertTrue(sql.contains("`ts` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP"))
    }

    @Test fun createTable_compositePkAndEngine() {
        val sql = TableSql.buildCreateTableSql(
            "db", "rel",
            listOf(
                TableSql.NewColumnSpec("a_id", "INT", nullable = false, primaryKey = true),
                TableSql.NewColumnSpec("b_id", "INT", nullable = false, primaryKey = true)
            ),
            engine = "MyISAM"
        )
        assertTrue(sql.contains("PRIMARY KEY (`a_id`, `b_id`)"))
        assertTrue(sql.endsWith(") ENGINE=MyISAM"))
    }

    @Test fun createTable_typeWithModifiersKeepsRaw() {
        val sql = TableSql.buildCreateTableSql(
            "db", "t",
            listOf(TableSql.NewColumnSpec("n", "INT UNSIGNED"))
        )
        assertTrue(sql.contains("`n` INT UNSIGNED NULL"))
    }

    @Test fun createTable_noPkNoPkClause() {
        val sql = TableSql.buildCreateTableSql(
            "db", "t",
            listOf(TableSql.NewColumnSpec("x", "TEXT"))
        )
        assertFalse(sql.contains("PRIMARY KEY"))
    }

    @Test fun createTable_identifierEscaped() {
        val sql = TableSql.buildCreateTableSql(
            "d`b", "t`x",
            listOf(TableSql.NewColumnSpec("c`1", "INT"))
        )
        assertTrue(sql.startsWith("CREATE TABLE `d``b`.`t``x` ("))
        assertTrue(sql.contains("`c``1` INT NULL"))
    }

    @Test fun createTable_rejectsBlank() {
        try {
            TableSql.buildCreateTableSql("db", "t", emptyList())
            fail("expected blank-column rejection")
        } catch (e: IllegalArgumentException) { /* expected */ }
        try {
            TableSql.buildCreateTableSql("db", "  ", listOf(TableSql.NewColumnSpec("x", "INT")))
            fail("expected blank-table rejection")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test fun splitAndWithLength_roundTrip() {
        assertEquals("VARCHAR" to "255", TableSql.splitType("VARCHAR(255)"))
        assertEquals("DECIMAL" to "10,2", TableSql.splitType("DECIMAL(10,2)"))
        assertEquals("INT" to "", TableSql.splitType("INT"))
        assertEquals("VARCHAR(255)", TableSql.withLength("VARCHAR", "255"))
        assertEquals("INT", TableSql.withLength("INT", "11"))
        assertEquals("TEXT", TableSql.withLength("TEXT", "100"))
        assertEquals("INT UNSIGNED", TableSql.withLength("INT UNSIGNED", null))
    }

    @Test fun createTable_uniqueInline() {
        val sql = TableSql.buildCreateTableSql(
            "db", "t",
            listOf(
                TableSql.NewColumnSpec("email", "VARCHAR", length = "100", nullable = false, unique = true),
                TableSql.NewColumnSpec("id", "INT", nullable = false, primaryKey = true, unique = true)
            )
        )
        assertTrue(sql.contains("`email` VARCHAR(100) NOT NULL UNIQUE"))
        // PK implies uniqueness: no redundant UNIQUE on the PK column
        assertFalse(sql.contains("`id` INT NOT NULL UNIQUE"))
        assertTrue(SqlUtil.isWriteQuery(sql))
    }

    @Test fun isValidLength_rules() {
        assertTrue(TableSql.isValidLength("VARCHAR", ""))
        assertTrue(TableSql.isValidLength("VARCHAR", "100"))
        assertTrue(TableSql.isValidLength("DECIMAL", "10,2"))
        assertFalse(TableSql.isValidLength("VARCHAR", "abc"))
        assertFalse(TableSql.isValidLength("DECIMAL", "10,2,x"))
        assertTrue(TableSql.isValidLength("TEXT", "whatever"))
        assertTrue(TableSql.isValidLength("INT", "11"))
    }

    @Test fun createTable_onUpdateCurrentTimestamp() {
        val sql = TableSql.buildCreateTableSql(
            "db", "t",
            listOf(
                TableSql.NewColumnSpec(
                    "updated_at", "TIMESTAMP", nullable = false,
                    default = "CURRENT_TIMESTAMP", onUpdateCurrentTimestamp = true
                )
            )
        )
        assertTrue(sql.contains("`updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"))
        assertTrue(SqlUtil.isWriteQuery(sql))
    }

    @Test fun baseTypeList_coversCommonStorageTypes() {
        val list = TableSql.columnBaseTypes
        listOf(
            "INT", "BIGINT", "BOOLEAN", "BIT",
            "VARCHAR", "CHAR", "TEXT", "LONGTEXT", "BLOB",
            "DATE", "DATETIME", "YEAR", "DECIMAL", "JSON"
        ).forEach { assertTrue("missing $it", it in list) }
        // ENUM/SET need value lists: intentionally Custom-only, never suggested bare.
        assertFalse("ENUM" in list)
        assertFalse("SET" in list)
    }

    @Test fun dropRenameTruncate_exact() {
        assertEquals("DROP TABLE `db`.`t`", TableSql.buildDropTableSql("db", "t"))
        assertEquals(
            "RENAME TABLE `db`.`old` TO `db`.`new`",
            TableSql.buildRenameTableSql("db", "old", "new")
        )
        assertEquals("TRUNCATE TABLE `db`.`t`", TableSql.buildTruncateTableSql("db", "t"))
    }

    @Test fun allBuilders_classifiedWrite() {
        // Contract: every DDL here must trip the write-confirm + lock gate.
        val stmts = listOf(
            TableSql.buildCreateTableSql("db", "t", listOf(TableSql.NewColumnSpec("x", "INT"))),
            TableSql.buildDropTableSql("db", "t"),
            TableSql.buildRenameTableSql("db", "a", "b"),
            TableSql.buildTruncateTableSql("db", "t")
        )
        assertTrue(stmts.all { SqlUtil.isWriteQuery(it) })
        assertTrue(stmts.none { SqlUtil.shouldApplyLimit(it) })
    }
}
