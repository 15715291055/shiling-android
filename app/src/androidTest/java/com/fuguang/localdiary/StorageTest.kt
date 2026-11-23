package com.fuguang.localdiary

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.backup.BackupManager
import com.fuguang.localdiary.data.*
import com.fuguang.localdiary.location.LocationHelper
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val vm = DiaryViewModel(app)

    @Before fun setUp() { runBlocking { vm.clearAll() } }
    @After fun tearDown() { runBlocking { vm.clearAll() } }

    @Test fun databaseSurvivesCloseAndReopen() = runBlocking {
        val name = "persistence-test.db"
        app.deleteDatabase(name)
        val db = Room.databaseBuilder(app, AppDatabase::class.java, name).build()
        val id = db.diaryDao().upsert(DiaryEntity(date = "2026-11-22", time = "12:00", body = "持久化"))
        db.footprintDao().upsert(FootprintEntity("2026-11-22", "12:01", 1.3, 103.8, 20f))
        db.close()
        val reopened = Room.databaseBuilder(app, AppDatabase::class.java, name).build()
        assertEquals("持久化", reopened.diaryDao().getById(id)?.body)
        assertEquals(1, reopened.footprintDao().getAll().size)
        reopened.close()
        app.deleteDatabase(name)
        Unit
    }

    @Test fun zipRoundTripPreservesImagesAndMergeKeepsLocalRecords() = runBlocking {
        val image = File(app.filesDir, "diary-images/shared.png").apply { parentFile!!.mkdirs() }
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val originalBytes = image.readBytes()
        vm.save(DiaryEntity(id = 101, date = "2026-11-22", time = "12:00", body = "备份原文", imagePaths = image.absolutePath))
        vm.save(DiaryEntity(id = 102, date = "2026-11-21", time = "12:00", body = "共享图片", imagePaths = image.absolutePath))
        vm.addFootprint(FootprintEntity("2026-11-22", "12:00", 1.3, 103.8, 20f))
        val archive = File(app.cacheDir, "roundtrip.zip")
        assertEquals(2, BackupManager.export(app, Uri.fromFile(archive), vm))
        vm.clearAll()
        // A local file with the same name must never be overwritten by restore.
        image.parentFile!!.mkdirs()
        image.writeText("existing-local-file")
        vm.save(DiaryEntity(id = 101, date = "2026-11-22", time = "13:00", body = "本机新内容", imagePaths = image.absolutePath))
        assertEquals(1 to 1, BackupManager.import(app, Uri.fromFile(archive), vm))
        assertEquals("本机新内容", vm.getDiary(101)?.body)
        assertEquals("existing-local-file", image.readText())
        val restored = File(vm.getDiary(102)!!.images().single())
        assertNotEquals(image.absolutePath, restored.absolutePath)
        assertArrayEquals(originalBytes, restored.readBytes())
        val filesBefore = image.parentFile!!.list()!!.toSet()
        assertEquals(0 to 0, BackupManager.import(app, Uri.fromFile(archive), vm))
        assertEquals(filesBefore, image.parentFile!!.list()!!.toSet())
        assertEquals(2, vm.allDiaries().size)
        archive.delete()
        Unit
    }

    @Test fun malformedBackupCannotPartiallyImport() = runBlocking {
        val valid = JSONObject().put("id", 20).put("date", "2026-11-22").put("time", "12:00")
            .put("body", "should roll back").put("images", JSONArray()).put("createdAt", 1).put("updatedAt", 1)
        val invalid = JSONObject(valid.toString()).put("id", 21).put("date", "invalid-date")
        val json = JSONObject().put("format", "fuguang-backup").put("version", 1)
            .put("diaries", JSONArray().put(valid).put(invalid)).put("footprints", JSONArray())
        val archive = File(app.cacheDir, "invalid.zip")
        ZipOutputStream(archive.outputStream()).use {
            it.putNextEntry(ZipEntry("backup.json")); it.write(json.toString().toByteArray()); it.closeEntry()
        }
        assertTrue(runCatching { BackupManager.import(app, Uri.fromFile(archive), vm) }.isFailure)
        assertTrue(vm.allDiaries().isEmpty())
        archive.delete()
        Unit
    }

    @Test fun traversalArchiveIsRejectedWithoutWritingImages() = runBlocking {
        val archive = File(app.cacheDir, "traversal.zip")
        ZipOutputStream(archive.outputStream()).use {
            it.putNextEntry(ZipEntry("images/../escape.png")); it.write(byteArrayOf(1)); it.closeEntry()
        }
        assertTrue(runCatching { BackupManager.import(app, Uri.fromFile(archive), vm) }.isFailure)
        assertTrue(vm.allDiaries().isEmpty())
        assertFalse(File(app.filesDir, "escape.png").exists())
        archive.delete()
        Unit
    }

    @Test fun deletingOneDiaryKeepsSharedImageUntilLastReferenceIsGone() = runBlocking {
        val image = File(app.filesDir, "diary-images/shared-delete.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        val first = DiaryEntity(id = 1, date = "2026-11-22", time = "12:00", body = "first", imagePaths = image.absolutePath)
        val second = first.copy(id = 2, body = "second")
        vm.save(first)
        vm.save(second)
        vm.delete(first)
        assertTrue(image.exists())
        vm.delete(second)
        assertFalse(image.exists())
    }

    @Test fun locationWithoutPermissionReportsError() = runBlocking {
        val deniedContext = object : android.content.ContextWrapper(app) {
            override fun checkPermission(permission: String, pid: Int, uid: Int) = android.content.pm.PackageManager.PERMISSION_DENIED
        }
        val result = runCatching { LocationHelper.currentLocation(deniedContext) }
        assertTrue(result.exceptionOrNull() is SecurityException)
    }
}
