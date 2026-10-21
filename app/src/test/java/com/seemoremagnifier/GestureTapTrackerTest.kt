package com.seemoremagnifier

import com.seemoremagnifier.ui.magnifier.GestureTapTracker
import com.seemoremagnifier.ui.magnifier.LiveGesturePolicy
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTapTrackerTest {
    @Test
    fun upwardSwipeRequiresDistanceAndVerticalDominance() {
        assertTrue(
            LiveGesturePolicy.isSwipeUp(
                start = Offset(100f, 200f),
                end = Offset(110f, 100f),
                minimumDistance = 72f,
            ),
        )
        assertFalse(
            LiveGesturePolicy.isSwipeUp(
                start = Offset(100f, 200f),
                end = Offset(100f, 129f),
                minimumDistance = 72f,
            ),
        )
        assertFalse(
            LiveGesturePolicy.isSwipeUp(
                start = Offset(100f, 200f),
                end = Offset(20f, 100f),
                minimumDistance = 72f,
            ),
        )
        assertFalse(
            LiveGesturePolicy.isSwipeUp(
                start = Offset(100f, 200f),
                end = Offset(100f, 300f),
                minimumDistance = 72f,
            ),
        )
    }

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