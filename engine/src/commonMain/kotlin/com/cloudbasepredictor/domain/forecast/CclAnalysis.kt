package com.cloudbasepredictor.domain.forecast

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

enum class CclMethod(val id: String) {
    SURFACE("surface"),
    ML50("ml50"),
    ML100("ml100"),
}

enum class CclIntersectionType {
    BOTTOM,
    INTERMEDIATE,
    TOP,
}

data class CclPressureLevel(
    val pressureHpa: Float,
    val temperatureC: Float,
    val dewPointC: Float?,
    val heightMslM: Float?,
    val isSynthetic: Boolean = false,
)

data class CclHourlyInput(
    val time: String,
    val surfaceTemperatureC: Float,
    val surfaceDewPointC: Float,
    val surfacePressureHpa: Float,
    val surfaceElevationM: Float,
    val pressureLevels: List<CclPressureLevel>,
    val takeoffElevationM: Float? = null,
)

data class CclIntersection(
    val pressureHpa: Float,
    val temperatureC: Float,
    val heightMslM: Float,
    val heightAglGridM: Float,
    val heightAboveTakeoffM: Float?,
    val type: CclIntersectionType,
)

data class CclHourlyResult(
    val time: String,
    val method: CclMethod,
    val cclPressureHpa: Float?,
    val cclTemperatureC: Float?,
    val cclHeightMslM: Float?,
    val cclHeightAglGridM: Float?,
    val cclHeightAboveTakeoffM: Float?,
    val convectiveTemperatureC: Float?,
    val temperature2mC: Float,
    val heatingMarginC: Float?,
    val reachable: Boolean,
    val intersections: List<CclIntersection>,
    val warnings: List<String>,
)

fun analyzeCclHourly(input: CclHourlyInput): List<CclHourlyResult> {
    val profile = buildCclProfile(input)
    return listOf(
        analyzeCclMethod(
            input = input,
            profile = profile,
            method = CclMethod.SURFACE,
            mixingRatioKgKg = mixingRatioFromDewPointKgKg(input.surfacePressureHpa, input.surfaceDewPointC),
            humidityWarning = null,
        ),
        analyzeCclMethod(
            input = input,
            profile = profile,
            method = CclMethod.ML50,
            mixingRatioKgKg = mixedLayerMixingRatioKgKg(input, MIXED_LAYER_50_HPA),
            humidityWarning = "Mixed-layer humidity unavailable: insufficient real dewpoint data in the lowest 50 hPa",
        ),
        analyzeCclMethod(
            input = input,
            profile = profile,
            method = CclMethod.ML100,
            mixingRatioKgKg = mixedLayerMixingRatioKgKg(input, MIXED_LAYER_100_HPA),
            humidityWarning = "Mixed-layer humidity unavailable: insufficient real dewpoint data in the lowest 100 hPa",
        ),
    )
}

fun List<CclHourlyResult>.primaryCclResult(): CclHourlyResult? {
    return firstOrNull { it.method == CclMethod.ML50 && it.cclPressureHpa != null }
        ?: firstOrNull { it.method == CclMethod.SURFACE && it.cclPressureHpa != null }
        ?: firstOrNull { it.method == CclMethod.ML50 }
        ?: firstOrNull { it.method == CclMethod.SURFACE }
}

internal fun mixedLayerMixingRatioKgKg(
    input: CclHourlyInput,
    depthHpa: Float,
): Float? {
    val pressureProfile = buildCclProfile(input)
    val vaporSamples = pressureProfile
        .mapNotNull { level ->
            level.dewPointC?.let { dewPoint ->
                PressureMixingRatio(
                    pressureHpa = level.pressureHpa,
                    mixingRatioKgKg = mixingRatioFromDewPointKgKg(level.pressureHpa, dewPoint),
                )
            }
        }
        .sortedByDescending(PressureMixingRatio::pressureHpa)

    if (vaporSamples.size < 2) return null

    val surfacePressure = input.surfacePressureHpa
    val topPressure = surfacePressure - depthHpa
    val layerSamples = mutableListOf<PressureMixingRatio>()
    layerSamples += vaporSamples.first()

    vaporSamples
        .drop(1)
        .filter { it.pressureHpa < surfacePressure && it.pressureHpa > topPressure + PRESSURE_EPSILON_HPA }
        .forEach { layerSamples += it }

    interpolatedMixingRatioAtPressure(vaporSamples, topPressure)?.let { topSample ->
        layerSamples += topSample
        return trapezoidalPressureMean(layerSamples.sortedByDescending(PressureMixingRatio::pressureHpa))
    }

    val fallbackUpper = vaporSamples.drop(1).firstOrNull { sample ->
        val delta = surfacePressure - sample.pressureHpa
        delta >= FALLBACK_MIN_DEPTH_HPA && delta <= FALLBACK_MAX_DEPTH_HPA
    } ?: return null

    return trapezoidalPressureMean(listOf(vaporSamples.first(), fallbackUpper))
}

internal fun cclMixingRatioTemperatureC(
    mixingRatioKgKg: Float,
    pressureHpa: Float,
): Float {
    val vaporPressureHpa = pressureHpa * mixingRatioKgKg / (CCL_EPSILON + mixingRatioKgKg)
    return inverseSaturationVaporPressureC(vaporPressureHpa)
}

internal fun mixingRatioFromDewPointKgKg(
    pressureHpa: Float,
    dewPointC: Float,
): Float {
    val vaporPressureHpa = saturationVaporPressureHpa(dewPointC)
    return CCL_EPSILON * vaporPressureHpa / (pressureHpa - vaporPressureHpa).coerceAtLeast(0.01f)
}

private fun analyzeCclMethod(
    input: CclHourlyInput,
    profile: List<CclProfileLevel>,
    method: CclMethod,
    mixingRatioKgKg: Float?,
    humidityWarning: String?,
): CclHourlyResult {
    val baseWarnings = mutableListOf<String>()
    val surfaceNearSaturation = input.surfaceTemperatureC - input.surfaceDewPointC <= SURFACE_SATURATION_SPREAD_C
    if (surfaceNearSaturation) {
        baseWarnings += WARNING_SURFACE_NEAR_SATURATION
    }

    if (mixingRatioKgKg == null) {
        humidityWarning?.let { baseWarnings += it }
        return emptyCclResult(input = input, method = method, warnings = baseWarnings)
    }

    if (method == CclMethod.SURFACE && surfaceNearSaturation) {
        val surfaceIntersection = CclIntersection(
            pressureHpa = input.surfacePressureHpa,
            temperatureC = input.surfaceTemperatureC,
            heightMslM = input.surfaceElevationM,
            heightAglGridM = 0f,
            heightAboveTakeoffM = input.takeoffElevationM?.let { input.surfaceElevationM - it },
            type = CclIntersectionType.BOTTOM,
        )
        return resultFromIntersections(
            input = input,
            method = method,
            intersections = listOf(surfaceIntersection),
            warnings = baseWarnings,
        )
    }

    if (profile.size < 2) {
        return emptyCclResult(
            input = input,
            method = method,
            warnings = baseWarnings + WARNING_NO_CCL,
        )
    }

    val intersections = findCclIntersections(
        input = input,
        profile = profile,
        mixingRatioKgKg = mixingRatioKgKg,
    )

    if (intersections.isEmpty()) {
        return emptyCclResult(
            input = input,
            method = method,
            warnings = baseWarnings + WARNING_NO_CCL,
        )
    }

    return resultFromIntersections(
        input = input,
        method = method,
        intersections = intersections,
        warnings = baseWarnings,
    )
}

private fun resultFromIntersections(
    input: CclHourlyInput,
    method: CclMethod,
    intersections: List<CclIntersection>,
    warnings: List<String>,
): CclHourlyResult {
    val sortedIntersections = intersections.sortedByDescending(CclIntersection::pressureHpa)
    val bottom = sortedIntersections.first()
    val convectiveTemperatureC = convectiveTemperatureC(
        cclTemperatureC = bottom.temperatureC,
        cclPressureHpa = bottom.pressureHpa,
        surfacePressureHpa = input.surfacePressureHpa,
    )
    val heatingMarginC = input.surfaceTemperatureC - convectiveTemperatureC
    val reachable = input.surfaceTemperatureC >= convectiveTemperatureC - REACHABLE_TOLERANCE_C
    val resultWarnings = warnings.toMutableList()

    if (sortedIntersections.size > 1) {
        resultWarnings += WARNING_MULTIPLE_INTERSECTIONS
    }
    if (!reachable) {
        resultWarnings += WARNING_THEORETICAL_ONLY
    }
    if (bottom.heightAglGridM < LOW_CCL_AGL_M) {
        resultWarnings += WARNING_VERY_LOW_CCL
    }
    val ceilingReferenceM = bottom.heightAboveTakeoffM ?: bottom.heightAglGridM
    if (ceilingReferenceM > HIGH_CCL_ABOVE_REFERENCE_M) {
        resultWarnings += WARNING_HIGH_CCL
    }
    if ((bottom.heightAboveTakeoffM ?: Float.MAX_VALUE) <= LOW_CCL_AGL_M) {
        resultWarnings += WARNING_CCL_NEAR_TAKEOFF
    }

    return CclHourlyResult(
        time = input.time,
        method = method,
        cclPressureHpa = bottom.pressureHpa,
        cclTemperatureC = bottom.temperatureC,
        cclHeightMslM = bottom.heightMslM,
        cclHeightAglGridM = bottom.heightAglGridM,
        cclHeightAboveTakeoffM = bottom.heightAboveTakeoffM,
        convectiveTemperatureC = convectiveTemperatureC,
        temperature2mC = input.surfaceTemperatureC,
        heatingMarginC = heatingMarginC,
        reachable = reachable,
        intersections = sortedIntersections,
        warnings = resultWarnings.distinct(),
    )
}

private fun emptyCclResult(
    input: CclHourlyInput,
    method: CclMethod,
    warnings: List<String>,
): CclHourlyResult {
    return CclHourlyResult(
        time = input.time,
        method = method,
        cclPressureHpa = null,
        cclTemperatureC = null,
        cclHeightMslM = null,
        cclHeightAglGridM = null,
        cclHeightAboveTakeoffM = null,
        convectiveTemperatureC = null,
        temperature2mC = input.surfaceTemperatureC,
        heatingMarginC = null,
        reachable = false,
        intersections = emptyList(),
        warnings = warnings.distinct(),
    )
}

private fun buildCclProfile(input: CclHourlyInput): List<CclProfileLevel> {
    val surface = CclProfileLevel(
        pressureHpa = input.surfacePressureHpa,
        temperatureC = input.surfaceTemperatureC,
        dewPointC = input.surfaceDewPointC,
        heightMslM = input.surfaceElevationM,
    )
    val pressureLevels = input.pressureLevels
        .asSequence()
        .filter { !it.isSynthetic }
        .filter { it.pressureHpa >= MIN_CCL_PRESSURE_HPA }
        .filter { it.pressureHpa < input.surfacePressureHpa - PRESSURE_LEVEL_BELOW_SURFACE_MARGIN_HPA }
        .filter { it.heightMslM != null }
        .filter { it.heightMslM!! > input.surfaceElevationM + HEIGHT_ABOVE_SURFACE_MARGIN_M }
        .map { level ->
            CclProfileLevel(
                pressureHpa = level.pressureHpa,
                temperatureC = level.temperatureC,
                dewPointC = level.dewPointC,
                heightMslM = level.heightMslM!!,
            )
        }
        .sortedByDescending(CclProfileLevel::pressureHpa)
        .distinctBy { (it.pressureHpa * 10f).toInt() }
        .toList()

    return (listOf(surface) + pressureLevels).sortedByDescending(CclProfileLevel::pressureHpa)
}

private fun findCclIntersections(
    input: CclHourlyInput,
    profile: List<CclProfileLevel>,
    mixingRatioKgKg: Float,
): List<CclIntersection> {
    val candidates = mutableListOf<CclIntersectionCandidate>()
    var previous: CclProfileLevel? = null
    var previousDifference: Float? = null

    profile.forEach { level ->
        val mixingRatioTemperatureC = cclMixingRatioTemperatureC(mixingRatioKgKg, level.pressureHpa)
        val difference = level.temperatureC - mixingRatioTemperatureC

        if (abs(difference) <= ZERO_CROSSING_EPSILON_C) {
            candidates.addIfDistinct(level.toIntersectionCandidate())
        }

        val previousLevel = previous
        val previousDiff = previousDifference
        if (previousLevel != null && previousDiff != null && previousDiff * difference < 0f) {
            candidates.addIfDistinct(
                interpolateIntersectionCandidate(
                    input = input,
                    lower = previousLevel,
                    upper = level,
                    lowerDifference = previousDiff,
                    upperDifference = difference,
                ),
            )
        }

        previous = level
        previousDifference = difference
    }

    val sortedCandidates = candidates.sortedByDescending(CclIntersectionCandidate::pressureHpa)
    return sortedCandidates.mapIndexed { index, candidate ->
        val type = when {
            index == 0 -> CclIntersectionType.BOTTOM
            index == sortedCandidates.lastIndex -> CclIntersectionType.TOP
            else -> CclIntersectionType.INTERMEDIATE
        }
        candidate.toIntersection(input, type)
    }
}

private fun MutableList<CclIntersectionCandidate>.addIfDistinct(candidate: CclIntersectionCandidate) {
    if (none { abs(it.pressureHpa - candidate.pressureHpa) < DISTINCT_PRESSURE_EPSILON_HPA }) {
        add(candidate)
    }
}

private fun CclProfileLevel.toIntersectionCandidate(): CclIntersectionCandidate {
    return CclIntersectionCandidate(
        pressureHpa = pressureHpa,
        temperatureC = temperatureC,
        heightMslM = heightMslM,
    )
}

private fun interpolateIntersectionCandidate(
    input: CclHourlyInput,
    lower: CclProfileLevel,
    upper: CclProfileLevel,
    lowerDifference: Float,
    upperDifference: Float,
): CclIntersectionCandidate {
    val alpha = (-lowerDifference / (upperDifference - lowerDifference)).coerceIn(0f, 1f)
    val lowerLnPressure = ln(lower.pressureHpa)
    val upperLnPressure = ln(upper.pressureHpa)
    val pressureHpa = exp(lowerLnPressure + alpha * (upperLnPressure - lowerLnPressure))
    val temperatureC = lower.temperatureC + alpha * (upper.temperatureC - lower.temperatureC)
    val heightMslM = lower.heightMslM + alpha * (upper.heightMslM - lower.heightMslM)
    return CclIntersectionCandidate(
        pressureHpa = pressureHpa,
        temperatureC = temperatureC,
        heightMslM = heightMslM,
    )
}

private fun CclIntersectionCandidate.toIntersection(
    input: CclHourlyInput,
    type: CclIntersectionType,
): CclIntersection {
    return CclIntersection(
        pressureHpa = pressureHpa,
        temperatureC = temperatureC,
        heightMslM = heightMslM,
        heightAglGridM = heightMslM - input.surfaceElevationM,
        heightAboveTakeoffM = input.takeoffElevationM?.let { heightMslM - it },
        type = type,
    )
}

private fun interpolatedMixingRatioAtPressure(
    samples: List<PressureMixingRatio>,
    targetPressureHpa: Float,
): PressureMixingRatio? {
    samples.firstOrNull { abs(it.pressureHpa - targetPressureHpa) < PRESSURE_EPSILON_HPA }?.let {
        return PressureMixingRatio(targetPressureHpa, it.mixingRatioKgKg)
    }
    for (index in 0 until samples.size - 1) {
        val lower = samples[index]
        val upper = samples[index + 1]
        if (targetPressureHpa <= lower.pressureHpa && targetPressureHpa >= upper.pressureHpa) {
            val fraction = (lower.pressureHpa - targetPressureHpa) /
                (lower.pressureHpa - upper.pressureHpa)
            return PressureMixingRatio(
                pressureHpa = targetPressureHpa,
                mixingRatioKgKg = lower.mixingRatioKgKg +
                    fraction * (upper.mixingRatioKgKg - lower.mixingRatioKgKg),
            )
        }
    }
    return null
}

private fun trapezoidalPressureMean(samples: List<PressureMixingRatio>): Float? {
    if (samples.size < 2) return null
    var integral = 0f
    var pressureDepth = 0f
    for (index in 0 until samples.size - 1) {
        val lower = samples[index]
        val upper = samples[index + 1]
        val deltaPressure = lower.pressureHpa - upper.pressureHpa
        if (deltaPressure <= 0f) continue
        integral += ((lower.mixingRatioKgKg + upper.mixingRatioKgKg) / 2f) * deltaPressure
        pressureDepth += deltaPressure
    }
    return if (pressureDepth > PRESSURE_EPSILON_HPA) integral / pressureDepth else null
}

private fun convectiveTemperatureC(
    cclTemperatureC: Float,
    cclPressureHpa: Float,
    surfacePressureHpa: Float,
): Float {
    return (cclTemperatureC + KELVIN_OFFSET) *
        (surfacePressureHpa / cclPressureHpa).pow(CCL_KAPPA) -
        KELVIN_OFFSET
}

private fun saturationVaporPressureHpa(temperatureC: Float): Float {
    return 6.112f * exp(17.67f * temperatureC / (temperatureC + 243.5f))
}

private fun inverseSaturationVaporPressureC(vaporPressureHpa: Float): Float {
    val lnArg = ln(vaporPressureHpa / 6.112f)
    return 243.5f * lnArg / (17.67f - lnArg)
}

private data class CclProfileLevel(
    val pressureHpa: Float,
    val temperatureC: Float,
    val dewPointC: Float?,
    val heightMslM: Float,
)

private data class PressureMixingRatio(
    val pressureHpa: Float,
    val mixingRatioKgKg: Float,
)

private data class CclIntersectionCandidate(
    val pressureHpa: Float,
    val temperatureC: Float,
    val heightMslM: Float,
)

private const val CCL_EPSILON = 0.622f
private const val CCL_KAPPA = 0.2854f
private const val KELVIN_OFFSET = 273.15f
private const val MIN_CCL_PRESSURE_HPA = 500f
private const val PRESSURE_LEVEL_BELOW_SURFACE_MARGIN_HPA = 1f
private const val HEIGHT_ABOVE_SURFACE_MARGIN_M = 20f
private const val MIXED_LAYER_50_HPA = 50f
private const val MIXED_LAYER_100_HPA = 100f
private const val FALLBACK_MIN_DEPTH_HPA = 50f
private const val FALLBACK_MAX_DEPTH_HPA = 100f
private const val SURFACE_SATURATION_SPREAD_C = 0.5f
private const val REACHABLE_TOLERANCE_C = 0.5f
private const val LOW_CCL_AGL_M = 300f
private const val HIGH_CCL_ABOVE_REFERENCE_M = 2500f
private const val ZERO_CROSSING_EPSILON_C = 0.001f
private const val DISTINCT_PRESSURE_EPSILON_HPA = 0.05f
private const val PRESSURE_EPSILON_HPA = 0.001f

private const val WARNING_SURFACE_NEAR_SATURATION = "Surface near saturation: fog/low cloud possible"
private const val WARNING_NO_CCL =
    "No CCL found below 500 hPa: likely very high cloud base, blue thermals, or insufficient profile depth"
private const val WARNING_MULTIPLE_INTERSECTIONS =
    "Layered profile/inversion. First Cu base may be near lowest CCL, but cloud-base estimate is uncertain."
private const val WARNING_THEORETICAL_ONLY =
    "CCL theoretical only; surface heating is insufficient. Cu unlikely, blue thermals possible."
private const val WARNING_VERY_LOW_CCL = "Very low CCL: fog/stratus/covered slopes risk."
private const val WARNING_HIGH_CCL =
    "High CCL: good ceiling if reachable, but likely dry/blue sections and harder thermal finding."
private const val WARNING_CCL_NEAR_TAKEOFF =
    "CCL below/near takeoff: fog/low cloud or covered slope risk."
