package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import com.cloudbasepredictor.ui.screens.forecast.zoomedTopAltitudeKm
import kotlin.math.abs

suspend fun PointerInputScope.detectForecastZoomGestures(
    currentTopAltitudeKm: () -> Float,
    onVisibleTopAltitudeChange: (Float) -> Unit,
) {
    awaitEachGesture {
        var cumulativeZoom = 1f
        var pastTouchSlop = false
        var gestureTopAltitudeKm = currentTopAltitudeKm()

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
                    gestureTopAltitudeKm = zoomedTopAltitudeKm(
                        currentTopAltitudeKm = gestureTopAltitudeKm,
                        zoomChange = zoomChange,
                    )
                    onVisibleTopAltitudeChange(gestureTopAltitudeKm)
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
