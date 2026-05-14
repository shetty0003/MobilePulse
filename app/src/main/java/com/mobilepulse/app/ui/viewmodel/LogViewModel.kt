package com.mobilepulse.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilepulse.app.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepo: LogRepository,
    private val settingsRepo: com.mobilepulse.app.data.repository.SettingsRepository
) : ViewModel() {

    val hasAiKey: StateFlow<Boolean> = settingsRepo.settings
        .map { it.claudeApiKey.isNotBlank() || it.deepseekApiKey.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _filter = MutableStateFlow("ALL")
    val filter: StateFlow<String> = _filter.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val logs = _filter.flatMapLatest { f ->
        when {
            f == "ALL"    -> logRepo.getAllLogs()
            f == "LOGCAT" -> emptyFlow()
            else          -> logRepo.getLogsByType(f)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _logcatLines = MutableStateFlow<List<String>>(emptyList())
    val logcatLines: StateFlow<List<String>> = _logcatLines.asStateFlow()

    fun setFilter(f: String) {
        _filter.value = f
        if (f == "LOGCAT") loadLogcat()
    }

    fun clearLogs() = viewModelScope.launch { logRepo.clearAll() }

    fun loadLogcat(lines: Int = 200) = viewModelScope.launch {
        _logcatLines.value = withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-d", "-t", lines.toString(), "-v", "time")
                )
                BufferedReader(InputStreamReader(process.inputStream))
                    .readLines()
                    .reversed()
            } catch (e: Exception) {
                listOf("Error reading logcat: ${e.message}")
            }
        }
    }

    // ── JSON export ───────────────────────────────────────────────────────────

    fun exportLogs(context: Context) = viewModelScope.launch {
        val json = Json.encodeToString(logs.value)
        val file = File(context.cacheDir, "mobilepulse_logs.json")
        try {
            FileOutputStream(file).use { it.write(json.toByteArray()) }
            shareFile(context, file, "application/json", "Export Logs as JSON")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    fun exportLogsCsv(context: Context) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            try {
                val file    = File(context.cacheDir, "mobilepulse_logs.csv")
                val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                FileOutputStream(file).bufferedWriter().use { writer ->
                    writer.write("id,timestamp,type,message,affectedApp\n")
                    logs.value.forEach { log ->
                        val ts  = dateFmt.format(Date(log.timestamp))
                        val msg = log.message.replace("\"", "\"\"")
                        // FIX: field is affectedApp not packageName
                        val app = log.affectedApp ?: ""
                        writer.write(
                            "${log.id},\"$ts\",\"${log.type}\",\"$msg\",\"$app\"\n"
                        )
                    }
                }
                shareFile(context, file, "text/csv", "Export Logs as CSV")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Share helper ──────────────────────────────────────────────────────────

    private fun shareFile(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String
    ) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}