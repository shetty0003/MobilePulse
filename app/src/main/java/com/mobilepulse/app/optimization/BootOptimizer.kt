package com.mobilepulse.app.optimization

import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.data.repository.BootRestrictionRepository
import com.mobilepulse.app.enforcement.IShizukuService
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class BootOptimizeResult(
    val restrictedCount: Int,
    val errors: List<String> = emptyList()
)

@Singleton
class BootOptimizer @Inject constructor(
    private val bootRestrictionRepo: BootRestrictionRepository
) {
    suspend fun run(
        tier: EnforcementTier,
        shizukuService: IShizukuService?
    ): BootOptimizeResult = withContext(Dispatchers.IO) {
        if (tier == EnforcementTier.STANDARD) {
            return@withContext BootOptimizeResult(0, listOf("Requires Root or Shizuku"))
        }

        val restrictions = bootRestrictionRepo.getAllList()
        if (restrictions.isEmpty()) {
            return@withContext BootOptimizeResult(0, listOf("No apps added — add apps in the Boot Rules tab"))
        }

        var count = 0
        val errors = mutableListOf<String>()

        for (rule in restrictions) {
            val commands = buildList {
                add("cmd appops set ${rule.packageName} android:receive_boot_completed ignore")
                if (rule.restrictBackground) {
                    add("cmd appops set ${rule.packageName} RUN_IN_BACKGROUND deny")
                }
            }

            val ok = when (tier) {
                EnforcementTier.ROOT -> commands.all { Shell.cmd(it).exec().isSuccess }
                EnforcementTier.SHIZUKU -> {
                    if (shizukuService == null) {
                        errors.add("Shizuku service not bound")
                        return@withContext BootOptimizeResult(count, errors)
                    }
                    try {
                        commands.forEach { shizukuService.execute(it) }
                        true
                    } catch (_: Exception) { false }
                }
                EnforcementTier.STANDARD -> false
            }

            if (ok) count++ else errors.add("Failed: ${rule.appName}")
        }

        BootOptimizeResult(count, errors)
    }

    suspend fun reset(
        tier: EnforcementTier,
        shizukuService: IShizukuService?
    ): Boolean = withContext(Dispatchers.IO) {
        if (tier == EnforcementTier.STANDARD) return@withContext false
        val restrictions = bootRestrictionRepo.getAllList()
        restrictions.all { rule ->
            val commands = listOf(
                "cmd appops reset ${rule.packageName} android:receive_boot_completed",
                "cmd appops reset ${rule.packageName} RUN_IN_BACKGROUND"
            )
            when (tier) {
                EnforcementTier.ROOT -> commands.all { Shell.cmd(it).exec().isSuccess }
                EnforcementTier.SHIZUKU -> try {
                    shizukuService?.let { svc -> commands.forEach { svc.execute(it) } }
                    true
                } catch (_: Exception) { false }
                EnforcementTier.STANDARD -> false
            }
        }
    }
}
