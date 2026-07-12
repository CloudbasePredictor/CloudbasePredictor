package com.cloudbasepredictor.web.launch

import com.cloudbasepredictor.data.remote.ParaglidingEarthFeatureCollection

/**
 * Reads the static launch-site snapshot. Implementations must issue only same-origin requests to the
 * published `data/launch-sites/` directory; the browser never contacts ParaglidingEarth directly.
 */
interface LaunchSiteSnapshotSource {
    suspend fun loadManifest(): LaunchSiteSnapshotManifest

    suspend fun loadTile(relativePath: String): ParaglidingEarthFeatureCollection
}
