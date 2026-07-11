package com.cloudbasepredictor.data.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyValueStorageTest {
    @Test
    fun inMemoryStorageRoundTripsValuesAndKeepsOneTypePerKey() {
        val storage = InMemoryKeyValueStorage()

        storage.putString("preference", "AUTO")
        assertTrue(storage.contains("preference"))
        assertEquals("AUTO", storage.getString("preference"))

        storage.putBoolean("preference", true)
        assertNull(storage.getString("preference"))
        assertEquals(true, storage.getBoolean("preference"))

        storage.putFloat("preference", 3.5f)
        assertNull(storage.getBoolean("preference"))
        assertEquals(3.5f, storage.getFloat("preference"))

        storage.remove("preference")
        assertFalse(storage.contains("preference"))
    }

    @Test
    fun resilientStorageRetainsWritesWhenDurableStorageFails() {
        val durable = FailingKeyValueStorage()
        val storage = ResilientKeyValueStorage(durable)

        storage.putString("unit_preset", "AVIATION")
        storage.putBoolean("start_with_favorites", false)

        assertEquals("AVIATION", storage.getString("unit_preset"))
        assertEquals(false, storage.getBoolean("start_with_favorites"))
        assertEquals(1, durable.operationCount)
    }

    @Test
    fun resilientStorageMirrorsSuccessfulDurableReadsBeforeFailure() {
        val durable = ToggleableKeyValueStorage().apply {
            delegate.putString("theme_preference", "DARK")
        }
        val storage = ResilientKeyValueStorage(durable)

        assertEquals("DARK", storage.getString("theme_preference"))
        durable.fails = true

        assertEquals("DARK", storage.getString("theme_preference"))
    }
}

private class FailingKeyValueStorage : KeyValueStorage {
    var operationCount = 0

    private fun unavailable(): Nothing {
        operationCount += 1
        throw StorageUnavailableException("Unavailable")
    }

    override fun contains(key: String): Boolean = unavailable()
    override fun getString(key: String): String? = unavailable()
    override fun getBoolean(key: String): Boolean? = unavailable()
    override fun getFloat(key: String): Float? = unavailable()
    override fun putString(key: String, value: String) = unavailable()
    override fun putBoolean(key: String, value: Boolean) = unavailable()
    override fun putFloat(key: String, value: Float) = unavailable()
    override fun remove(key: String) = unavailable()
}

private class ToggleableKeyValueStorage : KeyValueStorage {
    val delegate = InMemoryKeyValueStorage()
    var fails = false

    private fun checkAvailable() {
        if (fails) throw StorageUnavailableException("Unavailable")
    }

    override fun contains(key: String): Boolean = delegate.contains(key).also { checkAvailable() }
    override fun getString(key: String): String? = delegate.getString(key).also { checkAvailable() }
    override fun getBoolean(key: String): Boolean? = delegate.getBoolean(key).also { checkAvailable() }
    override fun getFloat(key: String): Float? = delegate.getFloat(key).also { checkAvailable() }
    override fun putString(key: String, value: String) = checkAvailable().also { delegate.putString(key, value) }
    override fun putBoolean(key: String, value: Boolean) = checkAvailable().also { delegate.putBoolean(key, value) }
    override fun putFloat(key: String, value: Float) = checkAvailable().also { delegate.putFloat(key, value) }
    override fun remove(key: String) = checkAvailable().also { delegate.remove(key) }
}
