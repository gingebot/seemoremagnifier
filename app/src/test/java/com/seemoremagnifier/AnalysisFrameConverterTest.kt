package com.seemoremagnifier

import com.seemoremagnifier.data.camera.AnalysisFrameConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisFrameConverterTest {
    @Test
    fun stillSamplingPreservesResolutionWithinConfiguredDimensionBound() {
        val sample = AnalysisFrameConverter.stillSampleSizeFor(8064, 6048)

        assertEquals(4, sample)
        assertEquals(2016, 8064 / sample)
        assertEquals(1512, 6048 / sample)
    }

    @Test
    fun stillSamplingLeavesSmallImagesAtFullResolution() {
        assertEquals(1, AnalysisFrameConverter.stillSampleSizeFor(1920, 1080))
    }
}