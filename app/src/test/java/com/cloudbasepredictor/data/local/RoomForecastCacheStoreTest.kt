package com.cloudbasepredictor.data.local

import com.cloudbasepredictor.data.forecast.ForecastCacheRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomForecastCacheStoreTest {
    @Test
    fun read_mapsEveryRoomColumnAndUsesRequestedModelKey() = runBlocking {
        val dao = FakeForecastCacheDao().apply {
            entities += entity(
                placeId = "brauneck",
                modelApiName = "icon_seamless",
                fetchedAtMillis = 1_725_000_000_000,
            )
        }

        val record = RoomForecastCacheStore(dao).read("brauneck", "icon_seamless")

        assertEquals("brauneck" to "icon_seamless", dao.lastReadKey)
        assertEquals(
            ForecastCacheRecord(
                placeId = "brauneck",
                requestedModelApiName = "icon_seamless",
                resolvedModelApiName = "icon_global",
                forecastDays = 7,
                hourlyDataJson = "{\"temperature_2m\":[12.5]}",
                fetchedAtMillis = 1_725_000_000_000,
                nextExpectedUpdateMillis = 1_725_003_600_000,
            ),
            record,
        )
    }

    @Test
    fun read_returnsNullWhenDaoHasNoMatchingRecord() = runBlocking {
        assertNull(RoomForecastCacheStore(FakeForecastCacheDao()).read("missing", "icon_d2"))
    }

    @Test
    fun upsert_mapsRequestedModelToTheRoomPrimaryKey() = runBlocking {
        val dao = FakeForecastCacheDao()
        val record = record(
            placeId = "stuve",
            requestedModelApiName = "best_match",
            fetchedAtMillis = 500,
        )

        RoomForecastCacheStore(dao).upsert(record)

        assertEquals(
            CachedForecastEntity(
                placeId = "stuve",
                modelApiName = "best_match",
                resolvedModelApiName = "icon_seamless",
                forecastDays = 7,
                hourlyDataJson = "{\"temperature_2m\":[12.5]}",
                fetchedAtMillis = 500,
                nextExpectedUpdateMillis = 3_600_500,
            ),
            dao.upserted,
        )
    }

    @Test
    fun deleteOlderThan_keepsRecordsExactlyAtTheCutoff() = runBlocking {
        val dao = FakeForecastCacheDao().apply {
            entities += entity("old", "icon_d2", fetchedAtMillis = 99)
            entities += entity("boundary", "icon_d2", fetchedAtMillis = 100)
            entities += entity("new", "icon_d2", fetchedAtMillis = 101)
        }

        val deleted = RoomForecastCacheStore(dao).deleteOlderThan(100)

        assertEquals(100L, dao.lastCleanupCutoff)
        assertEquals(1, deleted)
        assertEquals(listOf("boundary", "new"), dao.entities.map(CachedForecastEntity::placeId))
    }

    @Test
    fun deleteAll_delegatesToDao() = runBlocking {
        val dao = FakeForecastCacheDao().apply {
            entities += entity("brauneck", "icon_seamless", fetchedAtMillis = 100)
        }

        RoomForecastCacheStore(dao).deleteAll()

        assertEquals(1, dao.deleteAllCalls)
        assertEquals(emptyList<CachedForecastEntity>(), dao.entities)
    }

    private fun record(
        placeId: String,
        requestedModelApiName: String,
        fetchedAtMillis: Long,
    ): ForecastCacheRecord = ForecastCacheRecord(
        placeId = placeId,
        requestedModelApiName = requestedModelApiName,
        resolvedModelApiName = "icon_seamless",
        forecastDays = 7,
        hourlyDataJson = "{\"temperature_2m\":[12.5]}",
        fetchedAtMillis = fetchedAtMillis,
        nextExpectedUpdateMillis = fetchedAtMillis + 3_600_000,
    )
}

private class FakeForecastCacheDao : ForecastCacheDao {
    val entities = mutableListOf<CachedForecastEntity>()
    var lastReadKey: Pair<String, String>? = null
    var upserted: CachedForecastEntity? = null
    var lastCleanupCutoff: Long? = null
    var deleteAllCalls: Int = 0

    override suspend fun getCachedForecast(
        placeId: String,
        modelApiName: String,
    ): CachedForecastEntity? {
        lastReadKey = placeId to modelApiName
        return entities.find { entity ->
            entity.placeId == placeId && entity.modelApiName == modelApiName
        }
    }

    override suspend fun upsertForecast(entity: CachedForecastEntity) {
        upserted = entity
    }

    override suspend fun deleteOlderThan(cutoffMillis: Long): Int {
        lastCleanupCutoff = cutoffMillis
        val oldRecords = entities.filter { entity -> entity.fetchedAtMillis < cutoffMillis }
        entities.removeAll(oldRecords)
        return oldRecords.size
    }

    override suspend fun deleteAll() {
        deleteAllCalls += 1
        entities.clear()
    }
}

private fun entity(
    placeId: String,
    modelApiName: String,
    fetchedAtMillis: Long,
): CachedForecastEntity = CachedForecastEntity(
    placeId = placeId,
    modelApiName = modelApiName,
    resolvedModelApiName = "icon_global",
    forecastDays = 7,
    hourlyDataJson = "{\"temperature_2m\":[12.5]}",
    fetchedAtMillis = fetchedAtMillis,
    nextExpectedUpdateMillis = fetchedAtMillis + 3_600_000,
)
