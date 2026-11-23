package com.fuguang.localdiary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.data.FootprintEntity
import com.fuguang.localdiary.ui.*
import com.fuguang.localdiary.ui.theme.FuguangTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class ScrollingTest {
    @get:Rule val compose = createComposeRule()

    private fun compact(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                FuguangTheme { Box(Modifier.size(width = 340.dp, height = 360.dp)) { content() } }
            }
        }
    }

    private fun assertSwipeMoves(tag: String) {
        val node = compose.onNodeWithTag(tag)
        val before = node.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        node.performTouchInput {
            // Drag the page gutter, outside the map and the independently scrollable text editor.
            swipe(start = androidx.compose.ui.geometry.Offset(width * .02f, height * .85f),
                end = androidx.compose.ui.geometry.Offset(width * .02f, height * .15f), durationMillis = 500)
        }
        compose.waitForIdle()
        val after = node.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("$tag must move after a real finger swipe: $before -> $after", after > before)
    }

    @Test fun calendarScrollsWithLargeFontAndSixWeekMonthIsComplete() {
        compact { CalendarScreen(emptyList(), {}) }
        val now = YearMonth.now()
        val target = (0L..12L).map { now.plusMonths(it) }.first { (it.atDay(1).dayOfWeek.value % 7) + it.lengthOfMonth() > 35 }
        repeat(java.time.temporal.ChronoUnit.MONTHS.between(now, target).toInt()) {
            compose.onNodeWithContentDescription("下个月").performClick()
        }
        compose.onNodeWithText(target.lengthOfMonth().toString()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("记录过的日子").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("回忆").performScrollTo()
        assertSwipeMoves("calendar-scroll")
    }

    @Test fun mineScrollsToLastSettingAndFootnote() {
        compact { MineScreen(emptyList(), emptyList(), {}, {}, {}, {}, {}) }
        assertSwipeMoves("mine-scroll")
        compose.onNodeWithText("清空全部数据").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("卸载应用或清除应用数据", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun sixtyFootprintsScrollAsOnePage() {
        val footprints = (0..59).map { FootprintEntity(LocalDate.now().minusDays(it.toLong()).toString(), "12:00", 1.3, 103.8, 20f) }
        compact { FootprintScreen(footprints, true, {}, false, null, {}, {}, {}) }
        assertSwipeMoves("footprint-scroll")
        compose.onNodeWithTag("footprint-scroll").performScrollToNode(hasText(footprints.last().date))
        compose.onNodeWithText(footprints.last().date).assertIsDisplayed()
        compose.onNodeWithTag("footprint-scroll").performScrollToIndex(0)
        compose.onNodeWithText("记录今日位置").assertIsDisplayed()
    }

    @Test fun hundredDiariesScrollToLastEntry() {
        val diaries = (1..100).map { DiaryEntity(id = it.toLong(), date = "2026-11-22", time = "12:00", body = "Long journal entry $it " + "测试正文 ".repeat(100)) }
        compact { JournalScreen(diaries, "", {}, false, {}, null, {}, {}) }
        assertSwipeMoves("journal-scroll")
        compose.onNodeWithTag("journal-scroll").performScrollToNode(hasText(diaries.last().body))
        compose.onNodeWithText(diaries.last().body).assertIsDisplayed()
    }

    @Test fun editorScrollsPastTextFieldToImagePicker() {
        compact { EditorScreen(DiaryEntity(id = 1, date = "2026-11-22", time = "12:00", body = "长正文\n".repeat(300)), {}, {}, {}) }
        assertSwipeMoves("editor-scroll")
        repeat(4) {
            compose.onNodeWithTag("editor-scroll").performTouchInput {
                swipe(start = androidx.compose.ui.geometry.Offset(width * .02f, height * .85f),
                    end = androidx.compose.ui.geometry.Offset(width * .02f, height * .15f), durationMillis = 500)
            }
        }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "deep-editor.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("保存后，内容和图片都会留在本机。").performScrollTo()
        compose.onNodeWithText("添加图片").assertIsDisplayed()
        compose.onNodeWithText("保存后，内容和图片都会留在本机。").performScrollTo().assertIsDisplayed()
    }
}
