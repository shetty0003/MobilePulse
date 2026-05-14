package com.mobilepulse.app.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mobilepulse.app.ui.component.LogEntryCard
import com.mobilepulse.app.ui.theme.*
import com.mobilepulse.app.ui.viewmodel.LogViewModel

private val LOG_FILTERS = listOf(
    "ALL", "INFO", "ACTION", "AUTOMATION", "SUCCESS", "WARNING", "ERROR", "LOGCAT"
)

private fun logLevelColor(line: String): Color = when {
    line.contains(" E ") || line.contains(" E/") -> Color(0xFFF85149)
    line.contains(" W ") || line.contains(" W/") -> Color(0xFFD29922)
    line.contains(" I ") || line.contains(" I/") -> Color(0xFFF0F6FC)
    line.contains(" D ") || line.contains(" D/") -> Color(0xFF8B949E)
    line.contains(" V ") || line.contains(" V/") -> Color(0xFF484F58)
    else                                          -> Color(0xFF8B949E)
}

private fun logLevelTag(line: String): String = when {
    line.contains(" E ") || line.contains(" E/") -> "E"
    line.contains(" W ") || line.contains(" W/") -> "W"
    line.contains(" I ") || line.contains(" I/") -> "I"
    line.contains(" D ") || line.contains(" D/") -> "D"
    line.contains(" V ") || line.contains(" V/") -> "V"
    else                                          -> "?"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(navController: NavController, vm: LogViewModel = hiltViewModel()) {
    val logs        by vm.logs.collectAsStateWithLifecycle()
    val filter      by vm.filter.collectAsStateWithLifecycle()
    val logcatLines by vm.logcatLines.collectAsStateWithLifecycle()
    val hasAiKey    by vm.hasAiKey.collectAsStateWithLifecycle()
    val context     = LocalContext.current
    val listState   = rememberLazyListState()

    var exportMenuExpanded by remember { mutableStateOf(false) }
    var selectedLine       by remember { mutableStateOf<String?>(null) }
    var searchQuery        by remember { mutableStateOf("") }

    fun navigateToAi(query: String) {
        navController.navigate("ai?query=${Uri.encode(query)}")
    }

    LaunchedEffect(logcatLines.size) {
        if (logcatLines.isNotEmpty() && filter == "LOGCAT") {
            listState.animateScrollToItem(0)
        }
    }

    // Detail dialog for a tapped logcat line
    selectedLine?.let { line ->
        val isError = line.contains(" E ") || line.contains(" E/")
        Dialog(onDismissRequest = { selectedLine = null }) {
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface02)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Log Detail", fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text       = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 11.sp,
                        color      = logLevelColor(line),
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        if (isError && hasAiKey) {
                            OutlinedButton(
                                onClick = {
                                    selectedLine = null
                                    navigateToAi("I got this error in my Android logs: \"$line\" — what caused it and how do I fix it?")
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null,
                                    tint = Primary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ask AI to fix", color = Primary, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        TextButton(onClick = { selectedLine = null }) {
                            Text("Close", color = Primary)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            Column(Modifier.fillMaxWidth().background(Background)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                    title = {
                        Column {
                            Text("Logs", fontWeight = FontWeight.ExtraBold)
                            Text(
                                if (filter == "LOGCAT") "${logcatLines.size} lines"
                                else "${logs.size} entries",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSub
                            )
                        }
                    },
                    actions = {
                        if (filter == "LOGCAT") {
                            IconButton(onClick = { vm.loadLogcat() }) {
                                Icon(Icons.Default.Refresh, "Refresh", tint = Primary)
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { exportMenuExpanded = true },
                                enabled = logs.isNotEmpty() && filter != "LOGCAT"
                            ) {
                                Icon(Icons.Default.Share, "Export", tint = Primary)
                            }
                            DropdownMenu(
                                expanded         = exportMenuExpanded,
                                onDismissRequest = { exportMenuExpanded = false },
                                containerColor   = Surface02
                            ) {
                                DropdownMenuItem(
                                    text    = { Text("Export as JSON") },
                                    onClick = { exportMenuExpanded = false; vm.exportLogs(context) }
                                )
                                DropdownMenuItem(
                                    text    = { Text("Export as CSV") },
                                    onClick = { exportMenuExpanded = false; vm.exportLogsCsv(context) }
                                )
                            }
                        }

                        IconButton(onClick = { vm.clearLogs() }) {
                            Icon(Icons.Default.Delete, "Clear", tint = Danger)
                        }
                    }
                )

                LazyRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(LOG_FILTERS) { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick  = { vm.setFilter(f) },
                            label    = { Text(f, fontSize = 11.sp) }
                        )
                    }
                }

                if (filter == "LOGCAT") {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder   = { Text("Filter logcat...", fontSize = 12.sp) },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .height(48.dp),
                        singleLine    = true,
                        textStyle     = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 12.sp
                        )
                    )
                }

                HorizontalDivider(color = DividerColor)
            }
        }
    ) { padding ->

        if (filter == "LOGCAT") {
            val filteredLines = if (searchQuery.isBlank()) logcatLines
            else logcatLines.filter { it.contains(searchQuery, ignoreCase = true) }

            if (filteredLines.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (logcatLines.isEmpty()) {
                            CircularProgressIndicator(color = Primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Loading logcat...", color = TextSub)
                        } else {
                            Text("No lines match \"$searchQuery\"", color = TextSub)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state    = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color(0xFF0D1117))
                ) {
                    itemsIndexed(filteredLines) { _, line ->
                        val color = logLevelColor(line)
                        val tag   = logLevelTag(line)

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedLine = line }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text       = tag,
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = color,
                                modifier   = Modifier
                                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                    .width(12.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text       = line,
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 10.sp,
                                color      = color,
                                lineHeight = 14.sp,
                                modifier   = Modifier.fillMaxWidth()
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.03f), thickness = 0.5.dp)
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

        } else {
            if (logs.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History, null,
                            tint = TextSub,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (filter == "ALL") "No log entries yet"
                            else "No $filter entries",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp
                        )
                        Text(
                            "Actions taken by MobilePulse appear here",
                            color    = TextSub,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(logs, key = { it.id }) {
                        LogEntryCard(
                            entry     = it,
                            modifier  = Modifier.animateItem(),
                            hasAiKey  = hasAiKey,
                            onAskAi   = if (hasAiKey) { query -> navigateToAi(query) } else null
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
