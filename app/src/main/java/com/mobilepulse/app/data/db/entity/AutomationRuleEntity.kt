package com.mobilepulse.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val metric: String,       // CPU, RAM, BATTERY, TEMP
    val operator: String,     // GT, LT, GTE, LTE
    val threshold: Float,
    val action: String,       // NOTIFY, STOP, CLEAR_CACHE, REDUCE_PRIORITY
    val responseType: String, // NOTIFY_ONLY, SEMI_AUTO, FULL_AUTO
    val notifyOnTrigger: Boolean = true,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
