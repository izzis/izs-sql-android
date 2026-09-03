package com.sqlclient.android.util

/**
 * Pure SQL builders for database-level objects (views, triggers, events, routines).
 *
 * The screens preview exactly what these functions return, and the ViewModel
 * executes exactly the previewed string — preview == general_log by construction.
 * Kept free of Android/VM dependencies so it is JVM-testable (see DbStructureSqlTest).
 */
object DbStructureSql {

    // ---------- views ----------

    fun buildCreateViewSql(database: String, name: String, definition: String, orReplace: Boolean): String {
        val replaceStr = if (orReplace) "OR REPLACE " else ""
        return "CREATE ${replaceStr}VIEW `$database`.`$name` AS $definition"
    }

    fun buildDropViewSql(database: String, view: String): String =
        "DROP VIEW `$database`.`$view`"

    /** Extracts the SELECT body after the first top-level " AS " from SHOW CREATE VIEW output. */
    fun extractViewSelect(createViewDdl: String): String {
        val idx = createViewDdl.indexOf(" AS ", ignoreCase = true)
        return if (idx >= 0) createViewDdl.substring(idx + 4).trim() else createViewDdl.trim()
    }

    // ---------- triggers ----------

    fun buildCreateTriggerSql(database: String, name: String, timing: String, event: String, table: String, body: String): String =
        "CREATE TRIGGER `$database`.`$name` $timing $event ON `$database`.`$table` FOR EACH ROW $body"

    fun buildDropTriggerSql(database: String, trigger: String): String =
        "DROP TRIGGER `$database`.`$trigger`"

    // ---------- events ----------

    /**
     * Assembles the ON SCHEDULE clause. Mode EVERY: "EVERY <n> <UNIT> [STARTS ..] [ENDS ..]";
     * mode AT: "AT <timestamp>".
     */
    fun buildEventScheduleClause(
        mode: String,
        everyValue: String,
        everyUnit: String,
        at: String,
        starts: String,
        ends: String
    ): String {
        return if (mode == "AT") {
            "AT ${at.trim()}"
        } else {
            buildString {
                append("EVERY ${everyValue.trim()} ${everyUnit.trim()}")
                if (starts.isNotBlank()) append(" STARTS ${starts.trim()}")
                if (ends.isNotBlank()) append(" ENDS ${ends.trim()}")
            }
        }
    }

    fun buildCreateEventSql(database: String, name: String, schedule: String, preserve: Boolean, enabled: Boolean, body: String): String =
        buildString {
            append("CREATE EVENT `$database`.`$name` ON SCHEDULE $schedule")
            if (preserve) append(" ON COMPLETION PRESERVE")
            if (!enabled) append(" DISABLE")
            append(" DO $body")
        }

    fun buildAlterEventSql(database: String, name: String, schedule: String, preserve: Boolean, enabled: Boolean, body: String): String =
        buildString {
            append("ALTER EVENT `$database`.`$name` ON SCHEDULE $schedule")
            if (preserve) append(" ON COMPLETION PRESERVE")
            append(if (enabled) " ENABLE" else " DISABLE")
            append(" DO $body")
        }

    fun buildToggleEventSql(database: String, name: String, enable: Boolean): String =
        "ALTER EVENT `$database`.`$name` ${if (enable) "ENABLE" else "DISABLE"}"

    fun buildDropEventSql(database: String, event: String): String =
        "DROP EVENT `$database`.`$event`"

    // ---------- routines ----------

    fun buildDropRoutineSql(database: String, kind: String, name: String): String =
        "DROP ${kind.uppercase()} `$database`.`$name`"

    /** Starter template for the raw routine editor (user edits freely; executed as-is). */
    fun routineTemplate(kind: String, database: String, name: String): String =
        if (kind == "FUNCTION") {
            "CREATE FUNCTION `$database`.`$name`() RETURNS INT DETERMINISTIC\nBEGIN\n  RETURN 0;\nEND"
        } else {
            "CREATE PROCEDURE `$database`.`$name`()\nBEGIN\n  SELECT 1;\nEND"
        }
}
