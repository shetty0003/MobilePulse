package com.mobilepulse.app.enforcement

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encapsulates the full Shizuku runtime-permission request flow.
 *
 * Usage in MainActivity:
 *
 *   lifecycleScope.launch {
 *       shizukuPermissionManager.permissionResults.collect { granted ->
 *           if (granted) viewModel.onShizukuPermissionGranted()
 *           else         viewModel.onShizukuPermissionDenied()
 *       }
 *   }
 *
 *   // Call when the user selects SHIZUKU tier or on app start:
 *   shizukuPermissionManager.requestIfNeeded()
 */
@Singleton
class ShizukuPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ShizukuPermission"
        private const val REQUEST_CODE = 101
    }

    // Emits true/false each time a permission result arrives
    val permissionResults: Flow<Boolean> = callbackFlow {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Permission result: granted=$granted")
                trySend(granted)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        awaitClose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    /**
     * Returns true if permission is already granted (no request needed).
     * Returns false if Shizuku is not running or permission not yet granted.
     */
    fun isGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: IllegalStateException) {
            // Shizuku not running
            false
        }
    }

    /**
     * Requests Shizuku permission only if not already granted.
     * Results are emitted via [permissionResults].
     *
     * Safe to call multiple times — will no-op if already granted.
     */
    fun requestIfNeeded() {
        if (isGranted()) {
            Log.d(TAG, "Shizuku permission already granted — skipping request")
            return
        }
        try {
            if (Shizuku.isPreV11()) {
                // Shizuku < v11: permission must be granted via ADB
                Log.w(TAG, "Shizuku pre-v11: permission must be granted via ADB")
            } else {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Shizuku not running — cannot request permission", e)
        }
    }

    /**
     * Returns true if the Shizuku service process is alive (binder reachable).
     */
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }
}