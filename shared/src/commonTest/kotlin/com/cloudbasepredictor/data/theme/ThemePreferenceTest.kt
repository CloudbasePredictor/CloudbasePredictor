package com.cloudbasepredictor.data.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePreferenceTest {
    @Test
    fun enumNamesRemainStableForPersistentStorage() {
        assertEquals(listOf("AUTO", "LIGHT", "DARK"), ThemePreference.entries.map { it.name })
    }
}
