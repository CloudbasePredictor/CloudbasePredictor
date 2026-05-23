package com.cloudbasepredictor.data.forecast

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastRepositoryDatabaseErrorTest {
    @Test
    fun isReportableDatabaseError_returnsFalseForCancellation() {
        assertFalse(isReportableDatabaseError(CancellationException("forecast load cancelled")))
    }

    @Test
    fun isReportableDatabaseError_returnsTrueForRoomStateErrors() {
        assertTrue(isReportableDatabaseError(IllegalStateException("migration failed")))
    }

    @Test
    fun isReportableDatabaseError_returnsFalseForNonDatabaseErrors() {
        assertFalse(isReportableDatabaseError(IllegalArgumentException("bad input")))
    }
}
