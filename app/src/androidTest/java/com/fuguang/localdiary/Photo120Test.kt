package com.fuguang.localdiary

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class Photo120Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun timelineAndReaderPhotosOpenFullScreenWithoutOpeningEditor() {
        val app=ApplicationProvider.getApplicationContext<Application>();val vm=DiaryViewModel(app)
        val image=File(app.filesDir,"diary-images/photo-viewer-120.jpg").apply{parentFile!!.mkdirs()}
        Bitmap.createBitmap(320,480,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.rgb(170,190,160));image.outputStream().use{compress(Bitmap.CompressFormat.JPEG,90,it)};recycle()}
        runBlocking{vm.clearAll();image.parentFile!!.mkdirs()}
        Bitmap.createBitmap(320,480,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.GREEN);image.outputStream().use{compress(Bitmap.CompressFormat.JPEG,90,it)};recycle()}
        runBlocking{vm.save(DiaryEntity(date=LocalDate.now().toString(),time="09:00",body="点击照片直接放大",imagePaths=image.absolutePath))}
        try{
            compose.waitUntil(10000){compose.onAllNodesWithText("点击照片直接放大").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("日记图片",useUnmergedTree=true).performScrollTo().performClick()
            compose.onNodeWithTag("photo-viewer").assertExists()
            compose.onAllNodesWithTag("editor-body").assertCountEquals(0)
            compose.onNodeWithContentDescription("关闭图片预览").performClick()
            compose.onNodeWithText("点击照片直接放大",useUnmergedTree=true).performClick()
            compose.onNodeWithContentDescription("放大查看日记图片").performScrollTo().performClick()
            compose.onNodeWithTag("photo-viewer").assertExists()
            compose.onNodeWithContentDescription("关闭图片预览").performClick()
        }finally{runBlocking{vm.clearAll()}}
    }
}
