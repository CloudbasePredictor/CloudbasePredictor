package com.cloudbasepredictor.web.map

import com.cloudbasepredictor.data.map.MapLayerPreference

internal sealed interface WebMapStyle {
    data class Url(val value: String) : WebMapStyle

    data class Raster(
        val sourceId: String,
        val tileUrls: List<String>,
        val maxNativeZoom: Int,
        val attribution: String,
    ) : WebMapStyle
}

internal fun buildWebMapStyle(
    layer: MapLayerPreference,
    nasaDateUtc: String,
): WebMapStyle = when (layer) {
    MapLayerPreference.OPENFREEMAP -> WebMapStyle.Url(OPENFREEMAP_STYLE_URL)
    MapLayerPreference.OPENTOPOMAP -> WebMapStyle.Raster(
        sourceId = "opentopomap",
        tileUrls = listOf(
            "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
            "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
            "https://c.tile.opentopomap.org/{z}/{x}/{y}.png",
        ),
        maxNativeZoom = 17,
        attribution = layer.attributionFull,
    )
    MapLayerPreference.NASA_GIBS -> WebMapStyle.Raster(
        sourceId = "nasa-gibs-true-color",
        tileUrls = listOf(
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/" +
                "MODIS_Terra_CorrectedReflectance_TrueColor/default/$nasaDateUtc/" +
                "GoogleMapsCompatible_Level9/{z}/{y}/{x}.jpg",
        ),
        maxNativeZoom = 9,
        attribution = layer.attributionFull,
    )
    MapLayerPreference.ESRI_WORLD_IMAGERY -> WebMapStyle.Raster(
        sourceId = "esri-world-imagery",
        tileUrls = listOf(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/" +
                "MapServer/tile/{z}/{y}/{x}",
        ),
        maxNativeZoom = 23,
        attribution = layer.attributionFull,
    )
}

internal fun WebMapStyle.Raster.toMapLibreJson(): String {
    val tileJson = tileUrls.joinToString(",") { value -> "\"${value.jsonEscape()}\"" }
    val id = sourceId.jsonEscape()
    return buildString {
        append("""{"version":8,"sources":{"$id":{"type":"raster","tiles":[$tileJson],"tileSize":256,""")
        append(""""minzoom":0,"maxzoom":$maxNativeZoom,"attribution":"${attribution.jsonEscape()}"}},""")
        append(""""layers":[{"id":"$id","type":"raster","source":"$id"}]}""")
    }
}

private fun String.jsonEscape(): String = replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")

private const val OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
