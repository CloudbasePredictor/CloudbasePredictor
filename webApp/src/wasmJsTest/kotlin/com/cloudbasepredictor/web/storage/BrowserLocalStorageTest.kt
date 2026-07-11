@file:Suppress("MagicNumber")

package com.cloudbasepredictor.web.storage

import com.cloudbasepredictor.model.SavedPlace
import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserLocalStorageTest {
    @Test
    fun keyValueStateRoundTripsAndTypedWritesReplaceTheOldType() {
        val storageKey = uniqueBrowserKey("user-state")
        val legacyKey = uniqueBrowserKey("legacy-settings")
        window.localStorage.removeItem(storageKey)
        try {
            val first = BrowserLocalStorageKeyValueStorage(storageKey, legacyKey)
            first.putString("theme_preference", "DARK")
            first.putBoolean("start_with_favorites", false)
            first.putFloat("visible_top_altitude_km", 4.5f)
            first.putString("start_with_favorites", "retyped")

            val reloaded = BrowserLocalStorageKeyValueStorage(storageKey, legacyKey)
            assertEquals("DARK", reloaded.getString("theme_preference"))
            assertEquals("retyped", reloaded.getString("start_with_favorites"))
            assertNull(reloaded.getBoolean("start_with_favorites"))
            assertEquals(4.5f, reloaded.getFloat("visible_top_altitude_km"))

            val root = Json.parseToJsonElement(window.localStorage.getItem(storageKey)!!).jsonObject
            assertEquals(1, root.getValue("schemaVersion").jsonPrimitive.content.toInt())
            assertTrue("start_with_favorites" in root.getValue("strings").jsonObject)
            assertFalse("start_with_favorites" in root.getValue("booleans").jsonObject)
        } finally {
            window.localStorage.removeItem(storageKey)
            window.localStorage.removeItem(legacyKey)
        }
    }

    @Test
    fun malformedAndFutureUserStateStayUntouchedWhileWritesFallBackToMemory() {
        listOf(
            "not-json",
            """{"schemaVersion":99,"strings":{"future":"value"},"booleans":{},"floats":{}}""",
        ).forEachIndexed { index, original ->
            val storageKey = uniqueBrowserKey("unreadable-$index")
            val legacyKey = uniqueBrowserKey("legacy-unreadable-$index")
            window.localStorage.setItem(storageKey, original)
            try {
                val storage = BrowserLocalStorageKeyValueStorage(storageKey, legacyKey)
                storage.putString("session", "value")

                assertEquals("value", storage.getString("session"))
                assertEquals(original, window.localStorage.getItem(storageKey))
                assertNull(BrowserLocalStorageKeyValueStorage(storageKey, legacyKey).getString("session"))
            } finally {
                window.localStorage.removeItem(storageKey)
                window.localStorage.removeItem(legacyKey)
            }
        }
    }

    @Test
    fun frozenTypeScriptSettingsMigrateOnceAndSystemThemeBecomesAuto() {
        val storageKey = uniqueBrowserKey("migrated-user-state")
        val legacyKey = uniqueBrowserKey("legacy-settings")
        window.localStorage.setItem(
            legacyKey,
            """{"schemaVersion":1,"settings":{"unitPreset":"AVIATION","themeMode":"system"}}""",
        )
        try {
            val storage = BrowserLocalStorageKeyValueStorage(storageKey, legacyKey)

            assertEquals("AVIATION", storage.getString("unit_preset"))
            assertEquals("AUTO", storage.getString("theme_preference"))
            assertNull(window.localStorage.getItem(legacyKey))

            window.localStorage.setItem(
                legacyKey,
                """{"schemaVersion":1,"settings":{"unitPreset":"IMPERIAL","themeMode":"dark"}}""",
            )
            val reloaded = BrowserLocalStorageKeyValueStorage(storageKey, legacyKey)
            assertEquals("AVIATION", reloaded.getString("unit_preset"))
            assertEquals("AUTO", reloaded.getString("theme_preference"))
            assertNull(window.localStorage.getItem(legacyKey))
        } finally {
            window.localStorage.removeItem(storageKey)
            window.localStorage.removeItem(legacyKey)
        }
    }

    @Test
    fun favoritesRoundTripAndFrozenTypeScriptFavoritesMigrateWithValidation() = runTest {
        val storageKey = uniqueBrowserKey("favorites")
        val legacyKey = uniqueBrowserKey("legacy-favorites")
        window.localStorage.setItem(
            legacyKey,
            """{
                "schemaVersion":1,
                "places":[
                    {"name":"  Brauneck  ","latitude":47.6632,"longitude":11.5564},
                    {"name":"duplicate","latitude":47.6632,"longitude":11.5564},
                    {"name":"invalid","latitude":91.0,"longitude":11.0}
                ]
            }""".trimIndent(),
        )
        try {
            val migrated = BrowserLocalStorageFavoritePlaceStore(storageKey, legacyKey)
            val places = migrated.readAll()

            assertEquals(1, places.size)
            assertEquals("Brauneck", places.single().name)
            assertTrue(places.single().isFavorite)
            assertNull(window.localStorage.getItem(legacyKey))

            val additional = SavedPlace.fromCoordinates(46.0, 10.0).copy(name = "Second")
            migrated.upsert(additional)
            val reloaded = BrowserLocalStorageFavoritePlaceStore(storageKey, legacyKey)
            assertEquals(listOf("Second", "Brauneck"), reloaded.readAll().map(SavedPlace::name))

            reloaded.delete(additional.id)
            val afterDelete = BrowserLocalStorageFavoritePlaceStore(storageKey, legacyKey).readAll()
            assertEquals(listOf("Brauneck"), afterDelete.map(SavedPlace::name))
        } finally {
            window.localStorage.removeItem(storageKey)
            window.localStorage.removeItem(legacyKey)
        }
    }
}

private var browserKeyCounter = 0

private fun uniqueBrowserKey(label: String): String {
    browserKeyCounter += 1
    return "cbp.test.$label.$browserKeyCounter"
}
