package com.cloudbasepredictor.web.map

import com.cloudbasepredictor.data.map.MapLayerPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebMapStylesTest {
    @Test
    fun openFreeMapUsesTheKeylessVectorStyle() {
        assertEquals(
            WebMapStyle.Url("https://tiles.openfreemap.org/styles/liberty"),
            buildWebMapStyle(MapLayerPreference.OPENFREEMAP, "2026-07-10"),
        )
    }

    @Test
    fun rasterStylesKeepTheirNativeZoomAndAttribution() {
        val topo = assertIs<WebMapStyle.Raster>(
            buildWebMapStyle(MapLayerPreference.OPENTOPOMAP, "2026-07-10"),
        )
        assertEquals(17, topo.maxNativeZoom)
        assertEquals(3, topo.tileUrls.size)

        val nasa = assertIs<WebMapStyle.Raster>(
            buildWebMapStyle(MapLayerPreference.NASA_GIBS, "2026-07-10"),
        )
        assertEquals(9, nasa.maxNativeZoom)
        assertTrue(nasa.tileUrls.single().contains("/2026-07-10/"))

        val esri = assertIs<WebMapStyle.Raster>(
            buildWebMapStyle(MapLayerPreference.ESRI_WORLD_IMAGERY, "2026-07-10"),
        )
        assertEquals(23, esri.maxNativeZoom)
        assertTrue(esri.attribution.isNotBlank())
    }

    @Test
    fun rasterStyleSerializesToMapLibreStyleJson() {
        val style = assertIs<WebMapStyle.Raster>(
            buildWebMapStyle(MapLayerPreference.NASA_GIBS, "2026-07-10"),
        )
        val json = style.toMapLibreJson()

        assertTrue(json.contains("\"version\":8"))
        assertTrue(json.contains("\"maxzoom\":9"))
        assertTrue(json.contains("nasa-gibs-true-color"))
    }
}
