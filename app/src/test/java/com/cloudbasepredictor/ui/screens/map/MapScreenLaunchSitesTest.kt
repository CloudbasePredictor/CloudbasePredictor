package com.cloudbasepredictor.ui.screens.map

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.model.ParaglidingLaunchSite
import com.cloudbasepredictor.model.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.Position

class MapScreenLaunchSitesTest {
    private val launchSite = ParaglidingLaunchSite(
        id = "6176",
        name = "Saint Hilaire du Touvet",
        latitude = 45.3069,
        longitude = 5.88806,
    )
    private val favoritePlace = SavedPlace(
        id = "place:45.3069:5.8881",
        name = "Favorite Saint Hilaire",
        latitude = 45.3069,
        longitude = 5.88806,
        isFavorite = true,
    )

    @Test
    fun buildLaunchSitesFeatureCollection_includesLaunchSiteIdAndName() {
        val geoJson = buildLaunchSitesFeatureCollection(
            listOf(launchSite),
        )

        assertTrue(geoJson.contains("\"launchSiteId\": \"6176\""))
        assertTrue(geoJson.contains("\"name\": \"Saint Hilaire du Touvet\""))
        assertTrue(geoJson.contains("\"coordinates\": [5.88806, 45.3069]"))
    }

    @Test
    fun resolveMapClickTarget_whenRenderedLaunchFeatureIsMissingButClickIsNearFlag_returnsLaunchSite() {
        val target = resolveMapClickTarget(
            position = Position(
                longitude = launchSite.longitude,
                latitude = launchSite.latitude,
            ),
            clickOffset = DpOffset(x = 114.dp, y = 108.dp),
            launchSiteFeatures = emptyList(),
            favoriteFeatures = emptyList(),
            favoritePlaces = emptyList(),
            launchSites = listOf(launchSite),
            favoritePlaceScreenOffsets = emptyList(),
            launchSiteScreenOffsets = listOf(
                LaunchSiteScreenOffset(
                    launchSite = launchSite,
                    screenOffset = DpOffset(x = 100.dp, y = 100.dp),
                ),
            ),
        )

        assertEquals(MapClickTarget.LaunchSite(launchSite), target)
    }

    @Test
    fun resolveMapClickTarget_whenRenderedFavoriteFeatureIsMissingButClickIsNearFavorite_returnsFavorite() {
        val target = resolveMapClickTarget(
            position = Position(
                longitude = favoritePlace.longitude,
                latitude = favoritePlace.latitude,
            ),
            clickOffset = DpOffset(x = 110.dp, y = 103.dp),
            launchSiteFeatures = emptyList(),
            favoriteFeatures = emptyList(),
            favoritePlaces = listOf(favoritePlace),
            launchSites = emptyList(),
            favoritePlaceScreenOffsets = listOf(
                FavoritePlaceScreenOffset(
                    place = favoritePlace,
                    screenOffset = DpOffset(x = 100.dp, y = 100.dp),
                ),
            ),
            launchSiteScreenOffsets = emptyList(),
        )

        assertEquals(MapClickTarget.FavoritePlace(favoritePlace), target)
    }

    @Test
    fun resolveMapClickTarget_whenFavoriteAndLaunchAreBothNearClick_prefersFavorite() {
        val target = resolveMapClickTarget(
            position = Position(
                longitude = favoritePlace.longitude,
                latitude = favoritePlace.latitude,
            ),
            clickOffset = DpOffset(x = 104.dp, y = 104.dp),
            launchSiteFeatures = emptyList(),
            favoriteFeatures = emptyList(),
            favoritePlaces = listOf(favoritePlace),
            launchSites = listOf(launchSite),
            favoritePlaceScreenOffsets = listOf(
                FavoritePlaceScreenOffset(
                    place = favoritePlace,
                    screenOffset = DpOffset(x = 100.dp, y = 100.dp),
                ),
            ),
            launchSiteScreenOffsets = listOf(
                LaunchSiteScreenOffset(
                    launchSite = launchSite,
                    screenOffset = DpOffset(x = 100.dp, y = 100.dp),
                ),
            ),
        )

        assertEquals(MapClickTarget.FavoritePlace(favoritePlace), target)
    }

    @Test
    fun resolveMapClickTarget_whenClickIsFarFromFlag_returnsCoordinateTarget() {
        val target = resolveMapClickTarget(
            position = Position(longitude = 6.0, latitude = 45.4),
            clickOffset = DpOffset(x = 180.dp, y = 180.dp),
            launchSiteFeatures = emptyList(),
            favoriteFeatures = emptyList(),
            favoritePlaces = emptyList(),
            launchSites = listOf(launchSite),
            favoritePlaceScreenOffsets = emptyList(),
            launchSiteScreenOffsets = listOf(
                LaunchSiteScreenOffset(
                    launchSite = launchSite,
                    screenOffset = DpOffset(x = 100.dp, y = 100.dp),
                ),
            ),
        )

        assertEquals(
            MapClickTarget.Coordinates(latitude = 45.4, longitude = 6.0),
            target,
        )
    }

    @Test
    fun launchSiteCardDisplay_whenFavoriteNameDiffers_usesFavoriteTitleAndOriginalName() {
        val display = launchSiteCardDisplay(
            launchSite = launchSite,
            favoritePlace = favoritePlace,
        )

        assertEquals("Favorite Saint Hilaire", display.title)
        assertEquals("Saint Hilaire du Touvet", display.originalName)
    }

    @Test
    fun launchSiteCardDisplay_whenFavoriteNameMatches_omitsOriginalName() {
        val display = launchSiteCardDisplay(
            launchSite = launchSite,
            favoritePlace = favoritePlace.copy(name = launchSite.name),
        )

        assertEquals("Saint Hilaire du Touvet", display.title)
        assertEquals(null, display.originalName)
    }
}
