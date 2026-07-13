package com.cloudbasepredictor.web.presentation

internal const val TWO_COLUMN_MIN_WIDTH_DP = 760f
internal const val NAVIGATION_RAIL_MIN_WIDTH_DP = 900f

internal fun usesNavigationRail(availableWidthDp: Float): Boolean {
    return availableWidthDp >= NAVIGATION_RAIL_MIN_WIDTH_DP
}

internal fun destinationColumnCount(availableWidthDp: Float): Int {
    return if (availableWidthDp >= TWO_COLUMN_MIN_WIDTH_DP) 2 else 1
}

internal fun <T> destinationRows(
    items: List<T>,
    columnCount: Int,
): List<List<T>> = items.chunked(columnCount.coerceAtLeast(1))
