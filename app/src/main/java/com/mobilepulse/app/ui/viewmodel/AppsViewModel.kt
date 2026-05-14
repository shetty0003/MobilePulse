package com.mobilepulse.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilepulse.app.data.model.AppProcess
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.data.repository.ExemptionRepository
import com.mobilepulse.app.data.repository.LogRepository
import com.mobilepulse.app.data.repository.SettingsRepository
import com.mobilepulse.app.di.IoDispatcher
import com.mobilepulse.app.enforcement.EnforcementAction
import com.mobilepulse.app.enforcement.EnforcementManager
import com.mobilepulse.app.enforcement.EnforcementResult
import com.mobilepulse.app.monitoring.AppTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher       private val ioDispatcher: CoroutineDispatcher,
    private val exemptionRepo: ExemptionRepository,
    private val logRepo:       LogRepository,
    private val settingsRepo:  SettingsRepository,
    private val enforcement:   EnforcementManager,
    private val appTracker:    AppTracker
) : ViewModel() {

    private val _apps    = MutableStateFlow<List<AppProcess>>(emptyList())
    private val _loading = MutableStateFlow(false)
    private val _error   = MutableStateFlow<String?>(null)
    private val _sortBy  = MutableStateFlow(SortOrder.CPU)

    val loading: StateFlow<Boolean>   = _loading.asStateFlow()
    val error:   StateFlow<String?>   = _error.asStateFlow()
    val sortBy:  StateFlow<SortOrder> = _sortBy.asStateFlow()

    val sortedApps: StateFlow<List<AppProcess>> = combine(_apps, _sortBy) { list, sort ->
        when (sort) {
            SortOrder.CPU  -> list.sortedByDescending { it.cpuPercent }
            SortOrder.RAM  -> list.sortedByDescending { it.ramMb }
            SortOrder.NAME -> list.sortedBy { it.appName }
            SortOrder.RISK -> list.sortedByDescending { it.risk.ordinal }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _loading.value = true
        _error.value   = null
        try {
            val settings = settingsRepo.settings.first()
            val tier     = settings.enforcementTier

            android.util.Log.d("MobilePulse", "AppsViewModel: fetching with tier=$tier")

            val apps = withContext(ioDispatcher) {
                appTracker.getRunningApps(tier)
            }

            android.util.Log.d("MobilePulse", "AppsViewModel: got ${apps.size} apps")

            if (apps.isEmpty()) {
                _error.value = when (tier) {
                    EnforcementTier.STANDARD ->
                        "No apps found — make sure Usage Access permission is granted"
                    EnforcementTier.SHIZUKU  ->
                        "No apps found — make sure Shizuku is running and permission is granted"
                    EnforcementTier.ROOT     ->
                        "No apps found — make sure device is rooted"
                }
            }

            _apps.value = apps
        } catch (e: Exception) {
            _error.value = "Failed to load apps: ${e.message}"
            android.util.Log.e("MobilePulse", "AppsViewModel error: ${e.message}")
        } finally {
            _loading.value = false
        }
    }

    fun setSortOrder(order: SortOrder) { _sortBy.value = order }

    fun addToWhitelist(process: AppProcess) = viewModelScope.launch {
        exemptionRepo.addToWhitelist(process.packageName, process.appName)
        logRepo.log("INFO", "Added ${process.appName} to whitelist", process.appName)
    }

    fun addToIgnoreList(process: AppProcess) = viewModelScope.launch {
        exemptionRepo.addToIgnoreList(process.packageName, process.appName)
        logRepo.log("INFO", "Added ${process.appName} to ignore list", process.appName)
    }

    fun stopApp(process: AppProcess) = viewModelScope.launch {
        val tier = settingsRepo.settings.first().enforcementTier

        logRepo.log(
            type        = "ACTION",
            message     = "Stop requested for ${process.appName} (Tier: $tier)",
            affectedApp = process.appName
        )

        val result = enforcement.execute(
            action      = EnforcementAction.STOP_APP,
            packageName = process.packageName,
            tier        = tier
        )

        when (result) {
            is EnforcementResult.Success -> {
                _apps.value = _apps.value.filter { it.packageName != process.packageName }
                logRepo.log("SUCCESS", result.message, process.appName)
                android.util.Log.d("MobilePulse", "Stopped ${process.appName}: ${result.message}")
            }
            is EnforcementResult.Failure -> {
                logRepo.log("ERROR", result.reason, process.appName)
                android.util.Log.e("MobilePulse", "Stop failed: ${result.reason}")
            }
            is EnforcementResult.TierUnavailable -> {
                logRepo.log("WARNING", "Tier $tier not available", process.appName)
            }
            is EnforcementResult.ManualActionRequired -> {
                logRepo.log("INFO", result.message, process.appName)
            }
        }

        kotlinx.coroutines.delay(1000)
        refresh()
    }
}

enum class SortOrder { CPU, RAM, NAME, RISK }
