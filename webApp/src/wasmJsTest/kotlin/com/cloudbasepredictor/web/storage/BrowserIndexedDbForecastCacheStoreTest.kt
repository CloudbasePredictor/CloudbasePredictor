@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
// Detekt cannot see parameters referenced from Kotlin/Wasm js(...) test helpers.
@file:Suppress("LongParameterList", "MagicNumber", "TooManyFunctions", "UnusedParameter")

package com.cloudbasepredictor.web.storage

import com.cloudbasepredictor.data.forecast.ForecastCacheRecord
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.js
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserIndexedDbForecastCacheStoreTest {
    @Test
    fun recordsRoundTripAndCleanupIsStrictlyOlderThan() = runTest {
        val databaseName = uniqueDatabaseName("round-trip")
        try {
            val store = BrowserIndexedDbForecastCacheStore(databaseName)
            val old = testRecord("old", fetchedAtMillis = 99)
            val boundary = testRecord("boundary", fetchedAtMillis = 100)
            val recent = testRecord("recent", fetchedAtMillis = 101)
            store.upsert(old)
            store.upsert(boundary)
            store.upsert(recent)

            assertEquals(boundary, store.read(boundary.placeId, boundary.requestedModelApiName))
            assertEquals(1, store.deleteOlderThan(100))
            assertNull(store.read(old.placeId, old.requestedModelApiName))
            assertEquals(boundary, store.read(boundary.placeId, boundary.requestedModelApiName))
            assertEquals(recent, store.read(recent.placeId, recent.requestedModelApiName))
        } finally {
            deleteTestDatabase(databaseName)
        }
    }

    @Test
    fun deleteAllRemovesEveryModelAndPlace() = runTest {
        val databaseName = uniqueDatabaseName("delete-all")
        try {
            val store = BrowserIndexedDbForecastCacheStore(databaseName)
            val icon = testRecord("same-place", model = "icon")
            val gfs = testRecord("same-place", model = "gfs")
            store.upsert(icon)
            store.upsert(gfs)

            store.deleteAll()

            assertNull(store.read(icon.placeId, icon.requestedModelApiName))
            assertNull(store.read(gfs.placeId, gfs.requestedModelApiName))
        } finally {
            deleteTestDatabase(databaseName)
        }
    }

    @Test
    fun openingVersionOneDatabaseMigratesTheFetchedAtIndexToVersionTwo() = runTest {
        val databaseName = uniqueDatabaseName("migration")
        val record = testRecord("legacy", fetchedAtMillis = 42)
        val key = testCacheKey(record.placeId, record.requestedModelApiName)
        try {
            createVersionOneDatabase(databaseName, key, testIndexedDbEntry(record))

            val store = BrowserIndexedDbForecastCacheStore(databaseName)

            assertEquals(record, store.read(record.placeId, record.requestedModelApiName))
            assertTrue(databaseHasVersionTwoFetchedAtIndex(databaseName))
            assertEquals(1, store.deleteOlderThan(43))
        } finally {
            deleteTestDatabase(databaseName)
        }
    }

    @Test
    fun malformedRecordsAreCacheMissesAndAreRemoved() = runTest {
        val databaseName = uniqueDatabaseName("malformed")
        val placeId = "malformed-place"
        val model = "icon"
        val key = testCacheKey(placeId, model)
        val malformed = buildJsonObject {
            put("recordSchemaVersion", JsonPrimitive(1))
            put("fetchedAtMillis", JsonPrimitive(100))
            put("payloadJson", JsonPrimitive("not-json"))
        }.toString()
        try {
            putRawVersionTwoValue(databaseName, key, malformed)
            val store = BrowserIndexedDbForecastCacheStore(databaseName)

            assertNull(store.read(placeId, model))
            assertNull(readRawValue(databaseName, key))
        } finally {
            deleteTestDatabase(databaseName)
        }
    }

    @Test
    fun futureRecordSchemaIsAMissButIsPreserved() = runTest {
        val databaseName = uniqueDatabaseName("future-record")
        val placeId = "future-record-place"
        val model = "icon"
        val key = testCacheKey(placeId, model)
        val futureRecord = buildJsonObject {
            put("recordSchemaVersion", JsonPrimitive(2))
            put("fetchedAtMillis", JsonPrimitive(100))
            put("payloadJson", JsonPrimitive("future-payload"))
        }.toString()
        try {
            putRawVersionTwoValue(databaseName, key, futureRecord)
            val store = BrowserIndexedDbForecastCacheStore(databaseName)

            assertNull(store.read(placeId, model))
            assertEquals(futureRecord, readRawValue(databaseName, key))
        } finally {
            deleteTestDatabase(databaseName)
        }
    }

    @Test
    fun futureDatabaseVersionFallsBackWithoutDeletingTheDatabase() = runTest {
        val databaseName = uniqueDatabaseName("future")
        val record = testRecord("session-only")
        try {
            createFutureVersionDatabase(databaseName)
            val store = BrowserIndexedDbForecastCacheStore(databaseName)

            store.upsert(record)

            assertEquals(record, store.read(record.placeId, record.requestedModelApiName))
            assertFalse(databaseHasVersionTwoFetchedAtIndex(databaseName))
        } finally {
            deleteTestDatabase(databaseName)
        }
    }
}

private fun testRecord(
    placeId: String,
    model: String = "icon",
    fetchedAtMillis: Long = 1_000,
): ForecastCacheRecord {
    return ForecastCacheRecord(
        placeId = placeId,
        requestedModelApiName = model,
        resolvedModelApiName = "icon_seamless",
        forecastDays = 14,
        hourlyDataJson = "{\"hourly\":true}",
        fetchedAtMillis = fetchedAtMillis,
        nextExpectedUpdateMillis = fetchedAtMillis + 3_600_000,
    )
}

private fun testIndexedDbEntry(record: ForecastCacheRecord): String {
    val payload = buildJsonObject {
        put("placeId", JsonPrimitive(record.placeId))
        put("requestedModelApiName", JsonPrimitive(record.requestedModelApiName))
        put("resolvedModelApiName", JsonPrimitive(record.resolvedModelApiName))
        put("forecastDays", JsonPrimitive(record.forecastDays))
        put("hourlyDataJson", JsonPrimitive(record.hourlyDataJson))
        put("fetchedAtMillis", JsonPrimitive(record.fetchedAtMillis))
        put("nextExpectedUpdateMillis", JsonPrimitive(record.nextExpectedUpdateMillis))
    }.toString()
    return buildJsonObject {
        put("recordSchemaVersion", JsonPrimitive(1))
        put("fetchedAtMillis", JsonPrimitive(record.fetchedAtMillis))
        put("payloadJson", JsonPrimitive(payload))
    }.toString()
}

private fun testCacheKey(placeId: String, model: String): String = "${placeId.length}:$placeId$model"

private var databaseCounter = 0

private fun uniqueDatabaseName(label: String): String {
    databaseCounter += 1
    return "cbp-kmp-test-$label-$databaseCounter"
}

private suspend fun createVersionOneDatabase(databaseName: String, key: String, valueJson: String) {
    awaitTestUnitOperation { success, failure ->
        createVersionOneDatabaseInterop(databaseName, key, valueJson, success, failure)
    }
}

private suspend fun putRawVersionTwoValue(databaseName: String, key: String, valueJson: String) {
    awaitTestUnitOperation { success, failure ->
        putRawVersionTwoValueInterop(databaseName, key, valueJson, success, failure)
    }
}

private suspend fun createFutureVersionDatabase(databaseName: String) {
    awaitTestUnitOperation { success, failure ->
        createFutureVersionDatabaseInterop(databaseName, success, failure)
    }
}

private suspend fun databaseHasVersionTwoFetchedAtIndex(databaseName: String): Boolean {
    return suspendCoroutine { continuation ->
        inspectDatabaseInterop(
            databaseName,
            { hasExpectedIndex -> continuation.resume(hasExpectedIndex) },
            { errorName -> continuation.resumeWithException(IllegalStateException(errorName)) },
        )
    }
}

private suspend fun readRawValue(databaseName: String, key: String): String? {
    return suspendCoroutine { continuation ->
        readRawValueInterop(
            databaseName,
            key,
            { raw -> continuation.resume(raw) },
            { errorName -> continuation.resumeWithException(IllegalStateException(errorName)) },
        )
    }
}

private suspend fun deleteTestDatabase(databaseName: String) {
    awaitTestUnitOperation { success, failure ->
        deleteTestDatabaseInterop(databaseName, success, failure)
    }
}

private suspend fun awaitTestUnitOperation(
    start: (onSuccess: () -> Unit, onFailure: (String) -> Unit) -> Unit,
) {
    suspendCoroutine { continuation ->
        start(
            { continuation.resume(Unit) },
            { errorName -> continuation.resumeWithException(IllegalStateException(errorName)) },
        )
    }
}

private fun createVersionOneDatabaseInterop(
    databaseName: String,
    key: String,
    valueJson: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """{
        const request = indexedDB.open(databaseName, 1);
        request.onupgradeneeded = () => request.result.createObjectStore("forecast_cache");
        request.onerror = () => onFailure(request.error?.name ?? "UnknownError");
        request.onsuccess = () => {
            const database = request.result;
            const transaction = database.transaction("forecast_cache", "readwrite");
            transaction.objectStore("forecast_cache").put(JSON.parse(valueJson), key);
            transaction.oncomplete = () => {
                database.close();
                onSuccess();
            };
            transaction.onabort = () => {
                database.close();
                onFailure(transaction.error?.name ?? "AbortError");
            };
        };
    }""",
)

private fun putRawVersionTwoValueInterop(
    databaseName: String,
    key: String,
    valueJson: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """{
        const request = indexedDB.open(databaseName, 2);
        request.onupgradeneeded = () => {
            const database = request.result;
            const store = database.objectStoreNames.contains("forecast_cache")
                ? request.transaction.objectStore("forecast_cache")
                : database.createObjectStore("forecast_cache");
            if (!store.indexNames.contains("by_fetched_at")) {
                store.createIndex("by_fetched_at", "fetchedAtMillis");
            }
        };
        request.onerror = () => onFailure(request.error?.name ?? "UnknownError");
        request.onsuccess = () => {
            const database = request.result;
            const transaction = database.transaction("forecast_cache", "readwrite");
            transaction.objectStore("forecast_cache").put(JSON.parse(valueJson), key);
            transaction.oncomplete = () => {
                database.close();
                onSuccess();
            };
            transaction.onabort = () => {
                database.close();
                onFailure(transaction.error?.name ?? "AbortError");
            };
        };
    }""",
)

private fun createFutureVersionDatabaseInterop(
    databaseName: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """{
        const request = indexedDB.open(databaseName, 3);
        request.onupgradeneeded = () => request.result.createObjectStore("future_store");
        request.onerror = () => onFailure(request.error?.name ?? "UnknownError");
        request.onsuccess = () => {
            request.result.close();
            onSuccess();
        };
    }""",
)

private fun inspectDatabaseInterop(
    databaseName: String,
    onSuccess: (Boolean) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """{
        const request = indexedDB.open(databaseName);
        request.onerror = () => onFailure(request.error?.name ?? "UnknownError");
        request.onsuccess = () => {
            const database = request.result;
            let hasExpectedIndex = false;
            if (database.version === 2 && database.objectStoreNames.contains("forecast_cache")) {
                const transaction = database.transaction("forecast_cache", "readonly");
                hasExpectedIndex = transaction.objectStore("forecast_cache").indexNames.contains("by_fetched_at");
            }
            database.close();
            onSuccess(hasExpectedIndex);
        };
    }""",
)

private fun readRawValueInterop(
    databaseName: String,
    key: String,
    onSuccess: (String?) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """{
        const request = indexedDB.open(databaseName);
        request.onerror = () => onFailure(request.error?.name ?? "UnknownError");
        request.onsuccess = () => {
            const database = request.result;
            const transaction = database.transaction("forecast_cache", "readonly");
            const read = transaction.objectStore("forecast_cache").get(key);
            read.onsuccess = () => {
                const value = read.result === undefined ? null : JSON.stringify(read.result);
                database.close();
                onSuccess(value);
            };
            read.onerror = () => {
                database.close();
                onFailure(read.error?.name ?? "UnknownError");
            };
        };
    }""",
)

private fun deleteTestDatabaseInterop(
    databaseName: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """{
        const request = indexedDB.deleteDatabase(databaseName);
        request.onsuccess = () => onSuccess();
        request.onerror = () => onFailure(request.error?.name ?? "UnknownError");
        request.onblocked = () => onFailure("BlockedError");
    }""",
)
