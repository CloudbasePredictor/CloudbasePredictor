package com.cloudbasepredictor.web

import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.PlaceLocation

enum class WebDestination(
    val slug: String,
    val label: String,
) {
    Forecast(slug = "forecast", label = "Forecast"),
    Map(slug = "map", label = "Map"),
    Favorites(slug = "favorites", label = "Favorites"),
    Settings(slug = "settings", label = "Settings"),
}

data class WebRouteState(
    val destination: WebDestination = WebDestination.Forecast,
    val location: PlaceLocation? = null,
    val model: ForecastModel = ForecastModel.ICON_SEAMLESS,
    val mode: ForecastMode = ForecastMode.THERMIC,
    val dayIndex: Int = 0,
    val hour: Int = DEFAULT_FORECAST_HOUR,
)

object WebRouteStateCodec {
    const val PROJECT_BASE_PATH: String = "/CloudbasePredictor/"

    fun decode(
        fragment: String?,
        defaultModel: ForecastModel = ForecastModel.ICON_SEAMLESS,
    ): WebRouteState {
        val normalized = fragment
            .orEmpty()
            .substringAfter('#')
            .trim()
        val slug = normalized.substringBefore('?').trim('/')
        val destination = WebDestination.entries.firstOrNull { it.slug == slug }
            ?: WebDestination.Forecast
        val parameters = normalized.substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                percentDecode(entry.substring(0, separator)) to
                    percentDecode(entry.substring(separator + 1))
            }
            .toMap()
        val location = decodeLocation(parameters)
        val model = parameters["model"]
            ?.let(ForecastModel::fromApiName)
            ?: defaultModel
        val mode = parameters["view"]
            ?.uppercase()
            ?.let { value -> ForecastMode.entries.firstOrNull { it.name == value } }
            ?: ForecastMode.THERMIC

        return WebRouteState(
            destination = destination,
            location = location,
            model = model,
            mode = mode,
            dayIndex = parameters["day"]?.toIntOrNull()?.takeIf { it in 0..MAX_DAY_INDEX } ?: 0,
            hour = parameters["hour"]?.toIntOrNull()?.takeIf { it in FIRST_FORECAST_HOUR..LAST_FORECAST_HOUR }
                ?: DEFAULT_FORECAST_HOUR,
        )
    }

    fun encodeFragment(state: WebRouteState): String {
        val path = when (state.destination) {
            WebDestination.Forecast -> "#/"
            else -> "#/${state.destination.slug}"
        }
        val location = state.location ?: return path
        val parameters = buildList {
            add("lat" to location.latitude.toString())
            add("lon" to location.longitude.toString())
            location.name?.takeIf(String::isNotBlank)?.let { add("name" to it) }
            add("model" to state.model.apiName)
            add("view" to state.mode.name.lowercase())
            add("day" to state.dayIndex.coerceIn(0, MAX_DAY_INDEX).toString())
            add("hour" to state.hour.coerceIn(FIRST_FORECAST_HOUR, LAST_FORECAST_HOUR).toString())
        }
        return path + parameters.joinToString(prefix = "?", separator = "&") { (key, value) ->
            "${percentEncode(key)}=${percentEncode(value)}"
        }
    }

    fun encodePath(
        state: WebRouteState,
        basePath: String = PROJECT_BASE_PATH,
    ): String = normalizeBasePath(basePath) + encodeFragment(state)

    private fun normalizeBasePath(basePath: String): String {
        val normalized = basePath.trim().trim('/')
        return if (normalized.isEmpty()) "/" else "/$normalized/"
    }

    private fun percentEncode(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val unsigned = byte.toInt() and BYTE_MASK
            val char = unsigned.toChar()
            if (char.isAsciiRouteSafe()) {
                append(char)
            } else {
                append('%')
                append(unsigned.toString(HEX_RADIX).uppercase().padStart(2, '0'))
            }
        }
    }

    private fun percentDecode(value: String): String {
        val bytes = mutableListOf<Byte>()
        var index = 0
        while (index < value.length) {
            when {
                value[index] == '%' && index + 2 < value.length -> {
                    val decoded = value.substring(index + 1, index + PERCENT_ESCAPE_LENGTH)
                        .toIntOrNull(HEX_RADIX)
                    if (decoded != null) {
                        bytes += decoded.toByte()
                        index += PERCENT_ESCAPE_LENGTH
                    } else {
                        bytes += '%'.code.toByte()
                        index++
                    }
                }
                value[index] == '+' -> {
                    bytes += ' '.code.toByte()
                    index++
                }
                else -> {
                    bytes += value[index].toString().encodeToByteArray().toList()
                    index++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun Char.isAsciiRouteSafe(): Boolean {
        return this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' ||
            this == '-' || this == '_' || this == '.' || this == '~'
    }

    private fun decodeLocation(parameters: Map<String, String>): PlaceLocation? {
        val latitude = parameters["lat"]?.toDoubleOrNull() ?: Double.NaN
        val longitude = parameters["lon"]?.toDoubleOrNull() ?: Double.NaN
        return if (latitude.isValidLatitude() && longitude.isValidLongitude()) {
            PlaceLocation(
                latitude = latitude,
                longitude = longitude,
                name = parameters["name"]?.trim()?.takeIf(String::isNotEmpty),
            )
        } else {
            null
        }
    }

    private fun Double.isValidLatitude(): Boolean = isFinite() && this in MIN_LATITUDE..MAX_LATITUDE

    private fun Double.isValidLongitude(): Boolean = isFinite() && this in MIN_LONGITUDE..MAX_LONGITUDE

    private const val MAX_DAY_INDEX = 13
    private const val HEX_RADIX = 16
    private const val BYTE_MASK = 0xFF
    private const val PERCENT_ESCAPE_LENGTH = 3
    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0
}

private const val DEFAULT_FORECAST_HOUR = 12
private const val FIRST_FORECAST_HOUR = 6
private const val LAST_FORECAST_HOUR = 22
