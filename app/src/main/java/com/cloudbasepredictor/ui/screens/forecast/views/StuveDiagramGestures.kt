package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import com.cloudbasepredictor.ui.screens.forecast.zoomedTopAltitudeKm
import kotlin.math.abs

internal suspend fun PointerInputScope.detectSkewTGestures(
    currentTopAltitudeKm: () -> Float,
    onVisibleTopAltitudeChange: (Float) -> Unit,
    onCursorStateChanged: (SkewTCursorState?) -> Unit,
    isInHeatingZone: (x: Float, y: Float) -> Boolean,
    onHeatingHandleDragDelta: (deltaX: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)

        if (isInHeatingZone(down.position.x, down.position.y)) {
            // ── Heating-handle drag: updates parcel start temperature ──
            var prevX = down.position.x
            do {
                val event = awaitPointerEvent()
                val canceled = event.changes.any { it.isConsumed }
                if (!canceled && event.changes.count { it.pressed } == 1) {
                    event.changes.firstOrNull { it.pressed }?.let { change ->
                        onHeatingHandleDragDelta(change.position.x - prevX)
                        prevX = change.position.x
                        change.consume()
                    }
                }
            } while (event.changes.any { it.pressed })
        } else {
            // ── Normal cursor / pinch-zoom gesture ──
            var cumulativeZoom = 1f
            var isZooming = false
            var gestureTopAltitudeKm = currentTopAltitudeKm()
            var hasDragged = false

            val startY = down.position.y
            val startX = down.position.x
            var latestY = startY
            var latestX = startX
            onCursorStateChanged(SkewTCursorState(y = latestY, x = latestX, isPinned = false))

            do {
                val event = awaitPointerEvent()
                val canceled = event.changes.any { it.isConsumed }
                if (!canceled) {
                    val pressedPointers = event.changes.count { it.pressed }
                    if (pressedPointers >= 2) {
                        if (!isZooming) {
                            isZooming = true
                            onCursorStateChanged(null)
                        }

                        val zoomChange = event.calculateZoom()
                        cumulativeZoom *= zoomChange
                        val zoomMotion = abs(1 - cumulativeZoom) * event.calculateCentroidSize(useCurrent = false)
                        if (zoomMotion > viewConfiguration.touchSlop && zoomChange != 1f) {
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
                    } else {
                        event.changes.firstOrNull { it.pressed }?.let { change ->
                            latestY = change.position.y
                            latestX = change.position.x
                            if (abs(latestY - startY) > viewConfiguration.touchSlop ||
                                abs(latestX - startX) > viewConfiguration.touchSlop
                            ) {
                                hasDragged = true
                            }
                            onCursorStateChanged(SkewTCursorState(y = latestY, x = latestX, isPinned = false))
                        }
                    }
                }
            } while (event.changes.any { it.pressed })

            if (isZooming || hasDragged) {
                onCursorStateChanged(null)
            } else {
                onCursorStateChanged(SkewTCursorState(y = latestY, x = latestX, isPinned = true))
            }
        }
    }
}
