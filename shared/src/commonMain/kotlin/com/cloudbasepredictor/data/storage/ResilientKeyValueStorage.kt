package com.cloudbasepredictor.data.storage

/**
 * Mirrors durable values into session memory and switches to that mirror if storage becomes
 * unavailable. Once a durable operation fails it is not retried during the same session.
 */
class ResilientKeyValueStorage(
    private val durable: KeyValueStorage,
    private val session: KeyValueStorage = InMemoryKeyValueStorage(),
) : KeyValueStorage {
    private var durableAvailable = true

    override fun contains(key: String): Boolean {
        if (!durableAvailable) return session.contains(key)
        return useDurableOrFallback(
            durableOperation = { durable.contains(key) },
            fallbackOperation = { session.contains(key) },
        )
    }

    override fun getString(key: String): String? {
        if (!durableAvailable) return session.getString(key)
        return useDurableOrFallback(
            durableOperation = {
                durable.getString(key)?.also { session.putString(key, it) }
            },
            fallbackOperation = { session.getString(key) },
        )
    }

    override fun getBoolean(key: String): Boolean? {
        if (!durableAvailable) return session.getBoolean(key)
        return useDurableOrFallback(
            durableOperation = {
                durable.getBoolean(key)?.also { session.putBoolean(key, it) }
            },
            fallbackOperation = { session.getBoolean(key) },
        )
    }

    override fun getFloat(key: String): Float? {
        if (!durableAvailable) return session.getFloat(key)
        return useDurableOrFallback(
            durableOperation = {
                durable.getFloat(key)?.also { session.putFloat(key, it) }
            },
            fallbackOperation = { session.getFloat(key) },
        )
    }

    override fun putString(key: String, value: String) {
        session.putString(key, value)
        writeDurably { durable.putString(key, value) }
    }

    override fun putBoolean(key: String, value: Boolean) {
        session.putBoolean(key, value)
        writeDurably { durable.putBoolean(key, value) }
    }

    override fun putFloat(key: String, value: Float) {
        session.putFloat(key, value)
        writeDurably { durable.putFloat(key, value) }
    }

    override fun remove(key: String) {
        session.remove(key)
        writeDurably { durable.remove(key) }
    }

    private fun writeDurably(operation: () -> Unit) {
        if (!durableAvailable) return
        try {
            operation()
        } catch (_: StorageUnavailableException) {
            durableAvailable = false
        }
    }

    private inline fun <T> useDurableOrFallback(
        durableOperation: () -> T,
        fallbackOperation: () -> T,
    ): T {
        return try {
            durableOperation()
        } catch (_: StorageUnavailableException) {
            durableAvailable = false
            fallbackOperation()
        }
    }
}
