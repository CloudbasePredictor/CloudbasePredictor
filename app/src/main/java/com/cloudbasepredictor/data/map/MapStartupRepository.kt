package com.cloudbasepredictor.data.map

import android.content.SharedPreferences
import com.cloudbasepredictor.data.place.FavoritePlacesBackupStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface MapStartupRepository {
    val startWithFavorites: StateFlow<Boolean>

    fun setStartWithFavorites(startWithFavorites: Boolean)
}

@SingleIn(AppScope::class)
class SharedPrefsMapStartupRepository @Inject constructor(
    private val prefs: SharedPreferences,
    private val backupStore: FavoritePlacesBackupStore,
) : MapStartupRepository {
    private val mutableStartWithFavorites = MutableStateFlow(loadStartWithFavorites())

    override val startWithFavorites: StateFlow<Boolean> = mutableStartWithFavorites.asStateFlow()

    override fun setStartWithFavorites(startWithFavorites: Boolean) {
        mutableStartWithFavorites.value = startWithFavorites
        prefs.edit()
            .putBoolean(KEY_START_WITH_FAVORITES, startWithFavorites)
            .apply()
        backupStore.saveStartWithFavorites(startWithFavorites)
    }

    private fun loadStartWithFavorites(): Boolean {
        if (prefs.contains(KEY_START_WITH_FAVORITES)) {
            return prefs.getBoolean(KEY_START_WITH_FAVORITES, DEFAULT_START_WITH_FAVORITES)
        }

        backupStore.readStartWithFavorites()?.let { restored ->
            prefs.edit().putBoolean(KEY_START_WITH_FAVORITES, restored).apply()
            return restored
        }

        return DEFAULT_START_WITH_FAVORITES
    }

    private companion object {
        const val KEY_START_WITH_FAVORITES = "start_with_favorites"
        const val DEFAULT_START_WITH_FAVORITES = true
    }
}
