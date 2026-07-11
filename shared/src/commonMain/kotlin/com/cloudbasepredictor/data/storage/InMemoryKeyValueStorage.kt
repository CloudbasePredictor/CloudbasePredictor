package com.cloudbasepredictor.data.storage

/** Session-only [KeyValueStorage] with the same one-type-per-key semantics as SharedPreferences. */
class InMemoryKeyValueStorage : KeyValueStorage {
    private val strings = mutableMapOf<String, String>()
    private val booleans = mutableMapOf<String, Boolean>()
    private val floats = mutableMapOf<String, Float>()

    override fun contains(key: String): Boolean {
        return key in strings || key in booleans || key in floats
    }

    override fun getString(key: String): String? = strings[key]

    override fun getBoolean(key: String): Boolean? = booleans[key]

    override fun getFloat(key: String): Float? = floats[key]

    override fun putString(key: String, value: String) {
        remove(key)
        strings[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
        remove(key)
        booleans[key] = value
    }

    override fun putFloat(key: String, value: Float) {
        remove(key)
        floats[key] = value
    }

    override fun remove(key: String) {
        strings.remove(key)
        booleans.remove(key)
        floats.remove(key)
    }
}
