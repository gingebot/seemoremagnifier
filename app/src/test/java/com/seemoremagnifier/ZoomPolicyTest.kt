// SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.seemoremagnifier

import com.seemoremagnifier.domain.magnifier.ZoomPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ZoomPolicyTest {
    @Test
    fun zoomInMultipliesCurrentRatioByGestureScale() {
        assertEquals(3f, ZoomPolicy.ratioAfterGesture(2f, 1.5f, 1f, 8f), 0.001f)
    }

    @Test
    fun zoomOutMultipliesCurrentRatioByGestureScale() {
        assertEquals(1.5f, ZoomPolicy.ratioAfterGesture(3f, 0.5f, 1f, 8f), 0.001f)
    }

    @Test
    fun zoomClampsToCameraMinimumAndMaximum() {
        assertEquals(1f, ZoomPolicy.ratioAfterGesture(1.1f, 0.5f, 1f, 8f), 0.001f)
        assertEquals(8f, ZoomPolicy.ratioAfterGesture(7.5f, 2f, 1f, 8f), 0.001f)
    }

    @Test
    fun invalidGestureScaleIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ZoomPolicy.ratioAfterGesture(1f, Float.NaN, 1f, 8f)
        }
    }
}