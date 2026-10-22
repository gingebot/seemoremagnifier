package com.seemoremagnifier.domain.magnifier

import androidx.compose.ui.geometry.Offset
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Computes bounded translation for a fitted frozen image, using pixel dimensions. */
object FrozenPanPolicy {
    const val MIN_FLING_VELOCITY = 100f
    const val FLING_DURATION_MILLIS = 700L
    private const val FLING_DECAY_PER_SECOND = 5.0

    fun shouldFling(velocity: Offset): Boolean =
        velocity.x.isFinite() && velocity.y.isFinite() &&
            (kotlin.math.abs(velocity.x) >= MIN_FLING_VELOCITY ||
                kotlin.math.abs(velocity.y) >= MIN_FLING_VELOCITY)

    fun flingOffset(start: Offset, velocity: Offset, elapsedMillis: Long): Offset {
        require(start.x.isFinite() && start.y.isFinite()) { "Pan offset must be finite." }
        require(velocity.x.isFinite() && velocity.y.isFinite()) { "Pan velocity must be finite." }
        require(elapsedMillis >= 0L) { "Elapsed time must not be negative." }
        val seconds = (elapsedMillis.coerceAtMost(FLING_DURATION_MILLIS)) / 1000.0
        val distanceFactor = (1.0 - exp(-FLING_DECAY_PER_SECOND * seconds)) / FLING_DECAY_PER_SECOND
        return Offset(
            x = start.x + (velocity.x * distanceFactor).toFloat(),
            y = start.y + (velocity.y * distanceFactor).toFloat(),
        )
    }

    fun fitScale(viewportWidth: Int, viewportHeight: Int, imageWidth: Int, imageHeight: Int): Float {
        require(viewportWidth >= 0 && viewportHeight >= 0) { "Viewport dimensions must not be negative." }
        require(imageWidth > 0 && imageHeight > 0) { "Image dimensions must be positive." }
        if (viewportWidth == 0 || viewportHeight == 0) return 0f
        return min(
            viewportWidth.toFloat() / imageWidth,
            viewportHeight.toFloat() / imageHeight,
        )
    }

    fun viewportScaleCompensation(
        captureViewportWidth: Int,
        captureViewportHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
    ): Float {
        val capturedFitScale = fitScale(
            captureViewportWidth,
            captureViewportHeight,
            imageWidth,
            imageHeight,
        )
        val currentFitScale = fitScale(viewportWidth, viewportHeight, imageWidth, imageHeight)
        if (capturedFitScale == 0f || currentFitScale == 0f) return 1f
        return capturedFitScale / currentFitScale
    }

    fun clamp(
        offset: Offset,
        viewportWidth: Int,
        viewportHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
        scale: Float,
    ): Offset {
        require(offset.x.isFinite() && offset.y.isFinite()) { "Pan offset must be finite." }
        require(viewportWidth >= 0 && viewportHeight >= 0) { "Viewport dimensions must not be negative." }
        require(imageWidth > 0 && imageHeight > 0) { "Image dimensions must be positive." }
        require(scale.isFinite() && scale > 0f) { "Image scale must be finite and positive." }

        if (viewportWidth == 0 || viewportHeight == 0) return Offset.Zero

        val fitScale = fitScale(viewportWidth, viewportHeight, imageWidth, imageHeight)
        val maxX = max(0f, (imageWidth * fitScale * scale - viewportWidth) / 2f)
        val maxY = max(0f, (imageHeight * fitScale * scale - viewportHeight) / 2f)
        return Offset(
            x = offset.x.coerceIn(-maxX, maxX),
            y = offset.y.coerceIn(-maxY, maxY),
        )
    }
}