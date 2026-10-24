// SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.seemoremagnifier.ui.magnifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

internal fun Modifier.magnifierAccessibilitySemantics(
    contentDescription: String,
    stateDescription: String,
    actionLabel: String?,
    announceStateChange: Boolean = false,
    onAction: () -> Unit,
): Modifier = semantics {
    this.contentDescription = contentDescription
    this.stateDescription = stateDescription
    if (announceStateChange) liveRegion = LiveRegionMode.Polite
    customActions = actionLabel?.let { label ->
        listOf(
            CustomAccessibilityAction(label) {
                onAction()
                true
            },
        )
    }.orEmpty()
}