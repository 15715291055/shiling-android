package com.fuguang.localdiary.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "diaries")
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String,
    val body: String,
    val tags: String = "",
    val mood: String = "",
    val imagePaths: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val moodId: String = "",
    @ColumnInfo(defaultValue = "''") val moodIcon: String = "",
    @ColumnInfo(defaultValue = "0") val moodColor: Long = 0
) {
    fun images(): List<String> = imagePaths.split("\n").filter { it.isNotBlank() }
    fun tagList(): List<String> = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
