package com.sqlclient.android.util

/**
 * Pure SQL builders for table-level DDL (CREATE/DROP/RENAME/TRUNCATE TABLE).
 * No Android/JDBC dependency — unit-testable on JVM. The exact string built
 * here is both the Confirm-preview text and the statement sent to the server.
 */
object TableSql {

    /** Column spec from the guided Create Table dialog. */
    data class NewColumnSpec(
        val name: String,
        /** Base type, e.g. INT, VARCHAR, TEXT, DATETIME (may include modifiers like UNSIGNED). */
        val type: String,
        /** Length/precision, e.g. "100" or "10,2". Null/blank = no parens. */
        val length: String? = null,
        val nullable: Boolean = true,
        /** Raw DEFAULT expression or literal (without DEFAULT keyword). Null = no default. */
        val default: String? = null,
        val primaryKey: Boolean = false,
        val autoIncrement: Boolean = false,
        /** Inline UNIQUE (skipped when primaryKey — PK already implies uniqueness). */
        val unique: Boolean = false,
        /** ON UPDATE CURRENT_TIMESTAMP (meaningful for TIMESTAMP/DATETIME). */
        val onUpdateCurrentTimestamp: Boolean = false
    )

    /** Types that accept a (length) suffix. Everything else ignores [NewColumnSpec.length]. */
    private val lengthTypes = setOf(
        "CHAR", "VARCHAR", "BINARY", "VARBINARY", "BIT",
        "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE",
        "DATETIME", "TIMESTAMP", "TIME"
    )

    /** Base types offered by the guided dialogs (length entered in a separate field). */
    val columnBaseTypes = listOf(
        "INT", "BIGINT", "MEDIUMINT", "SMALLINT", "TINYINT", "BIT", "BOOLEAN",
        "VARCHAR", "CHAR", "BINARY", "VARBINARY",
        "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT",
        "TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB",
        "DATE", "DATETIME", "TIMESTAMP", "TIME", "YEAR",
        "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE",
        "JSON", "GEOMETRY", "POINT", "LINESTRING", "POLYGON"
    )

    /** Split "VARCHAR(255)" into ("VARCHAR", "255"); bare types yield ("INT", ""). */
    fun splitType(rawType: String): Pair<String, String> {
        val t = rawType.trim()
        val open = t.indexOf('(')
        if (open < 0 || !t.endsWith(')')) return t.uppercase() to ""
        return t.substring(0, open).trim().uppercase() to t.substring(open + 1, t.length - 1).trim()
    }

    /** Recombine base + length for ALTER/MODIFY dialogs. Bare when length blank or inapplicable. */
    fun withLength(baseType: String, length: String?): String {
        val base = baseType.trim().uppercase()
        val len = length?.trim().orEmpty()
        if (len.isEmpty() || base !in lengthTypes || base.contains('(')) return baseType.trim()
        return "${baseType.trim()}($len)"
    }

    /** Base types for which ON UPDATE CURRENT_TIMESTAMP is meaningful. */
    fun isTemporalType(baseType: String): Boolean {
        val base = baseType.trim().uppercase()
        return base == "TIMESTAMP" || base == "DATETIME"
    }

    /** Whether the base type accepts a (length) suffix — drives Len field visibility. */
    fun takesLength(baseType: String): Boolean {
        val base = baseType.trim().uppercase()
        return base in lengthTypes && !base.contains('(')
    }

    /** Soft validation for the Len field: blank or digits, optionally "p,s". */
    fun isValidLength(baseType: String, length: String?): Boolean {
        val len = length?.trim().orEmpty()
        if (len.isEmpty()) return true
        val base = baseType.trim().uppercase()
        if (base !in lengthTypes || base.contains('(')) return true
        return len.matches(Regex("\\d+(,\\d+)?"))
    }

    private fun qIdent(name: String): String {
        require(name.isNotBlank()) { "Identifier must not be blank" }
        require(!name.contains('\u0000')) { "Identifier must not contain NUL" }
        return "`" + name.replace("`", "``") + "`"
    }

    private fun qStringLiteral(value: String): String = "'" + value.replace("'", "''") + "'"

    /** Quote a DEFAULT value: numeric/function-like expressions stay bare, else string literal. */
    fun qDefault(value: String): String {
        val v = value.trim()
        if (v.isEmpty()) return "''"
        if (v.equals("NULL", ignoreCase = true)) return "NULL"
        if (v.equals("CURRENT_TIMESTAMP", ignoreCase = true)) return "CURRENT_TIMESTAMP"
        if (v.matches(Regex("-?\\d+(\\.\\d+)?"))) return v
        if (v.startsWith("'") && v.endsWith("'") && v.length >= 2) return v
        return qStringLiteral(v)
    }

    private fun columnDef(col: NewColumnSpec): String {
        require(col.name.isNotBlank()) { "Column name must not be blank" }
        require(col.type.isNotBlank()) { "Column type must not be blank" }
        val sb = StringBuilder()
        sb.append(qIdent(col.name.trim())).append(' ')
        val baseType = col.type.trim().uppercase()
        val len = col.length?.trim()
        if (!len.isNullOrEmpty() && baseType in lengthTypes && !baseType.contains('(')) {
            sb.append(col.type.trim()).append('(').append(len).append(')')
        } else {
            sb.append(col.type.trim())
        }
        sb.append(if (col.nullable) " NULL" else " NOT NULL")
        if (col.autoIncrement) sb.append(" AUTO_INCREMENT")
        if (col.unique && !col.primaryKey) sb.append(" UNIQUE")
        val def = col.default?.trim()
        if (!def.isNullOrEmpty()) sb.append(" DEFAULT ").append(qDefault(def))
        if (col.onUpdateCurrentTimestamp) sb.append(" ON UPDATE CURRENT_TIMESTAMP")
        return sb.toString()
    }

    fun buildCreateTableSql(
        database: String,
        table: String,
        columns: List<NewColumnSpec>,
        engine: String = "InnoDB"
    ): String {
        require(columns.isNotEmpty()) { "Table must have at least one column" }
        val defs = columns.map { columnDef(it) }.toMutableList()
        val pkCols = columns.filter { it.primaryKey }
        if (pkCols.isNotEmpty()) {
            defs.add("PRIMARY KEY (${pkCols.joinToString(", ") { qIdent(it.name.trim()) }})")
        }
        val eng = engine.trim().ifEmpty { "InnoDB" }
        return "CREATE TABLE ${qIdent(database)}.${qIdent(table)} (\n" +
            defs.joinToString(",\n") { "  $it" } +
            "\n) ENGINE=$eng"
    }

    fun buildDropTableSql(database: String, table: String): String =
        "DROP TABLE ${qIdent(database)}.${qIdent(table)}"

    fun buildRenameTableSql(database: String, oldTable: String, newTable: String): String {
        require(newTable.isNotBlank()) { "New table name must not be blank" }
        return "RENAME TABLE ${qIdent(database)}.${qIdent(oldTable)} TO ${qIdent(database)}.${qIdent(newTable.trim())}"
    }

    fun buildTruncateTableSql(database: String, table: String): String =
        "TRUNCATE TABLE ${qIdent(database)}.${qIdent(table)}"
}
