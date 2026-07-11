package com.cloudbasepredictor.data.local

import com.cloudbasepredictor.model.SavedPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomFavoritePlaceStoreTest {
    @Test
    fun observeAll_mapsDaoRowsToFavoritePlaces() = runBlocking {
        val dao = FakeSavedPlaceDao().apply {
            observedFavorites.value = listOf(
                entity(id = "brauneck", isFavorite = false),
                entity(id = "stuve", isFavorite = true),
            )
        }

        val favorites = RoomFavoritePlaceStore(dao).observeAll().first()

        assertEquals(
            SavedPlace(
                id = "brauneck",
                name = "Brauneck",
                latitude = 47.0,
                longitude = 11.0,
                isFavorite = true,
            ),
            favorites.first(),
        )
        assertEquals(listOf("brauneck", "stuve"), favorites.map(SavedPlace::id))
        assertTrue(favorites.all(SavedPlace::isFavorite))
    }

    @Test
    fun readAllAndFindById_mapOnlyFavoriteRows() = runBlocking {
        val favorite = entity(id = "brauneck", isFavorite = true)
        val notFavorite = entity(id = "coordinate", isFavorite = false)
        val dao = FakeSavedPlaceDao().apply {
            favoriteRows = listOf(favorite)
            rowsById[favorite.id] = favorite
            rowsById[notFavorite.id] = notFavorite
        }
        val store = RoomFavoritePlaceStore(dao)

        assertEquals(listOf("brauneck"), store.readAll().map(SavedPlace::id))
        assertEquals("brauneck", store.findById("brauneck")?.id)
        assertNull(store.findById("coordinate"))
        assertNull(store.findById("missing"))
        assertEquals(listOf("brauneck", "coordinate", "missing"), dao.findRequests)
    }

    @Test
    fun upsert_forcesFavoriteAndDelegatesEveryField() = runBlocking {
        val dao = FakeSavedPlaceDao()
        val place = SavedPlace(
            id = "innsbruck",
            name = "Innsbruck",
            latitude = 47.2692,
            longitude = 11.4041,
            isFavorite = false,
        )

        RoomFavoritePlaceStore(dao).upsert(place)

        assertEquals(
            SavedPlaceEntity(
                id = "innsbruck",
                name = "Innsbruck",
                latitude = 47.2692,
                longitude = 11.4041,
                isFavorite = true,
            ),
            dao.upserted,
        )
    }

    @Test
    fun delete_delegatesTheExactPlaceId() = runBlocking {
        val dao = FakeSavedPlaceDao()

        RoomFavoritePlaceStore(dao).delete("place:47.0000:11.0000")

        assertEquals("place:47.0000:11.0000", dao.deletedId)
    }

    private fun entity(
        id: String,
        isFavorite: Boolean,
    ): SavedPlaceEntity = SavedPlaceEntity(
        id = id,
        name = id.replaceFirstChar(Char::uppercase),
        latitude = 47.0,
        longitude = 11.0,
        isFavorite = isFavorite,
    )
}

private class FakeSavedPlaceDao : SavedPlaceDao {
    val observedFavorites = MutableStateFlow<List<SavedPlaceEntity>>(emptyList())
    val rowsById = mutableMapOf<String, SavedPlaceEntity>()
    val findRequests = mutableListOf<String>()
    var favoriteRows: List<SavedPlaceEntity> = emptyList()
    var upserted: SavedPlaceEntity? = null
    var deletedId: String? = null

    override fun observeSavedPlaces(): Flow<List<SavedPlaceEntity>> = MutableStateFlow(emptyList())

    override fun observeFavoritePlaces(): Flow<List<SavedPlaceEntity>> = observedFavorites

    override suspend fun getFavoritePlaces(): List<SavedPlaceEntity> = favoriteRows

    override suspend fun findById(id: String): SavedPlaceEntity? {
        findRequests += id
        return rowsById[id]
    }

    override suspend fun upsert(place: SavedPlaceEntity) {
        upserted = place
    }

    override suspend fun deleteById(id: String) {
        deletedId = id
    }
}
