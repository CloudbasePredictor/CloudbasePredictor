package com.cloudbasepredictor.data.place

import com.cloudbasepredictor.model.SavedPlace
import kotlinx.coroutines.flow.Flow

/** Durable favorite-place storage independent of Room or browser APIs. */
interface FavoritePlaceStore {
    fun observeAll(): Flow<List<SavedPlace>>

    suspend fun readAll(): List<SavedPlace>

    suspend fun findById(placeId: String): SavedPlace?

    suspend fun upsert(place: SavedPlace)

    suspend fun delete(placeId: String)
}
