package com.cloudbasepredictor.testing

import android.content.SharedPreferences

/** In-memory SharedPreferences test double with synchronous edit application. */
class FakeSharedPreferences(
    initialValues: Map<String, Any> = emptyMap(),
) : SharedPreferences {
    private val values = initialValues.toMutableMap()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        value(key)?.let { it as String } ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (value(key)?.let { it as Set<String> }?.toMutableSet() ?: defValues)

    override fun getInt(key: String?, defValue: Int): Int = value(key)?.let { it as Int } ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = value(key)?.let { it as Long } ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = value(key)?.let { it as Float } ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        value(key)?.let { it as Boolean } ?: defValue

    override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        listener?.let(listeners::add)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        listener?.let(listeners::remove)
    }

    private fun value(key: String?): Any? = key?.let(values::get)

    private inner class Editor : SharedPreferences.Editor {
        private val pendingValues = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            put(key, value)

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = put(key, values?.toSet())

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor = put(key, null)

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
        }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply {
            if (key != null) pendingValues[key] = value
        }

        private fun applyChanges() {
            val changedKeys = linkedSetOf<String>()
            if (clearRequested) {
                changedKeys += values.keys
                values.clear()
            }
            pendingValues.forEach { (key, value) ->
                changedKeys += key
                if (value == null) values.remove(key) else values[key] = value
            }
            changedKeys.forEach { key ->
                listeners.forEach { listener ->
                    listener.onSharedPreferenceChanged(this@FakeSharedPreferences, key)
                }
            }
        }
    }
}
