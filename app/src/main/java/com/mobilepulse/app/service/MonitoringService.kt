package com.mobilepulse.app.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.mobilepulse.app.MainActivity
import com.mobilepulse.app.automation.RuleEngine
import com.mobilepulse.app.data.model.DashboardMetrics
import com.mobilepulse.app.data.repository.BatteryStatsRepository
import com.mobilepulse.app.data.repository.SettingsRepository
import com.mobilepulse.app.enforcement.EnforcementAction
import com.mobilepulse.app.enforcement.EnforcementManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MonitoringService : Service() {

    @Inject lateinit var ruleEngine:          RuleEngine
    @Inject lateinit var settingsRepo:        SettingsRepository
    @Inject lateinit var enforcementManager:  EnforcementManager
    @Inject lateinit var batteryStatsRepo:    BatteryStatsRepository

    companion object {
        const val CHANNEL_ID = "mp_monitor"
        const val NOTIF_ID   = 1001

        private val _metricsFlow = MutableStateFlow<DashboardMetrics?>(null)
        val metricsFlow: StateFlow<DashboardMetrics?> = _metricsFlow

        /** Set to true by AutoCleanWorker while a deep clean is in progress.
         *  The monitoring loop backs off to 30-second ticks so it doesn't
         *  restart killed processes mid-clean. */
        val isCleanRunning = MutableStateFlow(false)
    }

    private val serviceScope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val refreshInterval          = 5_000L
    private val notificationUpdateInterval = 5 * 60 * 1_000L  // 5 minutes
    private var lastNotificationUpdate   = 0L

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        startMonitoring()
    }

    private fun startMonitoring() {
        serviceScope.launch {
            // Warmup — take baseline CPU reading and discard
            readCpuCoresUsage()
            delay(refreshInterval)

            while (isActive) {
                // Back off during a scheduled deep clean so we don't restart killed processes
                if (isCleanRunning.value) {
                    delay(30_000L)
                    continue
                }

                try {
                    val metrics = collectMetrics()
                    _metricsFlow.value = metrics

                    batteryStatsRepo.record(
                        levelPercent       = metrics.batteryLevel,
                        isCharging         = metrics.batteryCharging,
                        temperatureCelsius = metrics.batteryTemp
                    )

                    ruleEngine.checkRules(metrics)
                    updateNotification(metrics)

                } catch (e: Exception) {
                    android.util.Log.e("MobilePulse", "Monitoring cycle error: ${e.message}")
                }
                delay(refreshInterval)
            }
        }
    }

    private fun collectMetrics(): DashboardMetrics {
        val actMgr  = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actMgr.getMemoryInfo(memInfo)

        // ── Battery ──────────────────────────────────────────────────────────
        val battIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level    = battIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL,       -1)  ?: 0
        val scale    = battIntent?.getIntExtra(BatteryManager.EXTRA_SCALE,       -1)  ?: 100
        val status   = battIntent?.getIntExtra(BatteryManager.EXTRA_STATUS,      -1)  ?: -1
        val temp     = battIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,  0)  ?: 0
        val voltage  = battIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE,      0)  ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // ── CPU ───────────────────────────────────────────────────────────────
        val cpuCores = readCpuCoresUsage()
        val cpuTotal = if (cpuCores.isNotEmpty()) cpuCores.average().toInt() else 0

        return DashboardMetrics(
            cpuTotal        = cpuTotal,
            cpuCores        = cpuCores,
            ramTotalMb      = memInfo.totalMem  / (1024L * 1024L),
            ramUsedMb       = (memInfo.totalMem - memInfo.availMem) / (1024L * 1024L),
            ramFreeMb       = memInfo.availMem  / (1024L * 1024L),
            batteryLevel    = if (scale > 0) (level * 100 / scale) else 0,
            batteryCharging = charging,
            batteryTemp     = temp / 10f,
            batteryDrainRate = voltage
        )
    }

    // ── CPU reading with delta tracking ──────────────────────────────────────

    private data class CpuStats(val idle: Long, val total: Long)

    // ── HardwarePropertiesManager path (preferred) ────────────────────────
    // getCpuUsages() requires DEVICE_POWER (system permission). We try it
    // once; if denied we flip the flag and never retry for this session.
    private var hwManagerFailed = false
    private var lastHwCpuInfos: Array<out CpuUsageInfo>? = null

    // ── /proc/stat fallback path ─────────────────────────────────────────
    private var lastAggregateStat: CpuStats? = null
    private var lastPerCoreStats: List<CpuStats>? = null

    // CPU reading chain:
    // 1. HardwarePropertiesManager  — system API, most accurate, no file perms needed
    // 2. /proc/stat direct           — works on most devices (API 24–28 reliably)
    // 3. /proc/stat via Shizuku      — bypasses Samsung Knox /proc restriction
    // 4. ActivityManager memory proxy — last resort
    private fun readCpuCoresUsage(): List<Int> {
        if (!hwManagerFailed) {
            readCpuCoresViaHwManager()?.let { return it }
        }
        return parseProcStatContent(readProcStatLinesDirect())
            ?: parseProcStatContent(readProcStatLinesShizuku())
            ?: getCpuViaActivityManager()
    }

    @SuppressLint("MissingPermission")
    private fun readCpuCoresViaHwManager(): List<Int>? {
        return try {
            val hwm     = getSystemService(HardwarePropertiesManager::class.java)
            val current = hwm.cpuUsages
            if (current.isEmpty()) return null

            val last = lastHwCpuInfos
            lastHwCpuInfos = current

            if (last == null || last.size != current.size) {
                return current.map { 0 }   // warmup tick — no baseline yet
            }

            current.indices.map { i ->
                val curr = current[i]
                val prev = last[i]
                when {
                    curr.active < 0L || prev.active < 0L -> 0   // core offline (UNDEFINED = -1)
                    else -> {
                        val activeDelta = curr.active - prev.active
                        val totalDelta  = curr.total  - prev.total
                        if (totalDelta <= 0L) 0
                        else (activeDelta * 100L / totalDelta).toInt().coerceIn(0, 100)
                    }
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.w("MobilePulse",
                "HardwarePropertiesManager denied — falling back to /proc/stat")
            hwManagerFailed = true
            null
        } catch (e: Exception) {
            android.util.Log.e("MobilePulse", "HwManager CPU error: ${e.message}")
            null
        }
    }

    private fun readProcStatLinesDirect(): List<String>? {
        return try {
            val f = java.io.File("/proc/stat")
            if (!f.canRead()) null else f.readLines()
        } catch (_: Exception) { null }
    }

    private fun readProcStatLinesShizuku(): List<String>? {
        return try {
            val service = enforcementManager.getShizukuService() ?: return null
            val output  = service.execute("cat /proc/stat")
            if (output.isNullOrBlank() || output.startsWith("ERROR")) return null
            android.util.Log.d("MobilePulse", "CPU via Shizuku — success")
            output.lines()
        } catch (_: Exception) { null }
    }

    // Shared parser for both proc/stat sources.
    // Returns null when lines contain no usable cpu data (signals caller to try next source).
    private fun parseProcStatContent(lines: List<String>?): List<Int>? {
        if (lines == null) return null
        return try {
            val aggregateStat = lines.firstOrNull { it.startsWith("cpu ") }
                ?.let { parseProcStatLine(it) }

            val currentCoreStats = lines
                .filter { l -> l.length > 3 && l.startsWith("cpu") && l[3].isDigit() }
                .map { parseProcStatLine(it) }

            if (aggregateStat == null && currentCoreStats.isEmpty()) return null

            val coreResults = if (lastPerCoreStats != null &&
                lastPerCoreStats!!.size == currentCoreStats.size &&
                currentCoreStats.isNotEmpty()
            ) {
                currentCoreStats.zip(lastPerCoreStats!!).map { (curr, last) ->
                    computeProcDelta(curr, last)
                }
            } else null

            val aggregateResult = if (aggregateStat != null && lastAggregateStat != null)
                computeProcDelta(aggregateStat, lastAggregateStat!!) else null

            if (aggregateStat != null)         lastAggregateStat = aggregateStat
            if (currentCoreStats.isNotEmpty()) lastPerCoreStats  = currentCoreStats

            coreResults ?: aggregateResult?.let { listOf(it) }
        } catch (e: Exception) {
            android.util.Log.e("MobilePulse", "parseProcStatContent error: ${e.message}")
            null
        }
    }

    private fun parseProcStatLine(line: String): CpuStats {
        val values = line.trim().split(Regex("\\s+")).drop(1)
            .map { it.toLongOrNull() ?: 0L }
        val idle  = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L } // idle + iowait
        val total = values.sum()
        return CpuStats(idle, total)
    }

    private fun computeProcDelta(curr: CpuStats, last: CpuStats): Int {
        val idleDelta  = curr.idle  - last.idle
        val totalDelta = curr.total - last.total
        return if (totalDelta <= 0L) 0
        else ((totalDelta - idleDelta) * 100L / totalDelta).toInt().coerceIn(0, 100)
    }

    private fun getCpuViaActivityManager(): List<Int> {
        return try {
            val actMgr  = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actMgr.getMemoryInfo(memInfo)
            val usedRatio = 1f - (memInfo.availMem.toFloat() / memInfo.totalMem.toFloat())
            listOf((usedRatio * 100).toInt().coerceIn(0, 100))
        } catch (_: Exception) {
            listOf(0)
        }
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun updateNotification(metrics: DashboardMetrics) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdate < notificationUpdateInterval) return
        lastNotificationUpdate = now
        val text = "CPU: ${metrics.cpuTotal}% | RAM: ${metrics.ramUsedMb} MB used"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(content: String = "Monitoring system resources..."): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val tapPi = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MobilePulse Live")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setContentIntent(tapPi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MobilePulse Monitor",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    // ── Intent handling ───────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_APP" -> {
                val pkg = intent.getStringExtra("package")
                if (pkg != null) serviceScope.launch {
                    val settings = settingsRepo.settings.first()
                    enforcementManager.execute(
                        EnforcementAction.STOP_APP, pkg, settings.enforcementTier
                    )
                }
            }
            "EXECUTE_ACTION" -> {
                val pkg        = intent.getStringExtra("package")
                val actionName = intent.getStringExtra("action")
                if (pkg != null && actionName != null) serviceScope.launch {
                    val action   = EnforcementAction.valueOf(actionName)
                    val settings = settingsRepo.settings.first()
                    enforcementManager.execute(action, pkg, settings.enforcementTier)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}