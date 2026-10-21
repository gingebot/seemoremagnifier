package com.seemoremagnifier

import androidx.compose.ui.geometry.Offset
import com.seemoremagnifier.domain.magnifier.FrozenPanPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrozenPanPolicyTest {
    @Test
    fun flingRequiresFiniteVelocityAboveThresholdOnEitherAxis() {
        assertTrue(FrozenPanPolicy.shouldFling(Offset(100f, 0f)))
        assertTrue(FrozenPanPolicy.shouldFling(Offset(0f, -250f)))
        assertFalse(FrozenPanPolicy.shouldFling(Offset(99.9f, -99.9f)))
        assertFalse(FrozenPanPolicy.shouldFling(Offset(Float.NaN, 500f)))
    }

    @Test
    fun flingOffsetDecaysOverTimeAndStopsAtConfiguredDuration() {
        val start = Offset(10f, -20f)
        val velocity = Offset(1_000f, -500f)
        val early = FrozenPanPolicy.flingOffset(start, velocity, elapsedMillis = 200L)
        val finished = FrozenPanPolicy.flingOffset(start, velocity, elapsedMillis = 2_000L)

        assertTrue(early.x > start.x)
        assertTrue(early.y < start.y)
        assertEquals(
            FrozenPanPolicy.flingOffset(start, velocity, FrozenPanPolicy.FLING_DURATION_MILLIS),
            finished,
        )
    }

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