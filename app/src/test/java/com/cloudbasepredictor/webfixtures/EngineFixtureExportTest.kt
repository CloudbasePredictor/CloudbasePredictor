package com.cloudbasepredictor.webfixtures

import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import com.cloudbasepredictor.data.remote.OpenMeteoHourlyForecastResponse
import com.cloudbasepredictor.data.remote.toHourlyForecastData
import com.cloudbasepredictor.domain.forecast.ParcelAnalysisResult
import com.cloudbasepredictor.domain.forecast.ProfileLevel
import com.cloudbasepredictor.domain.forecast.SurfaceHeatingInput
import com.cloudbasepredictor.domain.forecast.ThermalForecastEngine
import com.cloudbasepredictor.domain.forecast.ThermalForecastInput
import com.cloudbasepredictor.domain.forecast.ThermalForecastResult
import com.cloudbasepredictor.domain.forecast.analyzeParcel
import com.cloudbasepredictor.domain.forecast.estimateSurfacePressure
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Exports the golden fixture that locks the Kotlin forecast pipeline (the
 * conversion plus the engine). The canonical copy lives in
 * `engine/src/commonTest/resources/` and backs the `:engine` golden tests on
 * every target; while `web/` still exists, the TypeScript port's copy in
 * `web/src/engine/__fixtures__/` is kept in sync as well.
 *
 * The exported JSON contains:
 * - the converted [HourlyForecastData] (including synthesized pressure levels),
 * - the [ThermalForecastEngine] output for every daytime hour, and
 * - the [analyzeParcel] output for every daytime hour.
 *
 * All numeric values are widened to `Double` and serialized with full precision
 * so the float32 engine outputs can be matched exactly.
 *
 * The test also asserts the committed fixture is up to date, doubling as a
 * regression guard: if the Kotlin engine changes, this test fails until the
 * fixture (and the ports consuming it) is regenerated. To regenerate after an
 * intentional change, delete the fixture file or run with
 * `-DupdateEngineFixtures=true`.
 */
class EngineFixtureExportTest {

    private val writerJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    @Test
    fun exportBrauneckFixtureAndAssertUpToDate() {
        val assetName = "brauneck_icon_seamless_20260418.json"
        val assetFile = resolveAssetFile(assetName)
        val response = Json { ignoreUnknownKeys = true }
            .decodeFromString<OpenMeteoHourlyForecastResponse>(assetFile.readText())
        val hourlyData = response.toHourlyForecastData()

        val fixture = EngineFixture(
            inputAsset = assetName,
            hourlyForecastData = hourlyData,
            thermalForecasts = buildThermalForecasts(hourlyData),
            parcelAnalyses = buildParcelAnalyses(hourlyData),
        )

        assertTrue(
            "Expected at least one thermal forecast entry from the fixture",
            fixture.thermalForecasts.isNotEmpty(),
        )
        assertTrue(
            "Expected at least one parcel analysis entry from the fixture",
            fixture.parcelAnalyses.isNotEmpty(),
        )

        val actualJson = writerJson.encodeToString(fixture)
        val outFiles = resolveFixtureOutputFiles()
        val forceUpdate = System.getProperty("updateEngineFixtures")?.toBoolean() == true
        outFiles.forEach { outFile ->
            if (forceUpdate || !outFile.exists()) {
                outFile.parentFile?.mkdirs()
                outFile.writeText(actualJson + "\n")
            }
        }

        outFiles.forEach { outFile ->
            val expected = outFile.readText()
            assertEquals(
                "Engine golden fixture is out of date. Delete ${outFile.path} or run with " +
                    "-DupdateEngineFixtures=true to regenerate, then rerun the :engine " +
                    "golden tests (and the TypeScript port while web/ exists).",
                expected.trim(),
                actualJson.trim(),
            )
        }
    }

    // ── Pipeline drivers: mirror ForecastChartBuilders.buildThermicChartFromData ──

    private fun buildThermalForecasts(hourlyData: HourlyForecastData): List<ThermalForecastEntry> {
        val entries = mutableListOf<ThermalForecastEntry>()
        forEachDaytimeHour(hourlyData) {
            hp, profile, surfaceTemp, surfaceDew, surfacePressure, elevationKm, heatingInput ->
            val forecast = ThermalForecastEngine.analyze(
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
            ) ?: return@forEachDaytimeHour
            entries += ThermalForecastEntry(hp.date, hp.hour, forecast.toDto())
        }
        return entries
    }

    private fun buildParcelAnalyses(hourlyData: HourlyForecastData): List<ParcelAnalysisEntry> {
        val entries = mutableListOf<ParcelAnalysisEntry>()
        forEachDaytimeHour(hourlyData) {
            hp, profile, surfaceTemp, surfaceDew, surfacePressure, elevationKm, heatingInput ->
            val parcel = analyzeParcel(
                profile = profile,
                surfaceTemperatureC = surfaceTemp,
                surfaceDewPointC = surfaceDew,
                surfacePressureHpa = surfacePressure,
                elevationKm = elevationKm,
                heatingInput = heatingInput,
                modelCapeJKg = hp.capeJKg?.toFloat(),
            ) ?: return@forEachDaytimeHour
            entries += ParcelAnalysisEntry(hp.date, hp.hour, parcel.toDto())
        }
        return entries
    }

    private inline fun forEachDaytimeHour(
        hourlyData: HourlyForecastData,
        block: (
            hp: HourlyPoint,
            profile: List<ProfileLevel>,
            surfaceTemp: Float,
            surfaceDew: Float,
            surfacePressure: Float,
            elevationKm: Float,
            heatingInput: SurfaceHeatingInput,
        ) -> Unit,
    ) {
        val pointsByDate = hourlyData.pointsByDate()
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

    private fun resolveAssetFile(assetName: String): File {
        val candidates = listOf(
            File("src/main/assets/simulated/$assetName"),
            File("app/src/main/assets/simulated/$assetName"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate simulated asset $assetName from ${File(".").absolutePath}")
    }

    /**
     * The engine commonTest resource is the canonical output; the TypeScript
     * fixture is included only while the frozen `web/` directory still exists.
     */
    private fun resolveFixtureOutputFiles(): List<File> {
        val repoRoot = generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "engine/src/commonTest").exists() }
            ?: error("Could not locate the engine/ module from ${File(".").absolutePath}")
        val engineCopy = File(repoRoot, "engine/src/commonTest/resources/brauneck_icon_seamless.json")
        val webCopy = File(repoRoot, "web/src/engine/__fixtures__/brauneck_icon_seamless.json")
        return if (webCopy.parentFile.exists()) listOf(engineCopy, webCopy) else listOf(engineCopy)
    }
}

// ── Mapping engine outputs to full-precision Double DTOs ──

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

@Serializable
private data class EngineFixture(
    val inputAsset: String,
    val hourlyForecastData: HourlyForecastData,
    val thermalForecasts: List<ThermalForecastEntry>,
    val parcelAnalyses: List<ParcelAnalysisEntry>,
)

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
