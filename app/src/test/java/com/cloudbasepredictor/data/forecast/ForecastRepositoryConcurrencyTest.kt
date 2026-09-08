package com.cloudbasepredictor.data.forecast

import android.content.ContextWrapper
import com.cloudbasepredictor.data.datasource.DataSourcePreference
import com.cloudbasepredictor.data.datasource.InMemoryDataSourceRepository
import com.cloudbasepredictor.data.datasource.SimulatedForecastDataSource
import com.cloudbasepredictor.data.local.DatabaseErrorManager
import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.OpenMeteoApi
import com.cloudbasepredictor.data.remote.OpenMeteoDailyResponse
import com.cloudbasepredictor.data.remote.OpenMeteoForecastResponse
import com.cloudbasepredictor.data.remote.OpenMeteoHourlyForecastResponse
import com.cloudbasepredictor.data.remote.OpenMeteoHourlyResponse
import com.cloudbasepredictor.data.remote.OpenMeteoRemoteDataSource
import com.cloudbasepredictor.data.remote.toHourlyForecastData
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.SavedPlace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastRepositoryConcurrencyTest {
    @Test
    fun clearDuringNetworkLoadPreventsMemoryAndDatabaseRepopulation() = runBlocking {
        withTimeout(5_000) {
            val gate = Gate()
            val fixture = Fixture(networkGate = gate)
            val load = launch { fixture.load() }
            gate.entered.await()

            fixture.repository.clearAllCaches()
            gate.release.complete(Unit)
            load.join()

            fixture.assertEmpty()
            fixture.load()
            assertNotNull(fixture.repository.observeForecast(PLACE.id, MODEL).first())
            assertNotNull(fixture.store.read(PLACE.id, MODEL.apiName))
        }
    }

    @Test
    fun clearDuringDatabaseReadPreventsPublishingTheOldRecord() = runBlocking {
        withTimeout(5_000) {
            val fixture = Fixture()
            fixture.store.upsert(record())
            val gate = Gate()
            fixture.store.readGate = gate
            val load = launch { fixture.load(forceRefresh = false) }
            gate.entered.await()

            fixture.repository.clearAllCaches()
            gate.release.complete(Unit)
            load.join()

            fixture.assertEmpty()
        }
    }

    @Test
    fun clearWaitsForAnAlreadyStartedDatabaseWriteThenDeletesIt() = runBlocking {
        withTimeout(5_000) {
            val fixture = Fixture()
            val gate = Gate()
            fixture.store.writeGate = gate
            val load = launch { fixture.load() }
            gate.entered.await()

            val clear = launch(Dispatchers.Unconfined) { fixture.repository.clearAllCaches() }
            gate.release.complete(Unit)
            load.join()
            clear.join()

            fixture.assertEmpty()
        }
    }

    @Test
    fun sourceChangeDuringNetworkLoadDiscardsTheOldSourceResult() = runBlocking {
        withTimeout(5_000) {
            val gate = Gate()
            val fixture = Fixture(networkGate = gate)
            val load = launch { fixture.load() }
            gate.entered.await()

            fixture.sources.setPreference(DataSourcePreference.FAKE)
            gate.release.complete(Unit)
            load.join()

            fixture.assertEmpty()
        }
    }

    @Test
    fun sourceChangeDuringDatabaseMissDoesNotPublishAnotherSourceUnderTheOldRequest() = runBlocking {
        withTimeout(5_000) {
            val fixture = Fixture()
            val gate = Gate()
            fixture.store.readGate = gate
            val load = launch { fixture.load(forceRefresh = false) }
            gate.entered.await()

            fixture.sources.setPreference(DataSourcePreference.FAKE)
            gate.release.complete(Unit)
            load.join()

            fixture.assertEmpty()
        }
    }

    @Test
    fun sourceChangeDuringDatabaseWritePreventsPublishingTheOldSource() = runBlocking {
        withTimeout(5_000) {
            val fixture = Fixture()
            val gate = Gate()
            fixture.store.writeGate = gate
            val load = launch { fixture.load() }
            gate.entered.await()

            fixture.sources.setPreference(DataSourcePreference.FAKE)
            gate.release.complete(Unit)
            load.join()

            assertNull(fixture.repository.observeForecast(PLACE.id, MODEL).first())
        }
    }

    @Test
    fun differentKeysCanFetchConcurrentlyAndBothRemainCached() = runBlocking {
        withTimeout(5_000) {
            val gate = Gate(expectedEntries = 2)
            val fixture = Fixture(networkGate = gate)
            val other = PLACE.copy(id = "other", latitude = 48.0)
            val first = launch { fixture.load() }
            val second = launch { fixture.load(place = other) }
            gate.entered.await()
            gate.release.complete(Unit)
            first.join()
            second.join()

            assertNotNull(fixture.repository.observeForecast(PLACE.id, MODEL).first())
            assertNotNull(fixture.repository.observeForecast(other.id, MODEL).first())
            assertNotNull(fixture.store.read(PLACE.id, MODEL.apiName))
            assertNotNull(fixture.store.read(other.id, MODEL.apiName))
        }
    }

    @Test
    fun newSourceRequestDoesNotReuseThePreviousSourcesMemoryCache() = runBlocking {
        val fixture = Fixture()
        fixture.sources.setPreference(DataSourcePreference.FAKE)
        fixture.load()
        fixture.sources.setPreference(DataSourcePreference.REAL)

        fixture.load(forceRefresh = false)

        val snapshot = fixture.repository.observeForecast(PLACE.id, MODEL).first()
        assertNotNull(snapshot?.hourlyData)
        assertNotNull(fixture.store.read(PLACE.id, MODEL.apiName))
    }

    @Test
    fun cancelledLoadDoesNotPublishOrReportADatabaseError() = runBlocking {
        withTimeout(5_000) {
            val fixture = Fixture()
            val gate = Gate()
            fixture.store.writeGate = gate
            val load = launch { fixture.load() }
            gate.entered.await()
            load.cancelAndJoin()

            assertTrue(load.isCancelled)
            assertFalse(fixture.errors.showError.value)
            fixture.assertEmpty()
            fixture.repository.clearAllCaches()
        }
    }

    @Test
    fun refreshMissingSurfaceDataRetainsPreviousMemoryAndDatabaseCache() = runBlocking {
        val valid = response()
        assertRejectedRefresh(valid.copy(hourly = valid.hourly.copy(dewPoint2m = null)))
    }

    @Test
    fun refreshWithIncompleteEveningHourRetainsPreviousMemoryAndDatabaseCache() = runBlocking {
        val base = response()
        val valid = base.copy(hourly = base.hourly.copy(
            time = listOf("2099-01-01T12:00", "2099-01-01T18:00"),
            temperature2m = listOf(18.0, 16.0),
            dewPoint2m = listOf(9.0, 8.0),
            windSpeed10m = listOf(12.0, 14.0),
            windDirection10m = listOf(180.0, 190.0),
        ))
        val incompleteHours = listOf(
            valid.hourly.copy(temperature2m = listOf(18.0, null)),
            valid.hourly.copy(dewPoint2m = listOf(9.0, null)),
        )

        for (hourly in incompleteHours) {
            assertRejectedRefresh(valid.copy(hourly = hourly), initialResponse = valid)
        }
    }

    @Test
    fun refreshMissingWindDataRetainsPreviousMemoryAndDatabaseCache() = runBlocking {
        val valid = response()
        assertRejectedRefresh(valid.copy(hourly = valid.hourly.copy(windDirection10m = null)))
    }

    @Test
    fun refreshWithEmptyDataRetainsPreviousMemoryAndDatabaseCache() = runBlocking {
        assertRejectedRefresh(response().copy(daily = null, hourly = OpenMeteoHourlyResponse(emptyList())))
    }

    @Test
    fun refreshWithIncompleteLaterDayRetainsPreviousMemoryAndDatabaseCache() = runBlocking {
        assertRejectedRefresh(responseWithUnmatchedSecondDay(), forecastDays = 2)
    }

    @Test
    fun validShorterReturnedHorizonCanBeCached() = runBlocking {
        val fixture = Fixture()

        fixture.load(forecastDays = 7)

        assertEquals(1, fixture.repository.observeForecast(PLACE.id, MODEL).first()?.forecastDays)
        assertEquals(1, fixture.store.read(PLACE.id, MODEL.apiName)?.forecastDays)
    }

    @Test
    fun validationChecksOnlyTheRequestedPartOfALongerResponse() = runBlocking {
        val fixture = Fixture()
        fixture.api.nextResponse = responseWithUnmatchedSecondDay()

        fixture.load(forecastDays = 1)

        assertEquals(1, fixture.repository.observeForecast(PLACE.id, MODEL).first()?.forecastDays)
        assertEquals(1, fixture.store.read(PLACE.id, MODEL.apiName)?.forecastDays)
    }

    @Test
    fun incompleteDatabaseRecordIsReplacedByANetworkForecast() = runBlocking {
        val fixture = Fixture()
        val valid = response()
        val incompleteData = valid.copy(hourly = valid.hourly.copy(dewPoint2m = null)).toHourlyForecastData()
        fixture.store.upsert(
            record().copy(hourlyDataJson = Json.encodeToString(HourlyForecastData.serializer(), incompleteData)),
        )

        fixture.load(forceRefresh = false)

        assertEquals(
            valid.toHourlyForecastData(),
            fixture.repository.observeForecast(PLACE.id, MODEL).first()?.hourlyData,
        )
        val repaired = requireNotNull(fixture.store.read(PLACE.id, MODEL.apiName))
        assertEquals(valid.toHourlyForecastData(), Json.decodeFromString<HourlyForecastData>(repaired.hourlyDataJson))
    }

    @Test
    fun databaseRecordWithOverstatedHorizonIsReplacedByANetworkForecast() = runBlocking {
        val fixture = Fixture()
        fixture.store.upsert(record().copy(forecastDays = 2))
        val twoDays = responseWithUnmatchedSecondDay().let { data ->
            data.copy(
                hourly = data.hourly.copy(
                    time = listOf("2099-01-01T12:00", "2099-01-02T12:00"),
                    temperature2m = listOf(18.0, 19.0),
                    dewPoint2m = listOf(9.0, 10.0),
                    windSpeed10m = listOf(12.0, 14.0),
                    windDirection10m = listOf(180.0, 190.0),
                ),
            )
        }
        fixture.api.nextResponse = twoDays

        fixture.load(forceRefresh = false, forecastDays = 2)

        assertEquals(
            twoDays.toHourlyForecastData(),
            fixture.repository.observeForecast(PLACE.id, MODEL).first()?.hourlyData,
        )
        val repaired = requireNotNull(fixture.store.read(PLACE.id, MODEL.apiName))
        assertEquals(twoDays.toHourlyForecastData(), Json.decodeFromString<HourlyForecastData>(repaired.hourlyDataJson))
    }

    private suspend fun assertRejectedRefresh(
        incompleteResponse: OpenMeteoHourlyForecastResponse,
        forecastDays: Int = 1,
        initialResponse: OpenMeteoHourlyForecastResponse = response(),
    ) {
        val fixture = Fixture()
        fixture.api.nextResponse = initialResponse
        fixture.load()
        val previousSnapshot = fixture.repository.observeForecast(PLACE.id, MODEL).first()
        val previousRecord = fixture.store.read(PLACE.id, MODEL.apiName)
        fixture.api.nextResponse = incompleteResponse

        val error = runCatching { fixture.load(forecastDays = forecastDays) }.exceptionOrNull()

        assertTrue("Incomplete refresh must signal failure", error is IncompleteForecastDataException)
        assertNotNull(previousSnapshot)
        assertNotNull(previousRecord)
        assertEquals(previousSnapshot, fixture.repository.observeForecast(PLACE.id, MODEL).first())
        assertEquals(previousRecord, fixture.store.read(PLACE.id, MODEL.apiName))
    }

    private class Fixture(networkGate: Gate? = null) {
        val store = GatedStore()
        val sources = InMemoryDataSourceRepository()
        private val context = ContextWrapper(null)
        val errors = DatabaseErrorManager(context)
        val api = FakeApi(networkGate)
        val repository = InMemoryForecastRepository(
            openMeteoRemoteDataSource = OpenMeteoRemoteDataSource(api),
            dataSourceRepository = sources,
            simulatedForecastDataSource = SimulatedForecastDataSource(context, Json),
            forecastCacheStore = store,
            databaseErrorManager = errors,
            json = Json,
            ioDispatcher = Dispatchers.Unconfined,
        )

        suspend fun load(place: SavedPlace = PLACE, forceRefresh: Boolean = true, forecastDays: Int = 1) {
            api.expectedForecastDays = forecastDays
            repository.loadForecast(place, forceRefresh, MODEL, forecastDays)
        }

        suspend fun assertEmpty() {
            assertNull(repository.observeForecast(PLACE.id, MODEL).first())
            assertNull(store.read(PLACE.id, MODEL.apiName))
        }
    }

    private class Gate(private val expectedEntries: Int = 1) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        private var entries = 0

        suspend fun await() {
            entries += 1
            if (entries == expectedEntries) entered.complete(Unit)
            release.await()
        }
    }

    private class GatedStore : ForecastCacheStore {
        private val delegate = InMemoryForecastCacheStore()
        var readGate: Gate? = null
        var writeGate: Gate? = null

        override suspend fun read(placeId: String, requestedModelApiName: String): ForecastCacheRecord? {
            val record = delegate.read(placeId, requestedModelApiName)
            readGate?.await()
            return record
        }

        override suspend fun upsert(record: ForecastCacheRecord) {
            writeGate?.await()
            delegate.upsert(record)
        }

        override suspend fun deleteOlderThan(cutoffMillis: Long) = delegate.deleteOlderThan(cutoffMillis)

        override suspend fun deleteAll() = delegate.deleteAll()
    }

    private class FakeApi(private val gate: Gate?) : OpenMeteoApi {
        var nextResponse = response()
        var expectedForecastDays = 1

        override suspend fun getForecast(
            latitude: Double,
            longitude: Double,
            daily: String,
            forecastDays: Int,
            timezone: String,
        ): OpenMeteoForecastResponse = error("Hourly forecast expected")

        override suspend fun getHourlyForecast(
            latitude: Double,
            longitude: Double,
            hourly: String,
            daily: String,
            forecastDays: Int,
            timezone: String,
            models: String?,
        ): OpenMeteoHourlyForecastResponse {
            assertEquals(expectedForecastDays, forecastDays)
            assertEquals(MODEL.apiName, models)
            gate?.await()
            return nextResponse.copy(latitude = latitude, longitude = longitude)
        }
    }

    companion object {
        private val MODEL = ForecastModel.ICON_SEAMLESS
        private val PLACE = SavedPlace("brauneck", "Brauneck", 47.663, 11.523)

        private fun response() = OpenMeteoHourlyForecastResponse(
            latitude = PLACE.latitude,
            longitude = PLACE.longitude,
            timezone = "UTC",
            daily = OpenMeteoDailyResponse(
                time = listOf("2099-01-01"),
                temperatureMaxCelsius = listOf(18.0),
                temperatureMinCelsius = listOf(9.0),
                weatherCodes = listOf(1),
            ),
            hourly = OpenMeteoHourlyResponse(
                time = listOf("2099-01-01T12:00"),
                temperature2m = listOf(18.0),
                dewPoint2m = listOf(9.0),
                surfacePressure = listOf(950.0),
                windSpeed10m = listOf(12.0),
                windDirection10m = listOf(180.0),
                temperature850hPa = listOf(8.0),
                dewPoint850hPa = listOf(3.0),
                geopotentialHeight850hPa = listOf(1_500.0),
            ),
        )

        private fun responseWithUnmatchedSecondDay(): OpenMeteoHourlyForecastResponse {
            val valid = response()
            return valid.copy(
                daily = OpenMeteoDailyResponse(
                    time = listOf("2099-01-01", "2099-01-02"),
                    temperatureMaxCelsius = listOf(18.0, 19.0),
                    temperatureMinCelsius = listOf(9.0, 10.0),
                    weatherCodes = listOf(1, 1),
                ),
            )
        }

        private fun record() = ForecastCacheRecord(
            placeId = PLACE.id,
            requestedModelApiName = MODEL.apiName,
            resolvedModelApiName = MODEL.apiName,
            forecastDays = 1,
            hourlyDataJson = Json.encodeToString(HourlyForecastData.serializer(), response().toHourlyForecastData()),
            fetchedAtMillis = 4_070_952_000_000,
            nextExpectedUpdateMillis = Long.MAX_VALUE,
        )
    }
}
