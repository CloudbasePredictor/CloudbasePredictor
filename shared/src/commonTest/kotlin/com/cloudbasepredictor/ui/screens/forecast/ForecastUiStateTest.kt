package com.cloudbasepredictor.ui.screens.forecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForecastUiStateTest {
    @Test
    fun loadingState_isDefaultScreenStateWithoutReadyData() {
        val state: ForecastUiState = ForecastLoadingUiState()

        assertFalse(state is ForecastReadyUiState)
        assertTrue(state is ForecastLoadingUiState)
        assertEquals(0, state.selectedDayIndex)
        assertNull(state.selectedPlace)
    }

    @Test
    fun loadingState_hasEmptyFavoritePlaces() {
        val state = ForecastLoadingUiState()
        assertTrue(state.favoritePlaces.isEmpty())
    }
}
