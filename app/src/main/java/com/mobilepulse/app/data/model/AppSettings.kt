package com.mobilepulse.app.data.model

data class AppSettings(
    val refreshIntervalSeconds: Int = 2,
    val enforcementTier: EnforcementTier = EnforcementTier.STANDARD,
    val notificationsEnabled: Boolean = true,
    val cpuThreshold: Int = 70,
    val ramThreshold: Int = 80,
    val batteryThreshold: Int = 20,
    val automationEnabled: Boolean = true,
    val claudeApiKey: String = "",
    val deepseekApiKey: String = "",
    val aiProvider: AiProvider = AiProvider.CLAUDE,
    val scheduledCleanEnabled: Boolean = false
)

enum class EnforcementTier { STANDARD, SHIZUKU, ROOT }

enum class AiProvider { CLAUDE, DEEPSEEK }
