package com.mobilepulse.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boot_restrictions")
data class BootRestrictionEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val restrictBackground: Boolean = false
)
