package com.fuguang.localdiary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Editorial121Test {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun image(name:String,width:Int,height:Int,color:Int):File {
        val file=File(context.cacheDir,"$name-${UUID.randomUUID()}.jpg")
        Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply{eraseColor(color);file.outputStream().use{compress(Bitmap.CompressFormat.JPEG,94,it)};recycle()}
        return file
    }
    @Test fun fourDesignsKeepEveryCharacterAndEveryPhotoAcrossColumnsAndPages() {
        val photos=listOf(image("wide",800,450,0xff9abdb4.toInt()),image("tall",400,750,0xffd7b697.toInt()))
        val bytes=photos.map{it.readBytes()}
        val body=(1..135).joinToString("\n"){"第 $it 行：散步🍃与日常，English / 中文。"}+"\n最后一句完整保留。"
        val entries=listOf(DiaryEntity(id=1,date="2026-11-23",time="09:00",body=body,imagePaths=photos.joinToString("\n"){it.absolutePath}),DiaryEntity(id=2,date="2026-11-23",time="19:00",body="第二篇正文\n".repeat(75),imagePaths=photos.reversed().joinToString("\n"){it.absolutePath}))
        try{ShareTemplate.entries.filter{it.editorial}.forEach{style->
            val directory=File(context.cacheDir,"audit-${UUID.randomUUID()}").apply{mkdirs()}
            val fragments=mutableListOf<EditorialShareRenderer.Fragment>();val renderedPhotos=mutableListOf<String>()
            try{
                val files=EditorialShareRenderer.render(context,entries[0].date,entries,style,36f,1.6f,directory,{fragments+=it},{renderedPhotos+=it})
                assertTrue(files.size>3)
                entries.forEach{entry->
                    val parts=fragments.filter{it.id==entry.id};assertEquals(0,parts.first().start)
                    parts.zipWithNext().forEach{(a,b)->assertEquals(a.end,b.start)}
                    assertEquals(entry.body,parts.joinToString(""){entry.body.substring(it.start,it.end)})
                }
                assertEquals(entries.flatMap{it.images()},renderedPhotos)
                files.forEach{file->val o=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.absolutePath,o);assertEquals(1080,o.outWidth);assertTrue(o.outHeight in 1440..2800)}
            }finally{directory.deleteRecursively()}
        };photos.forEachIndexed{index,file->assertArrayEquals(bytes[index],file.readBytes())}}finally{photos.forEach{it.delete()}}
    }

    @Test fun zeroOneAndTwelvePhotosRenderWithoutLosingAnyAttachment() {
        val photo=image("many",600,420,0xffa8bfa5.toInt())
        try{for(count in listOf(0,1,12))for(style in ShareTemplate.entries.filter{it.editorial}){
            val dir=File(context.cacheDir,"photo-count-${UUID.randomUUID()}").apply{mkdirs()}
            val item=DiaryEntity(id=1,date="2026-11-23",time="12:00",body="有图或无图，都完整记录。",imagePaths=List(count){photo.absolutePath}.joinToString("\n"))
            val seen=mutableListOf<String>()
            try{val files=EditorialShareRenderer.render(context,item.date,listOf(item),style,36f,1.6f,dir,photoAudit={seen+=it});assertTrue(files.isNotEmpty());assertEquals(count,seen.size)}finally{dir.deleteRecursively()}
        }}finally{photo.delete()}
    }
}
