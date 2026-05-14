package com.mobilepulse.app.worker

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.mobilepulse.app.MainActivity
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.data.repository.LogRepository
import com.mobilepulse.app.data.repository.SettingsRepository
import com.mobilepulse.app.enforcement.EnforcementManager
import com.mobilepulse.app.service.MonitoringService
import com.topjohnwu.superuser.Shell
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoCleanWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepo:    SettingsRepository,
    private val enforcementMgr:  EnforcementManager,
    private val logRepo:         LogRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME    = "mp_auto_clean"
        const val CHANNEL_ID   = "mp_auto_clean_notif"
        const val NOTIF_ID     = 2001

        private const val IDLE_THRESHOLD_MS = 5 * 60 * 1000L   // 5 minutes
        private const val REPEAT_HOURS      = 1L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoCleanWorker>(
                REPEAT_HOURS, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .setInitialDelay(REPEAT_HOURS, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val settings = settingsRepo.settings.first()

        // Respect the toggle
        if (!settings.scheduledCleanEnabled) return Result.success()

        // Only run when user has been away for 5+ minutes
        val lastForeground = settingsRepo.getLastForegroundMs()
        val idleMs = System.currentTimeMillis() - lastForeground
        if (idleMs < IDLE_THRESHOLD_MS) {
            logRepo.log("INFO", "Auto-clean skipped — user active ${idleMs / 1000}s ago")
            return Result.success()
        }

        // Freeze the monitoring loop so it doesn't restart killed processes
        MonitoringService.isCleanRunning.value = true

        return try {
            val actMgr    = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memBefore = ActivityManager.MemoryInfo().also { actMgr.getMemoryInfo(it) }

            val cacheFreedMb = withContext(Dispatchers.IO) {
                runDeepClean(settings.enforcementTier)
            }

            // Short settle time — let the OS reclaim pages
            delay(800)

            val memAfter   = ActivityManager.MemoryInfo().also { actMgr.getMemoryInfo(it) }
            val ramFreedMb = maxOf(0L, (memAfter.availMem - memBefore.availMem) / 1_048_576L)

            logRepo.log(
                "ACTION",
                "Scheduled auto-clean — freed ${ramFreedMb} MB RAM, ${cacheFreedMb} MB cache (idle ${idleMs / 60_000}min)"
            )

            showResultNotification(ramFreedMb, cacheFreedMb)
            Result.success()

        } catch (e: Exception) {
            logRepo.log("ERROR", "Auto-clean failed: ${e.message}")
            Result.success()
        } finally {
            // Always unfreeze the monitoring loop
            MonitoringService.isCleanRunning.value = false
        }
    }

    private fun runDeepClean(tier: EnforcementTier): Long {
        val service = enforcementMgr.getShizukuService()

        return when (tier) {
            EnforcementTier.ROOT -> {
                // Kill everything, wipe caches, put heavy apps to sleep
                Shell.cmd("am kill-all").exec()
                val cacheKb = Shell.cmd("du -sk /data/data/*/cache 2>/dev/null").exec().out
                    .sumOf { it.trim().split("\\s+".toRegex()).firstOrNull()?.toLongOrNull() ?: 0L }
                Shell.cmd("rm -rf /data/data/*/cache/*").exec()
                // Suspend known battery drainers via app-standby
                knownDrainerPackages().forEach { pkg ->
                    Shell.cmd("am set-inactive $pkg true").exec()
                }
                cacheKb / 1024L
            }

            EnforcementTier.SHIZUKU -> {
                if (service != null) {
                    service.execute("am kill-all")
                    try {
                        service.execute("pm trim-caches 999999999")
                    } catch (_: Exception) {
                        // Fallback: wipe per-package caches for user apps
                        userPackages().take(40).forEach { pkg ->
                            try { service.execute("rm -rf /data/data/$pkg/cache/*") } catch (_: Exception) {}
                        }
                    }
                    // Put known battery drainers into app standby
                    knownDrainerPackages().forEach { pkg ->
                        try { service.execute("am set-inactive $pkg true") } catch (_: Exception) {}
                    }
                }
                0L
            }

            EnforcementTier.STANDARD -> {
                val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                userPackages().forEach { pkg ->
                    try { actMgr.killBackgroundProcesses(pkg) } catch (_: Exception) {}
                }
                val ownCacheBytes = context.cacheDir.walkTopDown()
                    .filter { it.isFile }.sumOf { it.length() }
                context.cacheDir.deleteRecursively()
                context.cacheDir.mkdirs()
                Runtime.getRuntime().gc()
                ownCacheBytes / 1_048_576L
            }
        }
    }

    private fun userPackages(): List<String> = try {
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        else
            @Suppress("DEPRECATION") context.packageManager.getInstalledApplications(0)
        apps.filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    it.packageName != context.packageName
        }.map { it.packageName }
    } catch (_: Exception) { emptyList() }

    // Well-known heavy background apps that drain battery
    private fun knownDrainerPackages() = listOf(
        "com.facebook.katana", "com.facebook.orca", "com.instagram.android",
        "com.twitter.android", "com.snapchat.android", "com.tiktok.android",
        "com.google.android.youtube", "com.spotify.music", "com.amazon.mShop.android.shopping"
    ).filter { pkg ->
        try { context.packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
    }

    private fun showResultNotification(ramFreedMb: Long, cacheFreedMb: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(CHANNEL_ID) == null
        ) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Auto Clean", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val body = buildString {
            if (ramFreedMb > 0) append("${ramFreedMb} MB RAM freed")
            if (cacheFreedMb > 0) {
                if (isNotEmpty()) append(" · ")
                append("${cacheFreedMb} MB cache cleared")
            }
            if (isEmpty()) append("Background processes trimmed")
        }

        nm.notify(
            NOTIF_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("MobilePulse Auto Clean")
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_menu_rotate)
                .setContentIntent(tapIntent)
                .setAutoCancel(true)
                .build()
        )
    }
}
