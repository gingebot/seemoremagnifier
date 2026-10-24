// SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.seemoremagnifier.domain.magnifier

/** Pure zoom-ratio policy shared by gesture handling and tests. */
object ZoomPolicy {
    fun ratioAfterGesture(
        currentRatio: Float,
        gestureScale: Float,
        minRatio: Float,
        maxRatio: Float,
    ): Float {
        require(currentRatio.isFinite()) { "Current zoom ratio must be finite." }
        require(gestureScale.isFinite() && gestureScale > 0f) {
            "Gesture scale must be finite and positive."
        }
        require(minRatio.isFinite() && maxRatio.isFinite() && minRatio > 0f && minRatio <= maxRatio) {
            "Zoom bounds must be finite, positive, and ordered."
        }

        return (currentRatio * gestureScale).coerceIn(minRatio, maxRatio)
    }
}