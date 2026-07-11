package com.cloudbasepredictor.web.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class ResponsiveDestinationLayoutTest {
    @Test
    fun columnSelectionChangesAtTheResponsiveBreakpoint() {
        assertEquals(1, destinationColumnCount(TWO_COLUMN_MIN_WIDTH_DP - 1f))
        assertEquals(2, destinationColumnCount(TWO_COLUMN_MIN_WIDTH_DP))
        assertEquals(2, destinationColumnCount(TWO_COLUMN_MIN_WIDTH_DP + 1f))
    }

    @Test
    fun destinationRowsPreserveOrderAndRetainTheShortFinalRow() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c")),
            destinationRows(listOf("a", "b", "c"), columnCount = 2),
        )
        assertEquals(
            listOf(listOf("a"), listOf("b")),
            destinationRows(listOf("a", "b"), columnCount = 0),
        )
    }
}
