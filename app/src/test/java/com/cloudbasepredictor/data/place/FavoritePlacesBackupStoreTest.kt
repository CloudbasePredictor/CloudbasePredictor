package com.cloudbasepredictor.data.place

import android.content.SharedPreferences
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.theme.ThemePreference
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.model.SavedPlace
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class FavoritePlacesBackupStoreTest {

    private val prefs = FakeSharedPreferences()
    private val store = FavoritePlacesBackupStore(
        prefs = prefs,
        json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        },
    )

    @Test
    fun saveFavoritePlaces_writesOnlyBackupFields() {
        store.saveFavoritePlaces(
            listOf(
                SavedPlace(
                    id = "custom-id",
                    name = "Interlaken",
                    latitude = 46.6863,
                    longitude = 7.8632,
                    isFavorite = true,
                ),
            ),
        )

        val payload = prefs.getString("payload", null).orEmpty()
        assertTrue(payload.contains("\"schemaVersion\":4"))
        assertTrue(payload.contains("\"name\":\"Interlaken\""))
        assertTrue(payload.contains("\"latitude\":46.6863"))
        assertTrue(payload.contains("\"longitude\":7.8632"))
        assertFalse(payload.contains("custom-id"))
        assertFalse(payload.contains("isFavorite"))
    }

    @Test
    fun readFavoritePlaces_rebuildsPlacesFromCoordinates() {
        prefs.edit()
            .putString(
                "payload",
                """
                    {
                      "schemaVersion": 1,
                      "places": [
                        {
                          "name": "Interlaken",
                          "latitude": 46.68634,
                          "longitude": 7.86321
                        }
                      ]
                    }
                """.trimIndent(),
            )
            .apply()

        val places = store.readFavoritePlaces()

        assertEquals(1, places.size)
        assertEquals("place:46.6863:7.8632", places.single().id)
        assertEquals("Interlaken", places.single().name)
        assertEquals(46.68634, places.single().latitude, 0.0)
        assertEquals(7.86321, places.single().longitude, 0.0)
        assertTrue(places.single().isFavorite)
    }

    @Test
    fun readUnitPreset_returnsSavedUnitPreset() {
        prefs.edit()
            .putString(
                "payload",
                """
                    {
                      "schemaVersion": 2,
                      "places": [],
                      "unitPreset": "IMPERIAL"
                    }
                """.trimIndent(),
            )
            .apply()

        assertEquals(UnitPreset.IMPERIAL, store.readUnitPreset())
    }

    @Test
    fun readUnitPreset_missingOrUnknownValue_returnsNullWithoutBreakingFavorites() {
        prefs.edit()
            .putString(
                "payload",
                """
                    {
                      "schemaVersion": 2,
                      "places": [
                        {
                          "name": "Interlaken",
                          "latitude": 46.68634,
                          "longitude": 7.86321
                        }
                      ],
                      "unitPreset": "POTATOES_PER_MINUTE"
                    }
                """.trimIndent(),
            )
            .apply()

        assertEquals(null, store.readUnitPreset())
        assertEquals("Interlaken", store.readFavoritePlaces().single().name)
    }

    @Test
    fun readUnitPreset_malformedValue_returnsNullWithoutBreakingFavorites() {
        prefs.edit()
            .putString(
                "payload",
                """
                    {
                      "schemaVersion": 2,
                      "places": [
                        {
                          "name": "Interlaken",
                          "latitude": 46.68634,
                          "longitude": 7.86321
                        }
                      ],
                      "unitPreset": { "value": "IMPERIAL" }
                    }
                """.trimIndent(),
            )
            .apply()

        assertEquals(null, store.readUnitPreset())
        assertEquals("Interlaken", store.readFavoritePlaces().single().name)
    }

    @Test
    fun readMapLayer_returnsSavedMapLayer() {
        prefs.edit()
            .putString(
                "payload",
                """
                    {
                      "schemaVersion": 3,
                      "places": [],
                      "mapLayer": "ESRI_WORLD_IMAGERY"
                    }
                """.trimIndent(),
            )
            .apply()

        assertEquals(MapLayerPreference.ESRI_WORLD_IMAGERY, store.readMapLayer())
    }

    @Test
    fun readMapLayer_returnsSavedOpenTopoMapLayer() {
        prefs.edit()
            .putString(
                "payload",
                """
                    {
                      "schemaVersion": 3,
                      "places": [],
                      "mapLayer": "OPENTOPOMAP"
                    }
                """.trimIndent(),
            )
            .apply()

        assertEquals(MapLayerPreference.OPENTOPOMAP, store.readMapLayer())
    }

    @Test
    fun readMapLayer_missingOrUnknownValue_returnsNullWithoutBreakingFavorites() {
        prefs.edit()
            .putString(
                "payload",
                """
                    {
                      "schemaVersion": 3,
                      "places": [
                        {
                          "name": "Interlaken",
                          "latitude": 46.68634,
                          "longitude": 7.86321
                        }
                      ],
                      "mapLayer": "UNKNOWN_LAYER"
                    }
                """.trimIndent(),
            )
            .apply()

        assertEquals(null, store.readMapLayer())
        assertEquals("Interlaken", store.readFavoritePlaces().single().name)
    }

    @Test
    fun saveFavoritePlaces_preservesExistingUnitPreset() {
        store.saveUnitPreset(UnitPreset.AVIATION)

        store.saveFavoritePlaces(
            listOf(
                SavedPlace(
                    id = "custom-id",
                    name = "Interlaken",
                    latitude = 46.6863,
                    longitude = 7.8632,
                    isFavorite = true,
                ),
            ),
        )

        assertEquals(UnitPreset.AVIATION, store.readUnitPreset())
        assertTrue(prefs.getString("payload", null).orEmpty().contains("\"unitPreset\":\"AVIATION\""))
    }

    @Test
    fun saveFavoritePlaces_preservesExistingMapLayer() {
        store.saveMapLayer(MapLayerPreference.NASA_GIBS)

        store.saveFavoritePlaces(
            listOf(
                SavedPlace(
                    id = "custom-id",
                    name = "Interlaken",
                    latitude = 46.6863,
                    longitude = 7.8632,
                    isFavorite = true,
                ),
            ),
        )

        assertEquals(MapLayerPreference.NASA_GIBS, store.readMapLayer())
        assertTrue(prefs.getString("payload", null).orEmpty().contains("\"mapLayer\":\"NASA_GIBS\""))
    }

    @Test
    fun saveUnitPreset_preservesExistingFavoritePlaces() {
        store.saveFavoritePlaces(
            listOf(
                SavedPlace(
                    id = "custom-id",
                    name = "Interlaken",
                    latitude = 46.6863,
                    longitude = 7.8632,
                    isFavorite = true,
                ),
            ),
        )

        store.saveUnitPreset(UnitPreset.METRIC_MPS)

        assertEquals(UnitPreset.METRIC_MPS, store.readUnitPreset())
        assertEquals("Interlaken", store.readFavoritePlaces().single().name)
    }

    @Test
    fun saveUnitPreset_preservesExistingMapLayer() {
        store.saveMapLayer(MapLayerPreference.NASA_GIBS)

        store.saveUnitPreset(UnitPreset.METRIC_MPS)

        assertEquals(UnitPreset.METRIC_MPS, store.readUnitPreset())
        assertEquals(MapLayerPreference.NASA_GIBS, store.readMapLayer())
    }

    @Test
    fun saveMapLayer_preservesExistingFavoritePlacesAndUnitPreset() {
        store.saveFavoritePlaces(
            listOf(
                SavedPlace(
                    id = "custom-id",
                    name = "Interlaken",
                    latitude = 46.6863,
                    longitude = 7.8632,
                    isFavorite = true,
                ),
            ),
        )
        store.saveUnitPreset(UnitPreset.AVIATION)

        store.saveMapLayer(MapLayerPreference.NASA_GIBS)

        assertEquals(MapLayerPreference.NASA_GIBS, store.readMapLayer())
        assertEquals(UnitPreset.AVIATION, store.readUnitPreset())
        assertEquals("Interlaken", store.readFavoritePlaces().single().name)
    }

    @Test
    fun backupPreferencesFileName_isStableForRestoringExistingBackups() {
        // Do not change this file name: Google Auto Backup restores data by file path,
        // so renaming it would strand favorites saved by older app versions.
        assertEquals("favorite_places_backup.xml", FavoritePlacesBackupContract.PREFS_FILE_NAME)
        assertEquals("favorite_places_backup", FavoritePlacesBackupContract.PREFS_NAME)

        assertBackupRulesReferenceStableFileName("src/main/res/xml/backup_rules.xml")
        assertBackupRulesReferenceStableFileName("src/main/res/xml-v28/backup_rules.xml")
        assertBackupRulesReferenceStableFileName("src/main/res/xml/data_extraction_rules.xml")
    }

    @Test
    fun saveThemePreference_storesExplicitOverride() {
        store.saveThemePreference(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, store.readThemePreference())
        assertTrue(prefs.getString("payload", null).orEmpty().contains("\"themePreference\":\"DARK\""))
    }

    @Test
    fun saveThemePreference_auto_isNotBackedUp() {
        store.saveThemePreference(ThemePreference.LIGHT)
        store.saveThemePreference(ThemePreference.AUTO)

        assertEquals(null, store.readThemePreference())
        assertFalse(prefs.getString("payload", null).orEmpty().contains("themePreference"))
    }

    @Test
    fun saveStartWithFavorites_roundTrips() {
        store.saveStartWithFavorites(false)

        assertEquals(false, store.readStartWithFavorites())
        assertTrue(prefs.getString("payload", null).orEmpty().contains("\"startWithFavorites\":false"))
    }

    @Test
    fun saveFavoritePlaces_preservesThemeAndStartWithFavorites() {
        store.saveThemePreference(ThemePreference.DARK)
        store.saveStartWithFavorites(false)

        store.saveFavoritePlaces(
            listOf(
                SavedPlace(
                    id = "custom-id",
                    name = "Interlaken",
                    latitude = 46.6863,
                    longitude = 7.8632,
                    isFavorite = true,
                ),
            ),
        )

        assertEquals(ThemePreference.DARK, store.readThemePreference())
        assertEquals(false, store.readStartWithFavorites())
        assertEquals("Interlaken", store.readFavoritePlaces().single().name)
    }

    @Test
    fun readFavoritePlaces_ignoresInvalidPayload() {
        prefs.edit().putString("payload", "not-json").apply()

        assertEquals(emptyList<SavedPlace>(), store.readFavoritePlaces())
        assertEquals(null, store.readUnitPreset())
        assertEquals(null, store.readMapLayer())
    }

    private fun assertBackupRulesReferenceStableFileName(path: String) {
        val file = File(path)
        assertTrue("Missing backup rules file: ${file.path}", file.exists())

        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val includes = document.getElementsByTagName("include")
        val includedPaths = List(includes.length) { index ->
            includes.item(index).attributes.getNamedItem("path")?.nodeValue
        }

        assertTrue(
            "Backup rules in ${file.path} must include ${FavoritePlacesBackupContract.PREFS_FILE_NAME}",
            FavoritePlacesBackupContract.PREFS_FILE_NAME in includedPaths,
        )
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (data[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int =
            data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long =
            data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float =
            data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {
            listener?.let { listeners.add(it) }
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {
            listener?.let { listeners.remove(it) }
        }

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                key?.let { pending[it] = values }
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                key?.let { pending[it] = null }
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clear = true
                return this
            }

            override fun commit(): Boolean {
                flush()
                return true
            }

            override fun apply() {
                flush()
            }

            private fun flush() {
                if (clear) data.clear()
                pending.forEach { (key, value) ->
                    if (value == null) {
                        data.remove(key)
                    } else {
                        data[key] = value
                    }
                    listeners.forEach { it.onSharedPreferenceChanged(this@FakeSharedPreferences, key) }
                }
            }
        }
    }
}
