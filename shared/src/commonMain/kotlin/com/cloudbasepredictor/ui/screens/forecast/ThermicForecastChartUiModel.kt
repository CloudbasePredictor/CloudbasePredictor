@file:Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber", "ReturnCount")

package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.domain.forecast.ThermalForecastConfidence
import com.cloudbasepredictor.domain.forecast.ThermalCloudBaseStatus
import com.cloudbasepredictor.domain.forecast.ThermalForecastWarning
import com.cloudbasepredictor.domain.forecast.ThermalLayerSourceQuality
import com.cloudbasepredictor.domain.forecast.ThermalLimitingReason
import com.cloudbasepredictor.domain.forecast.ThermalSourceLevel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * UI model for the thermic forecast chart.
 *
 * Shows thermal strength grid for each time slot × altitude band,
 * diagnostic lines for dry thermal top / cloud base / moist top,
 * and cloud markers where cumulus formation is expected.
 */
data class ThermicForecastChartUiModel(
    /** Local time slots as minute-of-day (e.g. 360 = 06:00, 1320 = 22:00). */
    val timeSlots: List<Int>,
    /** Grid cells with thermal strength per time × altitude band. */
    val cells: List<ThermicForecastCellUiModel>,
    /** Points where cumulus cloud formation is predicted. */
    val cloudMarkers: List<ThermicForecastCloudMarkerUiModel>,
    /** Per-time-slot diagnostic data from parcel analysis. */
    val slotDiagnostics: List<ThermicSlotDiagnostics> = emptyList(),
    /** Real pressure-level altitudes available for the selected day, km ASL. */
    val pressureLevelAltitudesKm: List<Float> = emptyList(),
)

/**
 * Per-time-slot diagnostic data from the soaring thermal forecast.
 * Used for drawing top-range / cloud-base / moist-top lines and tooltip info.
 */
data class ThermicSlotDiagnostics(
    /** Time slot, minute-of-day in local time. */
    val startMinuteOfDayLocal: Int,
    /** Legacy dry thermal top altitude, km ASL. For the new thermic chart this equals [topNominalKm]. */
    val dryThermalTopKm: Float,
    /** Conservative thermal top estimate, km ASL. */
    val topLowKm: Float = dryThermalTopKm,
    /** Nominal thermal top estimate, km ASL. */
    val topNominalKm: Float = dryThermalTopKm,
    /** Optimistic thermal top estimate, km ASL. */
    val topHighKm: Float = dryThermalTopKm,
    /** Maximum conservative vertical air updraft, m/s. */
    val updraftLowMps: Float = 0f,
    /** Maximum nominal vertical air updraft, m/s. */
    val updraftNominalMps: Float = 0f,
    /** Maximum optimistic vertical air updraft, m/s. */
    val updraftHighMps: Float = 0f,
    /** Forecast confidence derived from raw vertical resolution and missing diagnostics. */
    val confidence: ThermalForecastConfidence = ThermalForecastConfidence.MEDIUM,
    /** Main limiting factor for thermal usability. */
    val limitingReason: ThermalLimitingReason = ThermalLimitingReason.MISSING_DATA,
    /** Lower raw source pressure level bracketing the top, hPa. */
    val topLowerPressureHpa: Float? = null,
    /** Upper raw source pressure level bracketing the top, hPa. */
    val topUpperPressureHpa: Float? = null,
    /** Cloud base altitude, km ASL. Null if cumulus not expected. */
    val cloudBaseKm: Float?,
    /** Moist/cloud equilibrium top, km ASL. Null if no moist convection. */
    val moistEquilibriumTopKm: Float?,
    /** Model-supplied CAPE, J/kg. */
    val modelCapeJKg: Float?,
    /** Model-supplied convective inhibition, J/kg. */
    val modelCinJKg: Float? = null,
    /** Model CIN normalized to positive inhibition, J/kg. */
    val normalizedCinJKg: Float? = null,
    /** Model-supplied lifted index, °C. */
    val liftedIndexC: Float? = null,
    /** Model-supplied boundary-layer height, metres above ground. */
    val boundaryLayerHeightM: Float? = null,
    /** Local thermal-bubble trigger excess above T2m, °C. */
    val triggerExcessC: Float = 0f,
    /** Nominal mixed-layer excess used for dry thermal top, °C. */
    val dryTopExcessC: Float = 0f,
    /** Time-smoothed shortwave radiation used for heating, W/m². */
    val effectiveRadiationWm2: Float? = null,
    /** Surface 2 m temperature used by the thermal forecast, °C. */
    val surfaceTemperatureC: Float? = null,
    /** Surface pressure used by the thermal forecast, hPa. */
    val surfacePressureHpa: Float? = null,
    /** Launch elevation, km ASL. */
    val elevationKm: Float? = null,
    /** Nominal dry-top parcel start temperature, °C. */
    val parcelStartTemperatureC: Float? = null,
    /** Nominal dry thermal top above launch elevation, metres AGL. */
    val dryTopAglM: Float? = null,
    /** Legacy computed-energy field retained for compatibility; not used as model CAPE or a strength multiplier. */
    val computedCapeJKg: Float,
    /** Computed CIN from parcel analysis, J/kg. */
    val computedCinJKg: Float,
    /** LCL altitude, km ASL. */
    val lclKm: Float,
    /** CCL altitude, km ASL. Null if unavailable or not reachable. */
    val cclKm: Float?,
    /** Cloud-base reachability and data availability status. */
    val cloudBaseStatus: ThermalCloudBaseStatus = ThermalCloudBaseStatus.NO_CCL,
    /** Forecast warnings affecting confidence or display clipping. */
    val warnings: List<ThermalForecastWarning> = emptyList(),
    /** Validated profile levels used by the thermal engine, including the local surface anchor. */
    val usedPressureLevels: List<ThermalSourceLevel> = emptyList(),
)

data class ThermicForecastCellUiModel(
    /** Start of the time slot, minute-of-day in local time (e.g. 720 = 12:00). */
    val startMinuteOfDayLocal: Int,
    /** Bottom of the altitude band, km ASL. */
    val startAltitudeKm: Float,
    /** Top of the altitude band, km ASL. */
    val endAltitudeKm: Float,
    /** Thermal updraft strength, m/s (metres per second). */
    val strengthMps: Float,
    /** Conservative vertical air updraft estimate, m/s. */
    val updraftLowMps: Float = strengthMps,
    /** Nominal vertical air updraft estimate, m/s. */
    val updraftNominalMps: Float = strengthMps,
    /** Optimistic vertical air updraft estimate, m/s. */
    val updraftHighMps: Float = strengthMps,
    /** Confidence for this altitude band. */
    val confidence: ThermalForecastConfidence = ThermalForecastConfidence.MEDIUM,
    /** Visual cell height in metres. */
    val visualDepthM: Float = ((endAltitudeKm - startAltitudeKm) * 1000f).coerceAtLeast(0f),
    /** Physical/evaluation depth used for updraft calculation in metres. */
    val effectiveDepthM: Float = visualDepthM.coerceAtMost(280f),
    /** Interpolated pressure at the visual bottom of this cell, hPa. */
    val pressureBottomHpa: Float? = null,
    /** Interpolated pressure at the visual top of this cell, hPa. */
    val pressureTopHpa: Float? = null,
    /** Data quality for profile interpolation used by this cell. */
    val sourceQuality: ThermalLayerSourceQuality = ThermalLayerSourceQuality.REAL,
    /** Cell-local warnings affecting interpretation. */
    val warnings: List<ThermalForecastWarning> = emptyList(),
)

data class ThermicForecastCloudMarkerUiModel(
    /** Time slot, minute-of-day in local time. */
    val startMinuteOfDayLocal: Int,
    /** Cloud base altitude, km ASL. */
    val altitudeKm: Float,
)

data class ThermicVisibleCellSegment(
    val startAltitudeKm: Float,
    val endAltitudeKm: Float,
)

fun ThermicForecastCellUiModel.visibleSegment(
    minAltitudeKm: Float,
    maxAltitudeKm: Float,
    cloudBaseKm: Float?,
): ThermicVisibleCellSegment? {
    val visibleStartAltitudeKm = startAltitudeKm.coerceAtLeast(minAltitudeKm)
    val cloudLimitedTopKm = cloudBaseKm?.minus(THERMIC_CLOUD_BASE_CLEARANCE_KM)
    val visibleEndAltitudeKm = minOf(
        endAltitudeKm,
        maxAltitudeKm,
        cloudLimitedTopKm ?: Float.MAX_VALUE,
    )

    if (visibleEndAltitudeKm <= visibleStartAltitudeKm + THERMIC_EPSILON) {
        return null
    }

    return ThermicVisibleCellSegment(
        startAltitudeKm = visibleStartAltitudeKm,
        endAltitudeKm = visibleEndAltitudeKm,
    )
}

fun buildPlaceholderThermicForecastChart(
    dayIndex: Int,
    timeSlots: List<Int> = THERMIC_FORECAST_TIME_SLOTS,
): ThermicForecastChartUiModel {
    val dayPhase = dayIndex * 0.33f
    val cells = buildList {
        timeSlots.forEach { startMinute ->
            val timeNormalized = (startMinute - timeSlots.first()).toFloat() /
                (timeSlots.last() - timeSlots.first()).toFloat()
            val hour = startMinute / MINUTES_PER_HOUR.toFloat()
            val solarPeak = (1f - (abs(hour - 14f) / 8f)).coerceIn(0f, 1f)
            val wave = ((sin((timeNormalized * PI.toFloat() * 2f) + dayPhase) + 1f) / 2f)
            val thermalBaseKm = 0f
            val thermalTopKm = (
                0.9f +
                    (solarPeak * 1.9f) +
                    (wave * 0.55f) +
                    ((((dayIndex * 3) + startMinute / FORECAST_TIME_SLOT_STEP_MINUTES) % 4) * 0.05f)
                ).coerceIn(0.6f, 3.6f)

            var currentAltitudeKm = thermalBaseKm
            while (currentAltitudeKm < thermalTopKm) {
                val nextAltitudeKm = (currentAltitudeKm + THERMIC_ALTITUDE_STEP_KM)
                    .coerceAtMost(thermalTopKm)
                val bandCenterKm = (currentAltitudeKm + nextAltitudeKm) / 2f
                val altitudePenalty = (bandCenterKm / thermalTopKm).coerceIn(0f, 1f)
                val rawStrength = (
                    0.35f +
                        (solarPeak * 1.8f) +
                        (wave * 0.65f) -
                        (altitudePenalty * 1.35f)
                    ).coerceIn(0f, MAX_THERMIC_STRENGTH_MPS)

                add(
                    ThermicForecastCellUiModel(
                        startMinuteOfDayLocal = startMinute,
                        startAltitudeKm = currentAltitudeKm,
                        endAltitudeKm = nextAltitudeKm,
                        strengthMps = roundDisplayedStrength(rawStrength),
                        updraftLowMps = roundDisplayedStrength(rawStrength * 0.75f),
                        updraftNominalMps = roundDisplayedStrength(rawStrength),
                        updraftHighMps = roundDisplayedStrength(rawStrength * 1.25f),
                        confidence = ThermalForecastConfidence.MEDIUM,
                    ),
                )

                currentAltitudeKm = nextAltitudeKm
            }
        }
    }

    val cloudMarkers = buildList {
        timeSlots.forEach { startMinute ->
            val slotCells = cells.filter { it.startMinuteOfDayLocal == startMinute }
            val topCell = slotCells.maxByOrNull(ThermicForecastCellUiModel::endAltitudeKm) ?: return@forEach
            val markerCount = (((topCell.endAltitudeKm - 1.2f) / 0.55f).roundToInt() + 1).coerceIn(0, 5)
            repeat(markerCount) { index ->
                add(
                    ThermicForecastCloudMarkerUiModel(
                        startMinuteOfDayLocal = startMinute,
                        altitudeKm = topCell.endAltitudeKm + 0.35f + (index * 0.32f),
                    ),
                )
            }
        }
    }

    val diagnostics = timeSlots.map { startMinute ->
        val slotCells = cells.filter { it.startMinuteOfDayLocal == startMinute }
        val topCell = slotCells.maxByOrNull(ThermicForecastCellUiModel::endAltitudeKm)
        val dryTop = topCell?.endAltitudeKm ?: 0f
        val cloudBase = if (dryTop > 1f) dryTop + 0.3f else null
        ThermicSlotDiagnostics(
            startMinuteOfDayLocal = startMinute,
            dryThermalTopKm = dryTop,
            topLowKm = (dryTop - 0.25f).coerceAtLeast(0f),
            topNominalKm = dryTop,
            topHighKm = dryTop + 0.25f,
            updraftLowMps = slotCells.maxOfOrNull { it.updraftLowMps } ?: 0f,
            updraftNominalMps = slotCells.maxOfOrNull { it.updraftNominalMps } ?: 0f,
            updraftHighMps = slotCells.maxOfOrNull { it.updraftHighMps } ?: 0f,
            confidence = ThermalForecastConfidence.MEDIUM,
            limitingReason = ThermalLimitingReason.SURFACE_HEATING,
            cloudBaseKm = cloudBase,
            moistEquilibriumTopKm = cloudBase?.let { it + 1.5f },
            modelCapeJKg = 400f + dryTop * 100f,
            computedCapeJKg = 350f + dryTop * 90f,
            computedCinJKg = 15f,
            lclKm = dryTop * 0.8f,
            cclKm = dryTop * 0.85f,
        )
    }

    return ThermicForecastChartUiModel(
        timeSlots = timeSlots,
        cells = cells,
        cloudMarkers = cloudMarkers,
        slotDiagnostics = diagnostics,
        pressureLevelAltitudesKm = listOf(0.5f, 1f, 1.5f, 2f, 3f, 4f),
    )
}

fun ThermicForecastChartUiModel.aggregatedForDisplay(
    timeBucketSlotCount: Int,
    altitudeBucketStepKm: Float,
): ThermicForecastChartUiModel {
    if (timeSlots.isEmpty()) {
        return this
    }

    val slotCount = timeBucketSlotCount.coerceAtLeast(1)
    val baseAltitudeStepKm = cells.representativeAltitudeStepKm()
    val altitudeStepMultiplier = maxOf(
        1,
        ceil(altitudeBucketStepKm / baseAltitudeStepKm).toInt(),
    )
    val resolvedAltitudeBucketStepKm = if (pressureLevelAltitudesKm.isEmpty()) {
        baseAltitudeStepKm * altitudeStepMultiplier
    } else {
        baseAltitudeStepKm
    }

    if (slotCount == 1 && resolvedAltitudeBucketStepKm <= baseAltitudeStepKm + THERMIC_EPSILON) {
        return this
    }

    val groupedSlots = timeSlots.chunked(slotCount)
    val aggregatedCells = mutableListOf<ThermicForecastCellUiModel>()
    val aggregatedCloudMarkers = mutableListOf<ThermicForecastCloudMarkerUiModel>()

    groupedSlots.forEach { slotGroup ->
        val groupStartMinute = slotGroup.first()
        val slotCells = cells.filter { it.startMinuteOfDayLocal in slotGroup }
        val slotCloudMarkers = cloudMarkers.filter { it.startMinuteOfDayLocal in slotGroup }

        if (slotCells.isNotEmpty()) {
            val minimumAltitudeKm = slotCells.minOf(ThermicForecastCellUiModel::startAltitudeKm)
            val thermalTopKm = slotCells.maxOf(ThermicForecastCellUiModel::endAltitudeKm)
            var currentAltitudeKm = minimumAltitudeKm

            while (currentAltitudeKm < thermalTopKm - THERMIC_EPSILON) {
                val nextAltitudeKm = (currentAltitudeKm + resolvedAltitudeBucketStepKm)
                    .coerceAtMost(thermalTopKm)
                val overlappingCells = slotCells.filter { cell ->
                    intervalsOverlap(
                        firstStart = cell.startAltitudeKm,
                        firstEnd = cell.endAltitudeKm,
                        secondStart = currentAltitudeKm,
                        secondEnd = nextAltitudeKm,
                    )
                }

                if (overlappingCells.isNotEmpty()) {
                    val averagedNominal = overlappingCells.weightedAverageValue(
                        bucketStartAltitudeKm = currentAltitudeKm,
                        bucketEndAltitudeKm = nextAltitudeKm,
                        selector = ThermicForecastCellUiModel::updraftNominalMps,
                    )
                    aggregatedCells += ThermicForecastCellUiModel(
                        startMinuteOfDayLocal = groupStartMinute,
                        startAltitudeKm = currentAltitudeKm,
                        endAltitudeKm = nextAltitudeKm,
                        strengthMps = roundDisplayedStrength(averagedNominal),
                        updraftLowMps = roundDisplayedStrength(
                            overlappingCells.weightedAverageValue(
                                bucketStartAltitudeKm = currentAltitudeKm,
                                bucketEndAltitudeKm = nextAltitudeKm,
                                selector = ThermicForecastCellUiModel::updraftLowMps,
                            ),
                        ),
                        updraftNominalMps = roundDisplayedStrength(averagedNominal),
                        updraftHighMps = roundDisplayedStrength(
                            overlappingCells.weightedAverageValue(
                                bucketStartAltitudeKm = currentAltitudeKm,
                                bucketEndAltitudeKm = nextAltitudeKm,
                                selector = ThermicForecastCellUiModel::updraftHighMps,
                            ),
                        ),
                        confidence = overlappingCells.lowestCellConfidence(),
                        visualDepthM = ((nextAltitudeKm - currentAltitudeKm) * 1000f).coerceAtLeast(0f),
                        effectiveDepthM = if (pressureLevelAltitudesKm.isEmpty()) {
                            ((nextAltitudeKm - currentAltitudeKm) * 1000f).coerceAtLeast(0f)
                        } else {
                            overlappingCells.minOf { it.effectiveDepthM }
                        },
                        pressureBottomHpa = overlappingCells
                            .minByOrNull(ThermicForecastCellUiModel::startAltitudeKm)
                            ?.pressureBottomHpa,
                        pressureTopHpa = overlappingCells
                            .maxByOrNull(ThermicForecastCellUiModel::endAltitudeKm)
                            ?.pressureTopHpa,
                        sourceQuality = overlappingCells.lowestSourceQuality(),
                        warnings = overlappingCells.flatMap { it.warnings }.distinct(),
                    )
                }

                currentAltitudeKm = nextAltitudeKm
            }
        }

        slotCloudMarkers
            .sortedBy(ThermicForecastCloudMarkerUiModel::altitudeKm)
            .distinctBy { marker -> marker.altitudeKm.roundToInt() }
            .forEach { marker ->
                aggregatedCloudMarkers += marker.copy(startMinuteOfDayLocal = groupStartMinute)
            }
    }

    // Aggregate diagnostics: take the first (or average) for each time bucket
    val diagnosticsBySlot = slotDiagnostics.associateBy { it.startMinuteOfDayLocal }
    val aggregatedDiagnostics = groupedSlots.mapNotNull { slotGroup ->
        val groupStartMinute = slotGroup.first()
        val slotDiags = slotGroup.mapNotNull { diagnosticsBySlot[it] }
        if (slotDiags.isEmpty()) return@mapNotNull null
        // Average numeric values, take first non-null for optional fields
        ThermicSlotDiagnostics(
            startMinuteOfDayLocal = groupStartMinute,
            dryThermalTopKm = slotDiags.map { it.topNominalKm }.average().toFloat(),
            topLowKm = slotDiags.minOf { it.topLowKm },
            topNominalKm = slotDiags.map { it.topNominalKm }.average().toFloat(),
            topHighKm = slotDiags.maxOf { it.topHighKm },
            updraftLowMps = slotDiags.minOf { it.updraftLowMps },
            updraftNominalMps = slotDiags.map { it.updraftNominalMps }.average().toFloat(),
            updraftHighMps = slotDiags.maxOf { it.updraftHighMps },
            confidence = slotDiags.lowestDiagnosticConfidence(),
            limitingReason = slotDiags.firstOrNull { it.limitingReason != ThermalLimitingReason.SURFACE_HEATING }
                ?.limitingReason ?: slotDiags.first().limitingReason,
            topLowerPressureHpa = slotDiags.mapNotNull { it.topLowerPressureHpa }.maxOrNull(),
            topUpperPressureHpa = slotDiags.mapNotNull { it.topUpperPressureHpa }.minOrNull(),
            cloudBaseKm = slotDiags.mapNotNull { it.cloudBaseKm }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            moistEquilibriumTopKm = slotDiags.mapNotNull { it.moistEquilibriumTopKm }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            modelCapeJKg = slotDiags.mapNotNull { it.modelCapeJKg }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            modelCinJKg = slotDiags.mapNotNull { it.modelCinJKg }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            normalizedCinJKg = slotDiags.mapNotNull { it.normalizedCinJKg }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            liftedIndexC = slotDiags.mapNotNull { it.liftedIndexC }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            boundaryLayerHeightM = slotDiags.mapNotNull { it.boundaryLayerHeightM }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            triggerExcessC = slotDiags.map { it.triggerExcessC }.average().toFloat(),
            dryTopExcessC = slotDiags.map { it.dryTopExcessC }.average().toFloat(),
            effectiveRadiationWm2 = slotDiags.mapNotNull { it.effectiveRadiationWm2 }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            surfaceTemperatureC = slotDiags.mapNotNull { it.surfaceTemperatureC }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            surfacePressureHpa = slotDiags.mapNotNull { it.surfacePressureHpa }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            elevationKm = slotDiags.mapNotNull { it.elevationKm }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            parcelStartTemperatureC = slotDiags.mapNotNull { it.parcelStartTemperatureC }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            dryTopAglM = slotDiags.mapNotNull { it.dryTopAglM }.let {
                if (it.isEmpty()) null else it.average().toFloat()
            },
            computedCapeJKg = slotDiags.map { it.computedCapeJKg }.average().toFloat(),
            computedCinJKg = slotDiags.map { it.computedCinJKg }.average().toFloat(),
            lclKm = slotDiags.map { it.lclKm }.average().toFloat(),
            cclKm = slotDiags.mapNotNull { it.cclKm }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            cloudBaseStatus = slotDiags.maxByOrNull { it.cloudBaseStatus.ordinal }?.cloudBaseStatus
                ?: ThermalCloudBaseStatus.NO_CCL,
            warnings = slotDiags.flatMap { it.warnings }.distinct(),
            usedPressureLevels = slotDiags.firstOrNull { it.usedPressureLevels.isNotEmpty() }
                ?.usedPressureLevels
                ?: emptyList(),
        )
    }

    return ThermicForecastChartUiModel(
        timeSlots = groupedSlots.map(List<Int>::first),
        cells = aggregatedCells,
        cloudMarkers = aggregatedCloudMarkers,
        slotDiagnostics = aggregatedDiagnostics,
        pressureLevelAltitudesKm = pressureLevelAltitudesKm,
    )
}

private fun List<ThermicForecastCellUiModel>.weightedAverageValue(
    bucketStartAltitudeKm: Float,
    bucketEndAltitudeKm: Float,
    selector: (ThermicForecastCellUiModel) -> Float,
): Float {
    var weightedValueSum = 0f
    var weightSum = 0f

    forEach { cell ->
        val overlapStart = maxOf(cell.startAltitudeKm, bucketStartAltitudeKm)
        val overlapEnd = minOf(cell.endAltitudeKm, bucketEndAltitudeKm)
        val overlapHeight = (overlapEnd - overlapStart).coerceAtLeast(0f)

        if (overlapHeight > THERMIC_EPSILON) {
            weightedValueSum += selector(cell) * overlapHeight
            weightSum += overlapHeight
        }
    }

    return if (weightSum <= THERMIC_EPSILON) {
        0f
    } else {
        weightedValueSum / weightSum
    }
}

private fun List<ThermicForecastCellUiModel>.representativeAltitudeStepKm(): Float {
    val steps = map { (it.endAltitudeKm - it.startAltitudeKm).coerceAtLeast(0f) }
        .filter { it > THERMIC_EPSILON }
    if (steps.isEmpty()) return THERMIC_ALTITUDE_STEP_KM
    val nonPartialSteps = steps.filter { it >= THERMIC_DISPLAY_MIN_FULL_BIN_KM }
    return (nonPartialSteps.minOrNull() ?: steps.maxOrNull() ?: THERMIC_ALTITUDE_STEP_KM)
        .coerceAtLeast(THERMIC_ALTITUDE_STEP_KM)
}

private fun List<ThermicForecastCellUiModel>.lowestCellConfidence(): ThermalForecastConfidence {
    return maxByOrNull { it.confidence.ordinal }?.confidence ?: ThermalForecastConfidence.LOW
}

private fun List<ThermicForecastCellUiModel>.lowestSourceQuality(): ThermalLayerSourceQuality {
    return maxByOrNull { it.sourceQuality.ordinal }?.sourceQuality ?: ThermalLayerSourceQuality.REAL
}

private fun List<ThermicSlotDiagnostics>.lowestDiagnosticConfidence(): ThermalForecastConfidence {
    return maxByOrNull { it.confidence.ordinal }?.confidence ?: ThermalForecastConfidence.LOW
}

private fun intervalsOverlap(
    firstStart: Float,
    firstEnd: Float,
    secondStart: Float,
    secondEnd: Float,
): Boolean {
    return minOf(firstEnd, secondEnd) - maxOf(firstStart, secondStart) > THERMIC_EPSILON
}

private fun roundDisplayedStrength(value: Float): Float {
    return ((value * 10f).roundToInt() / 10f).coerceIn(0f, MAX_THERMIC_STRENGTH_MPS)
}

private const val FORECAST_TIME_SLOT_STEP_MINUTES = 15
private const val MINUTES_PER_HOUR = 60
private const val THERMIC_ALTITUDE_STEP_KM = 0.05f
private const val THERMIC_DISPLAY_MIN_FULL_BIN_KM = 0.10f
private const val THERMIC_CLOUD_BASE_CLEARANCE_KM = 0.05f
private const val MAX_THERMIC_STRENGTH_MPS = 10f
private const val THERMIC_EPSILON = 0.0001f
private val THERMIC_FORECAST_TIME_SLOTS = buildList {
    var currentMinute = 6 * MINUTES_PER_HOUR
    val lastMinute = (22 * MINUTES_PER_HOUR) + 45
    while (currentMinute <= lastMinute) {
        add(currentMinute)
        currentMinute += FORECAST_TIME_SLOT_STEP_MINUTES
    }
}
