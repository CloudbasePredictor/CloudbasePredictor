@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@file:Suppress("ReturnCount", "TooGenericExceptionCaught", "TooManyFunctions")

package com.cloudbasepredictor.web.storage

import com.cloudbasepredictor.data.forecast.ForecastCacheRecord
import com.cloudbasepredictor.data.forecast.ForecastCacheStore
import com.cloudbasepredictor.data.forecast.ResilientForecastCacheStore
import com.cloudbasepredictor.data.storage.StorageUnavailableException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsAny

const val DEFAULT_FORECAST_CACHE_DATABASE_NAME = "cbp-kmp"

private const val FORECAST_CACHE_DATABASE_VERSION = 2
private const val FORECAST_CACHE_STORE_NAME = "forecast_cache"
private const val FETCHED_AT_INDEX_NAME = "by_fetched_at"
private const val RECORD_SCHEMA_VERSION = 1
private const val QUOTA_EXCEEDED_ERROR = "QuotaExceededError"

/** Durable browser forecast cache with a session-memory fallback. */
class BrowserIndexedDbForecastCacheStore(
    databaseName: String = DEFAULT_FORECAST_CACHE_DATABASE_NAME,
) : ForecastCacheStore {
    private val delegate = ResilientForecastCacheStore(
        durable = DurableBrowserIndexedDbForecastCacheStore(databaseName),
    )

    override suspend fun read(
        placeId: String,
        requestedModelApiName: String,
    ): ForecastCacheRecord? = delegate.read(placeId, requestedModelApiName)

    override suspend fun upsert(record: ForecastCacheRecord) = delegate.upsert(record)

    override suspend fun deleteOlderThan(cutoffMillis: Long): Int {
        return delegate.deleteOlderThan(cutoffMillis)
    }

    override suspend fun deleteAll() = delegate.deleteAll()
}

private class DurableBrowserIndexedDbForecastCacheStore(
    private val databaseName: String,
) : ForecastCacheStore {
    override suspend fun read(
        placeId: String,
        requestedModelApiName: String,
    ): ForecastCacheRecord? {
        val key = forecastCacheKey(placeId, requestedModelApiName)
        val rawEntry = storageOperation {
            withDatabase { database -> awaitRead(database, key) }
        } ?: return null
        return when (val decoded = decodeIndexedDbEntry(rawEntry)) {
            IndexedDbEntryDecodeResult.FutureSchema -> null
            IndexedDbEntryDecodeResult.Malformed -> {
                deleteMalformedEntryBestEffort(key)
                null
            }
            is IndexedDbEntryDecodeResult.Current -> {
                val record = decoded.record
                if (record.placeId != placeId || record.requestedModelApiName != requestedModelApiName) {
                    deleteMalformedEntryBestEffort(key)
                    null
                } else {
                    record
                }
            }
        }
    }

    override suspend fun upsert(record: ForecastCacheRecord) {
        val key = forecastCacheKey(record.placeId, record.requestedModelApiName)
        val encoded = encodeIndexedDbEntry(record)
        try {
            withDatabase { database -> awaitWrite(database, key, encoded) }
        } catch (failure: IndexedDbFailure) {
            if (failure.errorName != QUOTA_EXCEEDED_ERROR) throw failure.asUnavailable()
            try {
                withDatabase { database ->
                    awaitDeleteOlderThan(database, record.fetchedAtMillis)
                }
                withDatabase { database -> awaitWrite(database, key, encoded) }
            } catch (retryFailure: IndexedDbFailure) {
                throw retryFailure.asUnavailable()
            }
        }
    }

    override suspend fun deleteOlderThan(cutoffMillis: Long): Int = storageOperation {
        withDatabase { database -> awaitDeleteOlderThan(database, cutoffMillis) }
    }

    override suspend fun deleteAll() {
        storageOperation {
            withDatabase(::awaitClear)
        }
    }

    private suspend fun deleteMalformedEntryBestEffort(key: String) {
        try {
            withDatabase { database -> awaitDelete(database, key) }
        } catch (_: IndexedDbFailure) {
            // A malformed cache entry is still treated as a miss if cleanup is unavailable.
        }
    }

    private suspend fun <T> withDatabase(block: suspend (JsAny) -> T): T {
        val database = awaitOpenDatabase(databaseName)
        return try {
            block(database)
        } finally {
            closeIndexedDb(database)
        }
    }
}

private suspend fun awaitOpenDatabase(databaseName: String): JsAny = suspendCoroutine { continuation ->
    var completed = false
    try {
        openIndexedDb(
            databaseName = databaseName,
            version = FORECAST_CACHE_DATABASE_VERSION,
            storeName = FORECAST_CACHE_STORE_NAME,
            indexName = FETCHED_AT_INDEX_NAME,
            onSuccess = { database ->
                if (!completed) {
                    completed = true
                    continuation.resume(database)
                }
            },
            onFailure = { errorName ->
                if (!completed) {
                    completed = true
                    continuation.resumeWithException(IndexedDbFailure(errorName))
                }
            },
        )
    } catch (cause: Throwable) {
        if (!completed) {
            completed = true
            continuation.resumeWithException(IndexedDbFailure("UnavailableError", cause))
        }
    }
}

private suspend fun awaitRead(database: JsAny, key: String): String? = suspendCoroutine { continuation ->
    var completed = false
    readIndexedDbValue(
        database = database,
        storeName = FORECAST_CACHE_STORE_NAME,
        key = key,
        onSuccess = { value ->
            if (!completed) {
                completed = true
                continuation.resume(value)
            }
        },
        onFailure = { errorName ->
            if (!completed) {
                completed = true
                continuation.resumeWithException(IndexedDbFailure(errorName))
            }
        },
    )
}

private suspend fun awaitWrite(database: JsAny, key: String, valueJson: String) {
    awaitUnitOperation { success, failure ->
        writeIndexedDbValue(database, FORECAST_CACHE_STORE_NAME, key, valueJson, success, failure)
    }
}

private suspend fun awaitDelete(database: JsAny, key: String) {
    awaitUnitOperation { success, failure ->
        deleteIndexedDbValue(database, FORECAST_CACHE_STORE_NAME, key, success, failure)
    }
}

private suspend fun awaitDeleteOlderThan(database: JsAny, cutoffMillis: Long): Int {
    return suspendCoroutine { continuation ->
        var completed = false
        deleteIndexedDbValuesOlderThan(
            database = database,
            storeName = FORECAST_CACHE_STORE_NAME,
            indexName = FETCHED_AT_INDEX_NAME,
            cutoffMillis = cutoffMillis.toDouble(),
            onSuccess = { deleted ->
                if (!completed) {
                    completed = true
                    continuation.resume(deleted)
                }
            },
            onFailure = { errorName ->
                if (!completed) {
                    completed = true
                    continuation.resumeWithException(IndexedDbFailure(errorName))
                }
            },
        )
    }
}

private suspend fun awaitClear(database: JsAny) {
    awaitUnitOperation { success, failure ->
        clearIndexedDbStore(database, FORECAST_CACHE_STORE_NAME, success, failure)
    }
}

private suspend fun awaitUnitOperation(
    start: (onSuccess: () -> Unit, onFailure: (String) -> Unit) -> Unit,
) {
    suspendCoroutine { continuation ->
        var completed = false
        start(
            {
                if (!completed) {
                    completed = true
                    continuation.resume(Unit)
                }
            },
            { errorName ->
                if (!completed) {
                    completed = true
                    continuation.resumeWithException(IndexedDbFailure(errorName))
                }
            },
        )
    }
}

private suspend fun <T> storageOperation(operation: suspend () -> T): T {
    return try {
        operation()
    } catch (failure: IndexedDbFailure) {
        throw failure.asUnavailable()
    }
}

private fun IndexedDbFailure.asUnavailable(): StorageUnavailableException {
    return StorageUnavailableException("Browser IndexedDB operation failed: $errorName", this)
}

private class IndexedDbFailure(
    val errorName: String,
    cause: Throwable? = null,
) : RuntimeException(errorName, cause)

private fun forecastCacheKey(placeId: String, requestedModelApiName: String): String {
    return "${placeId.length}:$placeId$requestedModelApiName"
}

private fun encodeIndexedDbEntry(record: ForecastCacheRecord): String {
    return buildJsonObject {
        put("recordSchemaVersion", JsonPrimitive(RECORD_SCHEMA_VERSION))
        put("fetchedAtMillis", JsonPrimitive(record.fetchedAtMillis))
        put("payloadJson", JsonPrimitive(encodeForecastCacheRecord(record)))
    }.toString()
}

private sealed interface IndexedDbEntryDecodeResult {
    data class Current(val record: ForecastCacheRecord) : IndexedDbEntryDecodeResult
    data object FutureSchema : IndexedDbEntryDecodeResult
    data object Malformed : IndexedDbEntryDecodeResult
}

private fun decodeIndexedDbEntry(raw: String): IndexedDbEntryDecodeResult {
    return try {
        val root = Json.parseToJsonElement(raw).jsonObject
        val schemaVersion = root["recordSchemaVersion"]?.jsonPrimitive?.intOrNull
            ?: return IndexedDbEntryDecodeResult.Malformed
        if (schemaVersion > RECORD_SCHEMA_VERSION) return IndexedDbEntryDecodeResult.FutureSchema
        if (schemaVersion != RECORD_SCHEMA_VERSION) return IndexedDbEntryDecodeResult.Malformed
        val fetchedAtMillis = root["fetchedAtMillis"]?.jsonPrimitive?.longOrNull
            ?: return IndexedDbEntryDecodeResult.Malformed
        val payloadJson = root["payloadJson"]
            ?.jsonPrimitive
            ?.takeIf(JsonPrimitive::isString)
            ?.contentOrNull
            ?: return IndexedDbEntryDecodeResult.Malformed
        val record = decodeForecastCacheRecordOrNull(payloadJson)
            ?.takeIf { it.fetchedAtMillis == fetchedAtMillis }
            ?: return IndexedDbEntryDecodeResult.Malformed
        IndexedDbEntryDecodeResult.Current(record)
    } catch (_: IllegalArgumentException) {
        IndexedDbEntryDecodeResult.Malformed
    }
}

private fun encodeForecastCacheRecord(record: ForecastCacheRecord): String {
    return buildJsonObject {
        put("placeId", JsonPrimitive(record.placeId))
        put("requestedModelApiName", JsonPrimitive(record.requestedModelApiName))
        put("resolvedModelApiName", JsonPrimitive(record.resolvedModelApiName))
        put("forecastDays", JsonPrimitive(record.forecastDays))
        put("hourlyDataJson", JsonPrimitive(record.hourlyDataJson))
        put("fetchedAtMillis", JsonPrimitive(record.fetchedAtMillis))
        put("nextExpectedUpdateMillis", JsonPrimitive(record.nextExpectedUpdateMillis))
    }.toString()
}

private fun decodeForecastCacheRecordOrNull(raw: String): ForecastCacheRecord? {
    return try {
        val root = Json.parseToJsonElement(raw).jsonObject
        ForecastCacheRecord(
            placeId = root.requiredString("placeId"),
            requestedModelApiName = root.requiredString("requestedModelApiName"),
            resolvedModelApiName = root.requiredString("resolvedModelApiName"),
            forecastDays = root["forecastDays"]?.jsonPrimitive?.intOrNull ?: return null,
            hourlyDataJson = root.requiredString("hourlyDataJson"),
            fetchedAtMillis = root["fetchedAtMillis"]?.jsonPrimitive?.longOrNull ?: return null,
            nextExpectedUpdateMillis = root["nextExpectedUpdateMillis"]?.jsonPrimitive?.longOrNull ?: return null,
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun JsonObject.requiredString(name: String): String {
    return this[name]
        ?.jsonPrimitive
        ?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull
        ?: throw IllegalArgumentException("Missing string field: $name")
}
