package com.fuguang.localdiary

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fuguang.localdiary.backup.BackupManager
import com.fuguang.localdiary.data.*
import com.fuguang.localdiary.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class AppearanceBackup125Test {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)

    @Test fun roundTripRestoresBackgroundColorsStyleAndSharingPreferences()=runBlocking {
        val vm=DiaryViewModel(app)
        val original=AppearanceStore.current;val style=JournalAppearance.read(app);val share=ShareSettingsStore.read(app)
        val background=File(app.filesDir,"audit-background-125.png")
        val archive=File(app.cacheDir,"appearance-125.zip")
        var restored=""
        Bitmap.createBitmap(16,24,Bitmap.Config.ARGB_8888).apply {eraseColor(0xffaabbcc.toInt());background.outputStream().use {compress(Bitmap.CompressFormat.PNG,100,it)};recycle()}
        val bytes=background.readBytes()
        try {
            vm.clearAll()
            vm.addFootprint(FootprintEntity("2026-11-23","12:00",1.3,103.8,20f,"河边公园"))
            main {AppearanceStore.update(app,AppearanceSettings(image=background.absolutePath,accent=0xFF9B88BA,readingProtection=.7f))}
            JournalAppearance.PAPER.save(app)
            ShareSettingsStore.save(app,ShareSettings("FROST",44f,1.8f))
            BackupManager.export(app,Uri.fromFile(archive),vm)
            ZipFile(archive).use {z ->
                assertArrayEquals(bytes,z.getInputStream(z.getEntry("appearance/background")).use {it.readBytes()})
                val manifest=JSONObject(z.getInputStream(z.getEntry("backup.json")).reader().readText())
                assertEquals(2,manifest.getInt("version"))
                assertEquals("",manifest.getJSONObject("appearance").getJSONObject("settings").getString("image"))
            }
            vm.deleteFootprints()
            main {AppearanceStore.update(app,AppearanceSettings())}
            JournalAppearance.TIMELINE.save(app);ShareSettingsStore.save(app,ShareSettings())
            BackupManager.import(app,Uri.fromFile(archive),vm)
            assertEquals("河边公园",vm.allFootprints().single().placeName)
            assertEquals(1.3,vm.allFootprints().single().latitude,0.0)
            restored=AppearanceStore.current.image
            assertArrayEquals(bytes,File(restored).readBytes())
            assertEquals(0xFF9B88BAL,AppearanceStore.current.accent)
            assertEquals(.7f,AppearanceStore.current.readingProtection,.001f)
            assertEquals(JournalAppearance.PAPER,JournalAppearance.read(app))
            assertEquals(ShareSettings("FROST",44f,1.8f),ShareSettingsStore.read(app))
        } finally {
            main {AppearanceStore.update(app,original)}
            style.save(app);ShareSettingsStore.save(app,share)
            background.delete();archive.delete()
            if(restored.isNotBlank())File(restored).delete()
            vm.clearAll()
        }
    }

    @Test fun missingBackgroundRejectsBeforeChangingDiaryData()=runBlocking {
        val vm=DiaryViewModel(app);vm.clearAll()
        val item=JSONObject().put("id",901).put("date","2026-11-23").put("time","12:00").put("body","不能部分导入")
            .put("images",JSONArray()).put("createdAt",1).put("updatedAt",1)
        val appearance=JSONObject().put("settings",JSONObject()).put("journalStyle","PAPER").put("hasBackground",true)
        val json=JSONObject().put("format","fuguang-backup").put("version",2).put("diaries",JSONArray().put(item)).put("footprints",JSONArray()).put("appearance",appearance)
        val archive=File(app.cacheDir,"missing-background-125.zip")
        ZipOutputStream(archive.outputStream()).use {it.putNextEntry(ZipEntry("backup.json"));it.write(json.toString().toByteArray());it.closeEntry()}
        try {
            var failed=false
            try {BackupManager.import(app,Uri.fromFile(archive),vm)}catch(_:Exception){failed=true}
            assertTrue(failed);assertTrue(vm.allDiaries().isEmpty())
        } finally {archive.delete();vm.clearAll()}
    }
}
