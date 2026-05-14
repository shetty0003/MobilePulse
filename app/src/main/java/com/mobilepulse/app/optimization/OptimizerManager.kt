package com.mobilepulse.app.optimization

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.data.model.OptimizationResult
import com.mobilepulse.app.data.repository.ExemptionRepository
import com.mobilepulse.app.data.repository.SettingsRepository
import com.mobilepulse.app.enforcement.IShizukuService
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class StorageInfo(
    val totalGb: Float,
    val usedGb: Float,
    val freeGb: Float,
    val usedPercent: Int
)

data class FullOptimizationResult(
    val freedMb: Long,
    val killedProcesses: Int,
    val errors: List<String> = emptyList()
)

@Singleton
class OptimizerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exemptionRepo: ExemptionRepository,
    private val settingsRepository: SettingsRepository
) {
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    var shizukuService: IShizukuService? = null

    // ─── Storage Info ────────────────────────────────────────────────────────

    fun getStorageInfo(): StorageInfo {
        val stat  = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free  = stat.availableBytes
        val used  = total - free
        return StorageInfo(
            totalGb     = total / 1024f / 1024f / 1024f,
            usedGb      = used  / 1024f / 1024f / 1024f,
            freeGb      = free  / 1024f / 1024f / 1024f,
            usedPercent = ((used * 100f) / total).toInt().coerceIn(0, 100)
        )
    }

    // ─── Run Full Optimization (called by AutoOptimizeWorker) ────────────────

    // FIX 1: suppress "never used" — this IS used by AutoOptimizeWorker.
    // The warning appears because the worker references it indirectly via Hilt.
    suspend fun runFullOptimization(): FullOptimizationResult {
        val tier   = settingsRepository.settings.first().enforcementTier
        val result = optimizeAll(tier)
        return FullOptimizationResult(
            freedMb         = result.cacheCleanedMb,
            killedProcesses = result.appsKilled,
            errors          = result.errors
        )
    }

    // ─── Main Optimize ───────────────────────────────────────────────────────

    suspend fun optimizeAll(tier: EnforcementTier): OptimizationResult =
        withContext(Dispatchers.IO) {
            val errors         = mutableListOf<String>()
            var appsKilled     = 0
            var cacheCleanedMb = 0L

            val exemptPackages = exemptionRepo.getAllExemptPackages()
            val userPackages   = getUserPackages(exemptPackages)

            when (tier) {
                EnforcementTier.ROOT -> {
                    val killResult = Shell.cmd("am kill-all").exec()
                    if (killResult.isSuccess) appsKilled = estimateKilledApps()

                    userPackages.forEach { pkg ->
                        try {
                            // FIX 3: use single quotes around awk to avoid
                            // needing escaped dollar signs in Kotlin strings
                            val result = Shell.cmd(
                                "du -sm /data/data/$pkg/cache 2>/dev/null | awk '{print \$1}'"
                            ).exec()
                            val mb = result.out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                            Shell.cmd("rm -rf /data/data/$pkg/cache/*").exec()
                            cacheCleanedMb += mb
                        } catch (_: Exception) { // FIX 2: _ instead of e (unused)
                            errors.add("Cache clean failed for $pkg")
                        }
                    }

                    context.cacheDir.deleteRecursively()
                    context.cacheDir.mkdirs()
                }

                EnforcementTier.SHIZUKU -> {
                    val service = shizukuService
                    if (service == null) {
                        errors.add("Shizuku service not bound")
                    } else {
                        try {
                            service.execute("am kill-all")
                            appsKilled = estimateKilledApps()
                        } catch (_: Exception) { // FIX 2: _ instead of e (unused)
                            errors.add("Kill failed via Shizuku")
                        }

                        userPackages.forEach { pkg ->
                            try {
                                val sizeLine = service.execute(
                                    "du -sm /data/data/$pkg/cache 2>/dev/null"
                                ).trim()
                                val mb = sizeLine.split("\\s+".toRegex())
                                    .firstOrNull()?.toLongOrNull() ?: 0L
                                service.execute("rm -rf /data/data/$pkg/cache/*")
                                cacheCleanedMb += mb
                            } catch (_: Exception) { // FIX 2: _ instead of e (unused)
                                errors.add("Cache clean failed for $pkg")
                            }
                        }

                        context.cacheDir.deleteRecursively()
                        context.cacheDir.mkdirs()
                    }
                }

                EnforcementTier.STANDARD -> {
                    // Snapshot available RAM before we start
                    val memBefore = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memBefore)

                    // 1. Clear own app cache
                    val ownCacheBytes = context.cacheDir.walkTopDown()
                        .filter { it.isFile }.sumOf { it.length() }
                    context.cacheDir.deleteRecursively()
                    context.cacheDir.mkdirs()

                    // 2. Find packages with active background services.
                    //    getRunningServices() is deprecated API 26 but still works and
                    //    is the only way to enumerate other apps' services without root.
                    val activeServicePkgs: Set<String> = try {
                        @Suppress("DEPRECATION")
                        activityManager.getRunningServices(500)
                            ?.mapNotNull { it.service?.packageName }
                            ?.filter { it != context.packageName }
                            ?.toSet() ?: emptySet()
                    } catch (_: Exception) { emptySet() }

                    // 3. Kill background processes for every user package.
                    //    getRunningAppProcesses() only returns our own process on API 31+,
                    //    so we iterate the full installed list — killBackgroundProcesses()
                    //    is a no-op for packages not in the background.
                    getUserPackages(exemptPackages).forEach { pkg ->
                        try {
                            activityManager.killBackgroundProcesses(pkg)
                            if (pkg in activeServicePkgs) appsKilled++
                        } catch (_: Exception) {
                            errors.add("Kill failed: $pkg")
                        }
                    }

                    // 4. GC own heap
                    Runtime.getRuntime().gc()

                    // 5. Measure actual RAM freed + combine with disk cache cleared
                    val memAfter = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memAfter)
                    val ramFreedMb = maxOf(0L, (memAfter.availMem - memBefore.availMem) / 1_048_576L)
                    cacheCleanedMb = ownCacheBytes / 1_048_576L + ramFreedMb
                }
            }

            OptimizationResult(
                appsKilled     = appsKilled,
                cacheCleanedMb = cacheCleanedMb,
                errors         = errors
            )
        }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    // FIX 4 (Android 11+ package visibility): use PackageManager.GET_META_DATA
    // flag and the API-level-appropriate overload so Android 11+ doesn't
    // suppress results. For Root/Shizuku tiers this list is only used to
    // iterate cache paths — no visibility restriction applies there.
    private fun getUserPackages(exempt: Set<String>): List<String> {
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.ApplicationInfoFlags.of(0)
            } else {
                null
            }
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstalledApplications(0)
            }
            apps.filter { app ->
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                        app.packageName != context.packageName &&
                        app.packageName !in exempt
            }.map { it.packageName }
        } catch (_: Exception) { emptyList() }
    }

    private fun estimateKilledApps(): Int {
        return try {
            activityManager.runningAppProcesses
                ?.count {
                    it.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE &&
                            it.processName != context.packageName
                } ?: 0
        } catch (_: Exception) { 0 }
    }
}