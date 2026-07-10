package com.cloudbasepredictor.domain.forecast

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThermalForecastEngineTest {

    private val heatingInput = SurfaceHeatingInput(
        hourOfDay = 13,
        shortwaveRadiationWm2 = 700f,
        cloudCoverLowPercent = 10f,
        cloudCoverMidPercent = 5f,
        cloudCoverHighPercent = 0f,
        precipitationMm = 0f,
        isDay = true,
    )

    @Test
    fun analyze_includesExplicitSurfaceLevelForLowLevelBuoyancy() {
        val result = analyze(profile = crossingProfileWithoutSurface())

        assertNotNull(result)
        assertTrue(result.layers.isNotEmpty(), "Surface-to-first-level layer should be present")
        assertEquals(
            ELEVATION_KM,
            result.layers.first().startAltitudeKm,
            0.001f,
            "Thermic layers should start at launch elevation, not at first pressure level",
        )
        assertTrue(result.topNominalKm > ELEVATION_KM, "Top should be above launch elevation")
    }

    @Test
    fun analyze_topRangeIncludesPressureLevelBracket() {
        val result = analyze(profile = crossingProfileWithoutSurface())

        assertNotNull(result)
        val lower = requireNotNull(result.lowerSourceLevel)
        val upper = requireNotNull(result.upperSourceLevel)

        assertTrue(result.topNominalKm >= lower.altitudeKm - 0.001f, "Nominal top should be inside its raw bracket")
        assertTrue(result.topNominalKm <= upper.altitudeKm + 0.001f, "Nominal top should be inside its raw bracket")
        assertTrue(result.topLowKm <= lower.altitudeKm + 0.001f, "Low top should include the lower source level")
        assertTrue(result.topHighKm >= upper.altitudeKm - 0.001f, "High top should include the upper source level")
        assertTrue(lower.pressureHpa > upper.pressureHpa, "Source pressures should be ordered by altitude")
    }

    @Test
    fun analyze_coarseVerticalSpacingWidensTopRangeAndLowersConfidence() {
        val dense = analyze(profile = denseCrossingProfile())
        val coarse = analyze(profile = coarseCrossingProfile())

        assertNotNull(dense)
        assertNotNull(coarse)

        val denseWidthKm = dense.topHighKm - dense.topLowKm
        val coarseWidthKm = coarse.topHighKm - coarse.topLowKm
        assertTrue(coarseWidthKm > denseWidthKm, "Coarse pressure spacing should widen top uncertainty")
        assertTrue(
            coarse.confidence.ordinal >= dense.confidence.ordinal,
            "Coarse pressure spacing should not have higher confidence than dense spacing",
        )
    }

    @Test
    fun analyze_modelCapeAndInhibitionCalibrateUpdraftSpeed() {
        val noCape = analyze(
            profile = crossingProfileWithoutSurface(),
            modelCapeJKg = 0f,
            modelCinJKg = 20f,
        )
        val highCape = analyze(
            profile = crossingProfileWithoutSurface(),
            modelCapeJKg = 2500f,
            modelCinJKg = 0f,
        )

        assertNotNull(noCape)
        assertNotNull(highCape)
        assertTrue(noCape.updraftHighMps <= 4.2f, "Zero CAPE should keep optimistic lift capped")
        assertTrue(highCape.updraftNominalMps > noCape.updraftNominalMps, "High CAPE should allow stronger calibrated lift")
    }

    @Test
    fun analyze_greifenburgLikeZeroCapeProfile_staysWeakToModerate() {
        val result = analyze(
            profile = greifenburgIconProfile(),
            heatingInput = SurfaceHeatingInput(
                hourOfDay = 15,
                shortwaveRadiationWm2 = 817.2f,
                cloudCoverLowPercent = 7f,
                cloudCoverMidPercent = 0f,
                cloudCoverHighPercent = 0f,
                precipitationMm = 0f,
                isDay = true,
            ),
            modelCapeJKg = 0f,
            modelCinJKg = 0f,
            liftedIndexC = null,
            boundaryLayerHeightM = null,
            surfaceTemperatureC = 6.5f,
            surfaceDewPointC = -4.3f,
            surfacePressureHpa = 833.0f,
            elevationKm = 1.723f,
        )

        assertNotNull(result)
        assertTrue(
            result.updraftNominalMps <= 3.6f,
            "Nominal lift should stay practical with missing PBL, got ${result.updraftNominalMps}",
        )
        assertTrue(result.updraftHighMps < 5f, "Optimistic lift should not hit the old 10 m/s cap")
        assertTrue(result.confidence.ordinal > 0, "Missing PBL and zero CAPE should lower confidence")
    }

    @Test
    fun analyze_precipitationCloudAndWindShearDampUpdraftAndConfidence() {
        val baseline = analyze(profile = crossingProfileWithoutSurface())
        val degraded = analyze(
            profile = crossingProfileWithoutSurface(windSpeedsKmh = listOf(5f, 12f, 45f, 55f, 60f)),
            heatingInput = heatingInput.copy(
                shortwaveRadiationWm2 = 160f,
                cloudCoverLowPercent = 90f,
                cloudCoverMidPercent = 70f,
                precipitationMm = 1f,
            ),
        )

        assertNotNull(baseline)
        assertNotNull(degraded)
        assertTrue(degraded.updraftNominalMps < baseline.updraftNominalMps, "Degraded weather should lower updraft")
        assertTrue(
            degraded.confidence.ordinal >= baseline.confidence.ordinal,
            "Degraded weather should not improve confidence",
        )
        assertTrue(
            degraded.limitingReason in setOf(
                ThermalLimitingReason.PRECIPITATION,
                ThermalLimitingReason.WEAK_RADIATION,
                ThermalLimitingReason.WIND_SHEAR,
            ),
            "Main limiter should report one of the degrading factors",
        )
    }

    @Test
    fun analyze_knownStrongRadiation_doesNotDoublePenalizeHighAndMidCloud() {
        val clear = analyze(
            profile = crossingProfileWithoutSurface(),
            heatingInput = heatingInput.copy(
                shortwaveRadiationWm2 = 744f,
                cloudCoverLowPercent = 0f,
                cloudCoverMidPercent = 0f,
                cloudCoverHighPercent = 0f,
            ),
        )
        val thinHighMidCloud = analyze(
            profile = crossingProfileWithoutSurface(),
            heatingInput = heatingInput.copy(
                shortwaveRadiationWm2 = 744f,
                cloudCoverLowPercent = 0f,
                cloudCoverMidPercent = 100f,
                cloudCoverHighPercent = 100f,
            ),
        )

        assertNotNull(clear)
        assertNotNull(thinHighMidCloud)
        assertEquals(clear.updraftNominalMps, thinHighMidCloud.updraftNominalMps, 0.001f)
        assertEquals(clear.updraftHighMps, thinHighMidCloud.updraftHighMps, 0.001f)
    }

    @Test
    fun analyze_missingNewOpenMeteoFieldsStillProducesLowerConfidenceForecast() {
        val result = analyze(
            profile = crossingProfileWithoutSurface(includeMoistureDiagnostics = false),
            boundaryLayerHeightM = null,
        )

        assertNotNull(result)
        assertTrue(result.topNominalKm > ELEVATION_KM, "Forecast should still have a usable top")
        assertTrue(result.confidence.ordinal > 0, "Missing raw humidity/cloud/PBL fields should lower confidence")
    }

    @Test
    fun analyze_unreachableSharedCclDoesNotCreateCloudBase() {
        val result = analyze(
            profile = listOf(
                level(900f, 17f, 6f, 1.00f),
                level(850f, 4f, -2f, 1.50f),
                level(800f, -2f, -8f, 2.00f),
            ),
            surfaceTemperatureC = 9f,
            surfaceDewPointC = 7f,
            surfacePressureHpa = 955f,
            elevationKm = ELEVATION_KM,
        )

        assertNotNull(result)
        assertNull(result.cclKm, "Unreachable CCL should not be exposed as thermic CCL")
        assertNull(result.cloudBaseKm, "Unreachable CCL should not create cloud base")
    }

    @Test
    fun surfaceHeating_triggerExcessDoesNotBecomeDryTopExcess() {
        val result = analyze(
            profile = denseCrossingProfile(),
            heatingInput = heatingInput.copy(
                shortwaveRadiationWm2 = 900f,
                previousShortwaveRadiationWm2 = 850f,
                cloudCoverLowPercent = 0f,
                cloudCoverMidPercent = 0f,
                cloudCoverHighPercent = 0f,
            ),
            modelCinJKg = 0f,
        )

        assertNotNull(result)
        assertTrue(result.triggerExcessC >= 6f, "Trigger excess should expose the local bubble assumption")
        assertTrue(result.dryTopExcessC <= 3f, "Nominal dry-top excess should be entrainment-limited")
        assertTrue(
            result.dryTopExcessC < result.triggerExcessC,
            "Dry-top excess should not reuse the full trigger excess",
        )
    }

    @Test
    fun dryTopAbovePbl_lowersConfidenceAndDampsAbovePbl() {
        val result = analyze(
            profile = crossingProfileWithoutSurface(),
            boundaryLayerHeightM = 500f,
            modelCinJKg = 0f,
            liftedIndexC = 4f,
        )

        assertNotNull(result)
        assertTrue(result.warnings.contains(ThermalForecastWarning.PBL_EXCEEDED))
        assertEquals(ThermalForecastConfidence.LOW, result.confidence)
        assertTrue(
            result.layers.any { ThermalForecastWarning.PBL_EXCEEDED in it.warnings },
            "Layers above the exceeded PBL should carry a PBL warning",
        )
    }

    @Test
    fun clearDryThermalSupport_pblExceededCapsAtMediumNotLow() {
        val result = analyze(
            profile = deepDryThermalProfile(),
            heatingInput = heatingInput.copy(
                shortwaveRadiationWm2 = 820f,
                previousShortwaveRadiationWm2 = 780f,
                cloudCoverLowPercent = 0f,
                cloudCoverMidPercent = 0f,
                cloudCoverHighPercent = 0f,
                precipitationMm = 0f,
            ),
            boundaryLayerHeightM = 1200f,
            modelCapeJKg = 0f,
            modelCinJKg = 0f,
            liftedIndexC = 4.5f,
        )

        assertNotNull(result)
        assertTrue(result.warnings.contains(ThermalForecastWarning.PBL_EXCEEDED))
        assertEquals(
            ThermalForecastConfidence.MEDIUM,
            result.confidence,
            "Clear midday dry support should be uncertainty, not automatic low confidence",
        )
    }

    @Test
    fun cinNegativeOrPositive_normalizesToPositiveInhibition() {
        val negative = analyze(profile = crossingProfileWithoutSurface(), modelCinJKg = -80f)
        val positive = analyze(profile = crossingProfileWithoutSurface(), modelCinJKg = 80f)
        val missing = analyze(profile = crossingProfileWithoutSurface(), modelCinJKg = null)

        assertNotNull(negative)
        assertNotNull(positive)
        assertNotNull(missing)
        assertEquals(80f, negative.normalizedCinJKg!!, 0.001f)
        assertEquals(80f, positive.normalizedCinJKg!!, 0.001f)
        assertNull(missing.normalizedCinJKg)
        assertTrue(missing.warnings.contains(ThermalForecastWarning.MISSING_CIN))
    }

    @Test
    fun missingPblCinAndLiftedIndex_notNeutral() {
        val result = analyze(
            profile = crossingProfileWithoutSurface(),
            modelCinJKg = null,
            liftedIndexC = null,
            boundaryLayerHeightM = null,
        )

        assertNotNull(result)
        assertTrue(result.warnings.contains(ThermalForecastWarning.MISSING_PBL))
        assertTrue(result.warnings.contains(ThermalForecastWarning.MISSING_CIN))
        assertTrue(result.warnings.contains(ThermalForecastWarning.MISSING_LIFTED_INDEX))
        assertTrue(result.confidence.ordinal > 0, "Missing model diagnostics should prevent high confidence")
    }

    @Test
    fun nearSurfacePressureLevelMismatch_isDroppedOrDeweighted() {
        val mismatchedNearSurfaceHeightKm = ELEVATION_KM + 0.05f
        val result = analyze(
            profile = listOf(
                level(953.5f, 8f, 2f, mismatchedNearSurfaceHeightKm),
                level(920f, 19f, 8f, 0.90f),
                level(880f, 16f, 6f, 1.20f),
                level(840f, 12f, 2f, 1.60f),
            ),
        )

        assertNotNull(result)
        assertTrue(result.warnings.contains(ThermalForecastWarning.NEAR_SURFACE_PROFILE_MISMATCH))
        assertTrue(
            result.pressureLevelAltitudesKm.none { abs(it - mismatchedNearSurfaceHeightKm) < 0.001f },
            "Mismatched near-surface pressure level should not survive profile validation",
        )
    }

    @Test
    fun missingPressureDewpointProfile_doesNotForceUnknownCloudBaseOrLowConfidence() {
        val result = analyze(
            profile = crossingProfileWithoutSurface(includeMoistureDiagnostics = false)
                .map { it.copy(dewPointC = null) },
        )

        assertNotNull(result)
        assertFalse(
            result.cloudBaseStatus == ThermalCloudBaseStatus.UNKNOWN,
            "Surface dewpoint and temperature profile are enough to classify CCL status",
        )
        assertFalse(result.warnings.contains(ThermalForecastWarning.MISSING_CCL))
        assertEquals(ThermalForecastConfidence.MEDIUM, result.confidence)
    }

    @Test
    fun extrapolatedSyntheticLevels_cannotExtendDryTopAboveRealEnvelope() {
        val realProfileTopKm = 1.50f
        val result = analyze(
            profile = listOf(
                level(920f, 18f, 8f, 0.90f),
                level(850f, 14f, 4f, realProfileTopKm),
                ProfileLevel(
                    pressureHpa = 700f,
                    temperatureC = -16f,
                    dewPointC = -24f,
                    heightKm = 3.20f,
                    relativeHumidityPercent = 30f,
                    cloudCoverPercent = 0f,
                    windSpeedKmh = 18f,
                    isSynthetic = true,
                ),
            ),
        )

        assertNotNull(result)
        assertTrue(result.topNominalKm <= realProfileTopKm, "Synthetic extrapolation must not lift dry-top above real data")
        assertTrue(result.layers.all { it.endAltitudeKm <= realProfileTopKm + 0.001f })
    }

    private fun analyze(
        profile: List<ProfileLevel>,
        heatingInput: SurfaceHeatingInput = this.heatingInput,
        modelCapeJKg: Float? = 500f,
        modelCinJKg: Float? = 20f,
        liftedIndexC: Float? = -1.5f,
        boundaryLayerHeightM: Float? = 1600f,
        surfaceTemperatureC: Float = SURFACE_TEMPERATURE_C,
        surfaceDewPointC: Float = SURFACE_DEW_POINT_C,
        surfacePressureHpa: Float = SURFACE_PRESSURE_HPA,
        elevationKm: Float = ELEVATION_KM,
    ): ThermalForecastResult? {
        return ThermalForecastEngine.analyze(
            ThermalForecastInput(
                profile = profile,
                surfaceTemperatureC = surfaceTemperatureC,
                surfaceDewPointC = surfaceDewPointC,
                surfacePressureHpa = surfacePressureHpa,
                elevationKm = elevationKm,
                heatingInput = heatingInput,
                modelCapeJKg = modelCapeJKg,
                modelCinJKg = modelCinJKg,
                liftedIndexC = liftedIndexC,
                boundaryLayerHeightM = boundaryLayerHeightM,
            ),
        )
    }

    private fun crossingProfileWithoutSurface(
        includeMoistureDiagnostics: Boolean = true,
        windSpeedsKmh: List<Float> = listOf(8f, 12f, 16f, 20f, 24f),
    ): List<ProfileLevel> {
        return listOf(
            level(950f, 18f, 8f, 0.65f, includeMoistureDiagnostics, windSpeedsKmh[0]),
            level(900f, 17f, 7f, 1.00f, includeMoistureDiagnostics, windSpeedsKmh[1]),
            level(850f, 14f, 4f, 1.50f, includeMoistureDiagnostics, windSpeedsKmh[2]),
            level(800f, 18f, 2f, 2.00f, includeMoistureDiagnostics, windSpeedsKmh[3]),
            level(750f, 21f, -4f, 2.60f, includeMoistureDiagnostics, windSpeedsKmh[4]),
        )
    }

    private fun denseCrossingProfile(): List<ProfileLevel> {
        return listOf(
            level(950f, 18f, 8f, 0.65f),
            level(925f, 17f, 7f, 0.82f),
            level(900f, 16f, 6f, 1.00f),
            level(875f, 15f, 5f, 1.25f),
            level(850f, 14f, 4f, 1.50f),
            level(825f, 16f, 3f, 1.75f),
            level(800f, 18f, 2f, 2.00f),
            level(775f, 19f, 0f, 2.30f),
            level(750f, 21f, -4f, 2.60f),
        )
    }

    private fun coarseCrossingProfile(): List<ProfileLevel> {
        return listOf(
            level(950f, 18f, 8f, 0.65f),
            level(900f, 17f, 7f, 1.00f),
            level(800f, 18f, 2f, 2.00f),
            level(700f, 22f, -8f, 3.30f),
        )
    }

    private fun greifenburgIconProfile(): List<ProfileLevel> {
        return listOf(
            ProfileLevel(
                pressureHpa = 800f,
                temperatureC = 1.5f,
                dewPointC = -5f,
                heightKm = 2.03687f,
                relativeHumidityPercent = 60f,
                cloudCoverPercent = 0f,
                windSpeedKmh = 14f,
            ),
            ProfileLevel(
                pressureHpa = 700f,
                temperatureC = -6.2f,
                dewPointC = -15f,
                heightKm = 3.096f,
                relativeHumidityPercent = 40f,
                cloudCoverPercent = 0f,
                windSpeedKmh = 18f,
            ),
            ProfileLevel(
                pressureHpa = 600f,
                temperatureC = -12.3f,
                dewPointC = -25f,
                heightKm = 4.287f,
                relativeHumidityPercent = 25f,
                cloudCoverPercent = 0f,
                windSpeedKmh = 22f,
            ),
            ProfileLevel(
                pressureHpa = 500f,
                temperatureC = -21.5f,
                dewPointC = -35f,
                heightKm = 5.656f,
                relativeHumidityPercent = 20f,
                cloudCoverPercent = 0f,
                windSpeedKmh = 28f,
            ),
        )
    }

    private fun deepDryThermalProfile(): List<ProfileLevel> {
        return listOf(
            level(950f, 18f, 6f, 0.65f),
            level(900f, 14f, 2f, 1.05f),
            level(850f, 10f, -2f, 1.55f),
            level(800f, 6f, -6f, 2.05f),
            level(750f, 2f, -10f, 2.60f),
            level(700f, 0f, -14f, 3.25f),
        )
    }

    private fun level(
        pressureHpa: Float,
        temperatureC: Float,
        dewPointC: Float,
        heightKm: Float,
        includeMoistureDiagnostics: Boolean = true,
        windSpeedKmh: Float = 12f,
    ): ProfileLevel {
        return ProfileLevel(
            pressureHpa = pressureHpa,
            temperatureC = temperatureC,
            dewPointC = dewPointC,
            heightKm = heightKm,
            relativeHumidityPercent = if (includeMoistureDiagnostics) 45f else null,
            cloudCoverPercent = if (includeMoistureDiagnostics) 5f else null,
            windSpeedKmh = windSpeedKmh,
            isSynthetic = false,
        )
    }

    private companion object {
        const val SURFACE_TEMPERATURE_C = 22f
        const val SURFACE_DEW_POINT_C = 10f
        const val SURFACE_PRESSURE_HPA = 955f
        const val ELEVATION_KM = 0.58f
    }
}
