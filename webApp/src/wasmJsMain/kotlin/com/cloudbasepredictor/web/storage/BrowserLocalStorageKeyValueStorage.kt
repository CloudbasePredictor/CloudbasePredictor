@file:Suppress("ReturnCount")

package com.cloudbasepredictor.web.storage

import com.cloudbasepredictor.data.storage.KeyValueStorage
import com.cloudbasepredictor.data.storage.ResilientKeyValueStorage
import com.cloudbasepredictor.data.storage.StorageUnavailableException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Storage

const val DEFAULT_USER_STATE_STORAGE_KEY = "cbp.kmp.user-state"

private const val USER_STATE_SCHEMA_VERSION = 1
private const val LEGACY_SETTINGS_STORAGE_KEY = "cbp.settings.v1"
private const val UNIT_PRESET_KEY = "unit_preset"
private const val THEME_PREFERENCE_KEY = "theme_preference"

/** Schema-versioned localStorage implementation for small browser preferences. */
class BrowserLocalStorageKeyValueStorage(
    private val storageKey: String = DEFAULT_USER_STATE_STORAGE_KEY,
    private val legacySettingsKey: String = LEGACY_SETTINGS_STORAGE_KEY,
    storage: Storage? = browserLocalStorageOrNull(),
) : KeyValueStorage {
    private val durable = DurableLocalStorageKeyValueStorage(storageKey, storage)
    private val delegate = ResilientKeyValueStorage(durable)

    init {
        migrateLegacySettingsIfNeeded(storage)
    }

    override fun contains(key: String): Boolean = delegate.contains(key)
    override fun getString(key: String): String? = delegate.getString(key)
    override fun getBoolean(key: String): Boolean? = delegate.getBoolean(key)
    override fun getFloat(key: String): Float? = delegate.getFloat(key)
    override fun putString(key: String, value: String) = delegate.putString(key, value)
    override fun putBoolean(key: String, value: Boolean) = delegate.putBoolean(key, value)
    override fun putFloat(key: String, value: Float) = delegate.putFloat(key, value)
    override fun remove(key: String) = delegate.remove(key)

    private fun migrateLegacySettingsIfNeeded(storage: Storage?) {
        if (storage == null) return

        when (durable.inspect()) {
            LocalPayloadStatus.Current -> {
                removeLegacyAfterVerifiedKmpState(storage)
                return
            }
            LocalPayloadStatus.Unreadable -> return
            LocalPayloadStatus.Missing -> Unit
        }

        val legacyRaw = try {
            storage.getItemSafely(legacySettingsKey)
        } catch (_: BrowserStorageException) {
            return
        } ?: return
        val legacy = decodeLegacySettings(legacyRaw) ?: return

        legacy.unitPreset?.let { delegate.putString(UNIT_PRESET_KEY, it) }
        legacy.themePreference?.let { delegate.putString(THEME_PREFERENCE_KEY, it) }
        if (legacy.unitPreset == null && legacy.themePreference == null) return

        val verified = durable.inspect() == LocalPayloadStatus.Current &&
            legacy.unitPreset?.let { durable.getString(UNIT_PRESET_KEY) == it } != false &&
            legacy.themePreference?.let { durable.getString(THEME_PREFERENCE_KEY) == it } != false
        if (verified) {
            try {
                storage.removeItemSafely(legacySettingsKey)
            } catch (_: BrowserStorageException) {
                // A later session can safely retry deletion without rewriting KMP state.
            }
        }
    }

    private fun removeLegacyAfterVerifiedKmpState(storage: Storage) {
        try {
            if (storage.getItemSafely(legacySettingsKey) != null) {
                storage.removeItemSafely(legacySettingsKey)
            }
        } catch (_: BrowserStorageException) {
            // Existing KMP state stays authoritative; cleanup can be retried later.
        }
    }
}

private class DurableLocalStorageKeyValueStorage(
    private val storageKey: String,
    private val storage: Storage?,
) : KeyValueStorage {
    override fun contains(key: String): Boolean {
        val state = readState()
        return key in state.strings || key in state.booleans || key in state.floats
    }

    override fun getString(key: String): String? = readState().strings[key]
    override fun getBoolean(key: String): Boolean? = readState().booleans[key]
    override fun getFloat(key: String): Float? = readState().floats[key]

    override fun putString(key: String, value: String) {
        val state = readState()
        writeState(
            state.copy(
                strings = state.strings + (key to value),
                booleans = state.booleans - key,
                floats = state.floats - key,
            ),
        )
    }

    override fun putBoolean(key: String, value: Boolean) {
        val state = readState()
        writeState(
            state.copy(
                strings = state.strings - key,
                booleans = state.booleans + (key to value),
                floats = state.floats - key,
            ),
        )
    }

    override fun putFloat(key: String, value: Float) {
        if (!value.isFinite()) {
            throw StorageUnavailableException("Non-finite floats cannot be persisted as JSON")
        }
        val state = readState()
        writeState(
            state.copy(
                strings = state.strings - key,
                booleans = state.booleans - key,
                floats = state.floats + (key to value),
            ),
        )
    }

    override fun remove(key: String) {
        val state = readState()
        if (key !in state.strings && key !in state.booleans && key !in state.floats) return
        writeState(
            state.copy(
                strings = state.strings - key,
                booleans = state.booleans - key,
                floats = state.floats - key,
            ),
        )
    }

    fun inspect(): LocalPayloadStatus {
        val availableStorage = storage ?: return LocalPayloadStatus.Unreadable
        val raw = try {
            availableStorage.getItemSafely(storageKey)
        } catch (_: BrowserStorageException) {
            return LocalPayloadStatus.Unreadable
        } ?: return LocalPayloadStatus.Missing
        return try {
            decodeUserState(raw)
            LocalPayloadStatus.Current
        } catch (_: StorageUnavailableException) {
            LocalPayloadStatus.Unreadable
        }
    }

    private fun readState(): UserState {
        val availableStorage = storage
            ?: throw StorageUnavailableException("Browser localStorage is unavailable")
        val raw = try {
            availableStorage.getItemSafely(storageKey)
        } catch (cause: BrowserStorageException) {
            throw StorageUnavailableException(cause.message ?: "Browser localStorage is unavailable", cause)
        }
        return raw?.let(::decodeUserState) ?: UserState()
    }

    private fun writeState(state: UserState) {
        val availableStorage = storage
            ?: throw StorageUnavailableException("Browser localStorage is unavailable")
        val encoded = encodeUserState(state)
        try {
            availableStorage.setItemSafely(storageKey, encoded)
        } catch (cause: BrowserStorageException) {
            throw StorageUnavailableException(cause.message ?: "Browser localStorage is unavailable", cause)
        }
        if (readState() != state) {
            throw StorageUnavailableException("Browser localStorage write could not be verified")
        }
    }
}

private data class UserState(
    val strings: Map<String, String> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
)

private enum class LocalPayloadStatus {
    Missing,
    Current,
    Unreadable,
}

private data class LegacySettings(
    val unitPreset: String?,
    val themePreference: String?,
)

private fun decodeUserState(raw: String): UserState {
    try {
        val root = Json.parseToJsonElement(raw).jsonObject
        val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull
        if (schemaVersion != USER_STATE_SCHEMA_VERSION) {
            throw StorageUnavailableException("Unsupported browser user-state schema")
        }
        return UserState(
            strings = root.decodeStringMap("strings"),
            booleans = root.decodeBooleanMap("booleans"),
            floats = root.decodeFloatMap("floats"),
        )
    } catch (cause: StorageUnavailableException) {
        throw cause
    } catch (cause: IllegalArgumentException) {
        throw StorageUnavailableException("Browser user state is malformed", cause)
    }
}

private fun encodeUserState(state: UserState): String {
    return buildJsonObject {
        put("schemaVersion", JsonPrimitive(USER_STATE_SCHEMA_VERSION))
        put("strings", JsonObject(state.strings.mapValues { JsonPrimitive(it.value) }))
        put("booleans", JsonObject(state.booleans.mapValues { JsonPrimitive(it.value) }))
        put("floats", JsonObject(state.floats.mapValues { JsonPrimitive(it.value) }))
    }.toString()
}

private fun JsonObject.decodeStringMap(name: String): Map<String, String> {
    val values = this[name]?.jsonObject
        ?: throw StorageUnavailableException("Browser user-state string map is missing")
    return values.mapValues { (_, element) ->
        element.jsonPrimitive.takeIf(JsonPrimitive::isString)?.contentOrNull
            ?: throw StorageUnavailableException("Browser user-state string value is malformed")
    }
}

private fun JsonObject.decodeBooleanMap(name: String): Map<String, Boolean> {
    val values = this[name]?.jsonObject
        ?: throw StorageUnavailableException("Browser user-state boolean map is missing")
    return values.mapValues { (_, element) ->
        element.jsonPrimitive.takeUnless(JsonPrimitive::isString)?.booleanOrNull
            ?: throw StorageUnavailableException("Browser user-state boolean value is malformed")
    }
}

private fun JsonObject.decodeFloatMap(name: String): Map<String, Float> {
    val values = this[name]?.jsonObject
        ?: throw StorageUnavailableException("Browser user-state float map is missing")
    return values.mapValues { (_, element) ->
        element.jsonPrimitive.takeUnless(JsonPrimitive::isString)?.floatOrNull?.takeIf(Float::isFinite)
            ?: throw StorageUnavailableException("Browser user-state float value is malformed")
    }
}

private fun decodeLegacySettings(raw: String): LegacySettings? {
    return try {
        val root = Json.parseToJsonElement(raw).jsonObject
        if (root["schemaVersion"]?.jsonPrimitive?.intOrNull != 1) return null
        val settings = root["settings"]?.jsonObject ?: return null
        val unitPreset = settings["unitPreset"]
            ?.jsonPrimitive
            ?.takeIf(JsonPrimitive::isString)
            ?.contentOrNull
            ?.takeIf { it in LEGACY_UNIT_PRESETS }
        val themePreference = settings["themeMode"]
            ?.jsonPrimitive
            ?.takeIf(JsonPrimitive::isString)
            ?.contentOrNull
            ?.let(LEGACY_THEME_MODES::get)
        LegacySettings(unitPreset, themePreference)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private val LEGACY_UNIT_PRESETS = setOf("METRIC_KMH", "METRIC_MPS", "IMPERIAL", "AVIATION")
private val LEGACY_THEME_MODES = mapOf("system" to "AUTO", "light" to "LIGHT", "dark" to "DARK")
