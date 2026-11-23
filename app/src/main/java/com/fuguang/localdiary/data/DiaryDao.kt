package com.fuguang.localdiary.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diaries ORDER BY date DESC, time DESC, id DESC")
    fun observeAll(): Flow<List<DiaryEntity>>

    @Query("SELECT * FROM diaries ORDER BY date DESC, time DESC, id DESC")
    suspend fun getAll(): List<DiaryEntity>

    @Query("SELECT * FROM diaries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DiaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DiaryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImported(entry: DiaryEntity): Long

    @Delete
    suspend fun delete(entry: DiaryEntity)

    @Query("DELETE FROM diaries")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM diaries")
    suspend fun count(): Int
}
