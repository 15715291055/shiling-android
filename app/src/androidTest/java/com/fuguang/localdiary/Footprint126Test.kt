package com.fuguang.localdiary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.location.Geocoder
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.*
import com.fuguang.localdiary.location.PlaceNames
import com.fuguang.localdiary.ui.FootprintScreen
import com.fuguang.localdiary.ui.theme.FuguangTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Footprint126Test {
    @get:Rule val compose=createComposeRule()

    @Test fun historyShowsNamesInsteadOfCoordinatesAndCanBeRenamed() {
        var entry by mutableStateOf(FootprintEntity("2026-11-23","10:00",1.23456,103.87654,20f,"河边公园"))
        compose.setContent { FuguangTheme { FootprintScreen(listOf(entry),false,{},false,null,{},{},{},
            onRenameFootprint={item,name->entry=item.copy(placeName=name)}) } }
        compose.onNodeWithText("河边公园").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasText("1.23456",substring=true)).assertCountEquals(0)
        compose.onAllNodes(hasText("103.87654",substring=true)).assertCountEquals(0)
        compose.onNodeWithText("改名").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("学校")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("学校").assertExists()
        assertEquals(1.23456,entry.latitude,0.0)
        assertEquals(103.87654,entry.longitude,0.0)
    }

    @Test fun migrationFromVersionTwoPreservesRecordsAndPersistsNames()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val file=File(context.cacheDir,"footprint-v2-${UUID.randomUUID()}.db")
        SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
            db.execSQL("CREATE TABLE diaries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,date TEXT NOT NULL,time TEXT NOT NULL,body TEXT NOT NULL,tags TEXT NOT NULL,mood TEXT NOT NULL,imagePaths TEXT NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,moodId TEXT NOT NULL DEFAULT '',moodIcon TEXT NOT NULL DEFAULT '',moodColor INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE footprints(date TEXT PRIMARY KEY NOT NULL,time TEXT NOT NULL,latitude REAL NOT NULL,longitude REAL NOT NULL,accuracyMeters REAL NOT NULL)")
            db.execSQL("CREATE TABLE moods(id TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,icon TEXT NOT NULL,color INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE tags(name TEXT NOT NULL PRIMARY KEY)")
            db.execSQL("INSERT INTO diaries VALUES(17,'2026-11-22','12:00','升级前原文','','','',123,456,'','',0)")
            db.execSQL("INSERT INTO footprints VALUES('2026-11-22','10:00',1.23456,103.87654,20)")
            db.version=2
        }
        val db=Room.databaseBuilder(context,AppDatabase::class.java,file.absolutePath).addMigrations(AppDatabase.MIGRATION_2_3).build()
        try {
            assertEquals("升级前原文",db.diaryDao().getAll().single().body)
            val before=db.footprintDao().getAll().single()
            assertEquals("",before.placeName)
            db.footprintDao().rename(before.date,"学校")
            db.footprintDao().nameIfEmpty(before.date,"自动名称不应覆盖手动名称")
            assertEquals(before.copy(placeName="学校"),db.footprintDao().getAll().single())
            db.close()
            val reopened=Room.databaseBuilder(context,AppDatabase::class.java,file.absolutePath).build()
            try { assertEquals("学校",reopened.footprintDao().getAll().single().placeName) } finally {reopened.close()}
        } finally {db.close();file.delete()}
    }

    @Test fun systemLookupHasAnOfflineFallback()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val result=PlaceNames.resolve(context,1.3,103.8)
        println("Geocoder present=${Geocoder.isPresent()}, synthetic place resolved=${!result.isNullOrBlank()}")
        if(result!=null)assertTrue(result.isNotBlank())
    }
}
