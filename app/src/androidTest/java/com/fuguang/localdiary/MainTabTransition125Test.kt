package com.fuguang.localdiary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.ui.MainTab
import com.fuguang.localdiary.ui.MainTabTransition
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTabTransition125Test {
    @get:Rule val compose=createComposeRule()

    @Test fun enteringPageFollowsTabOrderInBothDirections() {
        var tab by mutableStateOf(MainTab.JOURNAL)
        compose.setContent { MainTabTransition(tab) { page -> Box(Modifier.fillMaxSize().testTag("page-${page.name}")) { Text(page.label) } } }
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread { tab=MainTab.CALENDAR }
        compose.mainClock.advanceTimeBy(80)
        assertTrue(compose.onNodeWithTag("page-CALENDAR").getUnclippedBoundsInRoot().left.value>0)
        compose.mainClock.advanceTimeBy(700)
        compose.runOnUiThread { tab=MainTab.JOURNAL }
        compose.mainClock.advanceTimeBy(80)
        assertTrue(compose.onNodeWithTag("page-JOURNAL").getUnclippedBoundsInRoot().left.value<0)
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("page-JOURNAL").assertIsDisplayed()
    }

    @Test fun rapidChangesSettleOnTheLastSelectedTab() {
        var tab by mutableStateOf(MainTab.JOURNAL)
        compose.setContent { MainTabTransition(tab) { page -> Box(Modifier.fillMaxSize().testTag("page-${page.name}")) { Text(page.label) } } }
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread { tab=MainTab.MINE }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnUiThread { tab=MainTab.CALENDAR }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("page-CALENDAR").assertIsDisplayed()
        compose.onAllNodesWithTag("page-MINE").assertCountEquals(0)
        compose.onAllNodesWithTag("page-JOURNAL").assertCountEquals(0)
    }
}
