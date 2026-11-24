package com.fuguang.localdiary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.ui.*
import com.fuguang.localdiary.ui.theme.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Ordering126Test {
    @get:Rule val compose=createComposeRule()
    private val entries=listOf(
        DiaryEntity(id=1,date="2026-11-22",time="23:00",body="昨天深夜"),
        DiaryEntity(id=2,date="2026-11-23",time="09:00",body="今天早晨"),
        DiaryEntity(id=3,date="2026-11-23",time="18:00",body="今天傍晚")
    )

    @Test fun homeShowsNewerDatesAndTimesBeforeOlderOnes() {
        compose.setContent { FuguangTheme { Box(Modifier.size(340.dp,650.dp)) {
            JournalScreen(entries,"",{},false,{},null,{}, {},appearance=JournalAppearance.PAPER)
        } } }
        val newest=compose.onNodeWithTag("diary-3").getUnclippedBoundsInRoot().top.value
        val morning=compose.onNodeWithTag("diary-2").getUnclippedBoundsInRoot().top.value
        val yesterday=compose.onNodeWithTag("diary-1").getUnclippedBoundsInRoot().top.value
        assertTrue(newest<morning&&morning<yesterday)
    }

    @Test fun calendarDayReadingRemainsOldestToNewest() {
        compose.setContent { FuguangTheme { Box(Modifier.size(340.dp,650.dp)) {
            DayReader(entries,"2026-11-23",onDate={},onBack={},onEdit={},onSave={},onShare={})
        } } }
        val morning=compose.onNodeWithTag("reader-body-2").getUnclippedBoundsInRoot().top.value
        val evening=compose.onNodeWithTag("reader-body-3").getUnclippedBoundsInRoot().top.value
        assertTrue(morning<evening)
        compose.onAllNodesWithText("昨天深夜").assertCountEquals(0)
    }
}
