package com.mobilepulse.app.enforcement

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.monitoring.AppTracker
import com.mobilepulse.app.optimization.OptimizerManager
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

sealed class EnforcementResult {
    data class Success(val message: String) : EnforcementResult()
    data class Failure(val reason: String) : EnforcementResult()
    data class TierUnavailable(val tier: EnforcementTier) : EnforcementResult()
    data class ManualActionRequired(val message: String) : EnforcementResult()
}

enum class EnforcementAction {
    STOP_APP,
    REDUCE_PRIORITY,
    CLEAR_CACHE,
    NOTIFY
}

@Singleton
class EnforcementManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appTracker: AppTracker,
    private val optimizerManager: OptimizerManager
) {
    private var shizukuService: IShizukuService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = IShizukuService.Stub.asInterface(binder)
            shizukuService = service
            appTracker.shizukuService = service
            optimizerManager.shizukuService = service
            android.util.Log.d("MobilePulse", "Shizuku service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shizukuService = null
            appTracker.shizukuService = null
            optimizerManager.shizukuService = null
            android.util.Log.d("MobilePulse", "Shizuku service disconnected")
        }
    }

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShizukuService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shizuku_service")
        .debuggable(false)
        .version(1)

    // ─── Tier Detection ──────────────────────────────────────────────────────

    suspend fun getAvailableTier(): EnforcementTier = withContext(Dispatchers.IO) {
        when {
            isRootAvailable()    -> EnforcementTier.ROOT
            isShizukuAvailable() -> EnforcementTier.SHIZUKU
            else                 -> EnforcementTier.STANDARD
        }
    }

    private fun isRootAvailable(): Boolean {
        return try {
            val result = Shell.cmd("id").exec()
            result.isSuccess && result.out.any { it.contains("uid=0") }
        } catch (e: Exception) {
            false
        }
    }

    private fun isShizukuAvailable(): Boolean {
        return try {
            if (Shizuku.getVersion() < 11) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    // ─── Shizuku Permission Request ──────────────────────────────────────────

    fun requestShizukuPermission() {
        try {
            if (Shizuku.getVersion() >= 11) {
                Shizuku.requestPermission(1001)
            }
        } catch (e: Exception) {
            android.util.Log.e("MobilePulse", "Shizuku permission request failed: ${e.message}")
        }
    }

    // ─── Shizuku Service Binding ─────────────────────────────────────────────

    suspend fun bindShizukuService(): Boolean = suspendCancellableCoroutine { cont ->
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            cont.resume(true)
        } catch (e: Exception) {
            android.util.Log.e("MobilePulse", "Shizuku bind failed: ${e.message}")
            cont.resume(false)
        }
    }

    fun unbindShizukuService() {
        try {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        } catch (_: Exception) {}
    }

    /**
     * Called by MainActivity immediately after Shizuku permission is granted.
     * Binds the user service only if permission is confirmed and it isn't
     * already bound — safe to call multiple times.
     */
    fun bindShizukuIfNeeded() {
        if (shizukuService != null) {
            android.util.Log.d("MobilePulse", "Shizuku already bound — skipping")
            return
        }
        try {
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                android.util.Log.w("MobilePulse", "bindShizukuIfNeeded: permission not granted")
                return
            }
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            android.util.Log.d("MobilePulse", "Shizuku bind requested via bindShizukuIfNeeded")
        } catch (e: Exception) {
            android.util.Log.e("MobilePulse", "bindShizukuIfNeeded failed: ${e.message}")
        }
    }

    /** Exposed so AppCacheInspector can use the live service reference. */
    fun getShizukuService(): IShizukuService? = shizukuService

    // ─── Execute Action ───────────────────────────────────────────────────────

    suspend fun execute(
        action: EnforcementAction,
        packageName: String,
        tier: EnforcementTier
    ): EnforcementResult = withContext(Dispatchers.IO) {
        when (tier) {
            EnforcementTier.ROOT     -> executeRootAction(packageName, action)
            EnforcementTier.SHIZUKU  -> executeShizukuAction(packageName, action)
            EnforcementTier.STANDARD -> executeStandardAction(packageName, action)
        }
    }

    // ─── Root Actions ─────────────────────────────────────────────────────────

    private fun executeRootAction(
        packageName: String,
        action: EnforcementAction
    ): EnforcementResult {
        return try {
            when (action) {
                EnforcementAction.STOP_APP -> {
                    val result = Shell.cmd("am force-stop $packageName").exec()
                    if (result.isSuccess)
                        EnforcementResult.Success("Force-stopped $packageName via Root")
                    else
                        EnforcementResult.Failure("Root force-stop failed: ${result.err.joinToString()}")
                }
                EnforcementAction.REDUCE_PRIORITY -> {
                    val pid = Shell.cmd("pidof $packageName").exec().out
                        .firstOrNull()?.trim()
                    if (!pid.isNullOrEmpty()) {
                        Shell.cmd("renice +10 -p $pid").exec()
                        EnforcementResult.Success("Reduced priority of $packageName via Root")
                    } else {
                        EnforcementResult.Failure("Could not find PID for $packageName")
                    }
                }
                EnforcementAction.CLEAR_CACHE -> {
                    Shell.cmd("rm -rf /data/data/$packageName/cache/*").exec()
                    EnforcementResult.Success("Cleared cache for $packageName via Root")
                }
                EnforcementAction.NOTIFY ->
                    EnforcementResult.Success("Notified for $packageName")
            }
        } catch (e: Exception) {
            EnforcementResult.Failure("Root error: ${e.message}")
        }
    }

    // ─── Shizuku Actions ──────────────────────────────────────────────────────

    private fun executeShizukuAction(
        packageName: String,
        action: EnforcementAction
    ): EnforcementResult {
        if (!isShizukuAvailable())
            return EnforcementResult.TierUnavailable(EnforcementTier.SHIZUKU)

        val service = shizukuService
            ?: return EnforcementResult.Failure(
                "Shizuku service not bound — try switching tier off and on again"
            )

        return try {
            when (action) {
                EnforcementAction.STOP_APP -> {
                    service.execute("am force-stop $packageName")
                    EnforcementResult.Success("Force-stopped $packageName via Shizuku")
                }
                EnforcementAction.REDUCE_PRIORITY -> {
                    val pid = service.execute("pidof $packageName").trim()
                    if (pid.isNotEmpty()) {
                        service.execute("renice +10 -p $pid")
                        EnforcementResult.Success("Reduced priority of $packageName via Shizuku")
                    } else {
                        EnforcementResult.Failure("Could not find PID for $packageName")
                    }
                }
                EnforcementAction.CLEAR_CACHE -> {
                    service.execute("rm -rf /data/data/$packageName/cache/*")
                    EnforcementResult.Success("Cleared cache for $packageName via Shizuku")
                }
                EnforcementAction.NOTIFY ->
                    EnforcementResult.Success("Notified for $packageName")
            }
        } catch (e: Exception) {
            EnforcementResult.Failure("Shizuku error: ${e.message}")
        }
    }

    // ─── Standard Actions ─────────────────────────────────────────────────────

    private fun executeStandardAction(
        packageName: String,
        action: EnforcementAction
    ): EnforcementResult {
        return try {
            when (action) {
                EnforcementAction.STOP_APP,
                EnforcementAction.CLEAR_CACHE -> {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    ).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    EnforcementResult.ManualActionRequired(
                        "Opened settings for $packageName — please act manually"
                    )
                }
                EnforcementAction.REDUCE_PRIORITY ->
                    EnforcementResult.Failure(
                        "Priority reduction not supported in Standard tier"
                    )
                EnforcementAction.NOTIFY ->
                    EnforcementResult.Success("Notified for $packageName")
            }
        } catch (e: Exception) {
            EnforcementResult.Failure("Standard error: ${e.message}")
        }
    }
}