package com.cloudbasepredictor.data.launch

import android.content.SharedPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface LaunchSiteDisplayRepository {
    val showLaunchSites: StateFlow<Boolean>

    fun setShowLaunchSites(showLaunchSites: Boolean)
}

@SingleIn(AppScope::class)
class SharedPrefsLaunchSiteDisplayRepository @Inject constructor(
    private val prefs: SharedPreferences,
) : LaunchSiteDisplayRepository {
    private val mutableShowLaunchSites = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_LAUNCH_SITES, DEFAULT_SHOW_LAUNCH_SITES),
    )

    override val showLaunchSites: StateFlow<Boolean> = mutableShowLaunchSites.asStateFlow()

    override fun setShowLaunchSites(showLaunchSites: Boolean) {
        mutableShowLaunchSites.value = showLaunchSites
        prefs.edit()
            .putBoolean(KEY_SHOW_LAUNCH_SITES, showLaunchSites)
            .apply()
    }

    private companion object {
        const val KEY_SHOW_LAUNCH_SITES = "show_launch_sites"
        const val DEFAULT_SHOW_LAUNCH_SITES = true
    }
}
