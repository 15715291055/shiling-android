package com.fuguang.localdiary

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.asAndroidBitmap

@RunWith(AndroidJUnit4::class)
class Background120Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun galleryBackgroundIsPrivateAndPersistsWithCustomColorsAfterRestart() {
        val context=compose.activity.application;val original=AppearanceStore.current
        val originalImage=original.image.takeIf{it.isNotBlank()}?.let{File(it)}
        val originalBytes=originalImage?.takeIf{it.exists()}?.readBytes()
        val resolver=context.contentResolver
        val source=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"shiling-background-test.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png")})!!
        Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888).apply{
            val canvas=android.graphics.Canvas(this);val paint=android.graphics.Paint()
            paint.shader=android.graphics.LinearGradient(0f,0f,600f,900f,intArrayOf(0xffffa995.toInt(),0xff9ba7d6.toInt(),0xffb8d8c5.toInt()),null,android.graphics.Shader.TileMode.CLAMP);canvas.drawRect(0f,0f,600f,900f,paint)
            paint.shader=null;paint.color=0xffffffff.toInt();canvas.drawCircle(350f,420f,35f,paint)
            resolver.openOutputStream(source)!!.use{compress(Bitmap.CompressFormat.PNG,100,it)};recycle()
        }
        var privatePath=""
        Intents.init()
        try{
            Intents.intending(IntentMatchers.anyIntent()).respondWith(ActivityResult(Activity.RESULT_OK,Intent().setData(source)))
            compose.onNodeWithContentDescription("我的",useUnmergedTree=true).performClick()
            compose.onNodeWithText("主题外观").performScrollTo().performClick()
            compose.onNodeWithText("从相册选择背景").performScrollTo().performClick()
            compose.waitUntil(10000){AppearanceStore.current.image.isNotBlank() && AppearanceStore.current.image!=original.image}
            privatePath=AppearanceStore.current.image
            assertEquals(context.filesDir.canonicalFile,File(privatePath).canonicalFile.parentFile)
            assertTrue(File(privatePath).length()>0)
            compose.onNodeWithTag("glass-card-opacity").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(.7f)}
            compose.onNodeWithTag("glass-navigation-opacity").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(.8f)}
            compose.onNodeWithTag("glass-blur").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(22f)}
            compose.onNodeWithTag("color-主题强调色").performScrollTo().performClick()
            compose.onNodeWithText("自定义色值 #RRGGBB").performTextReplacement("9B88BA")
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("日记正文").performScrollTo().performClick()
            compose.onNodeWithText("自定义色值 #RRGGBB").performTextReplacement("384053")
            compose.onNodeWithText("完成").performClick()
            compose.activityRule.scenario.recreate()
            assertEquals(privatePath,AppearanceStore.current.image)
            assertEquals(0xFF9B88BAL,AppearanceStore.current.accent)
            assertEquals(0xFF384053L,AppearanceStore.current.body)
            assertEquals(.3f,AppearanceStore.current.cardOpacity,.01f)
            assertEquals(.2f,AppearanceStore.current.navigationOpacity,.01f)
            assertEquals(22f,AppearanceStore.current.glassBlur,.01f)
            assertNotEquals(AppearanceStore.current.title,AppearanceStore.current.body)
            compose.onNodeWithText("主题与背景").assertExists()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithContentDescription("回忆",useUnmergedTree=true).performClick()
            compose.onRoot().captureToImage().asAndroidBitmap().let{capture->File(context.getExternalFilesDir(null),"glass-120-calendar.png").outputStream().use{capture.compress(Bitmap.CompressFormat.PNG,100,it)}}
        }finally{
            Intents.release();resolver.delete(source,null,null)
            val testImage=AppearanceStore.current.image
            AppearanceStore.update(context,original)
            if(originalBytes!=null)originalImage!!.writeBytes(originalBytes)
            if(testImage.isNotBlank()&&testImage!=original.image)File(testImage).takeIf{it.parentFile==context.filesDir&&it.name.startsWith("background-")}?.delete()
        }
    }
}
