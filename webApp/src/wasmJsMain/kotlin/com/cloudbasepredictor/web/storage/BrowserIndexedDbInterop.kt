@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
// Detekt cannot see parameters referenced from Kotlin/Wasm js(...) bodies.
@file:Suppress("LongParameterList", "UnusedParameter")

package com.cloudbasepredictor.web.storage

import kotlin.js.JsAny
import kotlin.js.js

/** The deliberately small IndexedDB surface used by the forecast cache. */
internal fun openIndexedDb(
    databaseName: String,
    version: Int,
    storeName: String,
    indexName: String,
    onSuccess: (JsAny) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """{
        if (globalThis.indexedDB == null) {
            onFailure("UnavailableError");
            return;
        }
        const request = globalThis.indexedDB.open(databaseName, version);
        let finished = false;
        request.onupgradeneeded = () => {
            const database = request.result;
            const store = database.objectStoreNames.contains(storeName)
                ? request.transaction.objectStore(storeName)
                : database.createObjectStore(storeName);
            if (!store.indexNames.contains(indexName)) {
                store.createIndex(indexName, "fetchedAtMillis");
            }
        };
        request.onblocked = () => {
            if (!finished) {
                finished = true;
                onFailure("BlockedError");
            }
        };
        request.onerror = () => {
            if (!finished) {
                finished = true;
                onFailure(request.error?.name ?? "UnknownError");
            }
        };
        request.onsuccess = () => {
            const database = request.result;
            if (finished) {
                database.close();
                return;
            }
            finished = true;
            database.onversionchange = () => database.close();
            onSuccess(database);
        };
    }""",
)

internal fun closeIndexedDb(database: JsAny): Unit = js("database.close()")

internal fun readIndexedDbValue(
    database: JsAny,
    storeName: String,
    key: String,
    onSuccess: (String?) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """{
        let finished = false;
        let value = null;
        const fail = (error) => {
            if (!finished) {
                finished = true;
                onFailure(error?.name ?? "UnknownError");
            }
        };
        try {
            const transaction = database.transaction(storeName, "readonly");
            const request = transaction.objectStore(storeName).get(key);
            request.onsuccess = () => {
                value = request.result === undefined ? null : JSON.stringify(request.result);
            };
            transaction.oncomplete = () => {
                if (!finished) {
                    finished = true;
                    onSuccess(value);
                }
            };
            transaction.onabort = () => fail(transaction.error);
        } catch (error) {
            fail(error);
        }
    }""",
)

internal fun writeIndexedDbValue(
    database: JsAny,
    storeName: String,
    key: String,
    valueJson: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """{
        let finished = false;
        const fail = (error) => {
            if (!finished) {
                finished = true;
                onFailure(error?.name ?? "UnknownError");
            }
        };
        try {
            const value = JSON.parse(valueJson);
            const transaction = database.transaction(storeName, "readwrite");
            transaction.oncomplete = () => {
                if (!finished) {
                    finished = true;
                    onSuccess();
                }
            };
            transaction.onabort = () => fail(transaction.error);
            transaction.objectStore(storeName).put(value, key);
        } catch (error) {
            fail(error);
        }
    }""",
)

internal fun deleteIndexedDbValue(
    database: JsAny,
    storeName: String,
    key: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """{
        let finished = false;
        const fail = (error) => {
            if (!finished) {
                finished = true;
                onFailure(error?.name ?? "UnknownError");
            }
        };
        try {
            const transaction = database.transaction(storeName, "readwrite");
            transaction.oncomplete = () => {
                if (!finished) {
                    finished = true;
                    onSuccess();
                }
            };
            transaction.onabort = () => fail(transaction.error);
            transaction.objectStore(storeName).delete(key);
        } catch (error) {
            fail(error);
        }
    }""",
)

internal fun deleteIndexedDbValuesOlderThan(
    database: JsAny,
    storeName: String,
    indexName: String,
    cutoffMillis: Double,
    onSuccess: (Int) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """{
        let finished = false;
        let deleted = 0;
        const fail = (error) => {
            if (!finished) {
                finished = true;
                onFailure(error?.name ?? "UnknownError");
            }
        };
        try {
            const transaction = database.transaction(storeName, "readwrite");
            const store = transaction.objectStore(storeName);
            const range = globalThis.IDBKeyRange.upperBound(cutoffMillis, true);
            const request = store.index(indexName).openKeyCursor(range);
            request.onsuccess = () => {
                const cursor = request.result;
                if (cursor != null) {
                    store.delete(cursor.primaryKey);
                    deleted += 1;
                    cursor.continue();
                }
            };
            transaction.oncomplete = () => {
                if (!finished) {
                    finished = true;
                    onSuccess(deleted);
                }
            };
            transaction.onabort = () => fail(transaction.error);
        } catch (error) {
            fail(error);
        }
    }""",
)

internal fun clearIndexedDbStore(
    database: JsAny,
    storeName: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """{
        let finished = false;
        const fail = (error) => {
            if (!finished) {
                finished = true;
                onFailure(error?.name ?? "UnknownError");
            }
        };
        try {
            const transaction = database.transaction(storeName, "readwrite");
            transaction.oncomplete = () => {
                if (!finished) {
                    finished = true;
                    onSuccess();
                }
            };
            transaction.onabort = () => fail(transaction.error);
            transaction.objectStore(storeName).clear();
        } catch (error) {
            fail(error);
        }
    }""",
)
