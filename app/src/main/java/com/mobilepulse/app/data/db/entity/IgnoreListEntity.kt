package com.mobilepulse.app.data.db.entity


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ignore_list")
data class IgnoreListEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val addedAt: Long = System.currentTimeMillis()
)
