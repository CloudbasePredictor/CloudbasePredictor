package com.cloudbasepredictor.web.launch

import com.cloudbasepredictor.data.launch.LaunchSiteBounds
import kotlinx.serialization.Serializable

/**
 * Parsed `manifest.json` describing the build-time ParaglidingEarth snapshot published alongside the
 * web app. The browser reads only this file plus the tiles intersecting the current viewport, all
 * same-origin from GitHub Pages.
 */
@Serializable
data class LaunchSiteSnapshotManifest(
    val schemaVersion: Int = 0,
    val datasetId: String = "",
    val generatedAt: String = "",
    val siteCount: Int = 0,
    val tileSizeDegrees: Int = 0,
    val source: LaunchSiteSnapshotAttribution? = null,
    val tiles: List<LaunchSiteSnapshotTile> = emptyList(),
)

@Serializable
data class LaunchSiteSnapshotAttribution(
    val name: String = "",
    val license: String = "",
)

@Serializable
data class LaunchSiteSnapshotTile(
    val key: String = "",
    val path: String = "",
    val south: Double = 0.0,
    val west: Double = 0.0,
    val north: Double = 0.0,
    val east: Double = 0.0,
    val siteCount: Int = 0,
    val bytes: Long = 0,
    val sha256: String = "",
) {
    fun bounds(): LaunchSiteBounds =
        LaunchSiteBounds(north = north, south = south, west = west, east = east)
}
