// Self-contained coordinate parser: multiple early returns and DMS numeric constants are inherent
// to the algorithm (these were accepted via the app detekt baseline before the move to :shared).
@file:Suppress("ReturnCount", "MagicNumber")

package com.cloudbasepredictor.model

import kotlin.math.abs

private const val MAX_MANUAL_FAVORITE_NAME_LENGTH = 80

/**
 * A validated manually-entered favorite location. Shared so Android's manual-add dialog and the web
 * map manual-add form apply identical parsing/validation rules.
 */
data class ManualFavoriteInput(
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    fun toSavedPlace(): SavedPlace {
        return SavedPlace.fromCoordinates(
            latitude = latitude,
            longitude = longitude,
        ).copy(
            name = name,
            isFavorite = true,
        )
    }
}

enum class ManualFavoriteInputError {
    BLANK_NAME,
    NAME_TOO_LONG,
    BLANK_COORDINATES,
    COORDINATES_FORMAT,
    LATITUDE_OUT_OF_RANGE,
    LONGITUDE_OUT_OF_RANGE,
}

sealed interface ManualFavoriteInputResult {
    data class Valid(val input: ManualFavoriteInput) : ManualFavoriteInputResult
    data class Invalid(val error: ManualFavoriteInputError) : ManualFavoriteInputResult
}

fun parseManualFavoriteInput(
    name: String,
    coordinates: String,
): ManualFavoriteInputResult {
    val normalizedName = name.trim().replace(WhitespaceRegex, " ")
    if (normalizedName.isBlank()) {
        return ManualFavoriteInputResult.Invalid(ManualFavoriteInputError.BLANK_NAME)
    }
    if (normalizedName.length > MAX_MANUAL_FAVORITE_NAME_LENGTH) {
        return ManualFavoriteInputResult.Invalid(ManualFavoriteInputError.NAME_TOO_LONG)
    }
    if (coordinates.isBlank()) {
        return ManualFavoriteInputResult.Invalid(ManualFavoriteInputError.BLANK_COORDINATES)
    }

    val parsedCoordinates = parseManualFavoriteCoordinates(coordinates)
        ?: return ManualFavoriteInputResult.Invalid(ManualFavoriteInputError.COORDINATES_FORMAT)

    if (parsedCoordinates.latitude !in MIN_LATITUDE..MAX_LATITUDE) {
        return ManualFavoriteInputResult.Invalid(ManualFavoriteInputError.LATITUDE_OUT_OF_RANGE)
    }
    if (parsedCoordinates.longitude !in MIN_LONGITUDE..MAX_LONGITUDE) {
        return ManualFavoriteInputResult.Invalid(ManualFavoriteInputError.LONGITUDE_OUT_OF_RANGE)
    }

    return ManualFavoriteInputResult.Valid(
        ManualFavoriteInput(
            name = normalizedName,
            latitude = parsedCoordinates.latitude,
            longitude = parsedCoordinates.longitude,
        ),
    )
}

private fun parseManualFavoriteCoordinates(coordinates: String): ManualFavoriteCoordinates? {
    val text = coordinates.trim()
    return parseLabelledDecimalCoordinates(text)
        ?: parseDirectionalCoordinates(text)
        ?: parsePlainDecimalCoordinates(text)
}

private data class ManualFavoriteCoordinates(
    val latitude: Double,
    val longitude: Double,
)

private sealed interface CoordinateToken {
    data class Number(val value: Double) : CoordinateToken
    data class Cardinal(val value: Char) : CoordinateToken
}

private enum class CardinalPlacement {
    PREFIX,
    SUFFIX,
}

private const val MIN_LATITUDE = -90.0
private const val MAX_LATITUDE = 90.0
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0
private const val MINUTES_PER_DEGREE = 60.0
private const val SECONDS_PER_DEGREE = 3600.0

private val WhitespaceRegex = Regex("\\s+")
private val CoordinateNumberRegex = Regex("""[+-]?\d+(?:[.,]\d+)?""")
private val CoordinateTokenRegex = Regex("""[NSEWnsew]|[+-]?\d+(?:[.,]\d+)?""")
private val LatitudeLabelRegex = Regex("""(?i)\b(?:lat|latitude)\b\s*[:=]?\s*([+-]?\d+(?:[.,]\d+)?)""")
private val LongitudeLabelRegex = Regex("""(?i)\b(?:lon|lng|long|longitude)\b\s*[:=]?\s*([+-]?\d+(?:[.,]\d+)?)""")

private fun parseLabelledDecimalCoordinates(text: String): ManualFavoriteCoordinates? {
    val latitude = LatitudeLabelRegex.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toCoordinateDouble()
    val longitude = LongitudeLabelRegex.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toCoordinateDouble()

    if (latitude == null || longitude == null) return null
    return ManualFavoriteCoordinates(latitude = latitude, longitude = longitude)
}

private fun parsePlainDecimalCoordinates(text: String): ManualFavoriteCoordinates? {
    val values = CoordinateNumberRegex.findAll(text)
        .mapNotNull { match -> match.value.toCoordinateDouble() }
        .toList()

    if (values.size != 2) return null
    return ManualFavoriteCoordinates(latitude = values[0], longitude = values[1])
}

private fun parseDirectionalCoordinates(text: String): ManualFavoriteCoordinates? {
    val tokens = CoordinateTokenRegex.findAll(text)
        .mapNotNull { match -> match.value.toCoordinateToken() }
        .toList()
    val cardinalIndices = tokens.withIndex()
        .filter { (_, token) -> token is CoordinateToken.Cardinal }
        .map { (index, _) -> index }

    if (cardinalIndices.size != 2) return null

    val parsed = parseDirectionalCoordinates(
        tokens = tokens,
        cardinalIndices = cardinalIndices,
        placement = CardinalPlacement.SUFFIX,
    ) ?: parseDirectionalCoordinates(
        tokens = tokens,
        cardinalIndices = cardinalIndices,
        placement = CardinalPlacement.PREFIX,
    ) ?: return null

    val latitude = parsed.singleOrNull { (cardinal, _) -> cardinal == 'N' || cardinal == 'S' }?.second
    val longitude = parsed.singleOrNull { (cardinal, _) -> cardinal == 'E' || cardinal == 'W' }?.second
    if (latitude == null || longitude == null) return null

    return ManualFavoriteCoordinates(latitude = latitude, longitude = longitude)
}

private fun parseDirectionalCoordinates(
    tokens: List<CoordinateToken>,
    cardinalIndices: List<Int>,
    placement: CardinalPlacement,
): List<Pair<Char, Double>>? {
    return cardinalIndices.map { cardinalIndex ->
        val cardinal = tokens[cardinalIndex] as CoordinateToken.Cardinal
        val previousCardinalIndex = cardinalIndices.lastOrNull { it < cardinalIndex }
        val nextCardinalIndex = cardinalIndices.firstOrNull { it > cardinalIndex }
        val numbers = numbersForCardinal(
            tokens = tokens,
            cardinalIndex = cardinalIndex,
            previousCardinalIndex = previousCardinalIndex,
            nextCardinalIndex = nextCardinalIndex,
            placement = placement,
        ) ?: return null
        val value = dmsToDecimalDegrees(numbers, cardinal.value) ?: return null
        cardinal.value to value
    }
}

private fun numbersForCardinal(
    tokens: List<CoordinateToken>,
    cardinalIndex: Int,
    previousCardinalIndex: Int?,
    nextCardinalIndex: Int?,
    placement: CardinalPlacement,
): List<Double>? {
    return when (placement) {
        CardinalPlacement.SUFFIX -> {
            if (cardinalIndex <= 0 || tokens[cardinalIndex - 1] !is CoordinateToken.Number) return null
            val start = (previousCardinalIndex ?: -1) + 1
            tokens.subList(start, cardinalIndex).numbersOrNull()
        }
        CardinalPlacement.PREFIX -> {
            if (cardinalIndex >= tokens.lastIndex || tokens[cardinalIndex + 1] !is CoordinateToken.Number) {
                return null
            }
            val end = nextCardinalIndex ?: tokens.size
            tokens.subList(cardinalIndex + 1, end).numbersOrNull()
        }
    }
}

private fun List<CoordinateToken>.numbersOrNull(): List<Double>? {
    val numbers = mapNotNull { token ->
        (token as? CoordinateToken.Number)?.value
    }
    return numbers.takeIf { it.size in 1..3 }
}

private fun dmsToDecimalDegrees(
    numbers: List<Double>,
    cardinal: Char,
): Double? {
    val degrees = numbers.getOrNull(0) ?: return null
    val minutes = numbers.getOrNull(1) ?: 0.0
    val seconds = numbers.getOrNull(2) ?: 0.0

    if (!degrees.isFinite() || !minutes.isFinite() || !seconds.isFinite()) return null
    val minutesValid = minutes >= 0.0 && minutes < MINUTES_PER_DEGREE
    val secondsValid = seconds >= 0.0 && seconds < MINUTES_PER_DEGREE
    if (!minutesValid || !secondsValid) return null

    val value = abs(degrees) + (minutes / MINUTES_PER_DEGREE) + (seconds / SECONDS_PER_DEGREE)
    val sign = when (cardinal) {
        'S', 'W' -> -1.0
        else -> 1.0
    }
    return value * sign
}

private fun String.toCoordinateToken(): CoordinateToken? {
    if (length == 1) {
        val cardinal = when (this[0]) {
            'N', 'n' -> 'N'
            'S', 's' -> 'S'
            'E', 'e' -> 'E'
            'W', 'w' -> 'W'
            else -> null
        }
        if (cardinal != null) {
            return CoordinateToken.Cardinal(cardinal)
        }
    }
    return toCoordinateDouble()?.let(CoordinateToken::Number)
}

private fun String.toCoordinateDouble(): Double? {
    return replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
