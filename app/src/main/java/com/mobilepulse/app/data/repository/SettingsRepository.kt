package com.mobilepulse.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.mobilepulse.app.data.model.AiProvider
import com.mobilepulse.app.data.model.AppSettings
import com.mobilepulse.app.data.model.EnforcementTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "mp_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val REFRESH    = intPreferencesKey("refresh_interval")
        val TIER       = stringPreferencesKey("enforcement_tier")
        val NOTIFY     = booleanPreferencesKey("notifications_enabled")
        val CPU_THRESH = intPreferencesKey("cpu_threshold")
        val RAM_THRESH = intPreferencesKey("ram_threshold")
        val BAT_THRESH = intPreferencesKey("battery_threshold")
        val AUTO_ON    = booleanPreferencesKey("automation_enabled")
        val CLAUDE_KEY        = stringPreferencesKey("claude_api_key")
        val DEEPSEEK_KEY      = stringPreferencesKey("deepseek_api_key")
        val AI_PROVIDER       = stringPreferencesKey("ai_provider")
        val SCHEDULED_CLEAN   = booleanPreferencesKey("scheduled_clean_enabled")
        val LAST_FOREGROUND   = longPreferencesKey("last_foreground_ms")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            refreshIntervalSeconds = prefs[Keys.REFRESH] ?: 2,
            enforcementTier = try {
                EnforcementTier.valueOf(prefs[Keys.TIER] ?: EnforcementTier.STANDARD.name)
            } catch (e: IllegalArgumentException) {
                EnforcementTier.STANDARD
            },
            notificationsEnabled = prefs[Keys.NOTIFY] ?: true,
            cpuThreshold         = prefs[Keys.CPU_THRESH] ?: 70,
            ramThreshold         = prefs[Keys.RAM_THRESH] ?: 80,
            batteryThreshold     = prefs[Keys.BAT_THRESH] ?: 20,
            automationEnabled    = prefs[Keys.AUTO_ON] ?: true,
            claudeApiKey         = prefs[Keys.CLAUDE_KEY] ?: "",
            deepseekApiKey       = prefs[Keys.DEEPSEEK_KEY] ?: "",
            aiProvider           = try {
                AiProvider.valueOf(prefs[Keys.AI_PROVIDER] ?: AiProvider.CLAUDE.name)
            } catch (e: IllegalArgumentException) {
                AiProvider.CLAUDE
            },
            scheduledCleanEnabled = prefs[Keys.SCHEDULED_CLEAN] ?: false
        )
    }

    val claudeApiKeyFlow: Flow<String> = context.dataStore.data.map { it[Keys.CLAUDE_KEY] ?: "" }
    val deepseekApiKeyFlow: Flow<String> = context.dataStore.data.map { it[Keys.DEEPSEEK_KEY] ?: "" }
    val aiProviderFlow: Flow<AiProvider> = context.dataStore.data.map {
        try { AiProvider.valueOf(it[Keys.AI_PROVIDER] ?: AiProvider.CLAUDE.name) }
        catch (e: IllegalArgumentException) { AiProvider.CLAUDE }
    }

    suspend fun setClaudeApiKey(key: String)   { context.dataStore.edit { it[Keys.CLAUDE_KEY]   = key } }
    suspend fun setDeepseekApiKey(key: String) { context.dataStore.edit { it[Keys.DEEPSEEK_KEY] = key } }
    suspend fun setAiProvider(provider: AiProvider) { context.dataStore.edit { it[Keys.AI_PROVIDER] = provider.name } }
    suspend fun setScheduledClean(enabled: Boolean) { context.dataStore.edit { it[Keys.SCHEDULED_CLEAN] = enabled } }
    suspend fun setLastForegroundMs(time: Long) { context.dataStore.edit { it[Keys.LAST_FOREGROUND] = time } }
    suspend fun getLastForegroundMs(): Long = context.dataStore.data.map { it[Keys.LAST_FOREGROUND] ?: 0L }.first()

    suspend fun setEnforcementTier(tier: EnforcementTier) {
        context.dataStore.edit { it[Keys.TIER] = tier.name }
    }

    suspend fun setCpuThreshold(value: Int) {
        context.dataStore.edit { it[Keys.CPU_THRESH] = value.coerceIn(10, 95) }
    }

    suspend fun setRamThreshold(value: Int) {
        context.dataStore.edit { it[Keys.RAM_THRESH] = value.coerceIn(10, 95) }
    }

    suspend fun setBatteryThreshold(value: Int) {
        context.dataStore.edit { it[Keys.BAT_THRESH] = value.coerceIn(5, 50) }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY] = enabled }
    }

    suspend fun setAutomationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_ON] = enabled }
    }

    suspend fun setRefreshInterval(seconds: Int) {
        context.dataStore.edit { it[Keys.REFRESH] = seconds.coerceIn(1, 60) }
    }

    // Kept for bulk updates if needed elsewhere
    suspend fun update(transform: suspend (AppSettings) -> AppSettings) {
        var current = AppSettings()
        context.dataStore.edit { prefs ->
            current = AppSettings(
                refreshIntervalSeconds = prefs[Keys.REFRESH] ?: 2,
                enforcementTier = try {
                    EnforcementTier.valueOf(prefs[Keys.TIER] ?: "STANDARD")
                } catch (e: IllegalArgumentException) {
                    EnforcementTier.STANDARD
                },
                notificationsEnabled = prefs[Keys.NOTIFY] ?: true,
                cpuThreshold         = prefs[Keys.CPU_THRESH] ?: 70,
                ramThreshold         = prefs[Keys.RAM_THRESH] ?: 80,
                batteryThreshold     = prefs[Keys.BAT_THRESH] ?: 20,
                automationEnabled    = prefs[Keys.AUTO_ON] ?: true
            )
        }
        val updated = transform(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.REFRESH]    = updated.refreshIntervalSeconds
            prefs[Keys.TIER]       = updated.enforcementTier.name
            prefs[Keys.NOTIFY]     = updated.notificationsEnabled
            prefs[Keys.CPU_THRESH] = updated.cpuThreshold
            prefs[Keys.RAM_THRESH] = updated.ramThreshold
            prefs[Keys.BAT_THRESH] = updated.batteryThreshold
            prefs[Keys.AUTO_ON]    = updated.automationEnabled
        }
    }
}