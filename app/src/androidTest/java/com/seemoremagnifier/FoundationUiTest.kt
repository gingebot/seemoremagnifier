// SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.seemoremagnifier

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationUiTest {
    @Test
    fun launcherActivityStarts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.window)
            }
        }
    }

    @Test
    fun activityDeclaresOrientationAndSizeChangesForInPlaceHandling() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val configChanges = activity.packageManager.getActivityInfo(activity.componentName, 0).configChanges
                assertTrue(configChanges and android.content.pm.ActivityInfo.CONFIG_ORIENTATION != 0)
                assertTrue(configChanges and android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE != 0)
                assertTrue(configChanges and android.content.pm.ActivityInfo.CONFIG_SCREEN_LAYOUT != 0)
                assertTrue(configChanges and android.content.pm.ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE != 0)
            }
        }
    }
}