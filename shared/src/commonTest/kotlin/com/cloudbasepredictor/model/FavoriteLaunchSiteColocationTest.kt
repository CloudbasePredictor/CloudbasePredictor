package com.cloudbasepredictor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FavoriteLaunchSiteColocationTest {
    private val launchSite = ParaglidingLaunchSite(
        id = "6176",
        name = "Saint Hilaire du Touvet",
        latitude = 45.3069,
        longitude = 5.88806,
    )
    private val otherLaunchSite = ParaglidingLaunchSite(
        id = "7342",
        name = "Annecy Planfait",
        latitude = 45.8401,
        longitude = 6.2182,
    )
    private val launchSiteFavorite = SavedPlace(
        id = "favorite-saint-hilaire",
        name = "Favorite Saint Hilaire",
        latitude = 45.3069,
        longitude = 5.88806,
        isFavorite = true,
    )
    private val otherFavorite = SavedPlace(
        id = "favorite-zurich",
        name = "Zurich",
        latitude = 47.3769,
        longitude = 8.5417,
        isFavorite = true,
    )

    @Test
    fun favoriteOnLaunchSiteMergesIntoOnePair() {
        val merged = mergeColocatedFavoriteLaunchSites(
            favorites = listOf(launchSiteFavorite, otherFavorite),
            launchSites = listOf(launchSite, otherLaunchSite),
        )

        assertEquals(listOf(FavoriteLaunchSite(launchSiteFavorite, launchSite)), merged)
    }

    @Test
    fun favoriteNearButBeyondThresholdIsNotMerged() {
        // ~44 m north of the launch site — outside the 30 m colocation threshold.
        val nearby = launchSiteFavorite.copy(latitude = 45.3073, longitude = 5.88806)

        val merged = mergeColocatedFavoriteLaunchSites(
            favorites = listOf(nearby),
            launchSites = listOf(launchSite),
        )

        assertEquals(emptyList(), merged)
    }

    @Test
    fun nonFavoritePlacesAreIgnored() {
        val savedButNotFavorite = launchSiteFavorite.copy(isFavorite = false)

        val merged = mergeColocatedFavoriteLaunchSites(
            favorites = listOf(savedButNotFavorite),
            launchSites = listOf(launchSite),
        )

        assertEquals(emptyList(), merged)
    }

    @Test
    fun eachLaunchSitePairsWithAtMostOneFavorite() {
        // Two favorites sit on the same launch site; the closer one wins and the launch site is used once.
        val closerFavorite = launchSiteFavorite.copy(id = "favorite-a", latitude = 45.30691)
        val fartherFavorite = launchSiteFavorite.copy(id = "favorite-b", latitude = 45.30700)

        val merged = mergeColocatedFavoriteLaunchSites(
            favorites = listOf(fartherFavorite, closerFavorite),
            launchSites = listOf(launchSite),
        )

        assertEquals(listOf(FavoriteLaunchSite(closerFavorite, launchSite)), merged)
    }

    @Test
    fun nearestColocatedLaunchSiteReturnsClosestOrNull() {
        assertEquals(
            FavoriteLaunchSite(launchSiteFavorite, launchSite),
            nearestColocatedLaunchSite(launchSiteFavorite, listOf(launchSite, otherLaunchSite)),
        )
        assertNull(nearestColocatedLaunchSite(otherFavorite, listOf(launchSite, otherLaunchSite)))
        assertNull(
            nearestColocatedLaunchSite(
                launchSiteFavorite.copy(isFavorite = false),
                listOf(launchSite),
            ),
        )
    }
}
