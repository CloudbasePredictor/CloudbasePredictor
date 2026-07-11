package com.cloudbasepredictor.data.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapLayerPreferenceTest {
    @Test
    fun layerOrderAndAttributionStayStableAcrossPlatforms() {
        assertEquals(
            listOf(
                MapLayerPreference.OPENFREEMAP,
                MapLayerPreference.OPENTOPOMAP,
                MapLayerPreference.NASA_GIBS,
                MapLayerPreference.ESRI_WORLD_IMAGERY,
            ),
            MapLayerPreference.entries,
        )
        MapLayerPreference.entries.forEach { layer ->
            assertTrue(layer.label.isNotBlank())
            assertTrue(layer.attributionCompact.isNotBlank())
            assertTrue(layer.attributionFull.isNotBlank())
        }
    }
}
