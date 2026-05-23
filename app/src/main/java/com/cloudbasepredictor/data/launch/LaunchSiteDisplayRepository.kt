package com.cloudbasepredictor.data.launch

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface LaunchSiteDisplayRepository {
    val showLaunchSites: StateFlow<Boolean>

    fun setShowLaunchSites(showLaunchSites: Boolean)
}

@Singleton
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
