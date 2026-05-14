package com.mobilepulse.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilepulse.app.data.model.AiProvider
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.data.repository.SettingsRepository
import com.mobilepulse.app.enforcement.EnforcementManager
import com.mobilepulse.app.ui.theme.AppTheme
import com.mobilepulse.app.ui.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val enforcementManager: EnforcementManager,
    private val themeManager: ThemeManager
) : ViewModel() {

    val settings = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Theme ─────────────────────────────────────────────────────────────────
    val currentTheme: StateFlow<AppTheme> = themeManager.   themeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.SYSTEM)

    fun setTheme(theme: AppTheme) = viewModelScope.launch {
        themeManager.setTheme(theme)
    }

    // ── Tier ──────────────────────────────────────────────────────────────────
    private val _tierCheckResult = MutableStateFlow<Pair<EnforcementTier, Boolean>?>(null)
    val tierCheckResult: StateFlow<Pair<EnforcementTier, Boolean>?> = _tierCheckResult.asStateFlow()

    fun setTier(tier: EnforcementTier) = viewModelScope.launch {
        val available = enforcementManager.getAvailableTier()
        val canUse = when (tier) {
            EnforcementTier.STANDARD -> true
            EnforcementTier.SHIZUKU  -> available == EnforcementTier.SHIZUKU ||
                    available == EnforcementTier.ROOT
            EnforcementTier.ROOT     -> available == EnforcementTier.ROOT
        }
        if (canUse) {
            settingsRepo.setEnforcementTier(tier)
            if (tier == EnforcementTier.SHIZUKU) {
                enforcementManager.bindShizukuService()
            }
        }
        _tierCheckResult.value = Pair(tier, canUse)
    }

    fun clearTierCheckResult() { _tierCheckResult.value = null }

    fun setCpuThreshold(value: Int) = viewModelScope.launch {
        settingsRepo.setCpuThreshold(value)
    }

    fun setRamThreshold(value: Int) = viewModelScope.launch {
        settingsRepo.setRamThreshold(value)
    }

    fun setBatteryThreshold(value: Int) = viewModelScope.launch {
        settingsRepo.setBatteryThreshold(value)
    }

    fun setNotifications(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setNotificationsEnabled(enabled)
    }

    fun setAutomation(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setAutomationEnabled(enabled)
    }

    // ── Cache ─────────────────────────────────────────────────────────────────
    private val _cacheCleared = MutableStateFlow<Long?>(null)
    val cacheCleared: StateFlow<Long?> = _cacheCleared.asStateFlow()

    fun clearAppCache() = viewModelScope.launch(Dispatchers.IO) {
        val bytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()
        _cacheCleared.value = bytes
    }

    fun dismissCacheResult() { _cacheCleared.value = null }

    // ── AI API Keys ───────────────────────────────────────────────────────────
    val claudeApiKey: StateFlow<String> = settingsRepo.claudeApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val deepseekApiKey: StateFlow<String> = settingsRepo.deepseekApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val aiProvider: StateFlow<AiProvider> = settingsRepo.aiProviderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProvider.CLAUDE)

    fun setClaudeApiKey(key: String)   = viewModelScope.launch { settingsRepo.setClaudeApiKey(key) }
    fun setDeepseekApiKey(key: String) = viewModelScope.launch { settingsRepo.setDeepseekApiKey(key) }
    fun setAiProvider(p: AiProvider)   = viewModelScope.launch { settingsRepo.setAiProvider(p) }

    // ── Scheduled Auto-Clean ──────────────────────────────────────────────────
    val scheduledCleanEnabled: StateFlow<Boolean> = settingsRepo.settings
        .map { it.scheduledCleanEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setScheduledClean(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setScheduledClean(enabled)
    }
}