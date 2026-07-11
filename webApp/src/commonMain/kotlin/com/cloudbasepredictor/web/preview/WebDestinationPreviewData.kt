package com.cloudbasepredictor.web.preview

import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.theme.ThemePreference
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.web.preferences.WebPreferencesState

object WebDestinationPreviewData {
    val favoritePlaces = listOf(
        SavedPlace(
            id = "place:47.6632:11.5564",
            name = "Brauneck",
            latitude = 47.6632,
            longitude = 11.5564,
            isFavorite = true,
        ),
        SavedPlace(
            id = "place:46.5580:10.0660",
            name = "Muottas Muragl",
            latitude = 46.558,
            longitude = 10.066,
            isFavorite = true,
        ),
        SavedPlace(
            id = "place:45.9237:6.8694",
            name = "Planpraz",
            latitude = 45.9237,
            longitude = 6.8694,
            isFavorite = true,
        ),
    )

    val preferences = WebPreferencesState(
        unitPreset = UnitPreset.AVIATION,
        themePreference = ThemePreference.AUTO,
        mapLayer = MapLayerPreference.OPENTOPOMAP,
        startWithFavorites = true,
        forecastModel = ForecastModel.ICON_SEAMLESS,
    )
}
