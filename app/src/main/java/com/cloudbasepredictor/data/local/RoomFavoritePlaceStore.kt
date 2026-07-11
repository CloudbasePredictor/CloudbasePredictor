package com.cloudbasepredictor.data.local

import com.cloudbasepredictor.data.place.FavoritePlaceStore
import com.cloudbasepredictor.model.SavedPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFavoritePlaceStore(
    private val dao: SavedPlaceDao,
) : FavoritePlaceStore {
    override fun observeAll(): Flow<List<SavedPlace>> {
        return dao.observeFavoritePlaces().map { entries -> entries.map(SavedPlaceEntity::toFavorite) }
    }

    override suspend fun readAll(): List<SavedPlace> {
        return dao.getFavoritePlaces().map(SavedPlaceEntity::toFavorite)
    }

    override suspend fun findById(placeId: String): SavedPlace? {
        return dao.findById(placeId)?.takeIf(SavedPlaceEntity::isFavorite)?.toFavorite()
    }

    override suspend fun upsert(place: SavedPlace) {
        dao.upsert(place.toFavoriteEntity())
    }

    override suspend fun delete(placeId: String) {
        dao.deleteById(placeId)
    }
}

private fun SavedPlaceEntity.toFavorite(): SavedPlace {
    return SavedPlace(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        isFavorite = true,
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
