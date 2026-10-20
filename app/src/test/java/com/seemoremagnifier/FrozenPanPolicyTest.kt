package com.seemoremagnifier

import androidx.compose.ui.geometry.Offset
import com.seemoremagnifier.domain.magnifier.FrozenPanPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class FrozenPanPolicyTest {
    @Test
    fun clampsPanToTheMagnifiedImageEdges() {
        val result = FrozenPanPolicy.clamp(
            offset = Offset(900f, -900f),
            viewportWidth = 1000,
            viewportHeight = 1000,
            imageWidth = 1000,
            imageHeight = 500,
            scale = 2f,
        )

        assertEquals(500f, result.x, 0.001f)
        assertEquals(-0f, result.y, 0.001f)
    }

    @Test
    fun allowsPanOnlyOnAxesWithOverflow() {
        val result = FrozenPanPolicy.clamp(
            offset = Offset(200f, 200f),
            viewportWidth = 1000,
            viewportHeight = 1000,
            imageWidth = 1000,
            imageHeight = 500,
            scale = 1.5f,
        )

        assertEquals(200f, result.x, 0.001f)
        assertEquals(0f, result.y, 0.001f)
    }

    @Test
    fun fitSizedImageCannotBePanned() {
        val result = FrozenPanPolicy.clamp(
            offset = Offset(150f, -80f),
            viewportWidth = 1000,
            viewportHeight = 1000,
            imageWidth = 1000,
            imageHeight = 500,
            scale = 1f,
        )

        assertEquals(0f, result.x, 0.001f)
        assertEquals(0f, result.y, 0.001f)
    }
}