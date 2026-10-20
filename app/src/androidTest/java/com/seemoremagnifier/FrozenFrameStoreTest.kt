package com.seemoremagnifier

import android.graphics.Bitmap
import com.seemoremagnifier.data.frame.LatestFrameStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrozenFrameStoreTest {
    @Test
    fun offeringNewCandidateReleasesPreviousCandidate() {
        val store = LatestFrameStore<Bitmap> { it.recycle() }
        val first = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        store.offer(first)
        store.offer(second)

        assertTrue(first.isRecycled)
        val transferred = store.freezeLatest()
        assertTrue(transferred === second)
        assertFalse(second.isRecycled)
        store.close()
        second.recycle()
    }

    @Test
    fun freezingWithoutCandidateReturnsNullAndDoesNotPauseAnalyzer() {
        val store = LatestFrameStore<Bitmap> { it.recycle() }

        assertNull(store.freezeLatest())
        assertFalse(store.isPaused())
        store.close()
    }

    @Test
    fun pauseRejectsAndReleasesIncomingCandidates() {
        val store = LatestFrameStore<Bitmap> { it.recycle() }
        val candidate = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        store.offer(candidate)
        assertNotNull(store.freezeLatest())

        val laterFrame = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        store.offer(laterFrame)

        assertTrue(laterFrame.isRecycled)
        store.close()
        candidate.recycle()
    }
}