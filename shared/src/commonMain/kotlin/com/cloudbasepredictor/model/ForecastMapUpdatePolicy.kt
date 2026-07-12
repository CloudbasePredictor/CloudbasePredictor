package com.cloudbasepredictor.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** A location reported by the embedded forecast map panel while the user pans it. */
data class ForecastMapLocation(
    val latitude: Double,
    val longitude: Double,
)

enum class ForecastMapLocationUpdateDecision {
    UPDATE,
    TOO_SOON,
    TOO_CLOSE,
}

const val FORECAST_MAP_LOCATION_UPDATE_RATE_LIMIT_MS = 1_500L
const val FORECAST_MAP_LOCATION_UPDATE_MIN_DISTANCE_METERS = 200.0

/**
 * Decides whether a panned map center should update the forecast, applying the same rate limit
 * (min interval) and minimum-move distance rules on Android and web so the behavior matches.
 */
fun forecastMapLocationUpdateDecision(
    nowMs: Long,
    lastUpdateTimeMs: Long,
    lastLocation: ForecastMapLocation?,
    candidate: ForecastMapLocation,
    rateLimitMs: Long = FORECAST_MAP_LOCATION_UPDATE_RATE_LIMIT_MS,
    minDistanceMeters: Double = FORECAST_MAP_LOCATION_UPDATE_MIN_DISTANCE_METERS,
): ForecastMapLocationUpdateDecision {
    val tooSoon = lastUpdateTimeMs > 0L && nowMs - lastUpdateTimeMs < rateLimitMs
    if (tooSoon) return ForecastMapLocationUpdateDecision.TOO_SOON
    val tooClose = lastLocation != null &&
        forecastMapDistanceMeters(lastLocation, candidate) < minDistanceMeters
    return if (tooClose) {
        ForecastMapLocationUpdateDecision.TOO_CLOSE
    } else {
        ForecastMapLocationUpdateDecision.UPDATE
    }
}

/** Fast equirectangular approximation of the distance between two points, good enough at map scale. */
fun forecastMapDistanceMeters(
    first: ForecastMapLocation,
    second: ForecastMapLocation,
): Double {
    val deltaLatitude = (first.latitude - second.latitude).toRadians()
    val midpointLatitude = ((first.latitude + second.latitude) / 2.0).toRadians()
    val deltaLongitude = (first.longitude - second.longitude).toRadians() * cos(midpointLatitude)
    return sqrt(deltaLatitude * deltaLatitude + deltaLongitude * deltaLongitude) * EARTH_RADIUS_METERS
}

private fun Double.toRadians(): Double = this * PI / HALF_CIRCLE_DEGREES

private const val HALF_CIRCLE_DEGREES = 180.0
private const val EARTH_RADIUS_METERS = 6_371_000.0
