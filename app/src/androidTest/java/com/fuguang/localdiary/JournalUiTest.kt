package com.fuguang.localdiary

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.DiaryViewModel
import com.fuguang.localdiary.data.FootprintEntity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun longDraftAndEditingIdentitySurviveActivityRecreation() {
        val vm = DiaryViewModel(ApplicationProvider.getApplicationContext<Application>())
        runBlocking { vm.clearAll() }
        val longBody = (1..400).joinToString("\n") { "第 $it 行，测试长日记与键盘滚动。" }
        compose.onNodeWithText("写日记").performClick()
        compose.onNodeWithTag("editor-body").performTextInput(longBody)
        compose.onNodeWithText("保存后，内容和图片都会留在本机。").performScrollTo()
        compose.onNodeWithText("添加图片").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasSetTextAction() and hasText(longBody)).assertExists()
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(longBody).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(longBody).performClick()
        compose.onNodeWithContentDescription("编辑日期、心情和图片").performClick()
        compose.onNodeWithTag("editor-body").performTextReplacement("$longBody\n编辑草稿")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("编辑日记").assertExists()
        compose.onNode(hasSetTextAction() and hasText("$longBody\n编辑草稿")).assertExists()
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("$longBody\n编辑草稿").fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            assertEquals(1, vm.allDiaries().size)
            assertEquals("$longBody\n编辑草稿", vm.allDiaries().single().body)
            vm.clearAll()
        }
    }

    @Test fun invalidDateIsRejectedAndValidLeapDateSaves() {
        val vm = DiaryViewModel(ApplicationProvider.getApplicationContext<Application>())
        runBlocking { vm.clearAll() }
        compose.onNodeWithText("写日记").performClick()
        compose.onNodeWithTag("editor-body").performTextInput("Leap date test")
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("2026-02-30")
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.onNodeWithText("请填写有效的日期和时间", substring = true).assertExists()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("2024-02-29")
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Leap date test").fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            assertEquals("2024-02-29", vm.allDiaries().single().date)
            vm.clearAll()
        }
    }

    @Test fun createEditSearchAndNavigate() {
        val body = "Native device journal test"
        compose.onNodeWithText("写日记").performClick()
        compose.onNodeWithTag("editor-body").performTextInput(body)
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(body).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(body).performClick()
        compose.onNodeWithContentDescription("编辑日期、心情和图片").performClick()
        compose.onNodeWithTag("editor-body").performTextReplacement("$body edited")
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("$body edited").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("搜索日记").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("no-match-12345")
        compose.onNodeWithText("没有找到这段记忆").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("Native")
        compose.onNodeWithText("$body edited").assertExists()
        compose.onNodeWithContentDescription("搜索日记").performClick()
        compose.onNodeWithContentDescription("回忆", useUnmergedTree = true).performClick()
        runBlocking {
            DiaryViewModel(ApplicationProvider.getApplicationContext<Application>())
                .addFootprint(FootprintEntity("2026-11-22", "12:00", 1.3, 103.8, 20f))
        }
        compose.onNodeWithContentDescription("足迹", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("历史足迹").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("我的", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("日记", useUnmergedTree = true).performClick()
        compose.onNodeWithText("$body edited").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("$body edited").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { DiaryViewModel(ApplicationProvider.getApplicationContext<Application>()).clearAll() }
    }
}
