package com.mobilepulse.app.ui.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.data.model.OptimizationResult
import com.mobilepulse.app.data.repository.LogRepository
import com.mobilepulse.app.data.repository.SettingsRepository
import com.mobilepulse.app.optimization.BootOptimizeResult
import com.mobilepulse.app.optimization.BootOptimizer
import com.mobilepulse.app.optimization.OptimizerManager
import com.mobilepulse.app.optimization.StorageInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OptimizerState {
    object Idle : OptimizerState()
    object Running : OptimizerState()
    data class Done(val result: OptimizationResult) : OptimizerState()
    data class Error(val message: String) : OptimizerState()
}

sealed class BootState {
    object Idle : BootState()
    object Running : BootState()
    data class Done(val result: BootOptimizeResult) : BootState()
    data class Error(val message: String) : BootState()
}

@HiltViewModel
class OptimizerViewModel @Inject constructor(
    private val optimizerManager: OptimizerManager,
    private val bootOptimizer: BootOptimizer,
    private val settingsRepo: SettingsRepository,
    private val logRepo: LogRepository
) : ViewModel() {

    private val _state = MutableStateFlow<OptimizerState>(OptimizerState.Idle)
    val state: StateFlow<OptimizerState> = _state.asStateFlow()

    private val _storageInfo = MutableStateFlow<StorageInfo?>(null)
    val storageInfo: StateFlow<StorageInfo?> = _storageInfo.asStateFlow()

    val settings = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init { refreshStorage() }

    fun refreshStorage() {
        _storageInfo.value = optimizerManager.getStorageInfo()
    }

    fun optimizeNow() = viewModelScope.launch {
        val tier = settingsRepo.settings.first().enforcementTier
        _state.value = OptimizerState.Running
        logRepo.log("ACTION", "Manual optimization started (Tier: $tier)")
        try {
            val result = optimizerManager.optimizeAll(tier)
            _state.value = OptimizerState.Done(result)
            logRepo.log(
                if (result.errors.isEmpty()) "SUCCESS" else "WARNING",
                "Optimization complete — killed ${result.appsKilled} apps, " +
                        "freed ${result.cacheCleanedMb} MB cache"
            )
            result.errors.forEach { err -> logRepo.log("ERROR", err) }
            refreshStorage()
        } catch (e: Exception) {
            _state.value = OptimizerState.Error(e.message ?: "Unknown error")
            logRepo.log("ERROR", "Optimization failed: ${e.message}")
        }
    }

    fun reset() { _state.value = OptimizerState.Idle }

    // ─── Boot Boost ───────────────────────────────────────────────────────────

    private val _bootState = MutableStateFlow<BootState>(BootState.Idle)
    val bootState: StateFlow<BootState> = _bootState.asStateFlow()

    fun bootOptimize() = viewModelScope.launch {
        val tier = settingsRepo.settings.first().enforcementTier
        _bootState.value = BootState.Running
        logRepo.log("ACTION", "Boot optimization started (Tier: $tier)")
        try {
            val result = bootOptimizer.run(tier, optimizerManager.shizukuService)
            _bootState.value = BootState.Done(result)
            logRepo.log(
                if (result.errors.isEmpty()) "SUCCESS" else "WARNING",
                "Boot optimization complete — restricted ${result.restrictedCount} apps"
            )
            result.errors.forEach { logRepo.log("ERROR", it) }
        } catch (e: Exception) {
            _bootState.value = BootState.Error(e.message ?: "Unknown error")
            logRepo.log("ERROR", "Boot optimization failed: ${e.message}")
        }
    }

    fun resetBoot() = viewModelScope.launch {
        _bootState.value = BootState.Running
        val tier = settingsRepo.settings.first().enforcementTier
        val ok = bootOptimizer.reset(tier, optimizerManager.shizukuService)
        _bootState.value = if (ok) BootState.Idle
        else BootState.Error("Reset failed — try again or reboot")
        if (ok) logRepo.log("INFO", "Boot optimization restrictions cleared")
    }

    fun resetBootState() { _bootState.value = BootState.Idle }
}