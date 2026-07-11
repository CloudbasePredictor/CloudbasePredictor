package com.cloudbasepredictor.data.place

import com.cloudbasepredictor.data.local.SavedPlaceDao
import com.cloudbasepredictor.data.local.SavedPlaceEntity
import com.cloudbasepredictor.di.ApplicationScope
import com.cloudbasepredictor.di.IoDispatcher
import com.cloudbasepredictor.model.SavedPlace
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface PlaceRepository {
    val selectedPlace: StateFlow<SavedPlace?>

    fun observeSavedPlaces(): Flow<List<SavedPlace>>

    fun observeFavoritePlaces(): Flow<List<SavedPlace>>

    suspend fun saveAndSelectPlace(place: SavedPlace)

    suspend fun saveFavorite(placeId: String, name: String)

    suspend fun saveFavoritePlace(place: SavedPlace)

    suspend fun deleteFavorite(placeId: String)

    suspend fun selectPlace(place: SavedPlace)
}

@SingleIn(AppScope::class)
class DefaultPlaceRepository @Inject constructor(
    private val savedPlaceDao: SavedPlaceDao,
    private val favoritePlacesBackupStore: FavoritePlacesBackupStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope applicationScope: CoroutineScope,
) : PlaceRepository {
    private val mutableSelectedPlace = MutableStateFlow<SavedPlace?>(null)
    private val restoreJob: Job = applicationScope.launch {
        restoreFavoritePlacesFromBackup()
    }

    override val selectedPlace: StateFlow<SavedPlace?> = mutableSelectedPlace.asStateFlow()

    override fun observeSavedPlaces(): Flow<List<SavedPlace>> {
        return savedPlaceDao.observeSavedPlaces().map { entities ->
            entities.map(SavedPlaceEntity::toDomainModel)
        }
    }

    override fun observeFavoritePlaces(): Flow<List<SavedPlace>> {
        return savedPlaceDao.observeFavoritePlaces().map { entities ->
            entities.map(SavedPlaceEntity::toDomainModel)
        }
    }

    override suspend fun saveAndSelectPlace(place: SavedPlace) = withContext(ioDispatcher) {
        val existing = savedPlaceDao.findById(place.id)
        if (existing != null) {
            mutableSelectedPlace.value = existing.toDomainModel()
        } else {
            savedPlaceDao.upsert(
                SavedPlaceEntity(
                    id = place.id,
                    name = place.name,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    isFavorite = false,
                )
            )
            mutableSelectedPlace.value = place
        }
    }

    override suspend fun saveFavorite(placeId: String, name: String) = withContext(ioDispatcher) {
        restoreJob.join()
        val existing = savedPlaceDao.findById(placeId) ?: return@withContext
        val updated = existing.copy(name = name, isFavorite = true)
        savedPlaceDao.upsert(updated)
        syncFavoritePlacesToBackup()
        if (mutableSelectedPlace.value?.id == placeId) {
            mutableSelectedPlace.value = updated.toDomainModel()
        }
    }

    override suspend fun saveFavoritePlace(place: SavedPlace) = withContext(ioDispatcher) {
        restoreJob.join()
        val updated = place.toFavoriteEntity()
        savedPlaceDao.upsert(updated)
        syncFavoritePlacesToBackup()
        if (mutableSelectedPlace.value?.id == place.id) {
            mutableSelectedPlace.value = updated.toDomainModel()
        }
    }

    override suspend fun deleteFavorite(placeId: String) = withContext(ioDispatcher) {
        restoreJob.join()
        val existing = savedPlaceDao.findById(placeId) ?: return@withContext
        val lat = String.format(Locale.US, "%.4f", existing.latitude)
        val lon = String.format(Locale.US, "%.4f", existing.longitude)
        val updated = existing.copy(name = "$lat, $lon", isFavorite = false)
        savedPlaceDao.upsert(updated)
        syncFavoritePlacesToBackup()
        if (mutableSelectedPlace.value?.id == placeId) {
            mutableSelectedPlace.value = updated.toDomainModel()
        }
    }

    override suspend fun selectPlace(place: SavedPlace) {
        mutableSelectedPlace.value = place
    }

    private suspend fun restoreFavoritePlacesFromBackup() = withContext(ioDispatcher) {
        favoritePlacesBackupStore.readFavoritePlaces().forEach { place ->
            savedPlaceDao.upsert(place.toFavoriteEntity())
        }
    }

    private suspend fun syncFavoritePlacesToBackup() {
        val favorites = savedPlaceDao.getFavoritePlaces().map(SavedPlaceEntity::toDomainModel)
        favoritePlacesBackupStore.saveFavoritePlaces(favorites)
    }
}

private fun SavedPlaceEntity.toDomainModel(): SavedPlace {
    return SavedPlace(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        isFavorite = isFavorite,
    )
}

private fun SavedPlace.toFavoriteEntity(): SavedPlaceEntity {
    return SavedPlaceEntity(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        isFavorite = true,
    )
}
