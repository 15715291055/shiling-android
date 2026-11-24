package com.fuguang.localdiary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.*
import com.fuguang.localdiary.ui.*
import com.fuguang.localdiary.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import com.fuguang.localdiary.backup.BackupManager

@RunWith(AndroidJUnit4::class)
class Upgrade120Test {
    private val context:Context get()=ApplicationProvider.getApplicationContext()

    @Test fun versionOneMigrationKeepsTextPhotosAndFootprintsAndSnapshotsMood() = runBlocking {
        val file=File(context.cacheDir,"migration-${UUID.randomUUID()}.db")
        val image=File(context.cacheDir,"migration-photo-${UUID.randomUUID()}.jpg").apply {writeBytes(byteArrayOf(1,2,3,4))}
        val body="原来的日记必须完整保留\n第二行"
        SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
            db.execSQL("CREATE TABLE diaries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,date TEXT NOT NULL,time TEXT NOT NULL,body TEXT NOT NULL,tags TEXT NOT NULL,mood TEXT NOT NULL,imagePaths TEXT NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE footprints(date TEXT PRIMARY KEY NOT NULL,time TEXT NOT NULL,latitude REAL NOT NULL,longitude REAL NOT NULL,accuracyMeters REAL NOT NULL)")
            db.execSQL("INSERT INTO diaries VALUES(17,'2026-11-22','17:38',?,'旧标签,实验','开心',?,123,456)",arrayOf(body,image.absolutePath))
            db.execSQL("INSERT INTO footprints VALUES('2026-11-22','12:00',1.3,103.8,20)")
            db.version=1
        }
        val db=Room.databaseBuilder(context,AppDatabase::class.java,file.absolutePath).addMigrations(AppDatabase.MIGRATION_1_2,AppDatabase.MIGRATION_2_3).build()
        try {
            val entry=db.diaryDao().getAll().single()
            assertEquals(17L,entry.id);assertEquals(body,entry.body);assertEquals(image.absolutePath,entry.images().single())
            assertArrayEquals(byteArrayOf(1,2,3,4),image.readBytes())
            assertEquals(1,db.footprintDao().getAll().size)
            assertEquals("☀️",entry.moodIcon);assertEquals("default-1",entry.moodId)
            assertTrue(db.moodDao().allTags().map{it.name}.containsAll(listOf("旧标签","实验")))
            db.moodDao().delete(entry.moodId)
            assertEquals("开心",db.diaryDao().getById(17)!!.mood)
            assertEquals("☀️",db.diaryDao().getById(17)!!.moodIcon)
        } finally {db.close();file.delete();image.delete()}
    }

    @Test fun veryLongSharingSplitsIntoBoundedReadableImagesForEveryTemplate() {
        val text=(1..260).joinToString("\n"){"第 $it 行，完整保留文字与时间。"}+"\n最后一句：所有正文已完整排版。"
        for(template in ShareTemplate.entries) {
            val files=ShareImages.generate(context,"2026-11-23",listOf(DiaryEntity(date="2026-11-23",time="08:45",body=text,mood="平静",moodIcon="🍃")),template,36f,1.6f)
            try {
                assertTrue(files.size>=6)
                files.forEach{file->val options=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.absolutePath,options);assertEquals(ShareImages.WIDTH,options.outWidth);assertTrue(options.outHeight in 1000..ShareImages.HEIGHT);assertTrue(file.length()>10000)}
                files.last().copyTo(File(context.getExternalFilesDir(null),"share-120-${template.name}-last.jpg"),overwrite=true)
            }finally{files.first().parentFile!!.deleteRecursively()}
        }
    }

    @Test fun appearancePersistsAndVeryLowContrastIsCorrected() {
        val original=AppearanceStore.current
        try{
            val s=AppearanceSettings(accent=0xFF9B88BA,body=0xFFF7F5EF,date=0xFF6478A0,blur=12f,opacity=.6f)
            AppearanceStore.update(context,s);AppearanceStore.load(context)
            assertEquals(s,AppearanceStore.current)
            assertNotEquals(androidx.compose.ui.graphics.Color(s.body),BodyInk)
            assertNotEquals(Sage,SageLight)
        }finally{AppearanceStore.update(context,original)}
    }

    @Test fun moodSnapshotAndTagSurviveDeleteBackupAndRestore()=runBlocking {
        val vm=DiaryViewModel(context.applicationContext as Application)
        val mood=MoodEntity("test-${UUID.randomUUID()}","小确幸","✨",0xFF9B88BA)
        val archive=File(context.cacheDir,"mood-${UUID.randomUUID()}.zip")
        vm.clearAll()
        try{
            vm.saveMood(mood);vm.moodAsTag(mood)
            val id=vm.save(DiaryEntity(date="2026-11-23",time="10:00",body="保留历史心情",mood=mood.name,moodId=mood.id,moodIcon=mood.icon,moodColor=mood.color,tags=mood.name))
            BackupManager.export(context,Uri.fromFile(archive),vm)
            vm.deleteMood(mood.id)
            assertEquals("✨",vm.getDiary(id)!!.moodIcon)
            vm.clearAll()
            BackupManager.import(context,Uri.fromFile(archive),vm)
            assertEquals(mood,vm.moodSnapshot().first.single{it.id==mood.id})
            assertTrue(vm.moodSnapshot().second.any{it.name==mood.name})
            assertEquals(mood.color,vm.allDiaries().single().moodColor)
        }finally{vm.clearAll();vm.deleteMood(mood.id);archive.delete()}
    }

    @Test fun generatedPhotoShareCanBeReadThroughProviderAndSavedToGallery() {
        val image=File(context.cacheDir,"share-photo-${UUID.randomUUID()}.jpg")
        Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.CYAN);image.outputStream().use{compress(Bitmap.CompressFormat.JPEG,95,it)};recycle()}
        val files=ShareImages.generate(context,"2026-11-23",listOf(DiaryEntity(date="2026-11-23",time="10:00",body="图文分享测试",imagePaths="${image.absolutePath}\n${image.absolutePath}")),ShareTemplate.FILM,36f,1.6f)
        val saved=mutableListOf<Uri>()
        try{
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",files.single())
            assertArrayEquals(files.single().readBytes(),context.contentResolver.openInputStream(uri)!!.use{it.readBytes()})
            saved+=ShareImages.save(context,files)
            assertEquals(1,saved.size)
            assertArrayEquals(files.single().readBytes(),context.contentResolver.openInputStream(saved.single())!!.use{it.readBytes()})
            files.single().copyTo(File(context.getExternalFilesDir(null),"share-120-film-photo.jpg"),overwrite=true)
        }finally{saved.forEach{context.contentResolver.delete(it,null,null)};files.first().parentFile!!.deleteRecursively();image.delete()}
    }

    @Test fun allStylesRenderDistinctPhotoAndTextCardsForVisualReview() {
        val image=File(context.cacheDir,"style-photo-${UUID.randomUUID()}.jpg")
        Bitmap.createBitmap(900,560,Bitmap.Config.ARGB_8888).apply{
            val c=android.graphics.Canvas(this);val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            p.shader=android.graphics.LinearGradient(0f,0f,900f,560f,intArrayOf(0xffa4c7c5.toInt(),0xffe8d6b2.toInt()),null,android.graphics.Shader.TileMode.CLAMP);c.drawRect(0f,0f,900f,560f,p)
            p.shader=null;p.color=0xfffcf0cb.toInt();c.drawCircle(660f,130f,55f,p)
            p.color=0xff748b70.toInt();val path=android.graphics.Path().apply{moveTo(0f,470f);lineTo(230f,220f);lineTo(500f,560f);close()};c.drawPath(path,p)
            p.color=0xff4a6b61.toInt();c.drawPath(android.graphics.Path().apply{moveTo(330f,560f);lineTo(660f,270f);lineTo(900f,500f);lineTo(900f,560f);close()},p)
            image.outputStream().use{compress(Bitmap.CompressFormat.JPEG,95,it)};recycle()
        }
        val sample=DiaryEntity(date="2026-11-23",time="17:38",body="午后沿着河边慢慢走，风把树影吹到了脚边。\n\n回家后翻了几页书，做了一顿简单的晚饭。普通的一天，也有值得拾起的小事。",mood="平静",moodIcon="🍃",imagePaths="${image.absolutePath}\n${image.absolutePath}",tags="生活,散步")
        val hashes=mutableSetOf<Int>()
        try{ShareTemplate.entries.forEach{template->
            val files=ShareImages.generate(context,sample.date,listOf(sample),template,36f,1.6f)
            try{
                hashes+=files.first().readBytes().contentHashCode()
                files.first().copyTo(File(context.getExternalFilesDir(null),"share-120-sample-${template.name}.jpg"),overwrite=true)
            }finally{files.first().parentFile!!.deleteRecursively()}
        };assertEquals(ShareTemplate.entries.size,hashes.size)}finally{image.delete()}
    }
}
