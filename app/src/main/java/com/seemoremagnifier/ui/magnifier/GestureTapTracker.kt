package com.seemoremagnifier.ui.magnifier

/** Tracks whether a pointer sequence is still eligible to be interpreted as a tap. */
internal class GestureTapTracker(
    private val touchSlop: Float,
) {
    private var tapCancelled = false

    init {
        require(touchSlop.isFinite() && touchSlop >= 0f) {
            "Touch slop must be finite and non-negative."
        }
    }

    fun observe(pointerCount: Int, distanceFromDown: Float) {
        require(pointerCount > 0) { "At least one active pointer is required." }
        require(distanceFromDown.isFinite() && distanceFromDown >= 0f) {
            "Pointer movement must be finite and non-negative."
        }
        if (pointerCount != 1 || distanceFromDown > touchSlop) {
            tapCancelled = true
        }
    }

    fun cancel() {
        tapCancelled = true
    }

    fun canFinishAsTap(): Boolean = !tapCancelled
}