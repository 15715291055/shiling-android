package com.fuguang.localdiary

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.data.DiaryViewModel
import com.fuguang.localdiary.ui.theme.JournalAppearance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun snapshot(name: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        File(app.getExternalFilesDir(null), name).outputStream().use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    @Test fun bothThemesRenderMultipleDaysAndSelectionSurvivesRecreation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = DiaryViewModel(app)
        runBlocking {
            vm.clearAll()
            listOf(
                "今天把零碎的念头记下来，日子就有了自己的形状。",
                "窗边读了几页书，下午的阳光刚刚好。",
                "终于完成了实验，想把这一刻留下。",
                "和朋友吃了一顿饭，聊了很多近况。"
            ).forEachIndexed { index, body ->
                vm.save(DiaryEntity(date = java.time.LocalDate.now().minusDays(index.toLong()).toString(), time = "17:38", body = body))
            }
        }
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("daily-quote").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("拾零").assertExists()
            compose.onNodeWithTag("daily-quote").performClick()
            compose.onNodeWithText("每日一言").assertExists()
            compose.onNodeWithText("查看出处").assertExists()
            compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithContentDescription("我的", useUnmergedTree = true).performClick()
            compose.onNodeWithText("主题外观").performScrollTo().performClick()
            compose.onNodeWithText("时间线样式", substring = true).performScrollTo().performClick()
            compose.onNodeWithTag("theme-PAPER").performClick()
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithContentDescription("日记", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("journal-scroll").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "纸页手记"))
            snapshot("shiling-paper.png")
            compose.activityRule.scenario.recreate()
            assertEquals(JournalAppearance.PAPER, JournalAppearance.read(app))
            compose.onNodeWithTag("journal-scroll").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "纸页手记"))
            compose.onNodeWithContentDescription("我的", useUnmergedTree = true).performClick()
            compose.onNodeWithText("主题外观").performScrollTo().performClick()
            compose.onNodeWithText("时间线样式", substring = true).performScrollTo().performClick()
            compose.onNodeWithTag("theme-TIMELINE").performClick()
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithContentDescription("日记", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("journal-scroll").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "简约时序"))
            snapshot("shiling-timeline.png")
            compose.onNodeWithText("今天把零碎的念头记下来，日子就有了自己的形状。").assertExists()
        } finally {
            JournalAppearance.TIMELINE.save(app)
            runBlocking { vm.clearAll() }
        }
    }
}
