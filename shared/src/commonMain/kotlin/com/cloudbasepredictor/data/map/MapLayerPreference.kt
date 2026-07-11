package com.cloudbasepredictor.data.map

enum class MapLayerPreference(
    val label: String,
    val attributionCompact: String,
    val attributionFull: String,
) {
    OPENFREEMAP(
        label = "Streets",
        attributionCompact = "© OpenMapTiles · © OpenStreetMap",
        attributionFull = "Map service: OpenFreeMap\nVector tiles: © OpenMapTiles\n" +
            "Map data: © OpenStreetMap contributors (ODbL)",
    ),
    OPENTOPOMAP(
        label = "Topo",
        attributionCompact = "© OpenTopoMap CC-BY-SA · © OpenStreetMap/SRTM",
        attributionFull = "Map data: © OpenStreetMap contributors (ODbL), SRTM\n" +
            "Map style: © OpenTopoMap (CC-BY-SA)",
    ),
    NASA_GIBS(
        label = "Satellite (GIBS)",
        attributionCompact = "NASA GIBS",
        attributionFull = "Imagery: NASA Global Imagery Browse Services (GIBS)",
    ),
    ESRI_WORLD_IMAGERY(
        label = "Satellite (Esri)",
        attributionCompact = "Powered by Esri · Sources",
        attributionFull = "Powered by Esri\nSources: Esri, Vantor, GeoEye, Earthstar Geographics, " +
            "CNES/Airbus DS, USDA, USGS, AeroGRID, IGN, © OpenStreetMap contributors, " +
            "TomTom, Garmin, FAO, NOAA, and the GIS User Community",
    ),
}
