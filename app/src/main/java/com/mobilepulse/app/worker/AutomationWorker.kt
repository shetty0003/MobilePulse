package com.mobilepulse.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.mobilepulse.app.data.repository.BatteryStatsRepository
import com.mobilepulse.app.data.repository.ExemptionRepository
import com.mobilepulse.app.data.repository.LogRepository
import com.mobilepulse.app.data.repository.RuleRepository
import com.mobilepulse.app.data.repository.SettingsRepository
import com.mobilepulse.app.enforcement.EnforcementAction
import com.mobilepulse.app.enforcement.EnforcementManager
import com.mobilepulse.app.enforcement.EnforcementResult
import com.mobilepulse.app.monitoring.AppTracker
import com.mobilepulse.app.service.MonitoringService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class AutomationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ruleRepo:        RuleRepository,
    private val exemptionRepo:   ExemptionRepository,
    private val logRepo:         LogRepository,
    private val settingsRepo:    SettingsRepository,
    private val enforcement:     EnforcementManager,
    private val appTracker:      AppTracker,
    private val batteryStatsRepo: BatteryStatsRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "mp_automation"
        const val ALERT_CHANNEL = "mp_alerts"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutomationWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            batteryStatsRepo.pruneOld()

            val settings = settingsRepo.settings.first()
            if (!settings.automationEnabled) return Result.success()

            val rules = ruleRepo.getActiveRules()
            val metrics = MonitoringService.metricsFlow.replayCache.firstOrNull()
                ?: return Result.success()

            for (rule in rules) {
                val currentValue: Float = when (rule.metric) {
                    "CPU"     -> metrics.cpuTotal.toFloat()
                    "RAM"     -> if (metrics.ramTotalMb > 0)
                        (metrics.ramUsedMb * 100f / metrics.ramTotalMb) else 0f
                    "BATTERY" -> metrics.batteryLevel.toFloat()
                    "TEMP"    -> metrics.batteryTemp
                    else      -> continue
                }

                val exceeded = when (rule.operator) {
                    "GT"  -> currentValue >  rule.threshold
                    "GTE" -> currentValue >= rule.threshold
                    "LT"  -> currentValue <  rule.threshold
                    "LTE" -> currentValue <= rule.threshold
                    else  -> false
                }
                if (!exceeded) continue

                val action = when (rule.action) {
                    "NOTIFY"          -> EnforcementAction.NOTIFY
                    "STOP"            -> EnforcementAction.STOP_APP
                    "CLEAR_CACHE"     -> EnforcementAction.CLEAR_CACHE
                    "REDUCE_PRIORITY" -> EnforcementAction.REDUCE_PRIORITY
                    else              -> EnforcementAction.NOTIFY
                }

                if (action == EnforcementAction.NOTIFY || rule.responseType == "NOTIFY_ONLY") {
                    if (rule.notifyOnTrigger && settings.notificationsEnabled) {
                        sendNotification(
                            "MobilePulse Alert",
                            "Rule '${rule.name}' triggered: ${rule.metric} exceeded threshold."
                        )
                    }
                    continue
                }

                val runningApps = appTracker.getRunningApps(settings.enforcementTier)
                val targetApp = runningApps
                    .filter { !exemptionRepo.isExempt(it.packageName) && !it.isSystem }
                    .maxByOrNull { if (rule.metric == "RAM") it.ramMb.toInt() else it.cpuPercent }

                if (targetApp != null) {
                    if (rule.responseType == "SEMI_AUTO") {
                        if (rule.notifyOnTrigger && settings.notificationsEnabled) {
                            sendNotificationWithAction(
                                "Optimization Recommended",
                                "${targetApp.appName} is consuming high ${rule.metric}. Tap to $action.",
                                action,
                                targetApp.packageName
                            )
                        }
                        continue
                    }

                    val result = enforcement.execute(
                        action      = action,
                        packageName = targetApp.packageName,
                        tier        = settings.enforcementTier
                    )

                    when (result) {
                        is EnforcementResult.Success -> {
                            logRepo.log(
                                type = "ACTION",
                                message = result.message,
                                affectedApp = targetApp.appName
                            )
                            if (rule.notifyOnTrigger && settings.notificationsEnabled) {
                                sendNotification(
                                    title = "MobilePulse: ${rule.name}",
                                    body  = "Optimized ${targetApp.appName}: ${result.message}"
                                )
                            }
                        }
                        is EnforcementResult.Failure -> {
                            logRepo.log(
                                type = "ERROR",
                                message = "Rule '${rule.name}' failed: ${result.reason}",
                                affectedApp = targetApp.appName
                            )
                        }
                        is EnforcementResult.TierUnavailable -> {
                            logRepo.log(
                                type = "WARNING",
                                message = "Tier ${result.tier} unavailable for rule '${rule.name}'",
                                affectedApp = targetApp.appName
                            )
                        }
                        is EnforcementResult.ManualActionRequired -> {
                            logRepo.log(
                                type = "INFO",
                                message = "Manual action required: ${result.message}",
                                affectedApp = targetApp.appName
                            )
                        }
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            logRepo.log(type = "ERROR", message = "AutomationWorker crashed: ${e.message}")
            Result.success()
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(ALERT_CHANNEL) == null) {
                val channel = NotificationChannel(
                    ALERT_CHANNEL,
                    "MobilePulse Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun sendNotificationWithAction(
        title: String,
        body: String,
        action: EnforcementAction,
        packageName: String
    ) {
        createNotificationChannel()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(applicationContext, MonitoringService::class.java).apply {
            this.action = "EXECUTE_ACTION"
            putExtra("action", action.name)
            putExtra("package", packageName)
        }

        val pendingIntent = PendingIntent.getService(
            applicationContext,
            packageName.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(applicationContext, ALERT_CHANNEL)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_menu_edit, "Execute", pendingIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(packageName.hashCode(), notif)
    }

    private fun sendNotification(title: String, body: String) {
        createNotificationChannel()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(applicationContext, ALERT_CHANNEL)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}