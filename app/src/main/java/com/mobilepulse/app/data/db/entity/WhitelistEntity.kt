package com.mobilepulse.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whitelist")
data class WhitelistEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val addedAt: Long = System.currentTimeMillis()
)
