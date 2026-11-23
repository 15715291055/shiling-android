package com.fuguang.localdiary

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.ui.*
import com.fuguang.localdiary.ui.theme.FuguangTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Reading120Test {
    @get:Rule val compose=createComposeRule()

    @Test fun doubleTapEditsInPlaceCancelDoesNotWriteAndSaveIsExplicit() {
        var saved:DiaryEntity?=null
        val entry=DiaryEntity(id=8,date="2026-11-23",time="09:00",body="第一句，双击这里修改。\n第二句仍然在同一页。")
        compose.setContent{FuguangTheme{DayReader(listOf(entry),entry.date,onDate={},onBack={},onEdit={},onSave={saved=it},onShare={})}}
        compose.onNodeWithTag("reader-body-8").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithTag("reader-body-8").performTouchInput{doubleClick(Offset(40f,20f))}
        compose.onNodeWithTag("reader-body-8").assert(hasSetTextAction()).performTextReplacement("独立草稿")
        assertNull(saved)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithText(entry.body).assertExists();assertNull(saved)
        compose.onNodeWithTag("reader-body-8").performTouchInput{doubleClick(Offset(40f,20f))}
        compose.onNodeWithTag("reader-body-8").performTextReplacement("明确保存的新正文")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil { saved!=null }
        assertEquals("明确保存的新正文",saved!!.body)
    }

    @Test fun longReadingScrollsWithoutSwitchingAndOnlyReleaseBeyondThresholdSwitches() {
        val entries=listOf(DiaryEntity(id=1,date="2026-11-23",time="12:00",body="长篇日记\n".repeat(160)),DiaryEntity(id=2,date="2026-11-22",time="11:00",body="前一天的完整正文"))
        var date="2026-11-23"
        compose.setContent{var selected by remember{mutableStateOf(date)};FuguangTheme{DayReader(entries,selected,onDate={selected=it;date=it},onBack={},onEdit={},onSave={},onShare={})}}
        val scroll=compose.onNodeWithTag("reader-scroll")
        scroll.performTouchInput {swipe(Offset(width*.03f,height*.8f),Offset(width*.03f,height*.2f),500)}
        assertEquals("2026-11-23",date)
        assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()>0)
        scroll.performScrollToIndex(0)
        // A short drag must recover without changing the date.
        scroll.performTouchInput {swipe(Offset(width*.03f,20f),Offset(width*.03f,90f),700)}
        compose.waitForIdle();assertEquals("2026-11-23",date)
        scroll.performTouchInput {down(Offset(width*.03f,25f));moveTo(Offset(width*.03f,height*.78f),1000)}
        compose.waitForIdle();assertEquals("2026-11-23",date)
        scroll.performTouchInput {up()}
        compose.waitUntil(5000){date=="2026-11-22"}
        compose.onNodeWithText("前一天的完整正文").assertExists()
    }

    @Test fun reversingPullCancelsAndEditingDisablesDateSwitchWhileEmptyDaysRemainReachable() {
        val entry=DiaryEntity(id=3,date="2026-11-23",time="12:00",body="短日记，用于检查边界手势。")
        var date=entry.date
        compose.setContent{var selected by remember{mutableStateOf(date)};FuguangTheme{DayReader(listOf(entry),selected,onDate={selected=it;date=it},onBack={},onEdit={},onSave={},onShare={})}}
        val scroll=compose.onNodeWithTag("reader-scroll")
        scroll.performTouchInput {
            down(Offset(width*.02f,20f));moveTo(Offset(width*.02f,height*.7f),900);moveTo(Offset(width*.02f,65f),900);up()
        }
        compose.waitForIdle();assertEquals(entry.date,date)
        compose.onNodeWithTag("reader-body-3").performTouchInput{doubleClick(Offset(30f,20f))}
        scroll.performTouchInput{swipe(Offset(width*.02f,20f),Offset(width*.02f,height*.85f),800)}
        compose.waitForIdle();assertEquals(entry.date,date)
        compose.onNodeWithText("取消").performClick()
        scroll.performTouchInput{swipe(Offset(width*.02f,height*.85f),Offset(width*.02f,20f),900)}
        compose.waitUntil(5000){date=="2026-11-24"}
        compose.onNodeWithText("这一天还没有记录").assertExists()
    }
}
