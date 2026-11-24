package com.fuguang.localdiary

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.backup.BackupManager
import com.fuguang.localdiary.data.*
import com.fuguang.localdiary.ui.*
import com.fuguang.localdiary.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class Ux125Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()

    @Test fun readerRestoresItsPositionAfterReturningFromSharing() {
        val vm=DiaryViewModel(app)
        val body=(1..100).joinToString("\n") { "阅读恢复第 $it 行：普通的一天也值得记录。" }
        runBlocking { vm.clearAll();vm.save(DiaryEntity(date=LocalDate.now().toString(),time="09:00",body=body)) }
        try {
            compose.waitUntil(10000) { compose.onAllNodesWithText(body).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(body,useUnmergedTree=true).performClick()
            val scroll=compose.onNodeWithTag("reader-scroll")
            scroll.performTouchInput { swipe(Offset(width*.5f,height*.8f),Offset(width*.5f,height*.2f),550) }
            val before=scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue(before>0)
            compose.onNodeWithContentDescription("分享当天日记").performClick()
            compose.onNodeWithContentDescription("返回").performClick()
            val after=scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertEquals(before,after,.00001f)
        } finally { runBlocking {vm.clearAll()} }
    }

    @Test fun sharingRemembersParametersAndKeepsGenerateVisible() {
        val original=ShareSettingsStore.read(app)
        val entries=(1..25).map { DiaryEntity(id=it.toLong(),date=LocalDate.now().toString(),time="09:00",body="用于检查固定操作栏的示例日记 $it") }
        try {
            compose.runOnUiThread { compose.activity.setContent { var open by remember { mutableStateOf(true) };FuguangTheme {
                if(open)ShareScreen(entries,entries.first().date) {open=false}
                else Button(onClick={open=true}) {Text("重新打开分享")}
            } } }
            compose.onNodeWithTag("share-generate").assertIsDisplayed()
            compose.onNodeWithTag("share-style-FROST").performScrollTo().performClick()
            compose.onNodeWithTag("share-style-FROST").assertIsSelected()
            compose.onNodeWithTag("share-font").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(42f) }
            compose.onNodeWithTag("share-spacing").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(1.9f) }
            compose.onNodeWithTag("share-generate").assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("重新打开分享").performClick()
            compose.onNodeWithTag("share-style-FROST").assertIsSelected()
            assertEquals("FROST",ShareSettingsStore.read(app).template)
            assertEquals(42f,ShareSettingsStore.read(app).font,.01f)
            assertEquals(1.9f,ShareSettingsStore.read(app).spacing,.01f)
        } finally { ShareSettingsStore.save(app,original) }
    }
}
