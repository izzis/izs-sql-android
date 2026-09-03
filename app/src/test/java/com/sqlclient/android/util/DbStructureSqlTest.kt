package com.sqlclient.android.util

import org.junit.Assert.*
import org.junit.Test

class DbStructureSqlTest {

    // Views
    @Test fun createView_exact() {
        assertEquals(
            "CREATE VIEW `db`.`v` AS SELECT 1",
            DbStructureSql.buildCreateViewSql("db", "v", "SELECT 1", orReplace = false)
        )
    }

    @Test fun createOrReplaceView_exact() {
        assertEquals(
            "CREATE OR REPLACE VIEW `db`.`v` AS SELECT a FROM t",
            DbStructureSql.buildCreateViewSql("db", "v", "SELECT a FROM t", orReplace = true)
        )
    }

    @Test fun dropView_exact() {
        assertEquals("DROP VIEW `db`.`v`", DbStructureSql.buildDropViewSql("db", "v"))
    }

    @Test fun extractViewSelect_fromShowCreate() {
        val ddl = "CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`%` SQL SECURITY DEFINER VIEW `v` AS select `a` from `t`"
        assertEquals("select `a` from `t`", DbStructureSql.extractViewSelect(ddl))
    }

    @Test fun extractViewSelect_noAs_returnsTrimmed() {
        assertEquals("SELECT 1", DbStructureSql.extractViewSelect("  SELECT 1  "))
    }

    // Triggers
    @Test fun createTrigger_exact() {
        assertEquals(
            "CREATE TRIGGER `db`.`trg` BEFORE INSERT ON `db`.`t` FOR EACH ROW SET NEW.a = 1",
            DbStructureSql.buildCreateTriggerSql("db", "trg", "BEFORE", "INSERT", "t", "SET NEW.a = 1")
        )
    }

    @Test fun dropTrigger_exact() {
        assertEquals("DROP TRIGGER `db`.`trg`", DbStructureSql.buildDropTriggerSql("db", "trg"))
    }

    // Events
    @Test fun scheduleClause_everyWithBounds() {
        assertEquals(
            "EVERY 1 DAY STARTS '2026-01-01 00:00:00' ENDS '2027-01-01 00:00:00'",
            DbStructureSql.buildEventScheduleClause("EVERY", "1", "DAY", "", "'2026-01-01 00:00:00'", "'2027-01-01 00:00:00'")
        )
    }

    @Test fun scheduleClause_everyBare() {
        assertEquals(
            "EVERY 5 MINUTE",
            DbStructureSql.buildEventScheduleClause("EVERY", "5", "MINUTE", "", "", "")
        )
    }

    @Test fun scheduleClause_at() {
        assertEquals(
            "AT '2026-06-01 12:00:00'",
            DbStructureSql.buildEventScheduleClause("AT", "", "", "'2026-06-01 12:00:00'", "", "")
        )
    }

    @Test fun createEvent_enabled_noExtraClauses() {
        assertEquals(
            "CREATE EVENT `db`.`e` ON SCHEDULE EVERY 1 HOUR DO UPDATE t SET a = 1",
            DbStructureSql.buildCreateEventSql("db", "e", "EVERY 1 HOUR", preserve = false, enabled = true, body = "UPDATE t SET a = 1")
        )
    }

    @Test fun createEvent_disabledPreserve() {
        assertEquals(
            "CREATE EVENT `db`.`e` ON SCHEDULE AT '2026-01-01 00:00:00' ON COMPLETION PRESERVE DISABLE DO DELETE FROM t",
            DbStructureSql.buildCreateEventSql("db", "e", "AT '2026-01-01 00:00:00'", preserve = true, enabled = false, body = "DELETE FROM t")
        )
    }

    @Test fun alterEvent_alwaysStatesEnable() {
        assertEquals(
            "ALTER EVENT `db`.`e` ON SCHEDULE EVERY 1 DAY ENABLE DO SELECT 1",
            DbStructureSql.buildAlterEventSql("db", "e", "EVERY 1 DAY", preserve = false, enabled = true, body = "SELECT 1")
        )
        assertEquals(
            "ALTER EVENT `db`.`e` ON SCHEDULE EVERY 1 DAY DISABLE DO SELECT 1",
            DbStructureSql.buildAlterEventSql("db", "e", "EVERY 1 DAY", preserve = false, enabled = false, body = "SELECT 1")
        )
    }

    @Test fun toggleEvent_exact() {
        assertEquals("ALTER EVENT `db`.`e` ENABLE", DbStructureSql.buildToggleEventSql("db", "e", true))
        assertEquals("ALTER EVENT `db`.`e` DISABLE", DbStructureSql.buildToggleEventSql("db", "e", false))
    }

    @Test fun dropEvent_exact() {
        assertEquals("DROP EVENT `db`.`e`", DbStructureSql.buildDropEventSql("db", "e"))
    }

    // Routines
    @Test fun dropRoutine_uppercasesKind() {
        assertEquals("DROP PROCEDURE `db`.`p`", DbStructureSql.buildDropRoutineSql("db", "PROCEDURE", "p"))
        assertEquals("DROP FUNCTION `db`.`f`", DbStructureSql.buildDropRoutineSql("db", "function", "f"))
    }

    @Test fun routineTemplate_containsKindAndName() {
        val proc = DbStructureSql.routineTemplate("PROCEDURE", "db", "p")
        assertTrue(proc.startsWith("CREATE PROCEDURE `db`.`p`"))
        val func = DbStructureSql.routineTemplate("FUNCTION", "db", "f")
        assertTrue(func.startsWith("CREATE FUNCTION `db`.`f`"))
        assertTrue(func.contains("RETURNS"))
    }
}
