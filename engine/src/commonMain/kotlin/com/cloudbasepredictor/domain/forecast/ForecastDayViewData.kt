package com.cloudbasepredictor.domain.forecast

import kotlin.jvm.JvmInline

/**
 * Shared domain contract for one processed local forecast day that is ready to be mapped into
 * forecast UI states.
 *
 * Requirements for every producer that builds this model:
 * - `localDate` must use the ISO local date format `yyyy-MM-dd`.
 * - `timeSlots` must be sorted, unique, and aligned to 15-minute boundaries.
 * - `surfaceLayer` is mandatory and owns all surface-only readings.
 * - `altitudeLayers` must never include the surface. The first altitude layer starts at 50 m AGL.
 * - Vertical layers must use a strict 50 m step with no gaps between neighbouring layers.
 * - Every layer sample list must have the same size as `timeSlots`; sample index `n` belongs to
 *   `timeSlots[n]`.
 *
 * This file intentionally contains only local data objects and invariants. Future domain
 * processing code can build on top of these contracts without leaking storage or UI details.
 */
data class ForecastDayViewData(
    val localDate: String,
    val timeSlots: List<ForecastTimeSlot>,
    val surfaceLayer: ForecastSurfaceLayerViewData,
    val altitudeLayers: List<ForecastAltitudeLayerViewData>,
) {
    init {
        require(localDate.matches(ISO_LOCAL_DATE_REGEX)) {
            "localDate must use yyyy-MM-dd format."
        }
        require(timeSlots.isNotEmpty()) {
            "timeSlots must not be empty."
        }
        require(timeSlots == timeSlots.sortedBy(ForecastTimeSlot::startMinuteOfDayLocal)) {
            "timeSlots must be sorted in ascending local time order."
        }
        require(timeSlots.distinct() == timeSlots) {
            "timeSlots must be unique."
        }
        require(surfaceLayer.samples.size == timeSlots.size) {
            "surfaceLayer sample count must match the shared timeSlots size."
        }

        if (altitudeLayers.isNotEmpty()) {
            require(altitudeLayers.first().altitudeMetersAgl == FORECAST_ALTITUDE_STEP_METERS) {
                "The first altitude layer must start at 50 m AGL."
            }
        }

        altitudeLayers.forEachIndexed { index, layer ->
            require(layer.samples.size == timeSlots.size) {
                "Altitude layer sample count must match the shared timeSlots size."
            }

            if (index == 0) {
                return@forEachIndexed
            }

            val previousLayer = altitudeLayers[index - 1]
            require(layer.altitudeMetersAgl - previousLayer.altitudeMetersAgl == FORECAST_ALTITUDE_STEP_METERS) {
                "Altitude layers must use a strict 50 m step without gaps."
            }
        }
    }
}

data class ForecastSurfaceLayerViewData(
    val samples: List<ForecastSlotValuesViewData>,
)

data class ForecastAltitudeLayerViewData(
    val altitudeMetersAgl: Int,
    val samples: List<ForecastSlotValuesViewData>,
) {
    init {
        require(altitudeMetersAgl >= FORECAST_ALTITUDE_STEP_METERS) {
            "Altitude layers must start above the surface."
        }
        require(altitudeMetersAgl % FORECAST_ALTITUDE_STEP_METERS == 0) {
            "Altitude layers must align to the 50 m vertical grid."
        }
    }
}

data class ForecastSlotValuesViewData(
    val values: List<ForecastMetricValueViewData>,
) {
    init {
        val metricIds = values.map { it.metricId }
        require(metricIds.distinct().size == metricIds.size) {
            "Each slot can contain at most one value per metric id."
        }
    }
}

data class ForecastMetricValueViewData(
    val metricId: ForecastMetricId,
    val value: Double,
    val unitLabel: String,
    val displayLabel: String? = null,
) {
    init {
        require(unitLabel.isNotBlank()) {
            "unitLabel must not be blank."
        }
    }
}

@JvmInline
value class ForecastMetricId(
    val value: String,
) {
    init {
        require(value.matches(FORECAST_METRIC_ID_REGEX)) {
            "Forecast metric ids must use lower-case snake-case identifiers."
        }
    }
}

data class ForecastTimeSlot(
    val startMinuteOfDayLocal: Int,
) {
    init {
        require(startMinuteOfDayLocal in 0 until MINUTES_PER_DAY) {
            "Forecast time slots must fit within a single local day."
        }
        require(startMinuteOfDayLocal % FORECAST_TIME_STEP_MINUTES == 0) {
            "Forecast time slots must align to 15-minute boundaries."
        }
    }
}

const val FORECAST_TIME_STEP_MINUTES = 15
const val FORECAST_ALTITUDE_STEP_METERS = 50

private const val MINUTES_PER_DAY = 24 * 60
private val ISO_LOCAL_DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
private val FORECAST_METRIC_ID_REGEX = Regex("""[a-z][a-z0-9_]*""")
