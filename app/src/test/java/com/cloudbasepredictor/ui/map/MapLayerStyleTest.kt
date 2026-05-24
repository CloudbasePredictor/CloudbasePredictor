package com.cloudbasepredictor.ui.map

import com.cloudbasepredictor.R
import com.cloudbasepredictor.data.map.MapLayerPreference
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLayerStyleTest {
    @Test
    fun nasaGibsTileDateUtc_usesPreviousUtcDayForStableCaching() {
        val nowMillis = utcMillis("2026-05-15 00:15:00")

        assertEquals("2026-05-14", nasaGibsTileDateUtc(nowMillis))
    }

    @Test
    fun nasaGibsTrueColorTileUrl_usesExplicitDateAndGoogleMapsCompatibleTiles() {
        val url = nasaGibsTrueColorTileUrl("2026-05-14")

        assertTrue(url.contains("/MODIS_Terra_CorrectedReflectance_TrueColor/default/2026-05-14/"))
        assertTrue(url.contains("/GoogleMapsCompatible_Level9/{z}/{y}/{x}.jpg"))
    }

    @Test
    fun openTopoMapTileUrls_useHttpsSubdomains() {
        assertEquals(
            listOf(
                "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
                "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
                "https://c.tile.opentopomap.org/{z}/{x}/{y}.png",
            ),
            openTopoMapTileUrls(),
        )
    }

    @Test
    fun esriWorldImageryTileUrl_usesArcGisTileEndpoint() {
        assertEquals(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
            esriWorldImageryTileUrl(),
        )
    }

    @Test
    fun mapLayerAttribution_usesCompactLabelsWithExpandableDetails() {
        assertEquals(
            R.string.map_attribution_full,
            mapLayerAttributionDetailRes(MapLayerPreference.OPENFREEMAP),
        )
        assertEquals(
            R.string.map_attribution_opentopomap_compact,
            mapLayerAttributionRes(MapLayerPreference.OPENTOPOMAP),
        )
        assertEquals(
            R.string.map_attribution_opentopomap_full,
            mapLayerAttributionDetailRes(MapLayerPreference.OPENTOPOMAP),
        )
        assertEquals(
            R.string.map_attribution_esri_world_imagery_compact,
            mapLayerAttributionRes(MapLayerPreference.ESRI_WORLD_IMAGERY),
        )
        assertEquals(
            R.string.map_attribution_esri_world_imagery_full,
            mapLayerAttributionDetailRes(MapLayerPreference.ESRI_WORLD_IMAGERY),
        )
        assertEquals(
            R.string.map_attribution_nasa_gibs_full,
            mapLayerAttributionDetailRes(MapLayerPreference.NASA_GIBS),
        )
    }

    private fun utcMillis(value: String): Long {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(value)!!.time
    }
}
