package com.fuguang.localdiary

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.ui.*
import com.fuguang.localdiary.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class Brightness121Test {
    @get:Rule val compose=createComposeRule()
    @Test fun longTranslucentPaperDoesNotChangeBrightnessWhileScrolling() {
        val context=ApplicationProvider.getApplicationContext<Context>();val original=AppearanceStore.current
        val entry=DiaryEntity(id=91,date="2026-11-23",time="00:45",body="长篇日记的纸页明度应该保持稳定。\n".repeat(150))
        var density=1f
        compose.runOnUiThread{AppearanceStore.update(context,AppearanceSettings())}
        try{
            compose.setContent{density=LocalDensity.current.density;FuguangTheme{Box(Modifier.size(340.dp,560.dp)){DateBoundary(entry.date,listOf(entry),onDate={}){JournalScreen(listOf(entry),"",{},false,{},entry.date,{}, {},appearance=JournalAppearance.PAPER)}}}}
            fun sample(name:String):Int{
                val bounds=compose.onNodeWithTag("journal-paper-91").getUnclippedBoundsInRoot()
                val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
                val x=((bounds.right.value-6)*density).toInt().coerceIn(0,bitmap.width-1)
                val y=(350*density).toInt().coerceIn(0,bitmap.height-1)
                File(context.getExternalFilesDir(null),name).outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                return bitmap.getPixel(x,y)
            }
            val before=sample("paper-brightness-before.png")
            compose.onNodeWithTag("journal-scroll").performTouchInput{down(Offset(width*.8f,height*.65f));advanceEventTime(160)}
            val held=sample("paper-brightness-held.png")
            listOf(16,8,0).forEach{shift->assertTrue("Touch must not dim reading paper",abs(((before shr shift)and 255)-((held shr shift)and 255))<=2)}
            compose.onNodeWithTag("journal-scroll").performTouchInput{moveTo(Offset(width*.8f,height*.15f),650);up()}
            val after=sample("paper-brightness-after.png")
            listOf(16,8,0).forEach{shift->assertTrue("Paper face changed: $before -> $after",abs(((before shr shift)and 255)-((after shr shift)and 255))<=2)}
        }finally{compose.runOnUiThread{AppearanceStore.update(context,original)}}
    }
}
