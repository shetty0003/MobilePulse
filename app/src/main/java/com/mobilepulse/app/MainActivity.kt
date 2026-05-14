package com.mobilepulse.app

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.data.repository.SettingsRepository
import com.mobilepulse.app.enforcement.EnforcementManager
import com.mobilepulse.app.enforcement.ShizukuPermissionManager
import com.mobilepulse.app.service.MonitoringService
import com.mobilepulse.app.ui.navigation.MobilePulseNavGraph
import com.mobilepulse.app.ui.theme.MobilePulseTheme
import com.mobilepulse.app.worker.AutoCleanWorker
import com.mobilepulse.app.worker.AutomationWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var shizukuPermissionManager: ShizukuPermissionManager
    @Inject lateinit var enforcementManager: EnforcementManager
    @Inject lateinit var settingsRepository: SettingsRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled by system automatically
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkUsageAccessPermission()
        checkNotificationPermission()
        observeShizukuPermission()
        requestShizukuIfNeeded()

        // FIX: ContextCompat.startForegroundService handles API level check
        // — uses startForegroundService on API 26+ and startService below that
        ContextCompat.startForegroundService(
            this,
            Intent(this, MonitoringService::class.java)
        )

        AutomationWorker.schedule(this)
        observeScheduledCleanToggle()

        setContent {
            MobilePulseTheme {
                MobilePulseNavGraph()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Record the moment the user leaves — AutoCleanWorker reads this to check idle time
        lifecycleScope.launch {
            settingsRepository.setLastForegroundMs(System.currentTimeMillis())
        }
    }

    private fun observeScheduledCleanToggle() {
        settingsRepository.settings
            .map { it.scheduledCleanEnabled }
            .distinctUntilChanged()
            .onEach { enabled ->
                if (enabled) AutoCleanWorker.schedule(this)
                else         AutoCleanWorker.cancel(this)
            }
            .launchIn(lifecycleScope)
    }

    private fun observeShizukuPermission() {
        shizukuPermissionManager.permissionResults
            .onEach { granted ->
                if (granted) {
                    enforcementManager.bindShizukuIfNeeded()
                    Toast.makeText(this, "Shizuku permission granted ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Shizuku permission denied — falling back to Standard tier",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun requestShizukuIfNeeded() {
        lifecycleScope.launch {
            val tier = enforcementManager.getAvailableTier()
            if (tier == EnforcementTier.SHIZUKU) {
                if (shizukuPermissionManager.isGranted()) {
                    // Permission already granted from a prior session — bind directly.
                    // requestIfNeeded() would no-op and never emit a result, so the
                    // observeShizukuPermission() listener would never fire bindShizukuIfNeeded().
                    enforcementManager.bindShizukuIfNeeded()
                } else {
                    shizukuPermissionManager.requestIfNeeded()
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkUsageAccessPermission() {
        val usm   = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            System.currentTimeMillis() - 10_000L,
            System.currentTimeMillis()
        )
        if (stats.isNullOrEmpty()) {
            startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }
}