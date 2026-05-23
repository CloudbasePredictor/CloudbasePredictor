package com.cloudbasepredictor.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface ParaglidingEarthApi {
    @GET("geojson/getBoundingBoxSites.php")
    suspend fun getLaunchSitesInBounds(
        @Query("north") north: Double,
        @Query("south") south: Double,
        @Query("west") west: Double,
        @Query("east") east: Double,
        @Query("limit") limit: Int = 150,
        @Query("style") style: String = "detailled",
    ): ParaglidingEarthFeatureCollection
}

@Serializable
data class ParaglidingEarthFeatureCollection(
    val features: List<ParaglidingEarthFeature> = emptyList(),
)

@Serializable
data class ParaglidingEarthFeature(
    val id: String? = null,
    val geometry: ParaglidingEarthGeometry? = null,
    val properties: ParaglidingEarthProperties? = null,
)

@Serializable
data class ParaglidingEarthGeometry(
    val coordinates: List<Double> = emptyList(),
)

@Serializable
data class ParaglidingEarthProperties(
    val name: String? = null,
    val place: String? = null,
    val countryCode: String? = null,
    @SerialName("takeoff_altitude")
    val takeoffAltitude: String? = null,
    @SerialName("takeoff_description")
    val takeoffDescription: String? = null,
    @SerialName("flight_rules")
    val flightRules: String? = null,
    @SerialName("going_there")
    val goingThere: String? = null,
    val comments: String? = null,
    val weather: String? = null,
    @SerialName("last_edit")
    val lastEdit: String? = null,
    @SerialName("pge_site_id")
    val pgeSiteId: String? = null,
    @SerialName("pge_link")
    val pgeLink: String? = null,
    @SerialName("N")
    val north: String? = null,
    @SerialName("NE")
    val northEast: String? = null,
    @SerialName("E")
    val east: String? = null,
    @SerialName("SE")
    val southEast: String? = null,
    @SerialName("S")
    val south: String? = null,
    @SerialName("SW")
    val southWest: String? = null,
    @SerialName("W")
    val west: String? = null,
    @SerialName("NW")
    val northWest: String? = null,
    val paragliding: String? = null,
    val hanggliding: String? = null,
    val thermals: String? = null,
    val soaring: String? = null,
    val winch: String? = null,
    val xc: String? = null,
    val flatland: String? = null,
    val hike: String? = null,
    val landing: ParaglidingEarthLanding? = null,
)

@Serializable
data class ParaglidingEarthLanding(
    @SerialName("landing_name")
    val landingName: String? = null,
    @SerialName("landing_lat")
    val landingLat: String? = null,
    @SerialName("landing_lng")
    val landingLng: String? = null,
)
