package com.seemoremagnifier

import com.seemoremagnifier.ui.magnifier.GestureTapTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTapTrackerTest {
    @Test
    fun stationarySinglePointerRemainsATap() {
        val tracker = GestureTapTracker(touchSlop = 10f)
        tracker.observe(pointerCount = 1, distanceFromDown = 0f)
        tracker.observe(pointerCount = 1, distanceFromDown = 10f)

        assertTrue(tracker.canFinishAsTap())
    }

    @Test
    fun movementBeyondSlopCancelsTap() {
        val tracker = GestureTapTracker(touchSlop = 10f)
        tracker.observe(pointerCount = 1, distanceFromDown = 11f)

        assertFalse(tracker.canFinishAsTap())
    }

    @Test
    fun multiPointerGestureRemainsCancelledAfterPointerCountDrops() {
        val tracker = GestureTapTracker(touchSlop = 10f)
        tracker.observe(pointerCount = 2, distanceFromDown = 0f)
        tracker.observe(pointerCount = 1, distanceFromDown = 0f)

        assertFalse(tracker.canFinishAsTap())
    }

    @Test
    fun cancelledGestureCannotBecomeTap() {
        val tracker = GestureTapTracker(touchSlop = 10f)
        tracker.cancel()
        tracker.observe(pointerCount = 1, distanceFromDown = 0f)

        assertFalse(tracker.canFinishAsTap())
    }
}