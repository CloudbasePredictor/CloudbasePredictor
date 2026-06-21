package com.cloudbasepredictor.ui.screens.forecast.views

import com.cloudbasepredictor.domain.forecast.ProfileLevel
import com.cloudbasepredictor.domain.forecast.dryAdiabatTempC
import com.cloudbasepredictor.domain.forecast.moistAdiabatTempFromPointC
import com.cloudbasepredictor.domain.forecast.potentialTemperatureK
import com.cloudbasepredictor.ui.screens.forecast.DEFAULT_TOP_ALTITUDE_KM
import com.cloudbasepredictor.ui.screens.forecast.StuveForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.StuveProfilePoint
import com.cloudbasepredictor.ui.screens.forecast.buildParcelAscentPath
import com.cloudbasepredictor.ui.screens.forecast.interpolateProfileHeightMeters
import com.cloudbasepredictor.ui.screens.forecast.interpolateProfileTemperature
import com.cloudbasepredictor.ui.screens.forecast.pressureToApproxHeightMeters
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

internal const val SKEWT_MIN_TOP_PRESSURE = 250f
internal const val SKEWT_BOTTOM_PRESSURE = 1050f
internal const val SKEWT_SKEW_RATIO = 0.45f
private const val STUVE_TEMP_AXIS_HALF_WIDTH_C = 20f

internal val STUVE_DRY_REFERENCE_PRESSURES = listOf(
    1050f, 1000f, 975f, 950f, 925f, 900f, 875f, 850f, 825f, 800f, 775f, 750f, 725f, 700f,
    675f, 650f, 625f, 600f, 575f, 550f, 525f, 500f, 475f, 450f, 425f, 400f, 375f, 350f,
    325f, 300f, 275f, 250f,
)

internal data class SkewTProjection(
    val topPressure: Float,
    val bottomPressure: Float,
    val temperatureRange: TempAxisRange,
    val plotLeft: Float,
    val plotRight: Float,
    val plotTop: Float,
    val plotBottom: Float,
) {
    val plotWidth: Float = plotRight - plotLeft
    val plotHeight: Float = plotBottom - plotTop

    fun pressureToY(pressureHpa: Float): Float = pressureToY(
        pressureHpa = pressureHpa,
        plotTop = plotTop,
        plotBottom = plotBottom,
        topPressure = topPressure,
        bottomPressure = bottomPressure,
    )

    fun yToPressure(y: Float): Float = yToPressure(
        y = y,
        plotTop = plotTop,
        plotBottom = plotBottom,
        topPressure = topPressure,
        bottomPressure = bottomPressure,
    )

    fun temperatureToX(temperatureC: Float, pressureHpa: Float): Float {
        val temperatureSpan = temperatureRange.spanC
        if (plotWidth <= 0f || temperatureSpan <= 0f) return plotLeft

        val normalizedTemperature = (temperatureC - temperatureRange.minC) / temperatureSpan
        val heightFraction = pressureHeightFraction(pressureHpa)
        return plotLeft + (normalizedTemperature + heightFraction * SKEWT_SKEW_RATIO) * plotWidth
    }

    fun xToTemperature(x: Float, pressureHpa: Float): Float {
        val temperatureSpan = temperatureRange.spanC
        if (plotWidth <= 0f || temperatureSpan <= 0f) return temperatureRange.minC

        val heightFraction = pressureHeightFraction(pressureHpa)
        val normalizedTemperature = ((x - plotLeft) / plotWidth) - heightFraction * SKEWT_SKEW_RATIO
        return normalizedTemperature * temperatureSpan + temperatureRange.minC
    }

    private fun pressureHeightFraction(pressureHpa: Float): Float {
        val logPressure = ln(pressureHpa)
        val logBottom = ln(bottomPressure)
        val logTop = ln(topPressure)
        val logSpan = logBottom - logTop
        if (logSpan <= 0f) return 0f
        return ((logBottom - logPressure) / logSpan).coerceIn(0f, 1f)
    }
}

internal fun buildSkewTProjection(
    chart: StuveForecastChartUiModel,
    topPressure: Float,
    bottomPressure: Float,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotBottom: Float,
): SkewTProjection {
    return SkewTProjection(
        topPressure = topPressure,
        bottomPressure = bottomPressure,
        temperatureRange = buildSkewTTemperatureAxisRange(
            chart = chart,
            topPressure = topPressure,
            bottomPressure = bottomPressure,
        ),
        plotLeft = plotLeft,
        plotRight = plotRight,
        plotTop = plotTop,
        plotBottom = plotBottom,
    )
}

internal fun pressureToY(
    pressureHpa: Float,
    plotTop: Float,
    plotBottom: Float,
    topPressure: Float,
    bottomPressure: Float = SKEWT_BOTTOM_PRESSURE,
): Float {
    val logPressure = ln(pressureHpa)
    val logBottom = ln(bottomPressure)
    val logTop = ln(topPressure)
    val fraction = (logPressure - logTop) / (logBottom - logTop)
    return plotTop + fraction * (plotBottom - plotTop)
}

internal fun yToPressure(
    y: Float,
    plotTop: Float,
    plotBottom: Float,
    topPressure: Float,
    bottomPressure: Float = SKEWT_BOTTOM_PRESSURE,
): Float {
    val logBottom = ln(bottomPressure)
    val logTop = ln(topPressure)
    val fraction = ((y - plotTop) / (plotBottom - plotTop)).coerceIn(0f, 1f)
    val logPressure = logTop + fraction * (logBottom - logTop)
    return kotlin.math.exp(logPressure)
}

internal fun altitudeKmToApproxPressureHpa(altitudeKm: Float): Float {
    val heightMeters = (altitudeKm * 1000f).coerceAtLeast(0f)
    return 1013.25f * (1f - 0.0000225577f * heightMeters).pow(5.25588f)
}

internal fun buildReferencePressures(
    bottomPressure: Float,
    topPressure: Float,
    stepHpa: Float,
): List<Float> {
    return buildList {
        var pressure = bottomPressure
        while (pressure >= topPressure) {
            add(pressure)
            pressure -= stepHpa
        }
    }
}

internal fun selectPressureLabels(
    topPressure: Float,
    plotHeight: Float,
): List<Float> {
    val visibleLabels = ISOBAR_LABELS.filter { it >= topPressure }
    return if (visibleLabels.isEmpty()) {
        emptyList()
    } else if (plotHeight / visibleLabels.size >= 28f) {
        visibleLabels
    } else {
        visibleLabels.filter { pressure ->
            pressure.toInt() % 100 == 0 || pressure in listOf(950f, 850f, 700f, 500f, 300f, 250f)
        }
    }
}

internal fun recommendedStuveTopAltitudeKm(chart: StuveForecastChartUiModel): Float {
    val topHeightMeters = chart.temperatureProfile.lastOrNull()?.heightMeters
        ?: chart.dewpointProfile.lastOrNull()?.heightMeters
        ?: chart.windBarbs.minByOrNull { it.pressureHpa }?.let { windBarb ->
            pressureToApproxHeightMeters(windBarb.pressureHpa).toFloat()
        }
        ?: pressureToApproxHeightMeters(
            chart.temperatureProfile.lastOrNull()?.pressureHpa ?: SKEWT_MIN_TOP_PRESSURE,
        ).toFloat()

    return ((topHeightMeters / 1000f) + STUVE_AUTO_FIT_MARGIN_KM)
        .coerceIn(DEFAULT_TOP_ALTITUDE_KM, STUVE_INITIAL_AUTO_FIT_MAX_KM)
}

internal data class TempAxisRange(
    val minC: Float,
    val maxC: Float,
) {
    val spanC: Float get() = maxC - minC
    val centerC: Float get() = (minC + maxC) / 2f
}

internal fun buildVisibleTemperatureAxisRange(
    chart: StuveForecastChartUiModel,
    topPressure: Float,
    bottomPressure: Float,
): TempAxisRange {
    val visibleTemperatures = collectProfileTemperatures(
        profile = chart.temperatureProfile,
        topPressure = topPressure,
        bottomPressure = bottomPressure,
    )
    val visibleDewpoints = collectProfileTemperatures(
        profile = chart.dewpointProfile,
        topPressure = topPressure,
        bottomPressure = bottomPressure,
    )

    if (visibleTemperatures.isEmpty() && visibleDewpoints.isEmpty()) {
        return TempAxisRange(TEMP_MIN, TEMP_MAX)
    }

    val focusTopPressure = maxOf(topPressure, TEMP_AXIS_FOCUS_TOP_PRESSURE_HPA)
    val focusedTemperatures = collectProfileTemperatures(
        profile = chart.temperatureProfile,
        topPressure = focusTopPressure,
        bottomPressure = bottomPressure,
    )
    val focusedDewpoints = collectProfileTemperatures(
        profile = chart.dewpointProfile,
        topPressure = focusTopPressure,
        bottomPressure = bottomPressure,
    )
    val temperatureReference = (focusedTemperatures.ifEmpty { visibleTemperatures }).maxOrNull()
        ?: visibleTemperatures.maxOrNull()
        ?: TEMP_MAX
    val lowerReferenceTemperatures = focusedTemperatures.ifEmpty { visibleTemperatures }
    val lowerReferenceDewpoints = focusedDewpoints.ifEmpty { visibleDewpoints }
    val temperatureMin = lowerReferenceTemperatures.minOrNull() ?: temperatureReference
    val boundedDewpointMin = lowerReferenceDewpoints.minOrNull()
        ?.coerceAtLeast(temperatureMin - TEMP_AXIS_MAX_DEWPOINT_EXTENSION_C)
        ?: temperatureMin
    val heatedSurfaceMax = maxOf(
        chart.temperatureProfile.firstOrNull()?.temperatureC ?: temperatureReference,
        chart.parcelAscentPath.firstOrNull()?.temperatureC ?: temperatureReference,
        chart.tconC ?: temperatureReference,
        temperatureReference,
    )

    val rawMin = minOf(temperatureMin, boundedDewpointMin) - TEMP_AXIS_LEFT_PADDING_C
    val rawMax = heatedSurfaceMax + TEMP_AXIS_RIGHT_PADDING_C
    val span = (rawMax - rawMin).coerceIn(TEMP_AXIS_MIN_SPAN_C, TEMP_AXIS_MAX_SPAN_C)
    val center = (rawMin + rawMax) / 2f
    return TempAxisRange(
        minC = floorToStep(center - span / 2f, TEMP_STEP),
        maxC = ceilToStep(center + span / 2f, TEMP_STEP),
    )
}

internal fun buildSkewTTemperatureAxisRange(
    chart: StuveForecastChartUiModel,
    topPressure: Float,
    bottomPressure: Float,
): TempAxisRange {
    // Deliberate product choice: the on-screen angle of every adiabat and curve stays constant across
    // zoom. That requires the temperature span to scale together with the visible log-pressure span
    // (span ∝ V holds the ratio °C-per-pixel : decade-per-pixel — and thus every line angle — fixed).
    // The accepted trade-off is that the bottom °C scale crops in when zooming in and widens when
    // zooming out. The center is anchored once from the auto-fit view so the diagram crops without
    // sliding sideways; at that reference zoom the width is ±[STUVE_TEMP_AXIS_HALF_WIDTH_C].
    val referenceTopPressure = altitudeKmToApproxPressureHpa(recommendedStuveTopAltitudeKm(chart))
        .coerceIn(SKEWT_MIN_TOP_PRESSURE, bottomPressure - 50f)
    val referenceRange = buildVisibleTemperatureAxisRange(
        chart = chart,
        topPressure = referenceTopPressure,
        bottomPressure = bottomPressure,
    )
    val referenceLogSpan = logPressureSpan(bottomPressure = bottomPressure, topPressure = referenceTopPressure)
    val visibleLogSpan = logPressureSpan(bottomPressure = bottomPressure, topPressure = topPressure)
    val referenceSpanC = STUVE_TEMP_AXIS_HALF_WIDTH_C * 2f
    val span = if (referenceLogSpan > 0f) {
        referenceSpanC * (visibleLogSpan / referenceLogSpan)
    } else {
        referenceSpanC
    }.coerceAtLeast(TEMP_AXIS_MIN_STABLE_SPAN_C)

    return TempAxisRange(
        minC = referenceRange.centerC - span / 2f,
        maxC = referenceRange.centerC + span / 2f,
    )
}

/**
 * The pre-computed dashed parcel guide is hidden while the tap overlay (cursor readout) is showing,
 * so it does not compete with the interactive parcel drawn through the tapped point.
 */
internal fun shouldDrawDefaultParcelGuide(isCursorActive: Boolean): Boolean = !isCursorActive

private fun collectProfileTemperatures(
    profile: List<StuveProfilePoint>,
    topPressure: Float,
    bottomPressure: Float,
): List<Float> = buildList {
    profile
        .filter { it.pressureHpa in topPressure..bottomPressure }
        .forEach { point -> add(point.temperatureC) }
    interpolateProfileTemperature(profile, topPressure)?.let(::add)
    interpolateProfileTemperature(profile, bottomPressure)?.let(::add)
}

private fun logPressureSpan(
    bottomPressure: Float,
    topPressure: Float,
): Float {
    if (bottomPressure <= 0f || topPressure <= 0f || bottomPressure <= topPressure) {
        return 0f
    }
    return ln(bottomPressure) - ln(topPressure)
}

internal fun buildTemperatureAxisLabels(range: TempAxisRange): List<Float> {
    val labels = mutableListOf<Float>()
    val step = temperatureAxisStep(range.spanC)
    var value = ceilToStep(range.minC, step)
    while (value <= range.maxC + 0.01f) {
        labels += value
        value += step
    }
    return labels.ifEmpty { listOf(range.minC, range.maxC) }
}

private fun temperatureAxisStep(spanC: Float): Float = when {
    spanC <= 8f -> 1f
    spanC <= 18f -> 2f
    spanC <= 34f -> 5f
    else -> TEMP_STEP
}

/**
 * Constructs a minimal list of [ProfileLevel] objects from the chart's temperature and
 * dewpoint profiles. Used as input to [buildParcelAscentPath] for live parcel recomputation.
 */
internal fun buildMinimalProfileLevels(
    chart: StuveForecastChartUiModel,
): List<ProfileLevel> = chart.temperatureProfile.map { point ->
    ProfileLevel(
        pressureHpa = point.pressureHpa,
        temperatureC = point.temperatureC,
        dewPointC = interpolateProfileTemperature(chart.dewpointProfile, point.pressureHpa),
        heightKm = (point.heightMeters ?: pressureToApproxHeightMeters(point.pressureHpa).toFloat()) / 1000f,
    )
}

/**
 * Builds a parcel guide anchored to a specific chart point selected by the user.
 *
 * The branch below the anchor follows the dry adiabat through the selected point, and the branch
 * above the anchor follows the moist adiabat through the same point. This guarantees that the
 * rendered interactive guide passes through the clicked point and visually separates the dry and
 * moist regimes around that anchor.
 */
internal fun buildInteractiveParcelFromPoint(
    anchorTemperatureC: Float,
    anchorPressureHpa: Float,
    chart: StuveForecastChartUiModel,
    parcelPressures: List<Float>,
): List<StuveProfilePoint> {
    val dryThetaK = potentialTemperatureK(anchorTemperatureC, anchorPressureHpa)
    val denseReferencePressures = buildReferencePressures(
        bottomPressure = maxOf(anchorPressureHpa, parcelPressures.maxOrNull() ?: anchorPressureHpa),
        topPressure = minOf(anchorPressureHpa, parcelPressures.minOrNull() ?: anchorPressureHpa),
        stepHpa = 10f,
    )
    val renderablePressures = (parcelPressures + denseReferencePressures + anchorPressureHpa)
        .distinct()
        .sortedDescending()

    return renderablePressures.map { pressure ->
        val heightMeters = interpolateProfileHeightMeters(chart.temperatureProfile, pressure)
            ?: pressureToApproxHeightMeters(pressure).toFloat()
        val temperatureC = if (pressure >= anchorPressureHpa) {
            dryAdiabatTempC(dryThetaK, pressure)
        } else {
            moistAdiabatTempFromPointC(
                startTemperatureC = anchorTemperatureC,
                startPressureHpa = anchorPressureHpa,
                targetPressureHpa = pressure,
            )
        }
        StuveProfilePoint(
            pressureHpa = pressure,
            temperatureC = temperatureC,
            heightMeters = heightMeters,
        )
    }
}

/**
 * Builds a live parcel ascent path starting at [parcelStartTempC] at the surface pressure.
 * Used when the bottom heating handle is dragged.
 */
internal fun buildInteractiveParcelFromSurface(
    parcelStartTempC: Float,
    chart: StuveForecastChartUiModel,
    profileLevels: List<ProfileLevel>,
    parcelPressures: List<Float>,
): List<StuveProfilePoint> {
    val surfaceDewPointC = chart.dewpointProfile.firstOrNull()?.temperatureC
        ?: (parcelStartTempC - 8f)
    return buildParcelAscentPath(
        pressures = parcelPressures,
        profile = profileLevels,
        surfaceTemperatureC = parcelStartTempC,
        surfaceDewPointC = surfaceDewPointC,
        surfacePressureHpa = chart.surfacePressureHpa,
        surfaceHeatingC = 0f,
    )
}

internal fun floorToStep(value: Float, step: Float): Float =
    floor(value / step) * step

private fun ceilToStep(value: Float, step: Float): Float =
    ceil(value / step) * step

private const val TEMP_MIN = -30f
private const val TEMP_MAX = 40f
internal const val TEMP_STEP = 10f
private const val STUVE_AUTO_FIT_MARGIN_KM = 0.35f
private const val TEMP_AXIS_FOCUS_TOP_PRESSURE_HPA = 650f
private const val TEMP_AXIS_LEFT_PADDING_C = 6f
private const val TEMP_AXIS_RIGHT_PADDING_C = 10f
private const val TEMP_AXIS_MIN_SPAN_C = 34f
private const val TEMP_AXIS_MIN_STABLE_SPAN_C = 6f
private const val TEMP_AXIS_MAX_SPAN_C = 48f
private const val TEMP_AXIS_MAX_DEWPOINT_EXTENSION_C = 14f
private const val STUVE_INITIAL_AUTO_FIT_MAX_KM = 6.5f

private val ISOBAR_LABELS = listOf(
    1000f, 950f, 900f, 850f, 800f, 750f, 700f, 650f,
    600f, 550f, 500f, 450f, 400f, 350f, 300f, 250f,
)
