package com.cloudbasepredictor.ui.map

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.cloudbasepredictor.R
import com.cloudbasepredictor.data.map.MapLayerPreference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.rememberRasterSource
import org.maplibre.compose.style.BaseStyle

private const val OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val OPENTOPOMAP_LAYER_ID = "opentopomap"
private const val OPENTOPOMAP_ATTRIBUTION_HTML =
    "Map data: &copy; OpenStreetMap contributors, SRTM | Map style: &copy; OpenTopoMap (CC-BY-SA)"
private const val OPENTOPOMAP_MAX_NATIVE_ZOOM = 17
private const val OPENTOPOMAP_TILE_SIZE = 256
private const val NASA_GIBS_LAYER_ID = "nasa-gibs-true-color"
private const val NASA_GIBS_TILE_MATRIX_SET = "GoogleMapsCompatible_Level9"
private const val NASA_GIBS_ATTRIBUTION_HTML = "NASA Global Imagery Browse Services (GIBS)"
private const val NASA_GIBS_MAX_NATIVE_ZOOM = 9
private const val NASA_GIBS_TILE_SIZE = 256
private const val ESRI_WORLD_IMAGERY_LAYER_ID = "esri-world-imagery"
private const val ESRI_WORLD_IMAGERY_TILE_URL =
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
private const val ESRI_WORLD_IMAGERY_ATTRIBUTION_HTML =
    "Sources: Esri, Vantor, GeoEye, Earthstar Geographics, CNES/Airbus DS, USDA, USGS, " +
        "AeroGRID, IGN, OpenStreetMap contributors, TomTom, Garmin, FAO, NOAA, and the GIS User Community"
private const val ESRI_WORLD_IMAGERY_MAX_NATIVE_ZOOM = 23
private const val ESRI_WORLD_IMAGERY_TILE_SIZE = 256

internal fun mapBaseStyle(layer: MapLayerPreference): BaseStyle {
    return when (layer) {
        MapLayerPreference.OPENFREEMAP -> BaseStyle.Uri(OPENFREEMAP_STYLE_URL)
        MapLayerPreference.OPENTOPOMAP -> BaseStyle.Empty
        MapLayerPreference.NASA_GIBS -> BaseStyle.Empty
        MapLayerPreference.ESRI_WORLD_IMAGERY -> BaseStyle.Empty
    }
}

@Composable
internal fun MapRasterBaseLayer(layer: MapLayerPreference) {
    when (layer) {
        MapLayerPreference.OPENFREEMAP -> return
        MapLayerPreference.OPENTOPOMAP -> {
            val source = rememberRasterSource(
                tiles = openTopoMapTileUrls(),
                options = TileSetOptions(
                    minZoom = 0,
                    maxZoom = OPENTOPOMAP_MAX_NATIVE_ZOOM,
                    attributionHtml = OPENTOPOMAP_ATTRIBUTION_HTML,
                ),
                tileSize = OPENTOPOMAP_TILE_SIZE,
            )
            RasterLayer(
                id = OPENTOPOMAP_LAYER_ID,
                source = source,
            )
        }
        MapLayerPreference.NASA_GIBS -> {
            val tileDate = remember { nasaGibsTileDateUtc() }
            val source = rememberRasterSource(
                tiles = listOf(nasaGibsTrueColorTileUrl(tileDate)),
                options = TileSetOptions(
                    minZoom = 0,
                    maxZoom = NASA_GIBS_MAX_NATIVE_ZOOM,
                    attributionHtml = NASA_GIBS_ATTRIBUTION_HTML,
                ),
                tileSize = NASA_GIBS_TILE_SIZE,
            )
            RasterLayer(
                id = NASA_GIBS_LAYER_ID,
                source = source,
            )
        }
        MapLayerPreference.ESRI_WORLD_IMAGERY -> {
            val source = rememberRasterSource(
                tiles = listOf(ESRI_WORLD_IMAGERY_TILE_URL),
                options = TileSetOptions(
                    minZoom = 0,
                    maxZoom = ESRI_WORLD_IMAGERY_MAX_NATIVE_ZOOM,
                    attributionHtml = ESRI_WORLD_IMAGERY_ATTRIBUTION_HTML,
                ),
                tileSize = ESRI_WORLD_IMAGERY_TILE_SIZE,
            )
            RasterLayer(
                id = ESRI_WORLD_IMAGERY_LAYER_ID,
                source = source,
            )
        }
    }
}

@StringRes
internal fun mapLayerAttributionRes(layer: MapLayerPreference): Int {
    return when (layer) {
        MapLayerPreference.OPENFREEMAP -> R.string.map_attribution_compact
        MapLayerPreference.OPENTOPOMAP -> R.string.map_attribution_opentopomap_compact
        MapLayerPreference.NASA_GIBS -> R.string.map_attribution_nasa_gibs_compact
        MapLayerPreference.ESRI_WORLD_IMAGERY -> R.string.map_attribution_esri_world_imagery_compact
    }
}

@StringRes
internal fun mapLayerAttributionDetailRes(layer: MapLayerPreference): Int? {
    return when (layer) {
        MapLayerPreference.OPENFREEMAP -> R.string.map_attribution_full
        MapLayerPreference.OPENTOPOMAP -> R.string.map_attribution_opentopomap_full
        MapLayerPreference.NASA_GIBS -> R.string.map_attribution_nasa_gibs_full
        MapLayerPreference.ESRI_WORLD_IMAGERY -> R.string.map_attribution_esri_world_imagery_full
    }
}

internal fun openTopoMapTileUrls(): List<String> {
    return listOf(
        "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
        "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
        "https://c.tile.opentopomap.org/{z}/{x}/{y}.png",
    )
}

internal fun nasaGibsTileDateUtc(nowMillis: Long = System.currentTimeMillis()): String {
    val calendar = Calendar.getInstance(UTC, Locale.US).apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = UTC
    }.format(calendar.time)
}

internal fun nasaGibsTrueColorTileUrl(date: String): String {
    return "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/" +
        "MODIS_Terra_CorrectedReflectance_TrueColor/default/" +
        "$date/$NASA_GIBS_TILE_MATRIX_SET/{z}/{y}/{x}.jpg"
}

internal fun esriWorldImageryTileUrl(): String = ESRI_WORLD_IMAGERY_TILE_URL

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
