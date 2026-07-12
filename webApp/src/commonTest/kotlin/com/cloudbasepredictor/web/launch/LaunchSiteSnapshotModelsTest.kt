package com.cloudbasepredictor.web.launch

import com.cloudbasepredictor.data.remote.ParaglidingEarthFeatureCollection
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LaunchSiteSnapshotModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesManifestProducedByTheSnapshotGenerator() {
        val manifest = json.decodeFromString(LaunchSiteSnapshotManifest.serializer(), MANIFEST_JSON)

        assertEquals(1, manifest.schemaVersion)
        assertEquals("eac7cebbdfe8b7d9f00f53f0b1a16b1d", manifest.datasetId)
        assertEquals(4, manifest.siteCount)
        assertEquals("ParaglidingEarth", manifest.source?.name)
        assertEquals("CC BY-SA 3.0", manifest.source?.license)
        assertEquals(1, manifest.tiles.size)

        val tile = manifest.tiles.single()
        assertEquals("tiles/68/93.json", tile.path)
        val bounds = tile.bounds()
        assertTrue(bounds.contains(latitude = 46.7, longitude = 7.5))
    }

    @Test
    fun parsesTileFeatureCollectionWithRawGeometryAndProperties() {
        val tile = json.decodeFromString(ParaglidingEarthFeatureCollection.serializer(), TILE_JSON)

        val feature = tile.features.single()
        assertEquals("1001", feature.properties?.pgeSiteId)
        assertEquals(listOf(7.7986, 46.696), feature.geometry?.coordinates)
        assertEquals("1200", feature.properties?.takeoffAltitude)
    }

    private companion object {
        val MANIFEST_JSON = """
            {
              "schemaVersion": 1,
              "datasetId": "eac7cebbdfe8b7d9f00f53f0b1a16b1d",
              "generatedAt": "2026-07-11T22:52:19.679Z",
              "siteCount": 4,
              "tileSizeDegrees": 2,
              "source": { "name": "ParaglidingEarth", "license": "CC BY-SA 3.0" },
              "tiles": [
                {
                  "key": "68:93",
                  "path": "tiles/68/93.json",
                  "south": 46, "west": 6, "north": 48, "east": 8,
                  "siteCount": 2, "bytes": 1438, "sha256": "1f231b49"
                }
              ]
            }
        """.trimIndent()

        val TILE_JSON = """
            {
              "features": [
                {
                  "id": "geojson-1",
                  "geometry": { "coordinates": [7.7986, 46.696] },
                  "properties": { "pge_site_id": "1001", "name": "Beatenberg", "takeoff_altitude": "1200" }
                }
              ]
            }
        """.trimIndent()
    }
}
