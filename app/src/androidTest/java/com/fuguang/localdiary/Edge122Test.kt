package com.fuguang.localdiary

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Edge122Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun wallpaperReachesStatusBarAndIconsAdaptWithoutCoveringHeader() {
        val context=compose.activity.application
        val original=AppearanceStore.current
        val files=mutableListOf<File>()
        try{
            for((name,color,lightIcons) in listOf(Triple("dark",Color.rgb(30,90,145),false),Triple("light",Color.rgb(238,194,175),true))){
                // Deliberately use a wide image on a tall display: Crop must still cover the bar.
                val file=File(context.filesDir,"edge-test-${UUID.randomUUID()}.png");files+=file
                Bitmap.createBitmap(900,360,Bitmap.Config.ARGB_8888).apply{eraseColor(color);file.outputStream().use{compress(Bitmap.CompressFormat.PNG,100,it)};recycle()}
                compose.runOnUiThread {AppearanceStore.update(context,original.copy(image=file.absolutePath,opacity=1f,blur=0f))}
                compose.waitUntil(10000){WindowCompat.getInsetsController(compose.activity.window,compose.activity.window.decorView).isAppearanceLightStatusBars==lightIcons}
                compose.waitForIdle()
                val position=IntArray(2)
                compose.runOnUiThread {compose.activity.findViewById<View>(android.R.id.content).getLocationOnScreen(position)}
                assertEquals("The app content must extend behind the status bar",0,position[1])
                assertEquals(Color.TRANSPARENT,compose.activity.window.statusBarColor)
                val inset=ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.statusBars()).top
                assertTrue(inset>0)
                val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                val pixel=screenshot.getPixel(screenshot.width/2,(inset*.15f).toInt().coerceAtLeast(1))
                if(name=="dark")assertTrue("Opaque cream status strip remains",Color.blue(pixel)>Color.red(pixel)+45)
                else assertTrue("Light wallpaper was not drawn behind icons",Color.red(pixel)>Color.blue(pixel)+25)
                File(context.getExternalFilesDir(null),"edge-122-$name.png").outputStream().use{screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
                screenshot.recycle()
                val title=compose.onNodeWithText("拾零").getUnclippedBoundsInRoot()
                assertTrue("Header overlaps system icons",title.top.value*context.resources.displayMetrics.density>=inset)
            }
        }finally{compose.runOnUiThread{AppearanceStore.update(context,original)};files.forEach{it.delete()}}
    }
}
