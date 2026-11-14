package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import com.cloudbasepredictor.ui.screens.forecast.zoomedTopAltitudeKm
import kotlin.math.abs

/**
 * Pinch zoom of a forecast chart's visible altitude range.
 *
 * Each pinch starts from [currentTopAltitudeKm] as it is when the pinch starts zooming, so that a range changed
 * between gestures (another day, a reset) is picked up, and then follows its own zoom steps, which the chart state
 * may not have caught up with yet.
 */
suspend fun PointerInputScope.detectForecastZoomGestures(
    currentTopAltitudeKm: () -> Float,
    onVisibleTopAltitudeChange: (Float) -> Unit,
) {
    awaitEachGesture {
        var cumulativeZoom = 1f
        var pastTouchSlop = false
        var gestureTopAltitudeKm: Float? = null

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                if (!pastTouchSlop) {
                    cumulativeZoom *= zoomChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - cumulativeZoom) * centroidSize
                    if (zoomMotion > viewConfiguration.touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop && zoomChange != 1f) {
                    val topAltitudeKm = zoomedTopAltitudeKm(
                        currentTopAltitudeKm = gestureTopAltitudeKm ?: currentTopAltitudeKm(),
                        zoomChange = zoomChange,
                    )
                    gestureTopAltitudeKm = topAltitudeKm
                    onVisibleTopAltitudeChange(topAltitudeKm)
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
