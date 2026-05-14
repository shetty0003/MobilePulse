package com.mobilepulse.app.monitoring

import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.enforcement.IShizukuService
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AppRamUsage(val packageName: String, val ramMb: Long)

@Singleton
class RogueRamEngine @Inject constructor() {

    private val packagePattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    // ps -A -o RSS,NAME: RSS in KB, NAME is the process nice-name (= package name for apps).
    // Isolated/privileged child processes appear as "com.example.pkg:process_suffix" — strip the suffix.
    private val cmd = "ps -A -o RSS,NAME"

    suspend fun fetchAppMemory(
        tier: EnforcementTier,
        shizukuService: IShizukuService?
    ): List<AppRamUsage> = withContext(Dispatchers.IO) {
        val rawOutput = when (tier) {
            EnforcementTier.ROOT -> Shell.cmd(cmd).exec().out.joinToString("\n")
            EnforcementTier.SHIZUKU -> {
                if (shizukuService == null) return@withContext emptyList()
                try { shizukuService.execute(cmd) ?: "" }
                catch (_: Exception) { return@withContext emptyList() }
            }
            EnforcementTier.STANDARD -> return@withContext emptyList()
        }
        parsePsOutput(rawOutput)
    }

    suspend fun killPackage(
        packageName: String,
        tier: EnforcementTier,
        shizukuService: IShizukuService?
    ): Boolean = withContext(Dispatchers.IO) {
        val killCmd = "am force-stop $packageName"
        when (tier) {
            EnforcementTier.ROOT    -> Shell.cmd(killCmd).exec().isSuccess
            EnforcementTier.SHIZUKU -> try {
                shizukuService?.execute(killCmd)
                true
            } catch (_: Exception) { false }
            EnforcementTier.STANDARD -> false
        }
    }

    private fun parsePsOutput(raw: String): List<AppRamUsage> {
        val appList = mutableListOf<AppRamUsage>()

        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("RSS")) continue
            val tokens = trimmed.split(Regex("\\s+"))
            if (tokens.size < 2) continue

            val rssKb = tokens[0].toLongOrNull() ?: continue
            val mb    = rssKb / 1024L
            if (mb < 1L) continue

            // Strip isolated-process suffix (e.g. "com.foo.bar:worker" → "com.foo.bar")
            val pkg = tokens[1].substringBefore(":")
            if (!packagePattern.matches(pkg)) continue

            appList.add(AppRamUsage(pkg, mb))
        }

        return appList
            .groupBy { it.packageName }
            .map { (pkg, procs) -> AppRamUsage(pkg, procs.sumOf { it.ramMb }) }
            .sortedByDescending { it.ramMb }
    }
}
