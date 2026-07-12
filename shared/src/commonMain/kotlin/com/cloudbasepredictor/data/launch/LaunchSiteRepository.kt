package com.cloudbasepredictor.data.launch

import com.cloudbasepredictor.model.ParaglidingLaunchSite

/**
 * Loads paragliding launch sites for a normalized [LaunchSiteBounds]. Android backs this with the
 * live ParaglidingEarth API plus a Room cache; the web app backs it with a build-time static
 * snapshot served same-origin from GitHub Pages.
 */
interface LaunchSiteRepository {
    suspend fun getLaunchSites(bounds: LaunchSiteBounds): List<ParaglidingLaunchSite>
}
