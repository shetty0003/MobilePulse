package com.mobilepulse.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.data.repository.SettingsRepository
import com.mobilepulse.app.enforcement.EnforcementManager
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TerminalLine(
    val text: String,
    val isInput: Boolean = false,
    val isError: Boolean = false
)

data class CommandSuggestion(
    val label: String,
    val command: String,
    val description: String
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val enforcer: EnforcementManager
) : ViewModel() {

    private val _lines = MutableStateFlow<List<TerminalLine>>(
        listOf(TerminalLine("MobilePulse Terminal — type 'help' or '/help' for commands", isInput = false))
    )
    val lines: StateFlow<List<TerminalLine>> = _lines.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val tier: StateFlow<EnforcementTier> = settingsRepo.settings
        .map { it.enforcementTier }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EnforcementTier.STANDARD)

    val allSuggestions: StateFlow<List<CommandSuggestion>> = tier
        .map { buildSuggestions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), buildSuggestions(EnforcementTier.STANDARD))

    private fun buildSuggestions(t: EnforcementTier): List<CommandSuggestion> = buildList {
        add(CommandSuggestion("ps",       "ps -A",                    "List all processes"))
        add(CommandSuggestion("meminfo",  "cat /proc/meminfo",        "Memory info"))
        add(CommandSuggestion("cpuinfo",  "cat /proc/cpuinfo",        "CPU info"))
        add(CommandSuggestion("df",       "df -h",                    "Disk usage"))
        add(CommandSuggestion("ls",       "ls /sdcard",               "List SD card"))
        add(CommandSuggestion("battery",  "dumpsys battery",          "Battery status"))
        add(CommandSuggestion("logcat",   "logcat -d -t 30",          "Last 30 log lines"))
        add(CommandSuggestion("packages", "pm list packages -3",      "3rd-party packages"))
        add(CommandSuggestion("net",      "cat /proc/net/dev",        "Network stats"))
        add(CommandSuggestion("date",     "date",                     "Current date/time"))
        add(CommandSuggestion("id",       "id",                       "Current UID/user"))
        add(CommandSuggestion("uname",    "uname -a",                 "Kernel version"))
        if (t == EnforcementTier.SHIZUKU || t == EnforcementTier.ROOT) {
            add(CommandSuggestion("kill-all",   "am kill-all",                     "Kill background processes"))
            add(CommandSuggestion("activities", "dumpsys activity processes",      "Running processes"))
            add(CommandSuggestion("wifi",       "settings get global wifi_on",     "WiFi state"))
            add(CommandSuggestion("force-stop", "am force-stop ",                  "Force stop <package>"))
            add(CommandSuggestion("trim",       "am send-trim-memory 0 MODERATE",  "Trim memory"))
        }
        if (t == EnforcementTier.ROOT) {
            add(CommandSuggestion("free",        "free -m",                   "Free memory (MB)"))
            add(CommandSuggestion("top",         "top -n 1 -b",               "CPU snapshot"))
            add(CommandSuggestion("mounts",      "cat /proc/mounts",          "Mount points"))
            add(CommandSuggestion("clr-cache",   "rm -rf /data/data/*/cache/*","Clear all caches (root)"))
        }
        add(CommandSuggestion("clear", "clear", "Clear the terminal screen"))
        add(CommandSuggestion("help",  "help",  "Show all available commands"))
    }

    fun execute(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank() || _isBusy.value) return

        append(TerminalLine("$ $trimmed", isInput = true))

        if (trimmed == "clear") { clearLines(); return }
        if (trimmed == "help" || trimmed == "/help") { showHelp(); return }

        _isBusy.value = true

        viewModelScope.launch {
            val currentTier = settingsRepo.settings.first()?.enforcementTier ?: EnforcementTier.STANDARD
            val output = runCommand(trimmed, currentTier)
            if (output.isNotEmpty()) {
                output.lines().forEach { line ->
                    append(TerminalLine(line, isError = line.startsWith("ERROR:") || line.startsWith("error:")))
                }
            }
            _isBusy.value = false
        }
    }

    private fun showHelp() {
        append(TerminalLine("── available commands ─────────────────────────"))
        allSuggestions.value.forEach { s ->
            append(TerminalLine("  ${s.command.padEnd(38)} ${s.description}"))
        }
        append(TerminalLine("───────────────────────────────────────────────"))
    }

    private suspend fun runCommand(cmd: String, tier: EnforcementTier): String = withContext(Dispatchers.IO) {
        try {
            when (tier) {
                EnforcementTier.ROOT -> {
                    val result = Shell.cmd(cmd).exec()
                    val out = result.out.joinToString("\n")
                    val err = result.err.joinToString("\n")
                    buildString {
                        if (out.isNotBlank()) append(out)
                        if (err.isNotBlank()) {
                            if (isNotEmpty()) append("\n")
                            append(err)
                        }
                    }
                }
                EnforcementTier.SHIZUKU -> {
                    val service = enforcer.getShizukuService()
                        ?: return@withContext "ERROR: Shizuku service not connected"
                    service.execute(cmd)
                }
                EnforcementTier.STANDARD -> {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                    val out  = process.inputStream.bufferedReader().readText()
                    val err  = process.errorStream.bufferedReader().readText()
                    process.waitFor()
                    process.destroy()
                    buildString {
                        if (out.isNotBlank()) append(out.trimEnd())
                        if (err.isNotBlank()) {
                            if (isNotEmpty()) append("\n")
                            append(err.trimEnd())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun clearLines() {
        _lines.value = listOf(TerminalLine("Screen cleared."))
    }

    private fun append(line: TerminalLine) {
        _lines.value = _lines.value + line
    }
}
