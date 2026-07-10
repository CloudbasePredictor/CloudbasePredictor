package com.cloudbasepredictor.domain.forecast

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden regression test locking the engine bit-exactly on every test target.
 *
 * The fixture (embedded as [goldenFixtureJson], canonical copy in
 * `src/commonTest/resources/brauneck_icon_seamless.json`) is exported by the
 * app-side `EngineFixtureExportTest`: it contains the converted
 * `HourlyForecastData` for a real Open-Meteo response plus the
 * [ThermalForecastEngine.analyze] and [analyzeParcel] outputs for every
 * daytime hour, serialized with full double precision.
 *
 * This test drives the engine from the fixture's converted data exactly like
 * the production chart builders and asserts every output field at
 * **tolerance 0** (exact data-class equality of the widened doubles). Wasm
 * `f32` arithmetic is spec-deterministic, so the wasmJs run must match the
 * JVM run bit for bit; a mismatch on any target means the engine's float
 * semantics diverged.
 */
class GoldenFixtureTest {

    private val fixture: GoldenFixture by lazy {
        fixtureJsonFormat.decodeFromString(goldenFixtureJson)
    }

    @Test
    fun engineReproducesEveryThermalForecastEntryExactly() {
        val actual = runPipeline().thermalForecasts
        val expected = fixture.thermalForecasts

        assertTrue(expected.isNotEmpty(), "Fixture must contain thermal forecast entries")
        assertEquals(
            expected.map { "${it.date}@${it.hour}" },
            actual.map { "${it.date}@${it.hour}" },
            "Engine must produce the same daytime hours as the fixture",
        )
        expected.zip(actual).forEachIndexed { index, (expectedEntry, actualEntry) ->
            assertEquals(
                expectedEntry.result,
                actualEntry.result,
                "thermal[$index ${expectedEntry.date}@${expectedEntry.hour}] must match at tolerance 0",
            )
        }
    }

    @Test
    fun engineReproducesEveryParcelAnalysisEntryExactly() {
        val actual = runPipeline().parcelAnalyses
        val expected = fixture.parcelAnalyses

        assertTrue(expected.isNotEmpty(), "Fixture must contain parcel analysis entries")
        assertEquals(
            expected.map { "${it.date}@${it.hour}" },
            actual.map { "${it.date}@${it.hour}" },
            "analyzeParcel must produce the same daytime hours as the fixture",
        )
        expected.zip(actual).forEachIndexed { index, (expectedEntry, actualEntry) ->
            assertEquals(
                expectedEntry.result,
                actualEntry.result,
                "parcel[$index ${expectedEntry.date}@${expectedEntry.hour}] must match at tolerance 0",
            )
        }
    }

    // ── Pipeline driver: mirrors the app-side EngineFixtureExportTest /
    // ForecastChartBuilders.buildThermicChartFromData mapping ──

    private data class PipelineOutput(
        val thermalForecasts: List<ThermalForecastEntry>,
        val parcelAnalyses: List<ParcelAnalysisEntry>,
    )

    private fun runPipeline(): PipelineOutput {
        val thermalForecasts = mutableListOf<ThermalForecastEntry>()
        val parcelAnalyses = mutableListOf<ParcelAnalysisEntry>()

        forEachDaytimeHour(fixture.hourlyForecastData) {
            hp, profile, surfaceTemp, surfaceDew, surfacePressure, elevationKm, heatingInput ->
            ThermalForecastEngine.analyze(
                ThermalForecastInput(
                    profile = profile,
                    surfaceTemperatureC = surfaceTemp,
                    surfaceDewPointC = surfaceDew,
                    surfacePressureHpa = surfacePressure,
                    elevationKm = elevationKm,
                    heatingInput = heatingInput,
                    modelCapeJKg = hp.capeJKg?.toFloat(),
                    modelCinJKg = hp.convectiveInhibitionJKg?.toFloat(),
                    liftedIndexC = hp.liftedIndexC?.toFloat(),
                    boundaryLayerHeightM = hp.boundaryLayerHeightM?.toFloat(),
                ),
            )?.let { forecast ->
                thermalForecasts += ThermalForecastEntry(hp.date, hp.hour, forecast.toDto())
            }

            analyzeParcel(
                profile = profile,
                surfaceTemperatureC = surfaceTemp,
                surfaceDewPointC = surfaceDew,
                surfacePressureHpa = surfacePressure,
                elevationKm = elevationKm,
                heatingInput = heatingInput,
                modelCapeJKg = hp.capeJKg?.toFloat(),
            )?.let { parcel ->
                parcelAnalyses += ParcelAnalysisEntry(hp.date, hp.hour, parcel.toDto())
            }
        }

        return PipelineOutput(thermalForecasts, parcelAnalyses)
    }

    private inline fun forEachDaytimeHour(
        hourlyData: FixtureHourlyForecastData,
        block: (
            hp: FixtureHourlyPoint,
            profile: List<ProfileLevel>,
            surfaceTemp: Float,
            surfaceDew: Float,
            surfacePressure: Float,
            elevationKm: Float,
            heatingInput: SurfaceHeatingInput,
        ) -> Unit,
    ) {
        val pointsByDate = hourlyData.hourlyPoints.groupBy { it.date }
        val dates = pointsByDate.keys.sorted()
        val elevation = hourlyData.elevation ?: 0.0
        val elevationKm = elevation.toFloat() / 1000f

        dates.forEach { dateKey ->
            val dayPoints = pointsByDate[dateKey] ?: return@forEach
            val dayPointsByHour = dayPoints.associateBy { it.hour }
            val daytimePoints = dayPoints.filter { it.hour in 6..22 }

            daytimePoints.forEach dayLoop@{ hp ->
                val surfaceTemp = hp.temperature2mC?.toFloat() ?: return@dayLoop
                val surfaceDew = hp.dewPoint2mC?.toFloat() ?: return@dayLoop
                val surfacePressure = hp.surfacePressureHpa?.toFloat()
                    ?: estimateSurfacePressure(elevation)

                val pressureProfile = hp.pressureLevels
                    .filter { it.geopotentialHeightM != null }
                    .map { pl ->
                        ProfileLevel(
                            pressureHpa = pl.pressureHpa.toFloat(),
                            temperatureC = pl.temperatureC.toFloat(),
                            dewPointC = pl.dewPointC?.toFloat(),
                            heightKm = (pl.geopotentialHeightM!! / 1000.0).toFloat(),
                            relativeHumidityPercent = pl.relativeHumidityPercent?.toFloat(),
                            cloudCoverPercent = pl.cloudCoverPercent?.toFloat(),
                            windSpeedKmh = pl.windSpeedKmh?.toFloat(),
                            isSynthetic = pl.isSynthetic,
                        )
                    }
                    .sortedByDescending { it.pressureHpa }

                val profile = buildList {
                    add(
                        ProfileLevel(
                            pressureHpa = surfacePressure,
                            temperatureC = surfaceTemp,
                            dewPointC = surfaceDew,
                            heightKm = elevationKm,
                            windSpeedKmh = hp.windSpeed10mKmh?.toFloat(),
                            isSynthetic = false,
                        ),
                    )
                    addAll(pressureProfile)
                }.sortedByDescending { it.pressureHpa }

                if (profile.size < 2) return@dayLoop

                val heatingInput = SurfaceHeatingInput(
                    hourOfDay = hp.hour,
                    shortwaveRadiationWm2 = hp.shortwaveRadiationWm2?.toFloat(),
                    previousShortwaveRadiationWm2 = dayPointsByHour[hp.hour - 1]
                        ?.shortwaveRadiationWm2
                        ?.toFloat(),
                    cloudCoverLowPercent = hp.cloudCoverLowPercent?.toFloat(),
                    cloudCoverMidPercent = hp.cloudCoverMidPercent?.toFloat(),
                    cloudCoverHighPercent = hp.cloudCoverHighPercent?.toFloat(),
                    precipitationMm = hp.precipitationMm?.toFloat(),
                    isDay = hp.isDay?.let { it > 0.5 },
                )

                block(hp, profile, surfaceTemp, surfaceDew, surfacePressure, elevationKm, heatingInput)
            }
        }
    }
}

private val fixtureJsonFormat = Json { ignoreUnknownKeys = true }

// ── Fixture input mirror (subset of the app's HourlyForecastData needed to
// drive the engine; unknown JSON keys are ignored) ──

@Serializable
private data class GoldenFixture(
    val inputAsset: String,
    val hourlyForecastData: FixtureHourlyForecastData,
    val thermalForecasts: List<ThermalForecastEntry>,
    val parcelAnalyses: List<ParcelAnalysisEntry>,
)

@Serializable
private data class FixtureHourlyForecastData(
    val elevation: Double?,
    val hourlyPoints: List<FixtureHourlyPoint>,
)

@Serializable
private data class FixtureHourlyPoint(
    val date: String,
    val hour: Int,
    val temperature2mC: Double?,
    val dewPoint2mC: Double?,
    val cloudCoverLowPercent: Double?,
    val cloudCoverMidPercent: Double?,
    val cloudCoverHighPercent: Double?,
    val precipitationMm: Double?,
    val windSpeed10mKmh: Double?,
    val capeJKg: Double?,
    val surfacePressureHpa: Double? = null,
    val shortwaveRadiationWm2: Double? = null,
    val isDay: Double? = null,
    val pressureLevels: List<FixturePressureLevelPoint>,
    val liftedIndexC: Double? = null,
    val convectiveInhibitionJKg: Double? = null,
    val boundaryLayerHeightM: Double? = null,
)

@Serializable
private data class FixturePressureLevelPoint(
    val pressureHpa: Int,
    val temperatureC: Double,
    val dewPointC: Double?,
    val windSpeedKmh: Double?,
    val geopotentialHeightM: Double?,
    val relativeHumidityPercent: Double? = null,
    val cloudCoverPercent: Double? = null,
    val isSynthetic: Boolean = false,
)

// ── Output DTOs: identical shape to the app-side exporter so the JSON
// round-trips into exact data-class equality ──

@Serializable
private data class ThermalForecastEntry(
    val date: String,
    val hour: Int,
    val result: ThermalForecastResultDto,
)

@Serializable
private data class ParcelAnalysisEntry(
    val date: String,
    val hour: Int,
    val result: ParcelAnalysisResultDto,
)

@Serializable
private data class SourceLevelDto(
    val pressureHpa: Double,
    val altitudeKm: Double,
    val isSynthetic: Boolean,
)

@Serializable
private data class LayerDto(
    val startAltitudeKm: Double,
    val endAltitudeKm: Double,
    val updraftLowMps: Double,
    val updraftNominalMps: Double,
    val updraftHighMps: Double,
    val confidence: String,
    val visualDepthM: Double,
    val effectiveDepthM: Double,
    val pressureBottomHpa: Double?,
    val pressureTopHpa: Double?,
    val sourceQuality: String,
    val warnings: List<String>,
)

@Serializable
private data class ThermalForecastResultDto(
    val topLowKm: Double,
    val topNominalKm: Double,
    val topHighKm: Double,
    val updraftLowMps: Double,
    val updraftNominalMps: Double,
    val updraftHighMps: Double,
    val confidence: String,
    val limitingReason: String,
    val lowerSourceLevel: SourceLevelDto?,
    val upperSourceLevel: SourceLevelDto?,
    val lclKm: Double,
    val cclKm: Double?,
    val cloudBaseKm: Double?,
    val moistEquilibriumTopKm: Double?,
    val thermalEnergyJKg: Double,
    val modelCapeJKg: Double?,
    val modelCinJKg: Double?,
    val normalizedCinJKg: Double?,
    val liftedIndexC: Double?,
    val boundaryLayerHeightM: Double?,
    val triggerExcessC: Double,
    val dryTopExcessC: Double,
    val effectiveRadiationWm2: Double?,
    val surfaceTemperatureC: Double,
    val surfacePressureHpa: Double,
    val elevationKm: Double,
    val parcelStartTemperatureC: Double,
    val dryTopAglM: Double,
    val computedCinJKg: Double,
    val cloudBaseStatus: String,
    val warnings: List<String>,
    val usedPressureLevels: List<SourceLevelDto>,
    val layers: List<LayerDto>,
    val pressureLevelAltitudesKm: List<Double>,
)

@Serializable
private data class ThermalCellDto(
    val startAltitudeKm: Double,
    val endAltitudeKm: Double,
    val strengthMps: Double,
    val buoyancyC: Double,
)

@Serializable
private data class ParcelAnalysisResultDto(
    val dryThermalTopKm: Double,
    val lclKm: Double,
    val lclPressureHpa: Double,
    val cclKm: Double?,
    val cclPressureHpa: Double?,
    val tconC: Double?,
    val cloudBaseKm: Double?,
    val moistEquilibriumTopKm: Double?,
    val computedCapeJKg: Double,
    val computedCinJKg: Double,
    val modelCapeJKg: Double?,
    val thermalCells: List<ThermalCellDto>,
    val surfaceHeatingC: Double,
)

private fun ThermalForecastResult.toDto(): ThermalForecastResultDto = ThermalForecastResultDto(
    topLowKm = topLowKm.toDouble(),
    topNominalKm = topNominalKm.toDouble(),
    topHighKm = topHighKm.toDouble(),
    updraftLowMps = updraftLowMps.toDouble(),
    updraftNominalMps = updraftNominalMps.toDouble(),
    updraftHighMps = updraftHighMps.toDouble(),
    confidence = confidence.name,
    limitingReason = limitingReason.name,
    lowerSourceLevel = lowerSourceLevel?.let {
        SourceLevelDto(it.pressureHpa.toDouble(), it.altitudeKm.toDouble(), it.isSynthetic)
    },
    upperSourceLevel = upperSourceLevel?.let {
        SourceLevelDto(it.pressureHpa.toDouble(), it.altitudeKm.toDouble(), it.isSynthetic)
    },
    lclKm = lclKm.toDouble(),
    cclKm = cclKm?.toDouble(),
    cloudBaseKm = cloudBaseKm?.toDouble(),
    moistEquilibriumTopKm = moistEquilibriumTopKm?.toDouble(),
    thermalEnergyJKg = thermalEnergyJKg.toDouble(),
    modelCapeJKg = modelCapeJKg?.toDouble(),
    modelCinJKg = modelCinJKg?.toDouble(),
    normalizedCinJKg = normalizedCinJKg?.toDouble(),
    liftedIndexC = liftedIndexC?.toDouble(),
    boundaryLayerHeightM = boundaryLayerHeightM?.toDouble(),
    triggerExcessC = triggerExcessC.toDouble(),
    dryTopExcessC = dryTopExcessC.toDouble(),
    effectiveRadiationWm2 = effectiveRadiationWm2?.toDouble(),
    surfaceTemperatureC = surfaceTemperatureC.toDouble(),
    surfacePressureHpa = surfacePressureHpa.toDouble(),
    elevationKm = elevationKm.toDouble(),
    parcelStartTemperatureC = parcelStartTemperatureC.toDouble(),
    dryTopAglM = dryTopAglM.toDouble(),
    computedCinJKg = computedCinJKg.toDouble(),
    cloudBaseStatus = cloudBaseStatus.name,
    warnings = warnings.map { it.name },
    usedPressureLevels = usedPressureLevels.map {
        SourceLevelDto(it.pressureHpa.toDouble(), it.altitudeKm.toDouble(), it.isSynthetic)
    },
    layers = layers.map { layer ->
        LayerDto(
            startAltitudeKm = layer.startAltitudeKm.toDouble(),
            endAltitudeKm = layer.endAltitudeKm.toDouble(),
            updraftLowMps = layer.updraftLowMps.toDouble(),
            updraftNominalMps = layer.updraftNominalMps.toDouble(),
            updraftHighMps = layer.updraftHighMps.toDouble(),
            confidence = layer.confidence.name,
            visualDepthM = layer.visualDepthM.toDouble(),
            effectiveDepthM = layer.effectiveDepthM.toDouble(),
            pressureBottomHpa = layer.pressureBottomHpa?.toDouble(),
            pressureTopHpa = layer.pressureTopHpa?.toDouble(),
            sourceQuality = layer.sourceQuality.name,
            warnings = layer.warnings.map { it.name },
        )
    },
    pressureLevelAltitudesKm = pressureLevelAltitudesKm.map { it.toDouble() },
)

private fun ParcelAnalysisResult.toDto(): ParcelAnalysisResultDto = ParcelAnalysisResultDto(
    dryThermalTopKm = dryThermalTopKm.toDouble(),
    lclKm = lclKm.toDouble(),
    lclPressureHpa = lclPressureHpa.toDouble(),
    cclKm = cclKm?.toDouble(),
    cclPressureHpa = cclPressureHpa?.toDouble(),
    tconC = tconC?.toDouble(),
    cloudBaseKm = cloudBaseKm?.toDouble(),
    moistEquilibriumTopKm = moistEquilibriumTopKm?.toDouble(),
    computedCapeJKg = computedCapeJKg.toDouble(),
    computedCinJKg = computedCinJKg.toDouble(),
    modelCapeJKg = modelCapeJKg?.toDouble(),
    thermalCells = thermalCells.map {
        ThermalCellDto(
            startAltitudeKm = it.startAltitudeKm.toDouble(),
            endAltitudeKm = it.endAltitudeKm.toDouble(),
            strengthMps = it.strengthMps.toDouble(),
            buoyancyC = it.buoyancyC.toDouble(),
        )
    },
    surfaceHeatingC = surfaceHeatingC.toDouble(),
)
