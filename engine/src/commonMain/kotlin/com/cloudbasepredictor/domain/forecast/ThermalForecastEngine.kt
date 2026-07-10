package com.cloudbasepredictor.domain.forecast

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class ThermalForecastConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

enum class ThermalLimitingReason {
    SURFACE_HEATING,
    INVERSION,
    CLOUD_BASE,
    PROFILE_TOP,
    PRECIPITATION,
    WEAK_RADIATION,
    WIND_SHEAR,
    MISSING_DATA,
}

enum class ThermalForecastWarning {
    MISSING_PBL,
    PBL_EXCEEDED,
    MISSING_CIN,
    MISSING_LIFTED_INDEX,
    MISSING_CCL,
    MOUNTAIN_OROGRAPHIC_OVERRIDE,
    NEAR_SURFACE_PROFILE_MISMATCH,
}

enum class ThermalCloudBaseStatus {
    REACHABLE,
    UNREACHABLE,
    NO_CCL,
    UNKNOWN,
}

enum class ThermalLayerSourceQuality {
    REAL,
    INTERPOLATED,
    SYNTHETIC,
}

data class ThermalSourceLevel(
    val pressureHpa: Float,
    val altitudeKm: Float,
    val isSynthetic: Boolean,
)

data class ThermalForecastInput(
    val profile: List<ProfileLevel>,
    val surfaceTemperatureC: Float,
    val surfaceDewPointC: Float,
    val surfacePressureHpa: Float,
    val elevationKm: Float,
    val heatingInput: SurfaceHeatingInput,
    val modelCapeJKg: Float? = null,
    val modelCinJKg: Float? = null,
    val liftedIndexC: Float? = null,
    val boundaryLayerHeightM: Float? = null,
)

data class ThermalForecastResult(
    val topLowKm: Float,
    val topNominalKm: Float,
    val topHighKm: Float,
    val updraftLowMps: Float,
    val updraftNominalMps: Float,
    val updraftHighMps: Float,
    val confidence: ThermalForecastConfidence,
    val limitingReason: ThermalLimitingReason,
    val lowerSourceLevel: ThermalSourceLevel?,
    val upperSourceLevel: ThermalSourceLevel?,
    val lclKm: Float,
    val cclKm: Float?,
    val cloudBaseKm: Float?,
    val moistEquilibriumTopKm: Float?,
    val thermalEnergyJKg: Float,
    val modelCapeJKg: Float?,
    val modelCinJKg: Float?,
    val normalizedCinJKg: Float?,
    val liftedIndexC: Float?,
    val boundaryLayerHeightM: Float?,
    val triggerExcessC: Float,
    val dryTopExcessC: Float,
    val effectiveRadiationWm2: Float?,
    val surfaceTemperatureC: Float,
    val surfacePressureHpa: Float,
    val elevationKm: Float,
    val parcelStartTemperatureC: Float,
    val dryTopAglM: Float,
    val computedCinJKg: Float,
    val cloudBaseStatus: ThermalCloudBaseStatus,
    val warnings: List<ThermalForecastWarning>,
    val usedPressureLevels: List<ThermalSourceLevel>,
    val layers: List<ThermalForecastLayer>,
    val pressureLevelAltitudesKm: List<Float>,
)

data class ThermalForecastLayer(
    val startAltitudeKm: Float,
    val endAltitudeKm: Float,
    val updraftLowMps: Float,
    val updraftNominalMps: Float,
    val updraftHighMps: Float,
    val confidence: ThermalForecastConfidence,
    val visualDepthM: Float = ((endAltitudeKm - startAltitudeKm) * 1000f).coerceAtLeast(0f),
    val effectiveDepthM: Float = visualDepthM.coerceAtMost(280f),
    val pressureBottomHpa: Float? = null,
    val pressureTopHpa: Float? = null,
    val sourceQuality: ThermalLayerSourceQuality = ThermalLayerSourceQuality.REAL,
    val warnings: List<ThermalForecastWarning> = emptyList(),
)

object ThermalForecastEngine {
    fun analyze(input: ThermalForecastInput): ThermalForecastResult? {
        val validatedProfile = buildValidatedProfileWithSurface(input)
        val profile = validatedProfile.levels
        if (profile.size < 2) return null

        val warnings = linkedSetOf<ThermalForecastWarning>()
        warnings += validatedProfile.warnings
        val normalizedCinJKg = input.modelCinJKg?.let(::abs)
        if (input.boundaryLayerHeightM == null) warnings += ThermalForecastWarning.MISSING_PBL
        if (input.modelCinJKg == null) warnings += ThermalForecastWarning.MISSING_CIN
        if (input.liftedIndexC == null) warnings += ThermalForecastWarning.MISSING_LIFTED_INDEX

        val heatingEstimate = estimateThermalHeatingEstimate(input.heatingInput)
        val optimisticDryTopExcessC = if (optimisticDryTopAllowed(input, normalizedCinJKg)) {
            heatingEstimate.optimisticDryTopExcessC
        } else {
            heatingEstimate.nominalDryTopExcessC
        }
        val scenarios = listOf(
            ThermalScenario(HEATING_CONSERVATIVE, heatingEstimate.conservativeDryTopExcessC),
            ThermalScenario(HEATING_NOMINAL, heatingEstimate.nominalDryTopExcessC),
            ThermalScenario(
                HEATING_OPTIMISTIC,
                optimisticDryTopExcessC,
            ),
        )
        val scenarioResults = scenarios.associate { scenario ->
            scenario.name to analyzeTopScenario(input, profile, scenario.surfaceHeatingC)
        }
        val conservativeTop = scenarioResults.getValue(HEATING_CONSERVATIVE)
        val nominalTop = scenarioResults.getValue(HEATING_NOMINAL)
        val optimisticTop = scenarioResults.getValue(HEATING_OPTIMISTIC)

        val nominalThetaK = potentialTemperatureK(
            input.surfaceTemperatureC + scenarios[1].surfaceHeatingC,
            input.surfacePressureHpa,
        )
        val surfaceMixingRatio = satMixingRatioGKg(input.surfaceDewPointC, input.surfacePressureHpa)
        val lcl = findThermalLcl(nominalThetaK, surfaceMixingRatio, profile, input.elevationKm)
        val cclResults = analyzeCclHourly(input.toCclInput(profile))
        val primaryCcl = cclResults.primaryCclResult()
        val cclKm = primaryCcl?.takeIf { it.reachable }?.cclHeightMslM?.div(1000f)
        val cloudBaseStatus = resolveCloudBaseStatus(
            primaryCcl = primaryCcl,
            profile = profile,
            elevationKm = input.elevationKm,
            nominalTopKm = nominalTop.topKm,
        )
        if (cloudBaseStatus == ThermalCloudBaseStatus.UNKNOWN) {
            warnings += ThermalForecastWarning.MISSING_CCL
        }
        val cloudBaseKm = if (
            cloudBaseStatus == ThermalCloudBaseStatus.REACHABLE &&
            cclKm != null &&
            nominalTop.topKm >= cclKm - CLOUD_BASE_REACH_TOLERANCE_KM
        ) {
            cclKm
        } else {
            null
        }
        val moistTopKm = if (cloudBaseKm != null && primaryCcl != null) {
            findMoistTop(
                saturationPointTemperatureC = primaryCcl.cclTemperatureC ?: dryAdiabatTempC(nominalThetaK, lcl.pressureHpa),
                saturationPointPressureHpa = primaryCcl.cclPressureHpa ?: lcl.pressureHpa,
                profile = profile,
                cloudBaseKm = cloudBaseKm,
            )
        } else {
            null
        }

        val pblSanity = resolvePblSanity(
            input = input,
            profile = profile,
            nominalThetaK = nominalThetaK,
            dryTopKm = nominalTop.topKm,
        )
        if (pblSanity.exceeded) warnings += ThermalForecastWarning.PBL_EXCEEDED
        if (pblSanity.mountainOrographicOverride) warnings += ThermalForecastWarning.MOUNTAIN_OROGRAPHIC_OVERRIDE

        val damping = dampingFactor(
            input = input,
            profile = profile,
            topKm = nominalTop.topKm,
            effectiveRadiationWm2 = heatingEstimate.effectiveRadiationWm2,
            normalizedCinJKg = normalizedCinJKg,
        )
        val layerTopKm = resolveDisplayLayerTopKm(
            input = input,
            nominalTopKm = nominalTop.topKm,
            cloudBaseStatus = cloudBaseStatus,
            pblSanity = pblSanity,
        )
        val layers = buildLayers(
            input = input,
            profile = profile,
            scenarios = scenarios,
            nominalTopKm = layerTopKm,
            damping = damping,
            pblSanity = pblSanity,
        )
        val updraftLow = layers.maxOfOrNull { it.updraftLowMps } ?: 0f
        val updraftNominal = layers.maxOfOrNull { it.updraftNominalMps } ?: 0f
        val updraftHigh = layers.maxOfOrNull { it.updraftHighMps } ?: 0f
        val topLowKm = min(conservativeTop.topKm, nominalTop.bracketLower?.heightKm ?: nominalTop.topKm)
            .coerceAtLeast(input.elevationKm)
        val topHighKm = max(optimisticTop.topKm, nominalTop.bracketUpper?.heightKm ?: nominalTop.topKm)
            .coerceAtLeast(topLowKm)
        var confidence = resolveConfidence(
            profile = profile,
            nominalTop = nominalTop,
            damping = damping,
            input = input,
        )
        if (cloudBaseStatus == ThermalCloudBaseStatus.UNKNOWN) {
            confidence = confidence.capAt(ThermalForecastConfidence.LOW)
        }
        if (pblSanity.exceeded && !pblSanity.mountainOrographicOverride) {
            confidence = if (pblSanity.severelyExceeded || !hasClearDryThermalSupport(input)) {
                confidence.capAt(ThermalForecastConfidence.LOW)
            } else {
                confidence.capAt(ThermalForecastConfidence.MEDIUM)
            }
        } else if (pblSanity.mountainOrographicOverride) {
            confidence = confidence.capAt(ThermalForecastConfidence.MEDIUM)
        }
        val limitingReason = resolveLimitingReason(
            input = input,
            nominalTop = nominalTop,
            cloudBaseKm = cloudBaseKm,
            damping = damping,
        )
        val dryCapeCin = computeDryCapeCin(nominalThetaK, profile)

        return ThermalForecastResult(
            topLowKm = topLowKm,
            topNominalKm = nominalTop.topKm,
            topHighKm = topHighKm,
            updraftLowMps = updraftLow,
            updraftNominalMps = updraftNominal,
            updraftHighMps = updraftHigh,
            confidence = confidence,
            limitingReason = limitingReason,
            lowerSourceLevel = nominalTop.bracketLower?.toSourceLevel(),
            upperSourceLevel = nominalTop.bracketUpper?.toSourceLevel(),
            lclKm = lcl.heightKm,
            cclKm = cclKm,
            cloudBaseKm = cloudBaseKm,
            moistEquilibriumTopKm = moistTopKm,
            thermalEnergyJKg = computeDryThermalEnergy(nominalThetaK, profile, nominalTop.topKm),
            modelCapeJKg = input.modelCapeJKg,
            modelCinJKg = input.modelCinJKg,
            normalizedCinJKg = normalizedCinJKg,
            liftedIndexC = input.liftedIndexC,
            boundaryLayerHeightM = input.boundaryLayerHeightM,
            triggerExcessC = heatingEstimate.triggerExcessC,
            dryTopExcessC = heatingEstimate.nominalDryTopExcessC,
            effectiveRadiationWm2 = heatingEstimate.effectiveRadiationWm2,
            surfaceTemperatureC = input.surfaceTemperatureC,
            surfacePressureHpa = input.surfacePressureHpa,
            elevationKm = input.elevationKm,
            parcelStartTemperatureC = input.surfaceTemperatureC + heatingEstimate.nominalDryTopExcessC,
            dryTopAglM = ((nominalTop.topKm - input.elevationKm) * 1000f).coerceAtLeast(0f),
            computedCinJKg = dryCapeCin.cinJKg,
            cloudBaseStatus = cloudBaseStatus,
            warnings = warnings.toList(),
            usedPressureLevels = profile.map { it.toSourceLevel() },
            layers = layers,
            pressureLevelAltitudesKm = profile
                .filter { !it.isSynthetic && it.heightKm >= input.elevationKm + PRESSURE_LEVEL_MIN_AGL_KM }
                .map { it.heightKm }
                .distinctBy { (it * 1000f).toInt() }
                .sorted(),
        )
    }

    private fun buildValidatedProfileWithSurface(input: ThermalForecastInput): ValidatedThermalProfile {
        val warnings = mutableListOf<ThermalForecastWarning>()
        val surfaceLevel = ProfileLevel(
            pressureHpa = input.surfacePressureHpa,
            temperatureC = input.surfaceTemperatureC,
            dewPointC = input.surfaceDewPointC,
            heightKm = input.elevationKm,
            windSpeedKmh = null,
            isSynthetic = false,
        )
        val realEnvelope = input.profile
            .filter { !it.isSynthetic }
            .filter { it.geometricallyAboveSurface(input) }
            .map { it.heightKm }
            .takeIf { it.isNotEmpty() }
            ?.let { heights -> (heights.minOrNull() ?: input.elevationKm)..(heights.maxOrNull() ?: input.elevationKm) }

        val levels = input.profile
            .asSequence()
            .filterNot { level ->
                abs(level.heightKm - input.elevationKm) <= SURFACE_MATCH_TOLERANCE_KM ||
                    abs(level.pressureHpa - input.surfacePressureHpa) <= SURFACE_PRESSURE_TOLERANCE_HPA
            }
            .filter { it.geometricallyAboveSurface(input) }
            .filter { level ->
                !level.isSynthetic ||
                    realEnvelope == null ||
                    level.heightKm in (realEnvelope.start - HEIGHT_EPSILON_KM)..(realEnvelope.endInclusive + HEIGHT_EPSILON_KM)
            }
            .mapNotNull { level ->
                val aglM = (level.heightKm - input.elevationKm) * 1000f
                if (aglM <= NEAR_SURFACE_PROFILE_CHECK_M &&
                    abs(level.temperatureC - input.surfaceTemperatureC) > NEAR_SURFACE_MAX_T2M_DIFFERENCE_C
                ) {
                    warnings += ThermalForecastWarning.NEAR_SURFACE_PROFILE_MISMATCH
                    null
                } else {
                    level
                }
            }
            .sortedWith(
                compareByDescending<ProfileLevel> { it.pressureHpa }
                    .thenBy { it.isSynthetic },
            )
            .distinctBy { (it.pressureHpa * 10f).toInt() }
            .toList()

        return ValidatedThermalProfile(
            levels = (listOf(surfaceLevel) + levels).sortedByDescending(ProfileLevel::pressureHpa),
            warnings = warnings.distinct(),
        )
    }

    private fun ThermalForecastInput.toCclInput(profile: List<ProfileLevel>): CclHourlyInput {
        return CclHourlyInput(
            time = "",
            surfaceTemperatureC = surfaceTemperatureC,
            surfaceDewPointC = surfaceDewPointC,
            surfacePressureHpa = surfacePressureHpa,
            surfaceElevationM = elevationKm * 1000f,
            pressureLevels = profile
                .filter { level ->
                    abs(level.pressureHpa - surfacePressureHpa) > SURFACE_PRESSURE_TOLERANCE_HPA ||
                        abs(level.heightKm - elevationKm) > SURFACE_MATCH_TOLERANCE_KM
                }
                .map { level ->
                    CclPressureLevel(
                        pressureHpa = level.pressureHpa,
                        temperatureC = level.temperatureC,
                        dewPointC = level.dewPointC,
                        heightMslM = level.heightKm * 1000f,
                        isSynthetic = level.isSynthetic,
                    )
                },
        )
    }

    private fun analyzeTopScenario(
        input: ThermalForecastInput,
        profile: List<ProfileLevel>,
        surfaceHeatingC: Float,
    ): ThermalTopScenarioResult {
        val thetaK = potentialTemperatureK(input.surfaceTemperatureC + surfaceHeatingC, input.surfacePressureHpa)
        var previous = profile.first()
        var previousDiff = dryAdiabatTempC(thetaK, previous.pressureHpa) - previous.temperatureC
        if (previousDiff <= MIN_TOP_BUOYANCY_C) {
            return ThermalTopScenarioResult(
                topKm = input.elevationKm,
                bracketLower = previous,
                bracketUpper = profile.getOrNull(1),
                profileTopLimited = false,
            )
        }
        for (index in 1 until profile.size) {
            val current = profile[index]
            val currentDiff = dryAdiabatTempC(thetaK, current.pressureHpa) - current.temperatureC
            if (currentDiff <= MIN_TOP_BUOYANCY_C) {
                val fraction = (if (previousDiff - currentDiff > 0.001f) {
                    previousDiff / (previousDiff - currentDiff)
                } else {
                    0.5f
                }).coerceIn(0f, 1f)
                return ThermalTopScenarioResult(
                    topKm = previous.heightKm + fraction * (current.heightKm - previous.heightKm),
                    bracketLower = previous,
                    bracketUpper = current,
                    profileTopLimited = false,
                )
            }
            previous = current
            previousDiff = currentDiff
        }
        return ThermalTopScenarioResult(
            topKm = profile.last().heightKm,
            bracketLower = profile.getOrNull(profile.lastIndex - 1),
            bracketUpper = profile.last(),
            profileTopLimited = true,
        )
    }

    private fun buildLayers(
        input: ThermalForecastInput,
        profile: List<ProfileLevel>,
        scenarios: List<ThermalScenario>,
        nominalTopKm: Float,
        damping: ThermalDamping,
        pblSanity: PblSanity,
    ): List<ThermalForecastLayer> {
        if (nominalTopKm <= input.elevationKm + HEIGHT_EPSILON_KM) return emptyList()
        val heightProfile = profile.sortedBy(ProfileLevel::heightKm)
        val maxDisplayTopKm = min(nominalTopKm, highestRealProfileHeightKm(profile) ?: heightProfile.last().heightKm)
        return buildList {
            var startKm = input.elevationKm
            while (startKm < maxDisplayTopKm - HEIGHT_EPSILON_KM) {
                val endKm = min(startKm + THERMAL_LAYER_BIN_DEPTH_KM, maxDisplayTopKm)
                if (endKm <= startKm + HEIGHT_EPSILON_KM) break

                val midKm = (startKm + endKm) / 2f
                val midPoint = interpolateProfileAtHeight(heightProfile, midKm) ?: break
                val bottomPoint = interpolateProfileAtHeight(heightProfile, startKm) ?: break
                val topPoint = interpolateProfileAtHeight(heightProfile, endKm) ?: break
                val updrafts = scenarios.map { scenario ->
                    layerUpdraftMps(
                        input = input,
                        startKm = startKm,
                        endKm = endKm,
                        midPressureHpa = midPoint.pressureHpa,
                        envTempC = midPoint.temperatureC,
                        surfaceHeatingC = scenario.surfaceHeatingC,
                        damping = damping,
                        pblSanity = pblSanity,
                    )
                }
                val low = updrafts.minOrNull() ?: 0f
                val nominal = updrafts.getOrNull(1) ?: low
                val high = updrafts.maxOrNull() ?: nominal
                val visualDepthM = ((endKm - startKm) * 1000f).coerceAtLeast(0f)
                val effectiveDepthM = visualDepthM.coerceAtMost(MAX_EFFECTIVE_UPDRAFT_LAYER_DEPTH_M)
                val sourceQuality = listOf(midPoint.sourceQuality, bottomPoint.sourceQuality, topPoint.sourceQuality)
                    .maxByOrNull(ThermalLayerSourceQuality::ordinal)
                    ?: ThermalLayerSourceQuality.REAL
                if (high >= MIN_DISPLAY_UPDRAFT_MPS) {
                    add(
                        ThermalForecastLayer(
                            startAltitudeKm = startKm,
                            endAltitudeKm = endKm,
                            updraftLowMps = roundUpdraft(low),
                            updraftNominalMps = roundUpdraft(nominal),
                            updraftHighMps = roundUpdraft(high),
                            confidence = if (sourceQuality == ThermalLayerSourceQuality.SYNTHETIC) {
                                ThermalForecastConfidence.MEDIUM
                            } else {
                                ThermalForecastConfidence.HIGH
                            },
                            visualDepthM = visualDepthM,
                            effectiveDepthM = effectiveDepthM,
                            pressureBottomHpa = bottomPoint.pressureHpa,
                            pressureTopHpa = topPoint.pressureHpa,
                            sourceQuality = sourceQuality,
                            warnings = if (pblSanity.exceeded && !pblSanity.mountainOrographicOverride &&
                                pblSanity.pblTopKm != null &&
                                endKm > pblSanity.pblTopKm
                            ) {
                                listOf(ThermalForecastWarning.PBL_EXCEEDED)
                            } else {
                                emptyList()
                            },
                        ),
                    )
                }
                startKm = endKm
            }
        }
    }

    private fun layerUpdraftMps(
        input: ThermalForecastInput,
        startKm: Float,
        endKm: Float,
        midPressureHpa: Float,
        envTempC: Float,
        surfaceHeatingC: Float,
        damping: ThermalDamping,
        pblSanity: PblSanity,
    ): Float {
        val thetaK = potentialTemperatureK(input.surfaceTemperatureC + surfaceHeatingC, input.surfacePressureHpa)
        val parcelTempC = dryAdiabatTempC(thetaK, midPressureHpa)
        val buoyancyC = (parcelTempC - envTempC).coerceAtLeast(0f)
        if (buoyancyC < MIN_LAYER_BUOYANCY_C) return 0f

        val dzMeters = min(
            (endKm - startKm) * 1000f,
            MAX_EFFECTIVE_UPDRAFT_LAYER_DEPTH_M,
        )
        val buoyancyAccel = GRAVITY_MPS2 * buoyancyC / (envTempC + KELVIN_OFFSET)
        val raw = sqrt(max(0f, 2f * buoyancyAccel * dzMeters)) *
            UPDRAFT_SCALE *
            damping.factor *
            pblLayerFactor(startKm, endKm, pblSanity)
        return raw.coerceIn(0f, min(damping.maxUpdraftMps, dampingFactorLimit(damping.factor)))
    }

    private fun computeDryThermalEnergy(
        thetaK: Float,
        profile: List<ProfileLevel>,
        topKm: Float,
    ): Float {
        var energy = 0f
        for (index in 0 until profile.size - 1) {
            val lower = profile[index]
            val upper = profile[index + 1]
            if (lower.heightKm >= topKm) break
            val endKm = min(upper.heightKm, topKm)
            val dz = (endKm - lower.heightKm) * 1000f
            if (dz <= 0f) continue
            val midPressure = (lower.pressureHpa + upper.pressureHpa) / 2f
            val envTempC = (lower.temperatureC + upper.temperatureC) / 2f
            val parcelTempC = dryAdiabatTempC(thetaK, midPressure)
            val buoyancy = GRAVITY_MPS2 * (parcelTempC - envTempC) / (envTempC + KELVIN_OFFSET) * dz
            if (buoyancy > 0f) energy += buoyancy
        }
        return energy
    }

    private fun dampingFactor(
        input: ThermalForecastInput,
        profile: List<ProfileLevel>,
        topKm: Float,
        effectiveRadiationWm2: Float?,
        normalizedCinJKg: Float?,
    ): ThermalDamping {
        val radiation = effectiveRadiationWm2
        val radiationFactor = when {
            radiation == null -> 0.85f
            radiation < 150f -> 0.55f
            radiation < 300f -> 0.75f
            else -> 1f
        }
        val cloudPenalty = if (radiation != null) {
            val lowCloud = input.heatingInput.cloudCoverLowPercent ?: 0f
            when {
                lowCloud >= 85f -> 0.25f
                lowCloud >= 70f -> 0.15f
                lowCloud >= 50f -> 0.08f
                else -> 0f
            }
        } else {
            (
                ((input.heatingInput.cloudCoverLowPercent ?: 0f) / 100f) * 0.45f +
                    ((input.heatingInput.cloudCoverMidPercent ?: 0f) / 100f) * 0.25f +
                    ((input.heatingInput.cloudCoverHighPercent ?: 0f) / 100f) * 0.10f
                ).coerceIn(0f, 0.55f)
        }
        val profileCloudPenalty = if (radiation == null) {
            profile
                .filter { it.heightKm <= topKm + 0.2f }
                .mapNotNull(ProfileLevel::cloudCoverPercent)
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toFloat()
                ?.let { (it / 100f * 0.25f).coerceIn(0f, 0.35f) }
                ?: 0f
        } else {
            0f
        }
        val precipFactor = if ((input.heatingInput.precipitationMm ?: 0f) > 0.1f) 0.55f else 1f
        val shearKmh = lowLevelWindShearKmh(profile, topKm)
        val shearFactor = when {
            shearKmh == null -> 1f
            shearKmh > 40f -> 0.65f
            shearKmh > 25f -> 0.8f
            else -> 1f
        }
        val capeFactor = modelCapeStrengthFactor(input)
        val cinFactor = modelCinStrengthFactor(normalizedCinJKg)
        val liftedIndexFactor = liftedIndexStrengthFactor(input.liftedIndexC)
        val boundaryLayerFactor = boundaryLayerStrengthFactor(input.boundaryLayerHeightM)
        val heavyLowCloudFactor = heavyLowCloudStrengthFactor(input.heatingInput.cloudCoverLowPercent)

        val factor = radiationFactor *
            (1f - cloudPenalty) *
            (1f - profileCloudPenalty) *
            precipFactor *
            shearFactor *
            capeFactor *
            cinFactor *
            liftedIndexFactor *
            boundaryLayerFactor *
            heavyLowCloudFactor
        return ThermalDamping(
            factor = factor.coerceIn(0.25f, 1f),
            maxUpdraftMps = diagnosticUpdraftCapMps(input, normalizedCinJKg),
            weakRadiation = radiation != null && radiation < 150f,
            precipitation = (input.heatingInput.precipitationMm ?: 0f) > 0.1f,
            strongWindShear = shearKmh != null && shearKmh > 25f,
            zeroCape = input.modelCapeJKg != null && input.modelCapeJKg <= 0f,
            strongCin = (normalizedCinJKg ?: 0f) >= STRONG_CIN_JKG,
            heavyLowCloud = (input.heatingInput.cloudCoverLowPercent ?: 0f) >= HEAVY_LOW_CLOUD_PERCENT,
            shallowBoundaryLayer = input.boundaryLayerHeightM != null &&
                input.boundaryLayerHeightM < SHALLOW_BOUNDARY_LAYER_M,
            missingBoundaryLayer = input.boundaryLayerHeightM == null,
        )
    }

    private fun dampingFactorLimit(dampingFactor: Float): Float {
        return when {
            dampingFactor <= 0.30f -> 2.4f
            dampingFactor <= 0.45f -> 3.0f
            dampingFactor <= 0.65f -> 4.4f
            dampingFactor <= 0.85f -> 5.6f
            else -> MAX_UPDRAFT_MPS
        }
    }

    private fun modelCapeStrengthFactor(input: ThermalForecastInput): Float {
        val cape = input.modelCapeJKg ?: return 0.95f
        return when {
            cape <= 0f -> {
                if (hasClearDryThermalSupport(input)) {
                    0.68f
                } else {
                    0.50f
                }
            }
            cape < 100f -> 0.66f
            cape < 300f -> 0.82f
            cape < 800f -> 0.96f
            else -> 1.08f
        }
    }

    private fun hasClearDryThermalSupport(input: ThermalForecastInput): Boolean {
        val radiation = input.heatingInput.shortwaveRadiationWm2 ?: return false
        val lowCloud = input.heatingInput.cloudCoverLowPercent ?: 100f
        val precipitation = input.heatingInput.precipitationMm ?: 0f
        val boundaryLayer = input.boundaryLayerHeightM ?: return false
        return radiation >= DRY_THERMAL_SUPPORT_RADIATION_WM2 &&
            lowCloud <= DRY_THERMAL_SUPPORT_LOW_CLOUD_PERCENT &&
            precipitation <= 0.1f &&
            boundaryLayer >= DRY_THERMAL_SUPPORT_BOUNDARY_LAYER_M
    }

    private fun modelCinStrengthFactor(modelCinJKg: Float?): Float {
        val cin = modelCinJKg ?: return 1f
        return when {
            cin >= 250f -> 0.50f
            cin >= STRONG_CIN_JKG -> 0.68f
            cin >= 75f -> 0.85f
            cin >= 25f -> 0.95f
            else -> 1f
        }
    }

    private fun liftedIndexStrengthFactor(liftedIndexC: Float?): Float {
        val liftedIndex = liftedIndexC ?: return 1f
        return when {
            liftedIndex <= -4f -> 1.08f
            liftedIndex <= -2f -> 1.02f
            liftedIndex >= 6f -> 0.75f
            liftedIndex >= 3f -> 0.90f
            else -> 1f
        }
    }

    private fun boundaryLayerStrengthFactor(boundaryLayerHeightM: Float?): Float {
        val boundaryLayer = boundaryLayerHeightM ?: return 0.95f
        return when {
            boundaryLayer < 300f -> 0.60f
            boundaryLayer < SHALLOW_BOUNDARY_LAYER_M -> 0.85f
            boundaryLayer > 1800f -> 1.05f
            else -> 1f
        }
    }

    private fun heavyLowCloudStrengthFactor(lowCloudPercent: Float?): Float {
        val lowCloud = lowCloudPercent ?: return 1f
        return when {
            lowCloud >= HEAVY_LOW_CLOUD_PERCENT -> 0.65f
            lowCloud >= 60f -> 0.78f
            lowCloud >= 40f -> 0.90f
            else -> 1f
        }
    }

    private fun diagnosticUpdraftCapMps(input: ThermalForecastInput, normalizedCinJKg: Float?): Float {
        val cape = input.modelCapeJKg
        val baseCap = when {
            cape == null -> 5.5f
            cape <= 0f -> if (hasClearDryThermalSupport(input)) 4.2f else 3.2f
            cape < 100f -> 4.2f
            cape < 300f -> 5.0f
            cape < 800f -> 5.8f
            else -> 7.5f
        }
        val cinCap = normalizedCinJKg?.let { cin ->
            when {
                cin >= 250f -> 2.4f
                cin >= STRONG_CIN_JKG -> 3.2f
                cin >= 75f -> 4.5f
                else -> MAX_UPDRAFT_MPS
            }
        } ?: MAX_UPDRAFT_MPS
        val boundaryLayerCap = input.boundaryLayerHeightM?.let { boundaryLayer ->
            when {
                boundaryLayer < 300f -> 2.4f
                boundaryLayer < SHALLOW_BOUNDARY_LAYER_M -> 3.6f
                else -> MAX_UPDRAFT_MPS
            }
        } ?: MAX_UPDRAFT_MPS

        return minOf(baseCap, cinCap, boundaryLayerCap, MAX_UPDRAFT_MPS)
    }

    private fun lowLevelWindShearKmh(profile: List<ProfileLevel>, topKm: Float): Float? {
        val samples = profile
            .filter { it.heightKm <= topKm.coerceAtMost(profile.first().heightKm + 2f) + 0.01f }
            .mapNotNull(ProfileLevel::windSpeedKmh)
        if (samples.size < 2) return null
        return (samples.maxOrNull() ?: 0f) - (samples.minOrNull() ?: 0f)
    }

    private fun resolveConfidence(
        profile: List<ProfileLevel>,
        nominalTop: ThermalTopScenarioResult,
        damping: ThermalDamping,
        input: ThermalForecastInput,
    ): ThermalForecastConfidence {
        var score = 4
        val bracketDepthKm = nominalTop.bracketDepthKm
        val hasTopMoistureContext = hasHumidityOrCloudNearTop(profile, nominalTop.topKm)
        if (bracketDepthKm > 0.5f) score -= 1
        if (bracketDepthKm > 1.0f) score -= 1
        if (nominalTop.profileTopLimited) score -= 1
        if (nominalTop.bracketLower?.isSynthetic == true || nominalTop.bracketUpper?.isSynthetic == true) score -= 1
        if (!hasTopMoistureContext) score -= 1
        if (damping.precipitation) score -= 1
        if (damping.weakRadiation) score -= 1
        if (damping.strongWindShear) score -= 1
        if (damping.zeroCape && !hasClearDryThermalSupport(input)) score -= 1
        if (damping.strongCin) score -= 1
        if (damping.heavyLowCloud) score -= 1
        if (damping.shallowBoundaryLayer) score -= 1
        if (damping.missingBoundaryLayer) score -= 1
        if (input.modelCinJKg == null || input.liftedIndexC == null) score -= 1

        var confidence = when {
            score >= 3 -> ThermalForecastConfidence.HIGH
            score >= 1 -> ThermalForecastConfidence.MEDIUM
            else -> ThermalForecastConfidence.LOW
        }
        if (!hasTopMoistureContext || damping.missingBoundaryLayer) {
            confidence = confidence.capAt(ThermalForecastConfidence.MEDIUM)
        }
        return confidence
    }

    private fun hasHumidityOrCloudNearTop(profile: List<ProfileLevel>, topKm: Float): Boolean {
        return profile
            .filter { abs(it.heightKm - topKm) <= TOP_MOISTURE_CONTEXT_KM }
            .any { it.dewPointC != null || it.relativeHumidityPercent != null || it.cloudCoverPercent != null }
    }

    private fun resolveLimitingReason(
        input: ThermalForecastInput,
        nominalTop: ThermalTopScenarioResult,
        cloudBaseKm: Float?,
        damping: ThermalDamping,
    ): ThermalLimitingReason {
        return when {
            input.heatingInput.isDay == false -> ThermalLimitingReason.SURFACE_HEATING
            damping.precipitation -> ThermalLimitingReason.PRECIPITATION
            damping.weakRadiation -> ThermalLimitingReason.WEAK_RADIATION
            damping.heavyLowCloud -> ThermalLimitingReason.WEAK_RADIATION
            damping.strongCin -> ThermalLimitingReason.INVERSION
            damping.shallowBoundaryLayer -> ThermalLimitingReason.INVERSION
            nominalTop.profileTopLimited -> ThermalLimitingReason.PROFILE_TOP
            nominalTop.topKm <= input.elevationKm + SHALLOW_THERMAL_DEPTH_KM -> ThermalLimitingReason.INVERSION
            cloudBaseKm != null && cloudBaseKm <= nominalTop.topKm + CLOUD_BASE_REACH_TOLERANCE_KM ->
                ThermalLimitingReason.CLOUD_BASE
            damping.strongWindShear -> ThermalLimitingReason.WIND_SHEAR
            else -> ThermalLimitingReason.SURFACE_HEATING
        }
    }

    private fun findThermalLcl(
        parcelThetaK: Float,
        surfaceMixingRatio: Float,
        profile: List<ProfileLevel>,
        elevationKm: Float,
    ): PressureHeight {
        var previous: ProfileLevel? = null
        for (level in profile) {
            val dryTemp = dryAdiabatTempC(parcelThetaK, level.pressureHpa)
            val saturationMixingRatio = satMixingRatioGKg(dryTemp, level.pressureHpa)
            if (saturationMixingRatio <= surfaceMixingRatio) {
                previous?.let { prev ->
                    val previousDryTemp = dryAdiabatTempC(parcelThetaK, prev.pressureHpa)
                    val previousMixingRatio = satMixingRatioGKg(previousDryTemp, prev.pressureHpa)
                    val fraction = (if (previousMixingRatio - saturationMixingRatio > 0.001f) {
                        (previousMixingRatio - surfaceMixingRatio) /
                            (previousMixingRatio - saturationMixingRatio)
                    } else {
                        0.5f
                    }).coerceIn(0f, 1f)
                    return PressureHeight(
                        pressureHpa = prev.pressureHpa + fraction * (level.pressureHpa - prev.pressureHpa),
                        heightKm = (prev.heightKm + fraction * (level.heightKm - prev.heightKm))
                            .coerceAtLeast(elevationKm),
                    )
                }
                return PressureHeight(level.pressureHpa, level.heightKm.coerceAtLeast(elevationKm))
            }
            previous = level
        }
        val top = profile.last()
        return PressureHeight(top.pressureHpa, top.heightKm)
    }

    private fun findMoistTop(
        saturationPointTemperatureC: Float,
        saturationPointPressureHpa: Float,
        profile: List<ProfileLevel>,
        cloudBaseKm: Float,
    ): Float? {
        val aboveCloud = profile.filter { it.heightKm >= cloudBaseKm - HEIGHT_EPSILON_KM }
        if (aboveCloud.size < 2) return null
        var foundBuoyancy = false
        var previous: ProfileLevel? = null
        for (level in aboveCloud) {
            val moistTemp = moistAdiabatTempFromPointC(
                saturationPointTemperatureC,
                saturationPointPressureHpa,
                level.pressureHpa,
            )
            if (moistTemp > level.temperatureC) {
                foundBuoyancy = true
            } else if (foundBuoyancy) {
                previous?.let { prev ->
                    val previousMoistTemp = moistAdiabatTempFromPointC(
                        saturationPointTemperatureC,
                        saturationPointPressureHpa,
                        prev.pressureHpa,
                    )
                    val prevDiff = previousMoistTemp - prev.temperatureC
                    val currentDiff = moistTemp - level.temperatureC
                    val fraction = (if (prevDiff - currentDiff > 0.001f) {
                        prevDiff / (prevDiff - currentDiff)
                    } else {
                        0.5f
                    }).coerceIn(0f, 1f)
                    return prev.heightKm + fraction * (level.heightKm - prev.heightKm)
                }
                return level.heightKm
            }
            previous = level
        }
        return if (foundBuoyancy) aboveCloud.last().heightKm else null
    }

    private fun optimisticDryTopAllowed(input: ThermalForecastInput, normalizedCinJKg: Float?): Boolean {
        val lowCloud = input.heatingInput.cloudCoverLowPercent ?: return false
        val precipitation = input.heatingInput.precipitationMm ?: 0f
        val liftedIndex = input.liftedIndexC ?: return false
        return precipitation <= 0.1f &&
            lowCloud < 40f &&
            input.boundaryLayerHeightM != null &&
            input.modelCinJKg != null &&
            normalizedCinJKg != null &&
            normalizedCinJKg < STRONG_CIN_JKG &&
            liftedIndex < 6f
    }

    private fun resolveCloudBaseStatus(
        primaryCcl: CclHourlyResult?,
        profile: List<ProfileLevel>,
        elevationKm: Float,
        nominalTopKm: Float,
    ): ThermalCloudBaseStatus {
        val hasTemperatureProfile = profile.count { level ->
            !level.isSynthetic && level.heightKm >= elevationKm + PRESSURE_LEVEL_MIN_AGL_KM
        } >= 1
        val cclHeightKm = primaryCcl?.cclHeightMslM?.div(1000f)
            ?: return if (hasTemperatureProfile) ThermalCloudBaseStatus.NO_CCL else ThermalCloudBaseStatus.UNKNOWN
        return if (primaryCcl.reachable && nominalTopKm >= cclHeightKm - CLOUD_BASE_REACH_TOLERANCE_KM) {
            ThermalCloudBaseStatus.REACHABLE
        } else {
            ThermalCloudBaseStatus.UNREACHABLE
        }
    }

    private fun resolvePblSanity(
        input: ThermalForecastInput,
        profile: List<ProfileLevel>,
        nominalThetaK: Float,
        dryTopKm: Float,
    ): PblSanity {
        val boundaryLayerHeightM = input.boundaryLayerHeightM
        val pblTopKm = boundaryLayerHeightM?.let { input.elevationKm + (it / 1000f) }
        val dryTopAglM = ((dryTopKm - input.elevationKm) * 1000f).coerceAtLeast(0f)
        val exceeded = boundaryLayerHeightM != null &&
            dryTopAglM > (boundaryLayerHeightM * PBL_EXCEED_FACTOR) + PBL_EXCEED_MARGIN_M
        val severelyExceeded = boundaryLayerHeightM != null &&
            dryTopAglM > (boundaryLayerHeightM * PBL_SEVERE_EXCEED_FACTOR) + PBL_SEVERE_EXCEED_MARGIN_M
        val positiveRealIntervals = countPositiveRealDryIntervals(
            profile = profile,
            thetaK = nominalThetaK,
            elevationKm = input.elevationKm,
        )
        val mountainOrographicOverride = if (boundaryLayerHeightM == null) {
            false
        } else {
            exceeded &&
                input.elevationKm >= MOUNTAIN_OVERRIDE_MIN_ELEVATION_KM &&
                boundaryLayerHeightM >= MOUNTAIN_OVERRIDE_MIN_PBL_M &&
                (input.heatingInput.precipitationMm ?: 0f) <= 0.1f &&
                (input.heatingInput.cloudCoverLowPercent ?: 100f) < 40f &&
                positiveRealIntervals >= MOUNTAIN_OVERRIDE_MIN_POSITIVE_INTERVALS
        }
        return PblSanity(
            exceeded = exceeded,
            severelyExceeded = severelyExceeded,
            mountainOrographicOverride = mountainOrographicOverride,
            pblTopKm = pblTopKm,
        )
    }

    private fun resolveDisplayLayerTopKm(
        input: ThermalForecastInput,
        nominalTopKm: Float,
        cloudBaseStatus: ThermalCloudBaseStatus,
        pblSanity: PblSanity,
    ): Float {
        if (cloudBaseStatus != ThermalCloudBaseStatus.UNKNOWN || pblSanity.mountainOrographicOverride) {
            return nominalTopKm
        }
        val pblSanityTopKm = input.boundaryLayerHeightM?.let { boundaryLayer ->
            input.elevationKm + ((boundaryLayer * PBL_EXCEED_FACTOR) + PBL_EXCEED_MARGIN_M) / 1000f
        } ?: (input.elevationKm + MISSING_CCL_FALLBACK_DISPLAY_DEPTH_KM)
        return min(nominalTopKm, pblSanityTopKm)
    }

    private fun countPositiveRealDryIntervals(
        profile: List<ProfileLevel>,
        thetaK: Float,
        elevationKm: Float,
    ): Int {
        val realProfile = profile
            .filter { !it.isSynthetic }
            .sortedByDescending(ProfileLevel::pressureHpa)
        var count = 0
        for (index in 0 until realProfile.size - 1) {
            val lower = realProfile[index]
            val upper = realProfile[index + 1]
            if (upper.heightKm <= elevationKm + PRESSURE_LEVEL_MIN_AGL_KM) continue
            val midPressure = (lower.pressureHpa + upper.pressureHpa) / 2f
            val envTempC = (lower.temperatureC + upper.temperatureC) / 2f
            if (dryAdiabatTempC(thetaK, midPressure) - envTempC >= MIN_LAYER_BUOYANCY_C) {
                count += 1
            }
        }
        return count
    }

    private fun pblLayerFactor(startKm: Float, endKm: Float, pblSanity: PblSanity): Float {
        val pblTopKm = pblSanity.pblTopKm ?: return 1f
        if (!pblSanity.exceeded || pblSanity.mountainOrographicOverride) return 1f
        return when {
            startKm >= pblTopKm -> 0.55f
            endKm > pblTopKm -> 0.75f
            else -> 1f
        }
    }

    private fun ProfileLevel.geometricallyAboveSurface(input: ThermalForecastInput): Boolean {
        return heightKm >= input.elevationKm + PRESSURE_LEVEL_MIN_AGL_KM &&
            pressureHpa <= input.surfacePressureHpa - SURFACE_PRESSURE_TOLERANCE_HPA
    }

    private fun highestRealProfileHeightKm(profile: List<ProfileLevel>): Float? {
        return profile
            .filter { !it.isSynthetic }
            .maxOfOrNull(ProfileLevel::heightKm)
    }

    private fun interpolateProfileAtHeight(
        profileByHeight: List<ProfileLevel>,
        heightKm: Float,
    ): InterpolatedProfilePoint? {
        if (profileByHeight.isEmpty()) return null
        val first = profileByHeight.first()
        val last = profileByHeight.last()
        if (heightKm < first.heightKm - HEIGHT_EPSILON_KM || heightKm > last.heightKm + HEIGHT_EPSILON_KM) {
            return null
        }
        profileByHeight.firstOrNull { abs(it.heightKm - heightKm) <= HEIGHT_EPSILON_KM }?.let { exact ->
            return InterpolatedProfilePoint(
                pressureHpa = exact.pressureHpa,
                temperatureC = exact.temperatureC,
                sourceQuality = if (exact.isSynthetic) ThermalLayerSourceQuality.SYNTHETIC else ThermalLayerSourceQuality.REAL,
            )
        }
        for (index in 0 until profileByHeight.size - 1) {
            val lower = profileByHeight[index]
            val upper = profileByHeight[index + 1]
            if (heightKm in lower.heightKm..upper.heightKm) {
                val fraction = ((heightKm - lower.heightKm) / (upper.heightKm - lower.heightKm))
                    .coerceIn(0f, 1f)
                return InterpolatedProfilePoint(
                    pressureHpa = lower.pressureHpa + fraction * (upper.pressureHpa - lower.pressureHpa),
                    temperatureC = lower.temperatureC + fraction * (upper.temperatureC - lower.temperatureC),
                    sourceQuality = if (lower.isSynthetic || upper.isSynthetic) {
                        ThermalLayerSourceQuality.SYNTHETIC
                    } else {
                        ThermalLayerSourceQuality.INTERPOLATED
                    },
                )
            }
        }
        return null
    }

    private fun computeDryCapeCin(
        thetaK: Float,
        profile: List<ProfileLevel>,
    ): DryCapeCin {
        var cape = 0f
        var cin = 0f
        var foundPositiveBuoyancy = false
        val sortedProfile = profile.sortedByDescending(ProfileLevel::pressureHpa)
        for (index in 0 until sortedProfile.size - 1) {
            val lower = sortedProfile[index]
            val upper = sortedProfile[index + 1]
            val dz = (upper.heightKm - lower.heightKm) * 1000f
            if (dz <= 0f) continue
            val midPressure = (lower.pressureHpa + upper.pressureHpa) / 2f
            val envTempC = (lower.temperatureC + upper.temperatureC) / 2f
            val parcelTempC = dryAdiabatTempC(thetaK, midPressure)
            val energy = GRAVITY_MPS2 * (parcelTempC - envTempC) / (envTempC + KELVIN_OFFSET) * dz
            if (energy > 0f) {
                cape += energy
                foundPositiveBuoyancy = true
            } else if (!foundPositiveBuoyancy) {
                cin += -energy
            }
        }
        return DryCapeCin(
            capeJKg = cape.coerceAtLeast(0f),
            cinJKg = cin.coerceAtLeast(0f),
        )
    }

    private fun ThermalForecastConfidence.capAt(maxConfidence: ThermalForecastConfidence): ThermalForecastConfidence {
        return if (ordinal < maxConfidence.ordinal) maxConfidence else this
    }

    private fun ProfileLevel.toSourceLevel(): ThermalSourceLevel {
        return ThermalSourceLevel(
            pressureHpa = pressureHpa,
            altitudeKm = heightKm,
            isSynthetic = isSynthetic,
        )
    }

    private fun roundUpdraft(value: Float): Float {
        return ((value * 10f).toInt() / 10f).coerceIn(0f, MAX_UPDRAFT_MPS)
    }

    private data class ThermalScenario(
        val name: String,
        val surfaceHeatingC: Float,
    )

    private data class ThermalTopScenarioResult(
        val topKm: Float,
        val bracketLower: ProfileLevel?,
        val bracketUpper: ProfileLevel?,
        val profileTopLimited: Boolean,
    ) {
        val bracketDepthKm: Float
            get() = if (bracketLower != null && bracketUpper != null) {
                abs(bracketUpper.heightKm - bracketLower.heightKm)
            } else {
                0f
            }
    }

    private data class ThermalDamping(
        val factor: Float,
        val maxUpdraftMps: Float,
        val weakRadiation: Boolean,
        val precipitation: Boolean,
        val strongWindShear: Boolean,
        val zeroCape: Boolean,
        val strongCin: Boolean,
        val heavyLowCloud: Boolean,
        val shallowBoundaryLayer: Boolean,
        val missingBoundaryLayer: Boolean,
    )

    private data class ValidatedThermalProfile(
        val levels: List<ProfileLevel>,
        val warnings: List<ThermalForecastWarning>,
    )

    private data class PblSanity(
        val exceeded: Boolean,
        val severelyExceeded: Boolean,
        val mountainOrographicOverride: Boolean,
        val pblTopKm: Float?,
    )

    private data class InterpolatedProfilePoint(
        val pressureHpa: Float,
        val temperatureC: Float,
        val sourceQuality: ThermalLayerSourceQuality,
    )

    private data class DryCapeCin(
        val capeJKg: Float,
        val cinJKg: Float,
    )

    private data class PressureHeight(
        val pressureHpa: Float,
        val heightKm: Float,
    )

    private const val HEATING_CONSERVATIVE = "conservative"
    private const val HEATING_NOMINAL = "nominal"
    private const val HEATING_OPTIMISTIC = "optimistic"
    private const val GRAVITY_MPS2 = 9.81f
    private const val KELVIN_OFFSET = 273.15f
    private const val UPDRAFT_SCALE = 0.56f
    private const val MAX_UPDRAFT_MPS = 10f
    private const val MAX_EFFECTIVE_UPDRAFT_LAYER_DEPTH_M = 230f
    private const val THERMAL_LAYER_BIN_DEPTH_KM = 0.23f
    private const val MIN_DISPLAY_UPDRAFT_MPS = 0.2f
    private const val MIN_LAYER_BUOYANCY_C = 0.25f
    private const val MIN_TOP_BUOYANCY_C = 0f
    private const val HEIGHT_EPSILON_KM = 0.001f
    private const val SURFACE_MATCH_TOLERANCE_KM = 0.03f
    private const val SURFACE_PRESSURE_TOLERANCE_HPA = 1f
    private const val PRESSURE_LEVEL_MIN_AGL_KM = 0.02f
    private const val NEAR_SURFACE_PROFILE_CHECK_M = 80f
    private const val NEAR_SURFACE_MAX_T2M_DIFFERENCE_C = 2.5f
    private const val CLOUD_BASE_REACH_TOLERANCE_KM = 0.05f
    private const val SHALLOW_THERMAL_DEPTH_KM = 0.2f
    private const val TOP_MOISTURE_CONTEXT_KM = 0.75f
    private const val DRY_THERMAL_SUPPORT_RADIATION_WM2 = 500f
    private const val DRY_THERMAL_SUPPORT_LOW_CLOUD_PERCENT = 40f
    private const val HEAVY_LOW_CLOUD_PERCENT = 80f
    private const val DRY_THERMAL_SUPPORT_BOUNDARY_LAYER_M = 700f
    private const val SHALLOW_BOUNDARY_LAYER_M = 700f
    private const val STRONG_CIN_JKG = 150f
    private const val PBL_EXCEED_FACTOR = 1.35f
    private const val PBL_EXCEED_MARGIN_M = 300f
    private const val PBL_SEVERE_EXCEED_FACTOR = 1.8f
    private const val PBL_SEVERE_EXCEED_MARGIN_M = 600f
    private const val MOUNTAIN_OVERRIDE_MIN_ELEVATION_KM = 0.8f
    private const val MOUNTAIN_OVERRIDE_MIN_PBL_M = 700f
    private const val MOUNTAIN_OVERRIDE_MIN_POSITIVE_INTERVALS = 2
    private const val MISSING_CCL_FALLBACK_DISPLAY_DEPTH_KM = 1.2f
}
