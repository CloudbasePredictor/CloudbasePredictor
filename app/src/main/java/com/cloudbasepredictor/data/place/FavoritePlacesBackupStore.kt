package com.cloudbasepredictor.data.place

import android.content.SharedPreferences
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.theme.ThemePreference
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.di.FavoritePlacesBackupPreferences
import com.cloudbasepredictor.model.SavedPlace
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

object FavoritePlacesBackupContract {
    const val PREFS_NAME = "favorite_places_backup"
    const val PREFS_FILE_NAME = "$PREFS_NAME.xml"
}

@SingleIn(AppScope::class)
class FavoritePlacesBackupStore @Inject constructor(
    @param:FavoritePlacesBackupPreferences private val prefs: SharedPreferences,
    private val json: Json,
) {
    fun readFavoritePlaces(): List<SavedPlace> {
        return readPayload()
            ?.places
            ?.mapNotNull { place ->
                runCatching {
                    SavedPlace.fromCoordinates(
                        latitude = place.latitude,
                        longitude = place.longitude,
                    ).copy(
                        name = place.name,
                        isFavorite = true,
                    )
                }.getOrNull()
            }
            ?.distinctBy(SavedPlace::id)
            .orEmpty()
    }

    fun saveFavoritePlaces(places: List<SavedPlace>) {
        savePayload(
            currentPayload().copy(
                places = places
                    .filter(SavedPlace::isFavorite)
                    .distinctBy(SavedPlace::id)
                    .map { place ->
                        FavoritePlaceBackupEntry(
                            name = place.name,
                            latitude = place.latitude,
                            longitude = place.longitude,
                        )
                    },
            ),
        )
    }

    fun readUnitPreset(): UnitPreset? {
        val unitPresetName = (readPayload()?.unitPreset as? JsonPrimitive)?.contentOrNull ?: return null
        return runCatching { UnitPreset.valueOf(unitPresetName) }.getOrNull()
    }

    fun saveUnitPreset(unitPreset: UnitPreset) {
        savePayload(currentPayload().copy(unitPreset = JsonPrimitive(unitPreset.name)))
    }

    fun readMapLayer(): MapLayerPreference? {
        val mapLayerName = (readPayload()?.mapLayer as? JsonPrimitive)?.contentOrNull ?: return null
        return runCatching { MapLayerPreference.valueOf(mapLayerName) }.getOrNull()
    }

    fun saveMapLayer(mapLayer: MapLayerPreference) {
        savePayload(currentPayload().copy(mapLayer = JsonPrimitive(mapLayer.name)))
    }

    fun readThemePreference(): ThemePreference? {
        val themeName = (readPayload()?.themePreference as? JsonPrimitive)?.contentOrNull ?: return null
        return runCatching { ThemePreference.valueOf(themeName) }.getOrNull()
    }

    /**
     * Mirrors the theme into the backup payload. [ThemePreference.AUTO] follows the
     * system, so it is cleared rather than stored: a restore then falls back to AUTO.
     */
    fun saveThemePreference(themePreference: ThemePreference) {
        val stored = themePreference
            .takeUnless { it == ThemePreference.AUTO }
            ?.let { JsonPrimitive(it.name) }
        savePayload(currentPayload().copy(themePreference = stored))
    }

    fun readStartWithFavorites(): Boolean? {
        return (readPayload()?.startWithFavorites as? JsonPrimitive)?.booleanOrNull
    }

    fun saveStartWithFavorites(startWithFavorites: Boolean) {
        savePayload(currentPayload().copy(startWithFavorites = JsonPrimitive(startWithFavorites)))
    }

    private fun currentPayload(): FavoritePlacesBackupPayload =
        readPayload() ?: FavoritePlacesBackupPayload()

    private fun readPayload(): FavoritePlacesBackupPayload? {
        val payload = prefs.getString(KEY_PAYLOAD, null) ?: return null
        return try {
            json.decodeFromString<FavoritePlacesBackupPayload>(payload)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SerializationException) {
            null
        }
    }

    private fun savePayload(payload: FavoritePlacesBackupPayload) {
        prefs.edit()
            .putString(KEY_PAYLOAD, json.encodeToString(payload.copy(schemaVersion = BACKUP_SCHEMA_VERSION)))
            .apply()
    }

    private companion object {
        const val KEY_PAYLOAD = "payload"
    }
}

private const val BACKUP_SCHEMA_VERSION = 4

@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class FavoritePlacesBackupPayload(
    @EncodeDefault
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val places: List<FavoritePlaceBackupEntry> = emptyList(),
    val unitPreset: JsonElement? = null,
    val mapLayer: JsonElement? = null,
    val themePreference: JsonElement? = null,
    val startWithFavorites: JsonElement? = null,
)

@Serializable
private data class FavoritePlaceBackupEntry(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)
