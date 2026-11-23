package com.fuguang.localdiary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DiaryEntity::class, FootprintEntity::class, MoodEntity::class, TagEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun footprintDao(): FootprintDao
    abstract fun moodDao(): MoodDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        private fun seed(db: SupportSQLiteDatabase) {
            listOf("平静" to "🍃", "开心" to "☀️", "感动" to "💧", "疲惫" to "🌙", "难过" to "🌧️", "期待" to "🌱", "焦虑" to "🌀").forEachIndexed { i, pair ->
                db.execSQL("INSERT OR IGNORE INTO moods(id,name,icon,color) VALUES(?,?,?,?)", arrayOf("default-$i", pair.first, pair.second, 0xFF71815AL))
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diaries ADD COLUMN moodId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE diaries ADD COLUMN moodIcon TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE diaries ADD COLUMN moodColor INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS moods (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, icon TEXT NOT NULL, color INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS tags (name TEXT NOT NULL PRIMARY KEY)")
                seed(db)
                db.execSQL("UPDATE diaries SET moodIcon = COALESCE((SELECT icon FROM moods WHERE name = diaries.mood), ''), moodId = COALESCE((SELECT id FROM moods WHERE name = diaries.mood), ''), moodColor = COALESCE((SELECT color FROM moods WHERE name = diaries.mood), 0)")
                db.query("SELECT tags FROM diaries").use { cursor ->
                    while(cursor.moveToNext()) cursor.getString(0).split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { db.execSQL("INSERT OR IGNORE INTO tags(name) VALUES(?)", arrayOf(it)) }
                }
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "fuguang-local.db"
            ).addMigrations(MIGRATION_1_2).addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) { seed(db) }
            }).build().also { INSTANCE = it }
        }
    }
}
