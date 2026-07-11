package com.cloudbasepredictor.data.storage

import android.content.SharedPreferences
import androidx.core.content.edit

class SharedPreferencesKeyValueStorage(
    private val preferences: SharedPreferences,
) : KeyValueStorage {
    override fun contains(key: String): Boolean = preferences.contains(key)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun getBoolean(key: String): Boolean? {
        return if (preferences.contains(key)) preferences.getBoolean(key, false) else null
    }

    override fun getFloat(key: String): Float? {
        return if (preferences.contains(key)) preferences.getFloat(key, 0f) else null
    }

    override fun putString(key: String, value: String) {
        preferences.edit { putString(key, value) }
    }

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit { putBoolean(key, value) }
    }

    override fun putFloat(key: String, value: Float) {
        preferences.edit { putFloat(key, value) }
    }

    override fun remove(key: String) {
        preferences.edit { remove(key) }
    }
}
