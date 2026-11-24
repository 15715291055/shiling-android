package com.fuguang.localdiary

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class Share120Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun generatePreviewFullScreenAndPageSwitchWorkEndToEnd() {
        val vm=DiaryViewModel(ApplicationProvider.getApplicationContext<Application>())
        runBlocking{vm.clearAll();vm.save(DiaryEntity(date=LocalDate.now().toString(),time="10:30",body=(1..100).joinToString("\n"){"分享预览第 $it 行，正文完整。"}))}
        try{
            compose.onNodeWithContentDescription("我的",useUnmergedTree=true).performClick()
            compose.onNodeWithText("每日分享").performScrollTo().performClick()
            compose.onNodeWithText("生成分享图片").assertIsDisplayed().performClick()
            compose.waitUntil(15000){compose.onAllNodesWithTag("share-preview").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("share-fullscreen").performScrollTo().performClick()
            compose.onNodeWithTag("photo-viewer").assertExists()
            compose.onNodeWithTag("photo-viewer").performTouchInput{swipeLeft(durationMillis=500)}
            compose.waitForIdle()
            compose.onNode(hasText("2 /",substring=true) and hasAnyAncestor(hasTestTag("photo-viewer"))).assertExists()
            compose.onNodeWithContentDescription("关闭图片预览").performClick()
            compose.onNodeWithText("下一张").performScrollTo().performClick()
            compose.onNodeWithTag("share-fullscreen").performClick()
            compose.onNode(hasText("2 /",substring=true) and hasAnyAncestor(hasTestTag("photo-viewer"))).assertExists()
            compose.onNodeWithContentDescription("关闭图片预览").performClick()
        }finally{runBlocking{vm.clearAll()}}
    }
}
