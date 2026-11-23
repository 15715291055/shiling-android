package com.fuguang.localdiary.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "footprints")
data class FootprintEntity(
    @PrimaryKey val date: String,
    val time: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float
)
