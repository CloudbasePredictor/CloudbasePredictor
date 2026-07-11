@file:Suppress("ComplexCondition", "ReturnCount")

package com.cloudbasepredictor.web.storage

import com.cloudbasepredictor.data.place.FavoritePlaceStore
import com.cloudbasepredictor.model.SavedPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Storage

const val DEFAULT_FAVORITES_STORAGE_KEY = "cbp.kmp.favorites"

private const val FAVORITES_SCHEMA_VERSION = 1
private const val LEGACY_FAVORITES_STORAGE_KEY = "cbp.favorites.v1"

/** Browser favorite store backed by a dedicated schema-versioned localStorage document. */
class BrowserLocalStorageFavoritePlaceStore(
    private val storageKey: String = DEFAULT_FAVORITES_STORAGE_KEY,
    private val legacyFavoritesKey: String = LEGACY_FAVORITES_STORAGE_KEY,
    private val storage: Storage? = browserLocalStorageOrNull(),
) : FavoritePlaceStore {
    private var durableAvailable = storage != null
    private val places = MutableStateFlow(loadInitialPlaces())

    override fun observeAll(): Flow<List<SavedPlace>> = places

    override suspend fun readAll(): List<SavedPlace> = places.value

    override suspend fun findById(placeId: String): SavedPlace? {
        return places.value.firstOrNull { it.id == placeId }
    }

    override suspend fun upsert(place: SavedPlace) {
        val favorite = place.copy(isFavorite = true)
        val updated = listOf(favorite) + places.value.filterNot { it.id == favorite.id }
        places.value = updated
        persistOrDisable(updated)
    }

    override suspend fun delete(placeId: String) {
        val updated = places.value.filterNot { it.id == placeId }
        if (updated.size == places.value.size) return
        places.value = updated
        persistOrDisable(updated)
    }

    private fun loadInitialPlaces(): List<SavedPlace> {
        val availableStorage = storage ?: return emptyList()
        val kmpRaw = try {
            availableStorage.getItemSafely(storageKey)
        } catch (_: BrowserStorageException) {
            durableAvailable = false
            return emptyList()
        }

        if (kmpRaw != null) {
            val decoded = decodeFavoritesOrNull(kmpRaw)
            if (decoded == null) {
                durableAvailable = false
                return emptyList()
            }
            removeVerifiedLegacyKey(availableStorage)
            return decoded
        }

        val legacyRaw = try {
            availableStorage.getItemSafely(legacyFavoritesKey)
        } catch (_: BrowserStorageException) {
            durableAvailable = false
            return emptyList()
        } ?: return emptyList()
        val migrated = decodeLegacyFavoritesOrNull(legacyRaw) ?: return emptyList()
        if (!writeAndVerify(migrated)) {
            durableAvailable = false
            return migrated
        }
        removeVerifiedLegacyKey(availableStorage)
        return migrated
    }

    private fun persistOrDisable(updated: List<SavedPlace>) {
        if (!durableAvailable) return
        if (!writeAndVerify(updated)) durableAvailable = false
    }

    private fun writeAndVerify(updated: List<SavedPlace>): Boolean {
        val availableStorage = storage ?: return false
        return try {
            availableStorage.setItemSafely(storageKey, encodeFavorites(updated))
            val persisted = availableStorage.getItemSafely(storageKey)?.let(::decodeFavoritesOrNull)
            persisted == updated
        } catch (_: BrowserStorageException) {
            false
        }
    }

    private fun removeVerifiedLegacyKey(storage: Storage) {
        try {
            if (storage.getItemSafely(legacyFavoritesKey) != null) {
                storage.removeItemSafely(legacyFavoritesKey)
            }
        } catch (_: BrowserStorageException) {
            // Existing KMP state remains authoritative; cleanup can be retried later.
        }
    }
}

private fun encodeFavorites(places: List<SavedPlace>): String {
    return buildJsonObject {
        put("schemaVersion", JsonPrimitive(FAVORITES_SCHEMA_VERSION))
        put(
            "places",
            buildJsonArray {
                places.forEach { place ->
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive(place.id))
                            put("name", JsonPrimitive(place.name))
                            put("latitude", JsonPrimitive(place.latitude))
                            put("longitude", JsonPrimitive(place.longitude))
                            put("isFavorite", JsonPrimitive(place.isFavorite))
                        },
                    )
                }
            },
        )
    }.toString()
}

private fun decodeFavoritesOrNull(raw: String): List<SavedPlace>? {
    return try {
        val root = Json.parseToJsonElement(raw).jsonObject
        if (root["schemaVersion"]?.jsonPrimitive?.intOrNull != FAVORITES_SCHEMA_VERSION) return null
        val rawPlaces = root["places"] as? JsonArray ?: return null
        rawPlaces.map { element -> decodeKmpFavorite(element) ?: return null }.distinctBy(SavedPlace::id)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun decodeKmpFavorite(element: kotlinx.serialization.json.JsonElement): SavedPlace? {
    val place = element as? JsonObject ?: return null
    val id = place.string("id")?.takeIf(String::isNotBlank) ?: return null
    val name = place.string("name")?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val latitude = place.number("latitude") ?: return null
    val longitude = place.number("longitude") ?: return null
    if (!coordinatesAreValid(latitude, longitude)) return null
    val isFavorite = place["isFavorite"]?.jsonPrimitive?.booleanOrNull ?: return null
    return SavedPlace(id, name, latitude, longitude, isFavorite = isFavorite).copy(isFavorite = true)
}

private fun decodeLegacyFavoritesOrNull(raw: String): List<SavedPlace>? {
    return try {
        val root = Json.parseToJsonElement(raw).jsonObject
        if (root["schemaVersion"]?.jsonPrimitive?.intOrNull != 1) return null
        val rawPlaces = root["places"] as? JsonArray ?: return null
        rawPlaces.mapNotNull { element ->
            val place = element as? JsonObject ?: return@mapNotNull null
            val latitude = place.number("latitude") ?: return@mapNotNull null
            val longitude = place.number("longitude") ?: return@mapNotNull null
            if (!coordinatesAreValid(latitude, longitude)) return@mapNotNull null
            val base = SavedPlace.fromCoordinates(latitude, longitude)
            val name = place.string("name")?.trim()?.takeIf(String::isNotEmpty) ?: base.name
            base.copy(name = name, isFavorite = true)
        }.distinctBy(SavedPlace::id)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun JsonObject.string(name: String): String? {
    return this[name]
        ?.jsonPrimitive
        ?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull
}

private fun JsonObject.number(name: String): Double? = this[name]?.jsonPrimitive?.doubleOrNull

private fun coordinatesAreValid(latitude: Double, longitude: Double): Boolean {
    return latitude.isFinite() && longitude.isFinite() &&
        latitude in MIN_LATITUDE..MAX_LATITUDE &&
        longitude in MIN_LONGITUDE..MAX_LONGITUDE
}

private const val MIN_LATITUDE = -90.0
private const val MAX_LATITUDE = 90.0
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0
