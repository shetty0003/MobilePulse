package com.mobilepulse.app.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mobilepulse.app.data.db.entity.AutomationRuleEntity
import com.mobilepulse.app.data.model.DashboardMetrics
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.data.repository.ExemptionRepository
import com.mobilepulse.app.data.repository.LogRepository
import com.mobilepulse.app.data.repository.RuleRepository
import com.mobilepulse.app.data.repository.SettingsRepository
import com.mobilepulse.app.enforcement.EnforcementAction
import com.mobilepulse.app.enforcement.EnforcementManager
import com.mobilepulse.app.enforcement.EnforcementResult
import com.mobilepulse.app.monitoring.AppTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ruleRepository: RuleRepository,
    private val settingsRepository: SettingsRepository,
    private val enforcementManager: EnforcementManager,
    private val logRepository: LogRepository,
    private val exemptionRepository: ExemptionRepository,
    private val appTracker: AppTracker
) {
    suspend fun checkRules(metrics: DashboardMetrics) {
        val settings = settingsRepository.settings.first()
        if (!settings.automationEnabled) return

        val rules = ruleRepository.getActiveRules()
        for (rule in rules) {
            if (evaluateRule(rule, metrics)) {
                executeRuleAction(rule, settings.enforcementTier)
            }
        }
    }

    private fun evaluateRule(rule: AutomationRuleEntity, metrics: DashboardMetrics): Boolean {
        val currentValue = when (rule.metric) {
            "CPU" -> metrics.cpuTotal.toFloat()
            "RAM" -> {
                if (metrics.ramTotalMb > 0)
                    ((metrics.ramUsedMb * 100f) / metrics.ramTotalMb)
                else 0f
            }
            "BATTERY" -> metrics.batteryLevel.toFloat()
            "TEMP" -> metrics.batteryTemp
            else -> return false
        }

        return when (rule.operator) {
            "GT" -> currentValue > rule.threshold
            "LT" -> currentValue < rule.threshold
            "GTE" -> currentValue >= rule.threshold
            "LTE" -> currentValue <= rule.threshold
            else -> false
        }
    }

    private suspend fun executeRuleAction(rule: AutomationRuleEntity, tier: EnforcementTier) {
        val action = when (rule.action) {
            "STOP" -> EnforcementAction.STOP_APP
            "CLEAR_CACHE" -> EnforcementAction.CLEAR_CACHE
            "REDUCE_PRIORITY" -> EnforcementAction.REDUCE_PRIORITY
            else -> EnforcementAction.NOTIFY
        }

        if (action == EnforcementAction.NOTIFY || rule.responseType == "NOTIFY_ONLY") {
            logRepository.log("AUTOMATION", "Threshold reached for ${rule.metric}: ${rule.name}")
            if (rule.notifyOnTrigger) {
                sendNotification("MobilePulse Alert", "Rule '${rule.name}' triggered: ${rule.metric} threshold reached.")
            }
            return
        }

        // Find the top consumer to target
        val runningApps = appTracker.getRunningApps(tier)
        val targetApp = runningApps
            .filter { !exemptionRepository.isExempt(it.packageName) && !it.isSystem }
            .maxByOrNull { if (rule.metric == "RAM") it.ramMb.toInt() else it.cpuPercent }

        if (targetApp != null) {
            if (rule.responseType == "SEMI_AUTO") {
                logRepository.log("AUTOMATION", "Semi-auto rule '${rule.name}' triggered. Recommendation: $action for ${targetApp.appName}")
                if (rule.notifyOnTrigger) {
                    sendNotificationWithAction(
                        "Optimization Recommended",
                        "${targetApp.appName} is consuming high ${rule.metric}. Tap to $action.",
                        action,
                        targetApp.packageName
                    )
                }
                return
            }

            logRepository.log("AUTOMATION", "Rule '${rule.name}' triggered. Targeting ${targetApp.appName} ($action)")
            
            val result = enforcementManager.execute(action, targetApp.packageName, tier)

            when (result) {
                is EnforcementResult.Success -> {
                    logRepository.log("SUCCESS", result.message, targetApp.appName)
                    if (rule.notifyOnTrigger) {
                        sendNotification("MobilePulse Action", "Auto-optimized ${targetApp.appName}: ${result.message}")
                    }
                }
                is EnforcementResult.Failure ->
                    logRepository.log("ERROR", "Automation failed: ${result.reason}", targetApp.appName)
                is EnforcementResult.TierUnavailable ->
                    logRepository.log("WARNING", "Tier $tier unavailable for automation", targetApp.appName)
                is EnforcementResult.ManualActionRequired ->
                    logRepository.log("INFO", "Manual action required: ${result.message}", targetApp.appName)
            }
        } else {
            logRepository.log("AUTOMATION", "Rule '${rule.name}' triggered, but no non-exempt culprit found.")
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "mp_automation_alerts"
            if (nm.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId, 
                    "Automation Alerts", 
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for automated rules and optimizations"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun sendNotificationWithAction(title: String, message: String, action: EnforcementAction, packageName: String) {
        ensureNotificationChannel()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "mp_automation_alerts"

        val intent = Intent(context, com.mobilepulse.app.service.MonitoringService::class.java).apply {
            this.action = "EXECUTE_ACTION"
            putExtra("action", action.name)
            putExtra("package", packageName)
        }
        
        val pendingIntent = PendingIntent.getService(
            context, 
            packageName.hashCode(), 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .addAction(android.R.drawable.ic_menu_edit, "Execute", pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(packageName.hashCode(), notification)
    }

    private fun sendNotification(title: String, message: String) {
        ensureNotificationChannel()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "mp_automation_alerts"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}
