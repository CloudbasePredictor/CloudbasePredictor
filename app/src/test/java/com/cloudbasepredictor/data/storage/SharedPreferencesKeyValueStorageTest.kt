package com.cloudbasepredictor.data.storage

import com.cloudbasepredictor.testing.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPreferencesKeyValueStorageTest {
    private val preferences = FakeSharedPreferences()
    private val storage = SharedPreferencesKeyValueStorage(preferences)

    @Test
    fun missingValues_returnNullAndAreNotContained() {
        assertFalse(storage.contains("missing"))
        assertNull(storage.getString("missing"))
        assertNull(storage.getBoolean("missing"))
        assertNull(storage.getFloat("missing"))
    }

    @Test
    fun typedValues_roundTripThroughSharedPreferences() {
        storage.putString("name", "Brauneck")
        storage.putBoolean("launch_sites", true)
        storage.putFloat("zoom", Float.fromBits(0x3f8ccccd))

        assertTrue(storage.contains("name"))
        assertEquals("Brauneck", storage.getString("name"))
        assertEquals(true, storage.getBoolean("launch_sites"))
        assertEquals(Float.fromBits(0x3f8ccccd), storage.getFloat("zoom"))
    }

    @Test
    fun storedDefaultValues_areDistinguishedFromMissingValues() {
        storage.putBoolean("disabled", false)
        storage.putFloat("zero", -0.0f)

        assertTrue(storage.contains("disabled"))
        assertEquals(false, storage.getBoolean("disabled"))
        assertTrue(storage.contains("zero"))
        assertEquals((-0.0f).toBits(), storage.getFloat("zero")?.toBits())
    }

    @Test
    fun put_replacesAnExistingValueUsingSharedPreferencesSemantics() {
        storage.putString("setting", "automatic")
        storage.putBoolean("setting", false)

        assertEquals(false, storage.getBoolean("setting"))
        assertEquals(false, preferences.getAll()["setting"])
    }

    @Test
    fun remove_deletesTheStoredValue() {
        storage.putFloat("zoom", 9.5f)

        storage.remove("zoom")

        assertFalse(storage.contains("zoom"))
        assertNull(storage.getFloat("zoom"))
    }
}
