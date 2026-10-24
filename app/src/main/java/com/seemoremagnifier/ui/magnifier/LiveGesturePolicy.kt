// SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.seemoremagnifier.ui.magnifier

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

internal object LiveGesturePolicy {
    fun isSwipeUp(start: Offset, end: Offset, minimumDistance: Float): Boolean {
        if (!start.x.isFinite() || !start.y.isFinite() ||
            !end.x.isFinite() || !end.y.isFinite() ||
            !minimumDistance.isFinite() || minimumDistance <= 0f
        ) {
            return false
        }
        val deltaX = end.x - start.x
        val deltaY = end.y - start.y
        return deltaY <= -minimumDistance && abs(deltaY) >= abs(deltaX) * 1.5f
    }
}