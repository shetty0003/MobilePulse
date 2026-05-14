package com.mobilepulse.app.data.model

data class AppProcess(
    val packageName: String,
    val appName: String,
    val cpuPercent: Int,
    val ramMb: Long,
    val risk: RiskLevel,
    val isSystem: Boolean = false
)

enum class RiskLevel { LOW, MEDIUM, HIGH }
