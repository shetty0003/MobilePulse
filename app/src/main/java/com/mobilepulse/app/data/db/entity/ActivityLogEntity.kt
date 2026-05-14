package com.mobilepulse.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "activity_log")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String, // INFO, ACTION, AUTOMATION, SUCCESS, WARNING, ERROR
    val message: String,
    val affectedApp: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)