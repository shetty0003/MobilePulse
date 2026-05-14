package com.mobilepulse.app.data.model

data class DashboardMetrics(
    val cpuTotal: Int = 0,
    val cpuCores: List<Int> = emptyList(),
    val ramTotalMb: Long = 0L,
    val ramUsedMb: Long = 0L,
    val ramFreeMb: Long = 0L,
    val batteryLevel: Int = 0,
    val batteryCharging: Boolean = false,
    val batteryTemp: Float = 0f,
    val batteryDrainRate: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
