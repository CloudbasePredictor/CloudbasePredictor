package com.cloudbasepredictor.web.presentation

internal const val TWO_COLUMN_MIN_WIDTH_DP = 760f

internal fun destinationColumnCount(availableWidthDp: Float): Int {
    return if (availableWidthDp >= TWO_COLUMN_MIN_WIDTH_DP) 2 else 1
}

internal fun <T> destinationRows(
    items: List<T>,
    columnCount: Int,
): List<List<T>> = items.chunked(columnCount.coerceAtLeast(1))
