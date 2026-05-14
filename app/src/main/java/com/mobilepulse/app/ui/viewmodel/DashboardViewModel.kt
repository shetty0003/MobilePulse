package com.mobilepulse.app.ui.viewmodel

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilepulse.app.data.model.DashboardMetrics
import com.mobilepulse.app.data.repository.SettingsRepository
import com.mobilepulse.app.service.MonitoringService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(val metrics: DashboardMetrics) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    settingsRepo: SettingsRepository
) : ViewModel() {

    private val _hasUsageStatsPermission = MutableStateFlow(checkUsageStatsPermission())
    val hasUsageStatsPermission: StateFlow<Boolean> = _hasUsageStatsPermission.asStateFlow()

    val uiState: StateFlow<DashboardUiState> =
        MonitoringService.metricsFlow
            .map { metrics ->
                if (metrics != null) DashboardUiState.Success(metrics)
                else DashboardUiState.Loading
            }
            .catch { emit(DashboardUiState.Error(it.message ?: "Unknown error")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = DashboardUiState.Loading
            )

    val settings = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            while (true) {
                _hasUsageStatsPermission.value = checkUsageStatsPermission()
                delay(2000)
            }
        }
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.noteOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}