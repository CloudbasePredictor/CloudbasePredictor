package com.cloudbasepredictor.data.storage

/**
 * Small synchronous key-value boundary for user preferences.
 *
 * Android implements this with SharedPreferences and the browser implements it
 * with localStorage. Implementations may throw [StorageUnavailableException]
 * when durable storage is disabled or exhausted; callers can then retain the
 * new value in memory for the current session.
 */
interface KeyValueStorage {
    fun contains(key: String): Boolean

    fun getString(key: String): String?

    fun getBoolean(key: String): Boolean?

    fun getFloat(key: String): Float?

    fun putString(key: String, value: String)

    fun putBoolean(key: String, value: Boolean)

    fun putFloat(key: String, value: Float)

    fun remove(key: String)
}

class StorageUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
