package com.fuguang.localdiary

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.DiaryViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageImportTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun pickerResultCopiesOriginalBytesToPrivateStorageAndSurvivesSourceRemoval() {
        val context = compose.activity.application
        val vm = DiaryViewModel(context)
        runBlocking { vm.clearAll() }
        val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
        bitmap.recycle()
        val bytes = buffer.toByteArray()
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Fuguang-instrumentation-image.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        })!!
        var sourceDeleted = false
        Intents.init()
        try {
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            // Exercise the app's picker callback with a real MediaStore URI; no personal photo is selected.
            Intents.intending(IntentMatchers.anyIntent()).respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
            compose.onNodeWithText("写日记").performClick()
            compose.onAllNodes(hasSetTextAction())[2].performTextInput("Private image persistence test")
            Espresso.closeSoftKeyboard()
            repeat(3) {
                compose.onNodeWithTag("editor-scroll").performTouchInput {
                    swipe(start = androidx.compose.ui.geometry.Offset(width * .02f, height * .85f),
                        end = androidx.compose.ui.geometry.Offset(width * .02f, height * .15f), durationMillis = 400)
                }
            }
            compose.onNodeWithText("添加图片").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("日记图片").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Private image persistence test").fetchSemanticsNodes().isNotEmpty() }
            val path = runBlocking { vm.allDiaries().single().images().single() }
            assertEquals(File(context.filesDir, "diary-images").canonicalFile, File(path).canonicalFile.parentFile)
            assertArrayEquals(bytes, File(path).readBytes())
            resolver.delete(uri, null, null)
            sourceDeleted = true
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Private image persistence test").fetchSemanticsNodes().isNotEmpty() }
            assertArrayEquals(bytes, File(path).readBytes())
        } finally {
            Intents.release()
            if (!sourceDeleted) runCatching { resolver.delete(uri, null, null) }
            runBlocking { vm.clearAll() }
        }
    }
}
