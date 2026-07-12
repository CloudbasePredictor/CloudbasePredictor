package com.cloudbasepredictor.web.preferences

import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.storage.InMemoryKeyValueStorage
import com.cloudbasepredictor.data.theme.ThemePreference
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.model.ForecastModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebPreferencesTest {
    @Test
    fun valuesRoundTripThroughCommonStorageContract() {
        val storage = InMemoryKeyValueStorage()
        val first = WebPreferences(storage)

        first.selectUnitPreset(UnitPreset.AVIATION)
        first.selectTheme(ThemePreference.DARK)
        first.selectMapLayer(MapLayerPreference.NASA_GIBS)
        first.setStartWithFavorites(false)
        first.selectForecastModel(ForecastModel.ECMWF_IFS)
        first.setShowLaunchSites(true)

        val restored = WebPreferences(storage).state.value
        assertEquals(UnitPreset.AVIATION, restored.unitPreset)
        assertEquals(ThemePreference.DARK, restored.themePreference)
        assertEquals(MapLayerPreference.NASA_GIBS, restored.mapLayer)
        assertFalse(restored.startWithFavorites)
        assertEquals(ForecastModel.ECMWF_IFS, restored.forecastModel)
        assertTrue(restored.showLaunchSites)
    }

    @Test
    fun showLaunchSitesDefaultsToDisabledOnWeb() {
        val preferences = WebPreferences(InMemoryKeyValueStorage())

        assertFalse(preferences.state.value.showLaunchSites)
    }
}
