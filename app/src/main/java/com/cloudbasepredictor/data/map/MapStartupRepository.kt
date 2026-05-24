package com.cloudbasepredictor.data.map

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface MapStartupRepository {
    val startWithFavorites: StateFlow<Boolean>

    fun setStartWithFavorites(startWithFavorites: Boolean)
}

@Singleton
class SharedPrefsMapStartupRepository @Inject constructor(
    private val prefs: SharedPreferences,
) : MapStartupRepository {
    private val mutableStartWithFavorites = MutableStateFlow(
        prefs.getBoolean(KEY_START_WITH_FAVORITES, DEFAULT_START_WITH_FAVORITES),
    )

    override val startWithFavorites: StateFlow<Boolean> = mutableStartWithFavorites.asStateFlow()

    override fun setStartWithFavorites(startWithFavorites: Boolean) {
        mutableStartWithFavorites.value = startWithFavorites
        prefs.edit()
            .putBoolean(KEY_START_WITH_FAVORITES, startWithFavorites)
            .apply()
    }

    private companion object {
        const val KEY_START_WITH_FAVORITES = "start_with_favorites"
        const val DEFAULT_START_WITH_FAVORITES = true
    }
}
