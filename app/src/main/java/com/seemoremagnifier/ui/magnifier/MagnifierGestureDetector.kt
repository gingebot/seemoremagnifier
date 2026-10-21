package com.seemoremagnifier.ui.magnifier

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.hypot

/** Shared, coordinated tap/pinch recognition for live preview and frozen content. */
internal suspend fun PointerInputScope.detectMagnifierGestures(
    onTap: (Offset) -> Unit,
    onZoom: (Float) -> Unit,
    onPan: (Offset) -> Unit = {},
    onPanFling: (Offset) -> Unit = {},
    onGestureStart: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onGestureStart()
        val tapTracker = GestureTapTracker(viewConfiguration.touchSlop)
        val velocityTracker = VelocityTracker().apply {
            addPosition(down.uptimeMillis, down.position)
        }
        var previousSpan = 0f
        var previousPosition = down.position
        var hadMultiplePointers = false
        var wasPanning = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                val released = event.changes.firstOrNull { it.id == down.id }
                if (event.type == PointerEventType.Release &&
                    !hadMultiplePointers && released != null &&
                    LiveGesturePolicy.isSwipeUp(
                        start = down.position,
                        end = released.position,
                        minimumDistance = with(density) { 72.dp.toPx() },
                    )
                ) {
                    onSwipeUp()
                }
                if (event.type == PointerEventType.Release &&
                    wasPanning && !hadMultiplePointers && released != null
                ) {
                    velocityTracker.addPosition(released.uptimeMillis, released.position)
                    val velocity = velocityTracker.calculateVelocity()
                    onPanFling(Offset(velocity.x, velocity.y))
                }
                if (event.type == PointerEventType.Release &&
                    tapTracker.canFinishAsTap() &&
                    event.changes.none { it.isConsumed }
                ) {
                    onTap(down.position)
                }
                break
            }

            val primary = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
            if (!hadMultiplePointers && pressed.size == 1) {
                velocityTracker.addPosition(primary.uptimeMillis, primary.position)
            }
            val movement = hypot(
                (primary.position.x - down.position.x).toDouble(),
                (primary.position.y - down.position.y).toDouble(),
            ).toFloat()
            tapTracker.observe(pressed.size, movement)

            if (pressed.size > 1) {
                hadMultiplePointers = true
                wasPanning = false
            } else if (!hadMultiplePointers && movement > viewConfiguration.touchSlop) {
                val panDelta = if (wasPanning) {
                    primary.position - previousPosition
                } else {
                    primary.position - down.position
                }
                if (panDelta != Offset.Zero) onPan(panDelta)
                wasPanning = true
            }
            previousPosition = primary.position

            if (pressed.size > 1 || movement > viewConfiguration.touchSlop) {
                pressed.forEach { it.consume() }
            }

            val span = if (pressed.size > 1) {
                averagePointerSpan(pressed.map { it.position })
            } else {
                0f
            }
            if (span > 0f && previousSpan > 0f) {
                val scale = span / previousSpan
                if (scale.isFinite() && scale > 0f && kotlin.math.abs(scale - 1f) > 0.001f) {
                    onZoom(scale)
                }
            }
            previousSpan = span
        }
    }
}

private fun averagePointerSpan(positions: List<Offset>): Float {
    if (positions.size < 2) return 0f
    val centerX = positions.sumOf { it.x.toDouble() } / positions.size
    val centerY = positions.sumOf { it.y.toDouble() } / positions.size
    return positions.sumOf { position ->
        hypot(position.x - centerX, position.y - centerY)
    }.div(positions.size).toFloat()
}