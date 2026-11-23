package com.fuguang.localdiary.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "moods")
data class MoodEntity(@PrimaryKey val id: String, val name: String, val icon: String, val color: Long)

@Entity(tableName = "tags")
data class TagEntity(@PrimaryKey val name: String)

@Dao
interface MoodDao {
    @Query("SELECT * FROM moods ORDER BY rowid") fun observe(): Flow<List<MoodEntity>>
    @Query("SELECT * FROM moods") suspend fun all(): List<MoodEntity>
    @Upsert suspend fun save(mood: MoodEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun importMood(mood: MoodEntity)
    @Query("DELETE FROM moods WHERE id = :id") suspend fun delete(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addTag(tag: TagEntity)
    @Query("SELECT * FROM tags ORDER BY name") fun tags(): Flow<List<TagEntity>>
    @Query("SELECT * FROM tags") suspend fun allTags(): List<TagEntity>
}
