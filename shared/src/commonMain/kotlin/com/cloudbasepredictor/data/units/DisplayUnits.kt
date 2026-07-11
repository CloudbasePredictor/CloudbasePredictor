package com.cloudbasepredictor.data.units

import com.cloudbasepredictor.util.toFixedDecimalString
import kotlin.math.roundToInt

enum class UnitPreset {
    METRIC_KMH,
    METRIC_MPS,
    IMPERIAL,
    AVIATION,
}

enum class WindSpeedUnit(val label: String) {
    KMH("km/h"),
    MPS("m/s"),
    MPH("mph"),
    KT("kt"),
}

enum class AltitudeUnit(val shortLabel: String) {
    METERS("m"),
    FEET("ft"),
}

enum class VerticalSpeedUnit(val label: String) {
    MPS("m/s"),
    FPM("ft/min"),
}

data class DisplayUnits(
    val windSpeed: WindSpeedUnit,
    val altitude: AltitudeUnit,
    val verticalSpeed: VerticalSpeedUnit,
)

fun UnitPreset.resolveDisplayUnits(): DisplayUnits {
    return when (this) {
        UnitPreset.METRIC_KMH -> metricKmhUnits
        UnitPreset.METRIC_MPS -> metricMpsUnits
        UnitPreset.IMPERIAL -> imperialUnits
        UnitPreset.AVIATION -> aviationUnits
    }
}

fun formatWindSpeed(
    speedKmh: Float,
    units: DisplayUnits,
    withUnit: Boolean = true,
): String {
    val converted = convertWindSpeedKmh(speedKmh, units.windSpeed)
    val value = when (units.windSpeed) {
        WindSpeedUnit.KMH,
        WindSpeedUnit.MPH,
        WindSpeedUnit.KT,
        -> converted.roundToInt().toString()
        WindSpeedUnit.MPS -> converted.toDouble().toFixedDecimalString(1)
    }
    return if (withUnit) "$value ${units.windSpeed.label}" else value
}

fun formatAltitudeKm(
    altitudeKm: Float,
    units: DisplayUnits,
    compact: Boolean = false,
    withUnit: Boolean = true,
): String {
    return when (units.altitude) {
        AltitudeUnit.METERS -> {
            val value = if (altitudeKm >= 1f) {
                altitudeKm.toDouble().toFixedDecimalString(1)
            } else {
                (altitudeKm * 1000f).roundToInt().toString()
            }
            if (withUnit) {
                if (altitudeKm >= 1f) {
                    if (compact) "${value}km" else "$value km"
                } else {
                    if (compact) "${value}m" else "$value m"
                }
            } else {
                value
            }
        }
        AltitudeUnit.FEET -> {
            val feet = altitudeKm * FEET_PER_KILOMETER
            val useKft = compact || feet >= 10_000f
            val value = if (useKft) {
                (feet / 1000f).toDouble().toFixedDecimalString(1)
            } else {
                feet.roundToInt().toString()
            }
            if (withUnit) {
                if (useKft) {
                    if (compact) "${value}kft" else "$value kft"
                } else {
                    if (compact) "${value}ft" else "$value ft"
                }
            } else {
                value
            }
        }
    }
}

fun formatAltitudeMeters(
    altitudeMeters: Float,
    units: DisplayUnits,
    compact: Boolean = false,
    withUnit: Boolean = true,
): String = formatAltitudeKm(
    altitudeKm = altitudeMeters / 1000f,
    units = units,
    compact = compact,
    withUnit = withUnit,
)

fun formatAltitudeAxisValue(
    altitudeKm: Float,
    units: DisplayUnits,
): String {
    return when (units.altitude) {
        AltitudeUnit.METERS -> altitudeKm.toDouble().toFixedDecimalString(1)
        AltitudeUnit.FEET ->
            (altitudeKm * FEET_PER_KILOMETER / 1000f).toDouble().toFixedDecimalString(1)
    }
}

fun altitudeAxisUnitLabel(units: DisplayUnits): String {
    return when (units.altitude) {
        AltitudeUnit.METERS -> "km"
        AltitudeUnit.FEET -> "kft"
    }
}

fun formatVerticalSpeed(
    speedMps: Float,
    units: DisplayUnits,
    withUnit: Boolean = true,
): String {
    val value = when (units.verticalSpeed) {
        VerticalSpeedUnit.MPS -> speedMps.toDouble().toFixedDecimalString(1)
        VerticalSpeedUnit.FPM -> (speedMps * FEET_PER_METER * SECONDS_PER_MINUTE).roundToInt().toString()
    }
    return if (withUnit) "$value ${units.verticalSpeed.label}" else value
}

fun formatVerticalSpeedRange(
    lowMps: Float,
    highMps: Float,
    units: DisplayUnits,
    withUnit: Boolean = true,
): String {
    val low = formatVerticalSpeed(lowMps, units, withUnit = false)
    val high = formatVerticalSpeed(highMps, units, withUnit = false)
    val value = if (kotlin.math.abs(highMps - lowMps) < 0.05f) high else "$low-$high"
    return if (withUnit) "$value ${units.verticalSpeed.label}" else value
}

fun convertWindSpeedKmh(speedKmh: Float, unit: WindSpeedUnit): Float {
    return when (unit) {
        WindSpeedUnit.KMH -> speedKmh
        WindSpeedUnit.MPS -> speedKmh / 3.6f
        WindSpeedUnit.MPH -> speedKmh * MILES_PER_KILOMETER
        WindSpeedUnit.KT -> speedKmh * KNOTS_PER_KMH
    }
}

private val metricKmhUnits = DisplayUnits(
    windSpeed = WindSpeedUnit.KMH,
    altitude = AltitudeUnit.METERS,
    verticalSpeed = VerticalSpeedUnit.MPS,
)

private val metricMpsUnits = DisplayUnits(
    windSpeed = WindSpeedUnit.MPS,
    altitude = AltitudeUnit.METERS,
    verticalSpeed = VerticalSpeedUnit.MPS,
)

private val imperialUnits = DisplayUnits(
    windSpeed = WindSpeedUnit.MPH,
    altitude = AltitudeUnit.FEET,
    verticalSpeed = VerticalSpeedUnit.FPM,
)

private val aviationUnits = DisplayUnits(
    windSpeed = WindSpeedUnit.KT,
    altitude = AltitudeUnit.FEET,
    verticalSpeed = VerticalSpeedUnit.FPM,
)

private const val FEET_PER_METER = 3.28084f
private const val FEET_PER_KILOMETER = 3280.84f
private const val SECONDS_PER_MINUTE = 60f
private const val MILES_PER_KILOMETER = 0.621371f
private const val KNOTS_PER_KMH = 0.539957f
