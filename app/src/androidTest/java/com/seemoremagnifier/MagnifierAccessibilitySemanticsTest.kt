// SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.seemoremagnifier

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.seemoremagnifier.ui.magnifier.magnifierAccessibilitySemantics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MagnifierAccessibilitySemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liveSemanticsExposeStateZoomAndFreezeAction() {
        var freezeInvoked = false
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .testTag("magnifier")
                    .magnifierAccessibilitySemantics(
                        contentDescription = "Live camera magnifier",
                        stateDescription = "Live, 2.0x zoom",
                        actionLabel = "Freeze image",
                        onAction = { freezeInvoked = true },
                    ),
            )
        }

        val config = composeRule.onNodeWithTag("magnifier").fetchSemanticsNode().config
        assertEquals(listOf("Live camera magnifier"), config[SemanticsProperties.ContentDescription])
        assertEquals("Live, 2.0x zoom", config[SemanticsProperties.StateDescription])
        val actions = config[SemanticsActions.CustomActions]
        assertEquals(listOf("Freeze image"), actions.map { it.label })
        composeRule.runOnIdle { assertTrue(actions.single().action()) }

        composeRule.runOnIdle { assertTrue(freezeInvoked) }
    }

    @Test
    fun frozenSemanticsExposeStateZoomAndResumeAction() {
        var resumeInvoked = false
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .testTag("magnifier")
                    .magnifierAccessibilitySemantics(
                        contentDescription = "Frozen magnified image",
                        stateDescription = "Frozen, 3.0x zoom",
                        actionLabel = "Resume live preview",
                        onAction = { resumeInvoked = true },
                    ),
            )
        }

        val config = composeRule.onNodeWithTag("magnifier").fetchSemanticsNode().config
        assertEquals(listOf("Frozen magnified image"), config[SemanticsProperties.ContentDescription])
        assertEquals("Frozen, 3.0x zoom", config[SemanticsProperties.StateDescription])
        val actions = config[SemanticsActions.CustomActions]
        assertEquals(listOf("Resume live preview"), actions.map { it.label })
        composeRule.runOnIdle { assertTrue(actions.single().action()) }

        composeRule.runOnIdle { assertTrue(resumeInvoked) }
    }

    @Test
    fun unavailableActionIsNotExposed() {
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .testTag("magnifier")
                    .magnifierAccessibilitySemantics(
                        contentDescription = "Frozen magnified image",
                        stateDescription = "Frozen, 1.0x zoom",
                        actionLabel = null,
                        onAction = {},
                    ),
            )
        }

        val actions = composeRule.onNodeWithTag("magnifier")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertTrue(actions.isEmpty())
    }

    @Test
    fun cameraErrorIsAnnouncedAndExposesRetryAction() {
        var retryInvoked = false
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .testTag("magnifier")
                    .magnifierAccessibilitySemantics(
                        contentDescription = "Live camera magnifier",
                        stateDescription = "Live, 1.0x zoom. Camera error: Camera unavailable",
                        actionLabel = "Retry camera",
                        announceStateChange = true,
                        onAction = { retryInvoked = true },
                    ),
            )
        }

        val config = composeRule.onNodeWithTag("magnifier").fetchSemanticsNode().config
        assertEquals(
            "Live, 1.0x zoom. Camera error: Camera unavailable",
            config[SemanticsProperties.StateDescription],
        )
        assertEquals(LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
        val retryAction = config[SemanticsActions.CustomActions].single()
        assertEquals("Retry camera", retryAction.label)
        composeRule.runOnIdle { assertTrue(retryAction.action()) }
        composeRule.runOnIdle { assertTrue(retryInvoked) }
    }
}