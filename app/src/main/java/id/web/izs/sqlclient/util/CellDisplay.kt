package id.web.izs.sqlclient.util

/**
 * Display/SQL normalization for cell values.
 *
 * The MariaDB JDBC driver returns DATETIME/TIMESTAMP/TIME columns as
 * `java.sql.Timestamp`, whose `toString()` always appends the nanos fraction —
 * so `2026-09-04 10:00:00` stored on the server renders as `...:00.0` on the
 * phone. The `.0` is NOT on the server; it is a client-side artifact.
 *
 * These helpers strip a fraction that is all zeros (`:00.0`, `:00.000000`)
 * and keep real fractions (`.5`, `.123`). The normalized form is
 * value-equivalent server-side, so it is safe for display, copy, dialog
 * defaults, and generated SQL alike. Raw objects in ViewModels are untouched.
 */
object CellDisplay {
    private val zeroFraction = Regex("""(\d{2}:\d{2}:\d{2})\.0+\b""")

    /** Trim an all-zero time fraction from a raw string. */
    fun trim(text: String): String = zeroFraction.replace(text, "$1")

    /** Null-safe display string: null -> "NULL", else trimmed [toString]. */
    fun format(value: Any?): String {
        if (value == null) return "NULL"
        return trim(value.toString())
    }
}
