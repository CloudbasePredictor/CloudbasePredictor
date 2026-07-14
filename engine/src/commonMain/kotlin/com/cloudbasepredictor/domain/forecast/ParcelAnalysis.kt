package com.cloudbasepredictor.domain.forecast

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Result of a full parcel analysis for one time slot.
 *
 * All altitudes are in km ASL (above sea level).
 * Pure data class — no Android / UI dependencies.
 */
data class ParcelAnalysisResult(
    /** Altitude where the dry adiabat parcel cools below the environmental profile, km ASL. */
    val dryThermalTopKm: Float,
    /** Lifting Condensation Level: where surface parcel reaches saturation on dry adiabat, km ASL. */
    val lclKm: Float,
    /** Lifting Condensation Level pressure, hPa. */
    val lclPressureHpa: Float,
    /** Convective Condensation Level: approximate cumulus base if heating is sufficient, km ASL. */
    val cclKm: Float?,
    /** Convective Condensation Level pressure, hPa. */
    val cclPressureHpa: Float?,
    /** Convective temperature at the surface required to reach the CCL, °C. */
    val tconC: Float?,
    /** Cloud base altitude for cumulus formation, km ASL. Null if CCL is unavailable or unreachable. */
    val cloudBaseKm: Float?,
    /** Equilibrium level of the moist (saturated) parcel, km ASL. Null if no moist convection. */
    val moistEquilibriumTopKm: Float?,
    /** CAPE computed from the environmental profile and parcel ascent (J/kg). */
    val computedCapeJKg: Float,
    /** CIN computed from the environmental profile and parcel ascent (J/kg, positive value = inhibition). */
    val computedCinJKg: Float,
    /** Model-supplied CAPE from the weather model (J/kg). Null if unavailable. */
    val modelCapeJKg: Float?,
    /** Local buoyancy-based thermal strength per altitude band below dry top, m/s. */
    val thermalCells: List<ThermalCell>,
    /** Surface heating estimate used for the parcel launch temperature, °C above T2m. */
    val surfaceHeatingC: Float,
)

/**
 * Thermal updraft strength in a single altitude band.
 */
data class ThermalCell(
    /** Bottom of band, km ASL. */
    val startAltitudeKm: Float,
    /** Top of band, km ASL. */
    val endAltitudeKm: Float,
    /** Estimated updraft strength, m/s. */
    val strengthMps: Float,
    /** Local buoyancy (parcel T - env T), °C. Positive = buoyant. */
    val buoyancyC: Float,
)

/**
 * One level in the atmospheric profile used for parcel analysis.
 */
data class ProfileLevel(
    val pressureHpa: Float,
    val temperatureC: Float,
    val dewPointC: Float?,
    val heightKm: Float,
    val relativeHumidityPercent: Float? = null,
    val cloudCoverPercent: Float? = null,
    val windSpeedKmh: Float? = null,
    val isSynthetic: Boolean = false,
)

/**
 * Input parameters for surface heating estimation.
 */
data class SurfaceHeatingInput(
    val hourOfDay: Int,
    val shortwaveRadiationWm2: Float?,
    val previousShortwaveRadiationWm2: Float? = null,
    val cloudCoverLowPercent: Float?,
    val cloudCoverMidPercent: Float?,
    val cloudCoverHighPercent: Float?,
    val precipitationMm: Float?,
    val isDay: Boolean?,
)

/**
 * Separates local trigger heating from the smaller excess that should be transported aloft.
 */
data class SurfaceHeatingEstimate(
    val triggerExcessC: Float,
    val conservativeDryTopExcessC: Float,
    val nominalDryTopExcessC: Float,
    val optimisticDryTopExcessC: Float,
    val effectiveRadiationWm2: Float?,
)

// ────────────────────────────────────────────────────────────────────
// Thermodynamic constants
// ────────────────────────────────────────────────────────────────────

/** Poisson constant R_d / C_pd for dry air. */
private const val KAPPA = 0.286f

/** Gravity, m/s². */
private const val G = 9.81f

/** Specific gas constant for dry air, J/(kg·K). */
private const val RD = 287f

/** Specific heat of dry air at constant pressure, J/(kg·K). */
private const val CPD = 1004f

/** Ratio of gas constants Rd/Rv used in mixing-ratio formulas. */
private const val EPSILON = 0.622f

/** Minimum parcel buoyancy (°C) to count as a usable thermal. */
private const val MIN_BUOYANCY_C = 0.3f

/** Cap for surface heating estimate, °C. */
private const val MAX_SURFACE_HEATING_C = 8f

/** Default conservative surface heating when no radiation data available, °C. */
private const val DEFAULT_SURFACE_HEATING_C = 2f

/** Minimum surface heating during daytime, °C. */
private const val MIN_DAYTIME_SURFACE_HEATING_C = 0.5f

/**
 * Maximum reference shortwave radiation for scaling, W/m².
 * A very sunny summer midday ~ 800–1000 W/m².
 */
private const val REFERENCE_RADIATION_WM2 = 900f

/** Scaling factor to convert local buoyancy (K) into updraft m/s. */
private const val BUOYANCY_TO_UPDRAFT_SCALE = 0.75f

/** Maximum thermal strength cap, m/s. */
private const val MAX_THERMAL_STRENGTH_MPS = 10f

/** Minimum displayable thermal strength, m/s. */
private const val MIN_DISPLAY_STRENGTH_MPS = 0.2f

// ────────────────────────────────────────────────────────────────────
// Public API
// ────────────────────────────────────────────────────────────────────

/**
 * Performs a full parcel analysis for one time slot.
 *
 * @param profile Environmental profile, sorted by decreasing pressure (surface first).
 *                Must have at least 2 levels with heights above [elevationKm].
 * @param surfaceTemperatureC Surface temperature at 2 m, °C.
 * @param surfaceDewPointC Surface dewpoint at 2 m, °C.
 * @param surfacePressureHpa Surface pressure, hPa.
 * @param elevationKm Station elevation, km ASL.
 * @param heatingInput Parameters for surface heating estimation.
 * @param modelCapeJKg Model-supplied CAPE (J/kg), if available.
 * @return Analysis result, or null if the profile is insufficient.
 */
fun analyzeParcel(
    profile: List<ProfileLevel>,
    surfaceTemperatureC: Float,
    surfaceDewPointC: Float,
    surfacePressureHpa: Float,
    elevationKm: Float,
    heatingInput: SurfaceHeatingInput,
    modelCapeJKg: Float? = null,
): ParcelAnalysisResult? {
    // Filter to levels above surface and sort descending pressure (low altitude first)
    val aboveSurface = profile
        .filter { it.heightKm >= elevationKm - 0.01f && it.pressureHpa < surfacePressureHpa + 1f }
        .sortedByDescending { it.pressureHpa }

    if (aboveSurface.size < 2) return null

    // Surface heating
    val surfaceHeatingC = estimateSurfaceHeating(heatingInput)
    val parcelTempC = surfaceTemperatureC + surfaceHeatingC
    val parcelThetaK = potentialTemperatureK(parcelTempC, surfacePressureHpa)
    val surfaceMixingRatio = satMixingRatioGKg(surfaceDewPointC, surfacePressureHpa)

    // ── Find LCL: where dry adiabat parcel reaches saturation ──
    val lclResult = findLcl(parcelThetaK, surfaceMixingRatio, surfacePressureHpa, aboveSurface, elevationKm)

    val cclPrimary = analyzeCclHourly(
        CclHourlyInput(
            time = "",
            surfaceTemperatureC = surfaceTemperatureC,
            surfaceDewPointC = surfaceDewPointC,
            surfacePressureHpa = surfacePressureHpa,
            surfaceElevationM = elevationKm * 1000f,
            pressureLevels = aboveSurface
                .filter { abs(it.pressureHpa - surfacePressureHpa) > 1f || abs(it.heightKm - elevationKm) > 0.03f }
                .map { level ->
                    CclPressureLevel(
                        pressureHpa = level.pressureHpa,
                        temperatureC = level.temperatureC,
                        dewPointC = level.dewPointC,
                        heightMslM = level.heightKm * 1000f,
                        isSynthetic = level.isSynthetic,
                    )
                },
        ),
    ).primaryCclResult()
    val cclKm = cclPrimary?.cclHeightMslM?.div(1000f)
    val cclPressureHpa = cclPrimary?.cclPressureHpa
    val tconC = cclPrimary?.convectiveTemperatureC

    // ── Find dry thermal top: where dry adiabat parcel T < environment T ──
    val dryTopKm = findDryThermalTop(parcelThetaK, aboveSurface, elevationKm)
    val lclTemperatureC = dryAdiabatTempC(parcelThetaK, lclResult.pressureHpa)

    // ── Cloud base determination ──
    val cloudBaseKm = if (
        cclPrimary?.reachable == true &&
        cclKm != null &&
        dryTopKm >= cclKm - 0.05f
    ) {
        cclKm
    } else {
        null
    }

    // ── Moist ascent above cloud base ──
    var moistEquilibriumTopKm: Float? = null
    if (cloudBaseKm != null && cclPrimary != null) {
        moistEquilibriumTopKm = findMoistEquilibriumTop(
            saturationPointTemperatureC = cclPrimary.cclTemperatureC ?: lclTemperatureC,
            saturationPointPressureHpa = cclPrimary.cclPressureHpa ?: lclResult.pressureHpa,
            profile = aboveSurface,
            cloudBaseKm = cloudBaseKm,
        )
    }

    // ── CAPE / CIN computation ──
    val capeCin = computeCapeCin(
        parcelThetaK = parcelThetaK,
        lclTemperatureC = lclTemperatureC,
        lclPressureHpa = lclResult.pressureHpa,
        surfaceMixingRatio = surfaceMixingRatio,
        profile = aboveSurface,
    )

    // ── Build thermal cells from local buoyancy ──
    val thermalCells = buildThermalCells(
        parcelThetaK = parcelThetaK,
        surfaceMixingRatio = surfaceMixingRatio,
        lclPressureHpa = lclResult.pressureHpa,
        profile = aboveSurface,
        elevationKm = elevationKm,
        dryTopKm = dryTopKm,
    )

    return ParcelAnalysisResult(
        dryThermalTopKm = dryTopKm,
        lclKm = lclResult.heightKm,
        lclPressureHpa = lclResult.pressureHpa,
        cclKm = cclKm,
        cclPressureHpa = cclPressureHpa,
        tconC = tconC,
        cloudBaseKm = cloudBaseKm,
        moistEquilibriumTopKm = moistEquilibriumTopKm,
        computedCapeJKg = capeCin.first,
        computedCinJKg = capeCin.second,
        modelCapeJKg = modelCapeJKg,
        thermalCells = thermalCells,
        surfaceHeatingC = surfaceHeatingC,
    )
}

// ────────────────────────────────────────────────────────────────────
// Surface heating estimation
// ────────────────────────────────────────────────────────────────────

/**
 * Estimates convective surface heating above T2m based on radiation, clouds,
 * precipitation and time of day. Returns °C offset.
 */
fun estimateSurfaceHeating(input: SurfaceHeatingInput): Float {
    // Night time — no convective heating
    if (input.isDay == false || input.hourOfDay < 6 || input.hourOfDay > 20) {
        return 0f
    }

    // If we have shortwave radiation, use it as primary driver
    val radiationFraction = input.shortwaveRadiationWm2?.let {
        (it / REFERENCE_RADIATION_WM2).coerceIn(0f, 1.2f)
    }

    val cloudPenalty = if (radiationFraction != null) {
        val low = input.cloudCoverLowPercent ?: 0f
        when {
            low >= 85f -> 0.60f
            low >= 70f -> 0.40f
            low >= 50f -> 0.20f
            else -> 0f
        }
    } else {
        val low = (input.cloudCoverLowPercent ?: 0f) / 100f
        val mid = (input.cloudCoverMidPercent ?: 0f) / 100f
        val high = (input.cloudCoverHighPercent ?: 0f) / 100f
        (low * 0.7f + mid * 0.4f + high * 0.15f).coerceIn(0f, 0.85f)
    }

    // Precipitation suppresses thermals
    val precipPenalty = if ((input.precipitationMm ?: 0f) > 0.1f) 0.6f else 0f

    // Solar elevation proxy from hour of day
    val solarFactor = solarElevationFactor(input.hourOfDay)

    val baseHeating = if (radiationFraction != null) {
        // Radiation-driven: up to ~MAX_SURFACE_HEATING_C at peak radiation
        radiationFraction * MAX_SURFACE_HEATING_C * solarFactor
    } else {
        // No radiation data: use conservative default with solar curve.
        // The cloud penalty is applied once below (shared with the radiation branch).
        DEFAULT_SURFACE_HEATING_C * solarFactor
    }

    val heating = baseHeating * (1f - cloudPenalty) * (1f - precipPenalty)
    return heating.coerceIn(MIN_DAYTIME_SURFACE_HEATING_C, MAX_SURFACE_HEATING_C)
}

/**
 * Estimates thermal-trigger excess and the smaller entrainment-aware excess used for dry-top.
 *
 * Open-Meteo shortwave radiation is an hourly mean for the preceding hour. Blend the current
 * and previous values lightly so a single timestamp is not treated as an instantaneous flux.
 */
fun estimateThermalHeatingEstimate(input: SurfaceHeatingInput): SurfaceHeatingEstimate {
    val effectiveRadiation = when {
        input.shortwaveRadiationWm2 != null && input.previousShortwaveRadiationWm2 != null ->
            (input.shortwaveRadiationWm2 * 0.65f) + (input.previousShortwaveRadiationWm2 * 0.35f)
        else -> input.shortwaveRadiationWm2
    }
    val triggerExcess = estimateSurfaceHeating(
        input.copy(shortwaveRadiationWm2 = effectiveRadiation),
    )
    val precipitation = input.precipitationMm ?: 0f
    val lowCloud = input.cloudCoverLowPercent ?: 100f
    val optimisticAllowed = precipitation <= 0.1f && lowCloud < 40f
    val optimisticExcess = if (optimisticAllowed) {
        (triggerExcess * 0.80f).coerceAtMost(5f)
    } else {
        (triggerExcess * 0.55f).coerceAtMost(3f)
    }
    return SurfaceHeatingEstimate(
        triggerExcessC = triggerExcess,
        conservativeDryTopExcessC = (triggerExcess * 0.35f).coerceAtMost(2f),
        nominalDryTopExcessC = (triggerExcess * 0.55f).coerceAtMost(3f),
        optimisticDryTopExcessC = optimisticExcess,
        effectiveRadiationWm2 = effectiveRadiation,
    )
}

/** Simple solar elevation factor peaking at 13:00 local. */
internal fun solarElevationFactor(hourOfDay: Int): Float {
    val dist = kotlin.math.abs(hourOfDay - 13f)
    return (1f - dist / 8f).coerceIn(0f, 1f)
}

// ────────────────────────────────────────────────────────────────────
// Thermodynamic helpers (pure functions)
// ────────────────────────────────────────────────────────────────────

/** Potential temperature θ (K) from temperature (°C) and pressure (hPa). */
fun potentialTemperatureK(temperatureC: Float, pressureHpa: Float): Float {
    return (temperatureC + 273.15f) * (1000f / pressureHpa).pow(KAPPA)
}

/** Temperature (°C) from potential temperature θ (K) at a given pressure. */
fun dryAdiabatTempC(thetaK: Float, pressureHpa: Float): Float {
    return thetaK * (pressureHpa / 1000f).pow(KAPPA) - 273.15f
}

/** Saturation mixing ratio (g/kg) at given temperature and pressure. */
fun satMixingRatioGKg(temperatureC: Float, pressureHpa: Float): Float {
    val es = satVaporPressureHpa(temperatureC)
    val denom = pressureHpa - es
    return if (denom > 0.01f) 622f * es / denom else 622f * es / 0.01f
}

/** Moist (pseudo-adiabatic) temperature at given pressure, starting from θ at LCL. */
fun moistAdiabatTempC(thetaWK: Float, pressureHpa: Float): Float {
    var tempK = thetaWK * (pressureHpa / 1000f).pow(KAPPA)
    repeat(4) {
        val es = satVaporPressureHpa(tempK - 273.15f)
        val ws = 0.622f * es / (pressureHpa - es).coerceAtLeast(0.01f)
        val lv = 2.501e6f - 2370f * (tempK - 273.15f)
        val correction = (lv * ws) / (1004f * tempK)
        tempK = thetaWK * (pressureHpa / 1000f).pow(KAPPA / (1f + correction))
    }
    return tempK - 273.15f
}

/**
 * Integrates a saturated parcel from one known point to another pressure level.
 *
 * This is the path that parcel guides and parcel analysis should follow above the LCL. Using the
 * dry parcel theta as a proxy here keeps the path too close to a dry adiabat, which is exactly
 * the regression reported on the Stuve interaction.
 *
 * Public because the Stuve chart UI renders parcel guide curves with the same integration.
 */
fun moistAdiabatTempFromPointC(
    startTemperatureC: Float,
    startPressureHpa: Float,
    targetPressureHpa: Float,
    stepHpa: Float = 2f,
): Float {
    if (abs(targetPressureHpa - startPressureHpa) < 0.01f) return startTemperatureC

    var temperatureK = startTemperatureC + 273.15f
    var pressureHpa = startPressureHpa
    val direction = if (targetPressureHpa < startPressureHpa) -1f else 1f
    val step = stepHpa * direction

    while (
        (direction < 0f && pressureHpa > targetPressureHpa + 0.01f) ||
            (direction > 0f && pressureHpa < targetPressureHpa - 0.01f)
    ) {
        val nextPressureHpa = if (abs(targetPressureHpa - pressureHpa) <= abs(step)) {
            targetPressureHpa
        } else {
            pressureHpa + step
        }
        val midpointPressureHpa = (pressureHpa + nextPressureHpa) / 2f
        val temperatureC = temperatureK - 273.15f
        val saturationVaporPressureHpa = satVaporPressureHpa(temperatureC)
        val saturationMixingRatioKgKg = EPSILON * saturationVaporPressureHpa /
            (midpointPressureHpa - saturationVaporPressureHpa).coerceAtLeast(0.01f)
        val latentHeatJKg = 2.501e6f - 2370f * temperatureC
        val moistLapseRateKPerM = G * (
            1f + (latentHeatJKg * saturationMixingRatioKgKg) / (RD * temperatureK)
            ) / (
            CPD + (latentHeatJKg * latentHeatJKg * saturationMixingRatioKgKg * EPSILON) /
                (RD * temperatureK * temperatureK)
            )
        val virtualTemperatureK = temperatureK * (1f + 0.61f * saturationMixingRatioKgKg)
        val dTemperatureDpHpa = moistLapseRateKPerM * RD * virtualTemperatureK / (G * midpointPressureHpa)

        temperatureK += dTemperatureDpHpa * (nextPressureHpa - pressureHpa)
        pressureHpa = nextPressureHpa
    }

    return temperatureK - 273.15f
}

/** Tetens formula: saturation vapor pressure (hPa) from temperature (°C). */
fun satVaporPressureHpa(temperatureC: Float): Float {
    return 6.112f * exp(17.67f * temperatureC / (temperatureC + 243.5f))
}

/** Relative humidity as fraction 0..1 from temperature and dew point. */
fun relativeHumidityFraction(temperatureC: Float, dewPointC: Float): Float {
    val saturationAtTemp = satVaporPressureHpa(temperatureC)
    val saturationAtDewPoint = satVaporPressureHpa(dewPointC)
    return (saturationAtDewPoint / saturationAtTemp).coerceIn(0f, 1f)
}

/** Temperature (°C) of a mixing-ratio line at a given pressure. */
fun mixingRatioTemperatureC(mixingRatioGKg: Float, pressureHpa: Float): Float {
    val vaporPressure = mixingRatioGKg * pressureHpa / (622f + mixingRatioGKg)
    val lnRatio = ln(vaporPressure / 6.112f)
    return (243.5f * lnRatio) / (17.67f - lnRatio)
}

/** Estimate surface pressure (hPa) from elevation (m) using ISA barometric formula. */
fun estimateSurfacePressure(elevationM: Double): Float {
    return (1013.25 * (1.0 - 0.0065 * elevationM / 288.15).pow(5.2561)).toFloat()
}

/** Linear interpolation of environmental temperature by pressure. */
fun interpolateTemperatureCAtPressure(
    profile: List<ProfileLevel>,
    pressureHpa: Float,
): Float? {
    val sorted = profile.sortedByDescending { it.pressureHpa }
    if (sorted.isEmpty()) return null
    sorted.firstOrNull { it.pressureHpa == pressureHpa }?.let { return it.temperatureC }
    for (i in 0 until sorted.size - 1) {
        val lower = sorted[i]
        val upper = sorted[i + 1]
        if (pressureHpa <= lower.pressureHpa && pressureHpa >= upper.pressureHpa) {
            val fraction = (lower.pressureHpa - pressureHpa) / (lower.pressureHpa - upper.pressureHpa)
            return lower.temperatureC + fraction * (upper.temperatureC - lower.temperatureC)
        }
    }
    return null
}

/** Linear interpolation of environmental height by pressure. */
fun interpolateHeightKmAtPressure(
    profile: List<ProfileLevel>,
    pressureHpa: Float,
): Float? {
    val sorted = profile.sortedByDescending { it.pressureHpa }
    if (sorted.isEmpty()) return null
    sorted.firstOrNull { it.pressureHpa == pressureHpa }?.let { return it.heightKm }
    for (i in 0 until sorted.size - 1) {
        val lower = sorted[i]
        val upper = sorted[i + 1]
        if (pressureHpa <= lower.pressureHpa && pressureHpa >= upper.pressureHpa) {
            val fraction = (lower.pressureHpa - pressureHpa) / (lower.pressureHpa - upper.pressureHpa)
            return lower.heightKm + fraction * (upper.heightKm - lower.heightKm)
        }
    }
    return null
}

// ────────────────────────────────────────────────────────────────────
// Internal analysis steps
// ────────────────────────────────────────────────────────────────────

private data class PressureHeightResult(val pressureHpa: Float, val heightKm: Float)

/**
 * Finds the Lifting Condensation Level by ascending the dry adiabat until
 * the parcel's saturation mixing ratio drops to the surface mixing ratio.
 */
private fun findLcl(
    parcelThetaK: Float,
    surfaceMixingRatio: Float,
    surfacePressureHpa: Float,
    profile: List<ProfileLevel>,
    elevationKm: Float,
): PressureHeightResult {
    var prevLevel: ProfileLevel? = null
    for (level in profile) {
        val dryTemp = dryAdiabatTempC(parcelThetaK, level.pressureHpa)
        val satMr = satMixingRatioGKg(dryTemp, level.pressureHpa)
        if (satMr <= surfaceMixingRatio) {
            // Interpolate between previous and current level
            if (prevLevel != null) {
                val prevDryTemp = dryAdiabatTempC(parcelThetaK, prevLevel.pressureHpa)
                val prevSatMr = satMixingRatioGKg(prevDryTemp, prevLevel.pressureHpa)
                val frac = if ((prevSatMr - satMr) > 0.001f) {
                    (prevSatMr - surfaceMixingRatio) / (prevSatMr - satMr)
                } else 0.5f
                val interpHeight = prevLevel.heightKm + frac * (level.heightKm - prevLevel.heightKm)
                val interpPressure = prevLevel.pressureHpa + frac * (level.pressureHpa - prevLevel.pressureHpa)
                return PressureHeightResult(interpPressure, interpHeight.coerceAtLeast(elevationKm))
            }
            return PressureHeightResult(level.pressureHpa, level.heightKm.coerceAtLeast(elevationKm))
        }
        prevLevel = level
    }
    // LCL above entire profile — use top level
    val top = profile.last()
    return PressureHeightResult(top.pressureHpa, top.heightKm)
}

/**
 * Finds the dry thermal top: the altitude where the dry-adiabat parcel
 * temperature falls below the environmental temperature.
 */
private fun findDryThermalTop(
    parcelThetaK: Float,
    profile: List<ProfileLevel>,
    elevationKm: Float,
): Float {
    var prevLevel: ProfileLevel? = null
    for (level in profile) {
        val parcelTemp = dryAdiabatTempC(parcelThetaK, level.pressureHpa)
        if (parcelTemp < level.temperatureC) {
            if (prevLevel != null) {
                val prevParcelTemp = dryAdiabatTempC(parcelThetaK, prevLevel.pressureHpa)
                val prevDiff = prevParcelTemp - prevLevel.temperatureC
                val currDiff = parcelTemp - level.temperatureC
                val frac = if ((prevDiff - currDiff) > 0.001f) {
                    prevDiff / (prevDiff - currDiff)
                } else 0.5f
                return (prevLevel.heightKm + frac * (level.heightKm - prevLevel.heightKm))
                    .coerceAtLeast(elevationKm)
            }
            return level.heightKm.coerceAtLeast(elevationKm)
        }
        prevLevel = level
    }
    // Parcel still buoyant at the top of profile
    return profile.lastOrNull()?.heightKm ?: elevationKm
}

/**
 * Finds the moist equilibrium top: where the moist-adiabat parcel temperature
 * falls below the environmental temperature above cloud base.
 */
private fun findMoistEquilibriumTop(
    saturationPointTemperatureC: Float,
    saturationPointPressureHpa: Float,
    profile: List<ProfileLevel>,
    cloudBaseKm: Float,
): Float? {
    val aboveCloudBase = profile.filter { it.heightKm >= cloudBaseKm - 0.01f }
    if (aboveCloudBase.size < 2) return null

    var foundBuoyant = false
    var prevLevel: ProfileLevel? = null
    for (level in aboveCloudBase) {
        val moistTemp = moistAdiabatTempFromPointC(
            saturationPointTemperatureC,
            saturationPointPressureHpa,
            level.pressureHpa,
        )
        if (moistTemp > level.temperatureC) {
            foundBuoyant = true
        } else if (foundBuoyant) {
            // Equilibrium level found
            if (prevLevel != null) {
                val prevMoistTemp = moistAdiabatTempFromPointC(
                    saturationPointTemperatureC,
                    saturationPointPressureHpa,
                    prevLevel.pressureHpa,
                )
                val prevDiff = prevMoistTemp - prevLevel.temperatureC
                val currDiff = moistTemp - level.temperatureC
                val frac = if ((prevDiff - currDiff) > 0.001f) {
                    prevDiff / (prevDiff - currDiff)
                } else 0.5f
                return prevLevel.heightKm + frac * (level.heightKm - prevLevel.heightKm)
            }
            return level.heightKm
        }
        prevLevel = level
    }

    // If buoyant all the way to top, return top
    return if (foundBuoyant) profile.last().heightKm else null
}

/**
 * Computes CAPE and CIN from the parcel ascent relative to the environmental profile.
 *
 * @return Pair(capeJKg, cinJKg) where CIN is a positive value representing inhibition.
 */
private fun computeCapeCin(
    parcelThetaK: Float,
    lclTemperatureC: Float,
    lclPressureHpa: Float,
    surfaceMixingRatio: Float,
    profile: List<ProfileLevel>,
): Pair<Float, Float> {
    if (profile.size < 2) return 0f to 0f

    var integration = CapeCinIntegration()

    for (i in 0 until profile.size - 1) {
        if (integration.phase == CapeCinPhase.COMPLETE) break
        val lower = profile[i]
        val upper = profile[i + 1]
        val dz = (upper.heightKm - lower.heightKm) * 1000f // meters
        if (dz <= 0f) continue

        val midPressure = (lower.pressureHpa + upper.pressureHpa) / 2f
        val envTempMid = (lower.temperatureC + upper.temperatureC) / 2f
        val parcelLayer = parcelTemperatureAtPressure(
            parcelThetaK = parcelThetaK,
            lclTemperatureC = lclTemperatureC,
            lclPressureHpa = lclPressureHpa,
            surfaceMixingRatio = surfaceMixingRatio,
            pressureHpa = midPressure,
            belowLcl = integration.phase == CapeCinPhase.BELOW_LCL,
        )
        if (parcelLayer.reachedLcl) {
            integration = integration.copy(phase = CapeCinPhase.BELOW_LFC)
        }
        val envTempK = envTempMid + 273.15f
        val buoyancy = G * (parcelLayer.temperatureC - envTempMid) / envTempK * dz
        integration = integration.addBuoyancy(buoyancy)
    }

    return integration.cape to integration.cin
}

private enum class CapeCinPhase {
    BELOW_LCL,
    BELOW_LFC,
    ABOVE_LFC,
    COMPLETE,
}

private data class CapeCinIntegration(
    val cape: Float = 0f,
    val cin: Float = 0f,
    val phase: CapeCinPhase = CapeCinPhase.BELOW_LCL,
)

private data class ParcelLayerTemperature(
    val temperatureC: Float,
    val reachedLcl: Boolean,
)

private fun parcelTemperatureAtPressure(
    parcelThetaK: Float,
    lclTemperatureC: Float,
    lclPressureHpa: Float,
    surfaceMixingRatio: Float,
    pressureHpa: Float,
    belowLcl: Boolean,
): ParcelLayerTemperature {
    val dryTemperatureC = dryAdiabatTempC(parcelThetaK, pressureHpa)
    return when {
        !belowLcl -> ParcelLayerTemperature(
            temperatureC = moistAdiabatTempFromPointC(lclTemperatureC, lclPressureHpa, pressureHpa),
            reachedLcl = false,
        )
        satMixingRatioGKg(dryTemperatureC, pressureHpa) > surfaceMixingRatio ->
            ParcelLayerTemperature(temperatureC = dryTemperatureC, reachedLcl = false)
        else -> ParcelLayerTemperature(
            temperatureC = moistAdiabatTempFromPointC(lclTemperatureC, lclPressureHpa, pressureHpa),
            reachedLcl = true,
        )
    }
}

private fun CapeCinIntegration.addBuoyancy(buoyancy: Float): CapeCinIntegration = when (phase) {
    CapeCinPhase.BELOW_LCL -> copy(cin = cin - buoyancy.coerceAtMost(0f))
    CapeCinPhase.BELOW_LFC -> if (buoyancy > 0f) {
        // A shallow dry-thermal layer below the LCL is not free moist convection and must not hide
        // a cap above it. The first positive saturated layer is the LFC and starts CAPE integration.
        copy(cape = cape + buoyancy, phase = CapeCinPhase.ABOVE_LFC)
    } else {
        // CIN is the negative area from the surface to the LFC.
        copy(cin = cin - buoyancy)
    }
    CapeCinPhase.ABOVE_LFC -> if (buoyancy > 0f) {
        copy(cape = cape + buoyancy)
    } else {
        // The first non-positive layer after the LFC marks the equilibrium level. Stable layers
        // above it are outside both the CAPE and CIN integration bounds.
        copy(phase = CapeCinPhase.COMPLETE)
    }
    CapeCinPhase.COMPLETE -> this
}

/**
 * Builds thermal updraft strength cells from local buoyancy between profile levels,
 * up to the dry thermal top.
 */
private fun buildThermalCells(
    parcelThetaK: Float,
    surfaceMixingRatio: Float,
    lclPressureHpa: Float,
    profile: List<ProfileLevel>,
    elevationKm: Float,
    dryTopKm: Float,
): List<ThermalCell> {
    val cells = mutableListOf<ThermalCell>()
    if (profile.size < 2) return cells

    for (i in 0 until profile.size - 1) {
        val lower = profile[i]
        val upper = profile[i + 1]
        if (upper.heightKm <= elevationKm) continue
        if (lower.heightKm >= dryTopKm) break

        val cellBottom = max(lower.heightKm, elevationKm)
        val cellTop = min(upper.heightKm, dryTopKm)
        if (cellTop <= cellBottom + 0.001f) continue

        // Parcel temperature at midpoint
        val midPressure = (lower.pressureHpa + upper.pressureHpa) / 2f
        val parcelTemp = dryAdiabatTempC(parcelThetaK, midPressure)
        val envTemp = (lower.temperatureC + upper.temperatureC) / 2f
        val buoyancyC = parcelTemp - envTemp

        if (buoyancyC < MIN_BUOYANCY_C) continue

        // Convert buoyancy to updraft velocity using simple w ~ sqrt(2 * B * dz)
        // where B = g * ΔT/T_env
        val dz = (cellTop - cellBottom) * 1000f
        val envTempK = envTemp + 273.15f
        val buoyancyAccel = G * buoyancyC / envTempK
        // Local updraft estimate using integrated buoyancy
        val rawStrength = sqrt(max(0f, 2f * buoyancyAccel * dz)) * BUOYANCY_TO_UPDRAFT_SCALE
        val strength = rawStrength.coerceIn(0f, MAX_THERMAL_STRENGTH_MPS)

        if (strength >= MIN_DISPLAY_STRENGTH_MPS) {
            cells += ThermalCell(
                startAltitudeKm = cellBottom,
                endAltitudeKm = cellTop,
                strengthMps = ((strength * 10f).toInt() / 10f),
                buoyancyC = buoyancyC,
            )
        }
    }

    return cells
}
