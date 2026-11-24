package com.fuguang.localdiary.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FootprintDao {
    @Query("SELECT * FROM footprints ORDER BY date DESC")
    fun observeAll(): Flow<List<FootprintEntity>>

    @Query("SELECT * FROM footprints ORDER BY date DESC")
    suspend fun getAll(): List<FootprintEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FootprintEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImported(item: FootprintEntity): Long

    @Query("DELETE FROM footprints")
    suspend fun clearAll()

    @Query("DELETE FROM footprints WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("UPDATE footprints SET placeName = :name WHERE date = :date")
    suspend fun rename(date:String,name:String)

    @Query("UPDATE footprints SET placeName = :name WHERE date = :date AND placeName = ''")
    suspend fun nameIfEmpty(date:String,name:String)
}
