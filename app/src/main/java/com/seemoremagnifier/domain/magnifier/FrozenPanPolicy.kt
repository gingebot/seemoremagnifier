package com.seemoremagnifier.domain.magnifier

import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/** Computes bounded translation for a fitted frozen image, using pixel dimensions. */
object FrozenPanPolicy {
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
        require(scale.isFinite() && scale >= 1f) { "Image scale must be finite and at least one." }

        if (viewportWidth == 0 || viewportHeight == 0) return Offset.Zero

        val fitScale = min(
            viewportWidth.toFloat() / imageWidth,
            viewportHeight.toFloat() / imageHeight,
        )
        val maxX = max(0f, (imageWidth * fitScale * scale - viewportWidth) / 2f)
        val maxY = max(0f, (imageHeight * fitScale * scale - viewportHeight) / 2f)
        return Offset(
            x = offset.x.coerceIn(-maxX, maxX),
            y = offset.y.coerceIn(-maxY, maxY),
        )
    }
}