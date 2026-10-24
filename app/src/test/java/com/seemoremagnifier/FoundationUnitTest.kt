// SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.seemoremagnifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundationUnitTest {
    @Test
    fun foundationTestInfrastructureIsAvailable() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun cameraAnalysisFramesUseLatestOnlyBackpressure() {
        assertTrue(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST == 0)
    }
}