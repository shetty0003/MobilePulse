package com.mobilepulse.app.monitoring

import android.app.ActivityManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.mobilepulse.app.data.model.AppProcess
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.data.model.RiskLevel
import com.mobilepulse.app.enforcement.IShizukuService
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pm = context.packageManager
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    var shizukuService: IShizukuService? = null

    suspend fun getRunningApps(tier: EnforcementTier): List<AppProcess> =
        withContext(Dispatchers.IO) {
            when (tier) {
                EnforcementTier.ROOT    -> getAppsViaShell()
                EnforcementTier.SHIZUKU -> getAppsViaShizuku()
                EnforcementTier.STANDARD -> getAppsViaUsageStats()
            }
        }

    // ── Standard: UsageStats (works on all Android versions) ─────────────────
    // NOTE: On Android 11+ getInstalledApplications / getRunningAppProcesses
    // no longer returns all apps due to package visibility restrictions.
    // We use UsageStatsManager which is exempt from these restrictions when
    // the PACKAGE_USAGE_STATS permission is granted.
    private fun getAppsViaUsageStats(): List<AppProcess> {
        val endTime   = System.currentTimeMillis()
        val startTime = endTime - 1000L * 60 * 60 * 24 // last 24 hours

        val usageMap: Map<String, UsageStats> = try {
            usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        } catch (e: Exception) {
            android.util.Log.e("MobilePulse", "UsageStats failed: ${e.message}")
            emptyMap()
        }

        if (usageMap.isEmpty()) {
            android.util.Log.w("MobilePulse",
                "UsageStats empty — Usage Access permission likely not granted")
            return emptyList()
        }

        val totalForegroundMs = usageMap.values
            .sumOf { it.totalTimeInForeground }
            .takeIf { it > 0L } ?: 1L

        return usageMap.entries.mapNotNull { (packageName, stats) ->
            if (stats.totalTimeInForeground <= 0L) return@mapNotNull null

            // On Android 11+ we can only reliably query apps that have
            // declared a <queries> entry or are visible to us.
            // getApplicationInfo still works for installed apps that appear
            // in UsageStats — the stats manager bypasses visibility filtering.
            val appInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                return@mapNotNull null
            }

            val appLabel = appInfo.loadLabel(pm).toString()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            // RAM — best effort via ActivityManager (restricted on Android 11+
            // for processes not belonging to us, but returns our own and some others)
            val ramMb = getRamForPackage(packageName)

            val cpuProxy = ((stats.totalTimeInForeground * 100f) / totalForegroundMs)
                .toInt().coerceIn(0, 99)

            AppProcess(
                packageName = packageName,
                appName     = appLabel,
                cpuPercent  = cpuProxy,
                ramMb       = ramMb,
                risk        = when {
                    cpuProxy > 30 || ramMb > 400 -> RiskLevel.HIGH
                    cpuProxy > 10 || ramMb > 150 -> RiskLevel.MEDIUM
                    else                          -> RiskLevel.LOW
                },
                isSystem = isSystem
            )
        }
            .distinctBy { it.packageName }
            .sortedByDescending { it.cpuPercent }
    }

    // ── RAM helper — gracefully handles Android 11+ restrictions ─────────────
    private fun getRamForPackage(packageName: String): Long {
        return try {
            // runningAppProcesses is restricted on Android 11+ — it only returns
            // our own process and a small subset. We try anyway and fall back to 0.
            val pids = activityManager.runningAppProcesses
                ?.filter { it.processName == packageName }
                ?.map { it.pid }
                ?: return 0L

            if (pids.isEmpty()) return 0L

            activityManager.getProcessMemoryInfo(pids.toIntArray())
                .firstOrNull()?.totalPss?.toLong()?.div(1024L) ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // ── Shizuku: run `top` via privileged shell ───────────────────────────────
    private fun getAppsViaShizuku(): List<AppProcess> {
        val service = shizukuService ?: return getAppsViaUsageStats()
        return try {
            val output = service.execute("top -b -n 1 -o %CPU,RES,NAME,ARGS")
            val parsed = parseTopOutput(output)
            // If top returns nothing useful, fall back
            if (parsed.isEmpty()) getAppsViaUsageStats() else parsed
        } catch (e: Exception) {
            android.util.Log.e("MobilePulse", "Shizuku top failed: ${e.message}")
            getAppsViaUsageStats()
        }
    }

    // ── Root: run `top` directly via libsu ───────────────────────────────────
    private fun getAppsViaShell(): List<AppProcess> {
        return try {
            val result = Shell.cmd("top -b -n 1 -o %CPU,RES,NAME,ARGS").exec()
            val parsed = parseTopOutput(result.out.joinToString("\n"))
            if (parsed.isEmpty()) getAppsViaUsageStats() else parsed
        } catch (e: Exception) {
            android.util.Log.e("MobilePulse", "Root top failed: ${e.message}")
            getAppsViaUsageStats()
        }
    }

    // ── Parse `top` output ────────────────────────────────────────────────────
    private fun parseTopOutput(output: String): List<AppProcess> {
        val lines = output.split("\n")
        val apps  = mutableListOf<AppProcess>()

        var cpuIndex  = -1
        var argsIndex = -1
        var resIndex  = -1

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val tokens = trimmed.split(Regex("\\s+"))

            // Header line detection
            if (tokens.any { it == "CPU%" || it == "%CPU" }) {
                cpuIndex  = tokens.indexOfFirst { it == "CPU%" || it == "%CPU" }
                argsIndex = tokens.indexOfFirst { it == "ARGS" || it == "NAME" || it == "CMD" }
                resIndex  = tokens.indexOfFirst { it == "RES" }
                continue
            }

            if (cpuIndex == -1 || tokens.size <= maxOf(cpuIndex, argsIndex, resIndex)) continue

            try {
                val cpu = tokens.getOrNull(cpuIndex)
                    ?.replace("%", "")?.split(".")?.firstOrNull()
                    ?.toIntOrNull() ?: continue

                val packageName = tokens.getOrNull(argsIndex) ?: continue

                // Only include entries that look like package names
                if (!packageName.contains(".") || packageName.startsWith("[")) continue

                val appLabel = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(packageName,
                            PackageManager.ApplicationInfoFlags.of(0)).loadLabel(pm).toString()
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    packageName.substringAfterLast('.')
                }

                val resStr = tokens.getOrNull(resIndex) ?: "0"
                val ramMb  = parseMemToMb(resStr)

                apps.add(AppProcess(
                    packageName = packageName,
                    appName     = appLabel,
                    cpuPercent  = cpu,
                    ramMb       = ramMb,
                    risk        = when {
                        cpu > 30 || ramMb > 400 -> RiskLevel.HIGH
                        cpu > 10 || ramMb > 150 -> RiskLevel.MEDIUM
                        else                    -> RiskLevel.LOW
                    },
                    isSystem = isSystemApp(packageName)
                ))
            } catch (_: Exception) { }
        }

        return apps
            .filter { it.cpuPercent > 0 }
            .distinctBy { it.packageName }
            .sortedByDescending { it.cpuPercent }
    }

    private fun parseMemToMb(memStr: String): Long {
        return try {
            val clean = memStr.uppercase().trim()
            when {
                clean.endsWith("G") -> (clean.dropLast(1).toDouble() * 1024).toLong()
                clean.endsWith("M") -> clean.dropLast(1).toDouble().toLong()
                clean.endsWith("K") -> (clean.dropLast(1).toDouble() / 1024).toLong()
                else                -> clean.toLongOrNull()?.div(1024L) ?: 0L
            }
        } catch (_: Exception) { 0L }
    }

    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: Exception) { false }
    }
}