@file:Suppress("TooGenericExceptionCaught")

package com.cloudbasepredictor.web.storage

import kotlinx.browser.window
import org.w3c.dom.Storage

internal fun browserLocalStorageOrNull(): Storage? {
    return try {
        window.localStorage
    } catch (_: Throwable) {
        null
    }
}

internal fun Storage.getItemSafely(key: String): String? {
    return try {
        getItem(key)
    } catch (cause: Throwable) {
        throw BrowserStorageException("Browser localStorage cannot be read", cause)
    }
}

internal fun Storage.setItemSafely(key: String, value: String) {
    try {
        setItem(key, value)
    } catch (cause: Throwable) {
        throw BrowserStorageException("Browser localStorage cannot be written", cause)
    }
}

internal fun Storage.removeItemSafely(key: String) {
    try {
        removeItem(key)
    } catch (cause: Throwable) {
        throw BrowserStorageException("Browser localStorage cannot be updated", cause)
    }
}

internal class BrowserStorageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
