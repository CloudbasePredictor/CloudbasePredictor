package com.cloudbasepredictor.util

/**
 * Formats a finite decimal with fixed precision without relying on a platform locale.
 *
 * Rounding is applied to Kotlin's shortest decimal representation, matching the
 * user-facing half-up behaviour of the former `String.format(Locale.US, ...)` calls.
 */
fun Double.toFixedDecimalString(fractionDigits: Int): String {
    require(fractionDigits >= 0) { "fractionDigits must not be negative." }
    if (!isFinite()) return toString()

    val negative = toBits() < 0
    val absoluteText = if (negative) toString().removePrefix("-") else toString()
    val exponentSeparator = absoluteText.indexOfAny(charArrayOf('e', 'E'))
    val mantissa = if (exponentSeparator >= 0) {
        absoluteText.substring(0, exponentSeparator)
    } else {
        absoluteText
    }
    val exponent = if (exponentSeparator >= 0) {
        absoluteText.substring(exponentSeparator + 1).toInt()
    } else {
        0
    }
    val decimalSeparator = mantissa.indexOf('.')
    val mantissaFractionDigits = if (decimalSeparator >= 0) {
        mantissa.length - decimalSeparator - 1
    } else {
        0
    }
    val digits = mantissa.replace(".", "")
    val scaledExponent = exponent - mantissaFractionDigits + fractionDigits

    val scaledDigits = if (scaledExponent >= 0) {
        digits + "0".repeat(scaledExponent)
    } else {
        val discardedDigitCount = -scaledExponent
        val retainedDigitCount = (digits.length - discardedDigitCount).coerceAtLeast(0)
        val retained = digits.take(retainedDigitCount).ifEmpty { "0" }
        val firstDiscardedIndex = digits.length - discardedDigitCount
        val firstDiscarded = digits.getOrNull(firstDiscardedIndex) ?: '0'
        if (firstDiscarded >= '5') retained.incrementDecimalDigits() else retained
    }.trimStart('0').ifEmpty { "0" }

    val padded = scaledDigits.padStart(fractionDigits + 1, '0')
    val unsignedResult = if (fractionDigits == 0) {
        padded
    } else {
        "${padded.dropLast(fractionDigits)}.${padded.takeLast(fractionDigits)}"
    }
    return if (negative) "-$unsignedResult" else unsignedResult
}

private fun String.incrementDecimalDigits(): String {
    val result = toCharArray()
    for (index in result.lastIndex downTo 0) {
        if (result[index] == '9') {
            result[index] = '0'
        } else {
            result[index]++
            return result.concatToString()
        }
    }
    return "1${result.concatToString()}"
}
