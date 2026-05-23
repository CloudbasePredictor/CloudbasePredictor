package com.cloudbasepredictor.data.launch

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchSiteDisplayRepositoryTest {
    @Test
    fun defaultShowsLaunchSites() {
        val repository = SharedPrefsLaunchSiteDisplayRepository(FakeSharedPreferences())

        assertEquals(true, repository.showLaunchSites.value)
    }

    @Test
    fun setShowLaunchSites_persistsToPreferences() {
        val prefs = FakeSharedPreferences()
        val repository = SharedPrefsLaunchSiteDisplayRepository(prefs)

        repository.setShowLaunchSites(false)

        assertEquals(false, repository.showLaunchSites.value)
        assertEquals(false, prefs.getBoolean("show_launch_sites", true))
    }

    @Test
    fun persistedPreferenceRestores() {
        val prefs = FakeSharedPreferences().apply {
            edit().putBoolean("show_launch_sites", false).apply()
        }

        val repository = SharedPrefsLaunchSiteDisplayRepository(prefs)

        assertEquals(false, repository.showLaunchSites.value)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (data[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {
            listener?.let { listeners.add(it) }
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {
            listener?.let { listeners.remove(it) }
        }

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                key?.let { pending[it] = values }
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                key?.let { pending[it] = null }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clear = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clear) data.clear()
                pending.forEach { (key, value) ->
                    if (value == null) {
                        data.remove(key)
                    } else {
                        data[key] = value
                    }
                    listeners.forEach { it.onSharedPreferenceChanged(this@FakeSharedPreferences, key) }
                }
            }
        }
    }
}
