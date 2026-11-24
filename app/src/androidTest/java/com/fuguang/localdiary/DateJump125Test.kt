package com.fuguang.localdiary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.ui.DateJumpDialog
import com.fuguang.localdiary.ui.theme.FuguangTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DateJump125Test {
    @get:Rule val compose=createComposeRule()

    @Test fun opensAsCalendarAndOnlyConfirmsTheTappedDate() {
        var selected:String?=null
        compose.setContent { FuguangTheme { DateJumpDialog("2026-11-23",{}, {selected=it}) } }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        val day=hasText("15",substring=true) or hasContentDescription("15",substring=true)
        compose.waitUntil(5_000) { compose.onAllNodes(day,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(day,useUnmergedTree=true).performClick()
        assertNull(selected)
        compose.onNodeWithText("跳转").performClick()
        assertEquals("2026-11-15",selected)
    }

    @Test fun cancellingDoesNotSelectAndExistingDateHasNoTimezoneShift() {
        var selected:String?=null
        var dismissed=false
        compose.setContent { FuguangTheme { DateJumpDialog("2024-02-29",{dismissed=true},{selected=it}) } }
        compose.onNodeWithText("取消").performClick()
        assertTrue(dismissed)
        assertNull(selected)
        compose.onNodeWithText("跳转").performClick()
        assertEquals("2024-02-29",selected)
    }
}
