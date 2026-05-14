package com.mobilepulse.app.ui.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.data.repository.LogRepository
import com.mobilepulse.app.data.repository.SettingsRepository
import com.mobilepulse.app.enforcement.EnforcementManager
import com.mobilepulse.app.monitoring.AppRamUsage
import com.mobilepulse.app.monitoring.RogueRamEngine
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class RamCleanState {
    object Idle    : RamCleanState()
    object Running : RamCleanState()
    data class Done(val ramFreedMb: Long, val cacheFreedMb: Long) : RamCleanState()
}

@HiltViewModel
class RamViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine:             RogueRamEngine,
    private val enforcementManager: EnforcementManager,
    private val settingsRepo:       SettingsRepository,
    private val logRepo:            LogRepository
) : ViewModel() {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _apps      = MutableStateFlow<List<AppRamUsage>>(emptyList())
    val apps: StateFlow<List<AppRamUsage>> = _apps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _pendingKill = MutableStateFlow<AppRamUsage?>(null)
    val pendingKill: StateFlow<AppRamUsage?> = _pendingKill.asStateFlow()

    private val _cleanState = MutableStateFlow<RamCleanState>(RamCleanState.Idle)
    val cleanState: StateFlow<RamCleanState> = _cleanState.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _isLoading.value = true
        val tier    = settingsRepo.settings.first().enforcementTier
        val service = enforcementManager.getShizukuService()
        _apps.value = engine.fetchAppMemory(tier, service)
        _isLoading.value = false
    }

    fun confirmKill(app: AppRamUsage) { _pendingKill.value = app }
    fun cancelKill()                  { _pendingKill.value = null }

    fun executeKill() = viewModelScope.launch {
        val app = _pendingKill.value ?: return@launch
        _pendingKill.value = null
        val tier    = settingsRepo.settings.first().enforcementTier
        val service = enforcementManager.getShizukuService()
        val ok = engine.killPackage(app.packageName, tier, service)
        logRepo.log(
            type        = if (ok) "ACTION" else "ERROR",
            message     = if (ok) "Force-stopped ${app.packageName} (${app.ramMb} MB freed)"
                          else    "Failed to force-stop ${app.packageName}",
            affectedApp = app.packageName
        )
        if (ok) refresh()
    }

    // ── Deep Clean ────────────────────────────────────────────────────────────

    fun deepClean() = viewModelScope.launch {
        if (_cleanState.value is RamCleanState.Running) return@launch
        _cleanState.value = RamCleanState.Running

        val tier    = settingsRepo.settings.first().enforcementTier
        val service = enforcementManager.getShizukuService()

        val memBefore = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }

        val cacheFreedMb = withContext(Dispatchers.IO) {
            var freed = 0L
            when (tier) {
                EnforcementTier.ROOT -> {
                    Shell.cmd("am kill-all").exec()
                    freed = measureCachesMb()
                    Shell.cmd("rm -rf /data/data/*/cache/*").exec()
                }
                EnforcementTier.SHIZUKU -> {
                    if (service != null) {
                        service.execute("am kill-all")
                        try { service.execute("pm trim-caches 999999999") } catch (_: Exception) {
                            // Fallback: clear per-package caches for top apps
                            userPackages().take(30).forEach { pkg ->
                                try { service.execute("rm -rf /data/data/$pkg/cache/*") }
                                catch (_: Exception) {}
                            }
                        }
                    }
                }
                EnforcementTier.STANDARD -> {
                    userPackages().forEach { pkg ->
                        try { activityManager.killBackgroundProcesses(pkg) } catch (_: Exception) {}
                    }
                    freed = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1_048_576L
                    Runtime.getRuntime().gc()
                }
            }
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
            freed
        }

        delay(600)
        val memAfter  = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val ramFreedMb = maxOf(0L, (memAfter.availMem - memBefore.availMem) / 1_048_576L)

        logRepo.log("ACTION", "Deep clean — freed ${ramFreedMb} MB RAM, ${cacheFreedMb} MB cache")
        _cleanState.value = RamCleanState.Done(ramFreedMb, cacheFreedMb)

        delay(3000)
        _cleanState.value = RamCleanState.Idle
        refresh()
    }

    private fun userPackages(): List<String> = try {
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        else
            @Suppress("DEPRECATION") context.packageManager.getInstalledApplications(0)
        apps.filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName
        }.map { it.packageName }
    } catch (_: Exception) { emptyList() }

    private fun measureCachesMb(): Long = try {
        Shell.cmd("du -sk /data/data/*/cache 2>/dev/null").exec().out
            .sumOf { it.trim().split("\\s+".toRegex()).firstOrNull()?.toLongOrNull() ?: 0L } / 1024L
    } catch (_: Exception) { 0L }
}
