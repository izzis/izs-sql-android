package com.sqlclient.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CellDisplayTest {

    @Test fun format_null_isNull() {
        assertEquals("NULL", CellDisplay.format(null))
    }

    @Test fun format_plainUntouched() {
        assertEquals("hello", CellDisplay.format("hello"))
        assertEquals("2026-09-04", CellDisplay.format("2026-09-04"))
        assertEquals("100.0", CellDisplay.format("100.0"))
        assertEquals("42", CellDisplay.format(42))
    }

    @Test fun trim_zeroFractionStripped() {
        assertEquals("2026-09-04 10:00:00", CellDisplay.trim("2026-09-04 10:00:00.0"))
        assertEquals("2026-09-04 10:00:00", CellDisplay.trim("2026-09-04 10:00:00.000000"))
        assertEquals("10:00:00", CellDisplay.trim("10:00:00.0"))
    }

    @Test fun trim_realFractionKept() {
        assertEquals("10:00:00.5", CellDisplay.trim("10:00:00.5"))
        assertEquals("10:00:00.123", CellDisplay.trim("10:00:00.123"))
        assertEquals("10:00:00.10", CellDisplay.trim("10:00:00.10"))
        assertEquals("10:00:00.01", CellDisplay.trim("10:00:00.01"))
    }

    @Test fun format_timestampObjectTrims() {
        assertEquals(
            "2026-09-04 10:00:00",
            CellDisplay.format(java.sql.Timestamp.valueOf("2026-09-04 10:00:00"))
        )
    }
}
