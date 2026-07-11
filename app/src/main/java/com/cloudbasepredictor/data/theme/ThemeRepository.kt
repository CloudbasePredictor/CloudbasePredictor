package com.cloudbasepredictor.data.theme

import android.content.SharedPreferences
import com.cloudbasepredictor.data.place.FavoritePlacesBackupStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ThemeRepository {
    val preference: StateFlow<ThemePreference>
    fun setPreference(preference: ThemePreference)
}

@SingleIn(AppScope::class)
class SharedPrefsThemeRepository @Inject constructor(
    private val prefs: SharedPreferences,
    private val backupStore: FavoritePlacesBackupStore,
) : ThemeRepository {
    private val mutablePreference = MutableStateFlow(loadPreference())

    override val preference: StateFlow<ThemePreference> = mutablePreference.asStateFlow()

    override fun setPreference(preference: ThemePreference) {
        mutablePreference.value = preference
        prefs.edit().putString(KEY_THEME_PREFERENCE, preference.name).apply()
        backupStore.saveThemePreference(preference)
    }

    private fun loadPreference(): ThemePreference {
        prefs.getString(KEY_THEME_PREFERENCE, null)
            ?.let(::decodePreference)
            ?.let { return it }

        backupStore.readThemePreference()?.let { restored ->
            prefs.edit().putString(KEY_THEME_PREFERENCE, restored.name).apply()
            return restored
        }

        return ThemePreference.AUTO
    }

    private fun decodePreference(value: String): ThemePreference? {
        return runCatching { ThemePreference.valueOf(value) }.getOrNull()
    }

    private companion object {
        const val KEY_THEME_PREFERENCE = "theme_preference"
    }
}
