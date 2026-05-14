package com.mobilepulse.app.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mobilepulse.app.monitoring.AppRamUsage
import com.mobilepulse.app.ui.theme.*
import com.mobilepulse.app.ui.viewmodel.RamCleanState
import com.mobilepulse.app.ui.viewmodel.RamViewModel

private val chartColors = listOf(
    Primary,
    Accent,
    Success,
    Warning,
    Danger,
    Color(0xFF06B6D4),
    Color(0xFFF97316)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RamScreen(
    navController: NavController,
    vm: RamViewModel = hiltViewModel()
) {
    val apps        by vm.apps.collectAsStateWithLifecycle()
    val isLoading   by vm.isLoading.collectAsStateWithLifecycle()
    val pendingKill by vm.pendingKill.collectAsStateWithLifecycle()
    val cleanState  by vm.cleanState.collectAsStateWithLifecycle()

    val totalMb = apps.sumOf { it.ramMb }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("RAM Monitor", fontWeight = FontWeight.ExtraBold)
                        Text("Per-process memory usage",
                            style = MaterialTheme.typography.labelSmall, color = TextSub)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick  = { vm.refresh() },
                        enabled  = !isLoading && cleanState is RamCleanState.Idle
                    ) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = Primary)
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading && apps.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Reading kernel memory...", color = TextSub, fontSize = 13.sp)
                    }
                }
            }

            apps.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(Icons.Filled.Memory, null, tint = TextSub, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No data", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Root or Shizuku required to read per-process memory.\nTap Refresh after enabling.",
                            color = TextSub, fontSize = 13.sp,
                            textAlign = TextAlign.Center, lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { vm.refresh() },
                            colors  = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh")
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    item {
                        RamDonutChart(
                            apps       = apps.take(7),
                            totalMb    = totalMb,
                            cleanState = cleanState,
                            onClean    = { vm.deepClean() }
                        )
                    }

                    item {
                        Text(
                            "Top Processes by Resident Memory",
                            fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            color = TextSub, letterSpacing = 0.5.sp
                        )
                    }

                    items(apps, key = { it.packageName }) { app ->
                        RamAppCard(
                            app   = app,
                            total = totalMb,
                            onKill = { vm.confirmKill(app) }
                        )
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    pendingKill?.let { app ->
        AlertDialog(
            onDismissRequest = { vm.cancelKill() },
            title = { Text("Force Stop App", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "Force-stop ${app.packageName}?\n\nThis will terminate the app and free ~${app.ramMb} MB.",
                    color = TextSub
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.executeKill() }) {
                    Text("Force Stop", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelKill() }) { Text("Cancel") }
            },
            containerColor    = CardColor,
            titleContentColor = TextPrimary,
            textContentColor  = TextSub
        )
    }
}

// ─── Animated Donut Chart ─────────────────────────────────────────────────────

@Composable
private fun RamDonutChart(
    apps: List<AppRamUsage>,
    totalMb: Long,
    cleanState: RamCleanState,
    onClean: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isRunning = cleanState is RamCleanState.Running

    // Continuous spin used while cleaning
    val infiniteTransition = rememberInfiniteTransition(label = "chart_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue   = 0f,
        targetValue    = 360f,
        animationSpec  = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label          = "spin"
    )

    // Scale bounce on tap
    val scale by animateFloatAsState(
        targetValue    = if (isRunning) 1.04f else 1f,
        animationSpec  = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label          = "scale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Hint text above chart
            AnimatedContent(
                targetState  = cleanState,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label        = "hint"
            ) { state ->
                Text(
                    text = when (state) {
                        RamCleanState.Idle    -> "Tap chart to deep clean"
                        RamCleanState.Running -> "Cleaning RAM, cache & junk..."
                        is RamCleanState.Done -> "Clean complete"
                    },
                    fontSize   = 11.sp,
                    color      = when (state) {
                        RamCleanState.Idle    -> TextMuted
                        RamCleanState.Running -> Warning
                        is RamCleanState.Done -> Success
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(180.dp)
                    .clickable(
                        indication          = null,
                        interactionSource   = remember { MutableInteractionSource() },
                        enabled             = cleanState is RamCleanState.Idle
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClean()
                    }
            ) {
                // Donut arcs
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = if (isRunning) spinAngle else 0f
                            scaleX    = scale
                            scaleY    = scale
                        }
                ) {
                    var startAngle = -90f
                    apps.forEachIndexed { i, app ->
                        val sweep = (app.ramMb.toFloat() / totalMb) * 360f
                        drawArc(
                            color      = chartColors.getOrElse(i) { TextMuted },
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter  = false,
                            size       = Size(size.width, size.height),
                            style      = Stroke(width = 28.dp.toPx())
                        )
                        startAngle += sweep
                    }
                }

                // Center content — changes per state
                AnimatedContent(
                    targetState  = cleanState,
                    transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) },
                    label        = "center"
                ) { state ->
                    when (state) {
                        RamCleanState.Idle -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$totalMb MB",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize   = 20.sp,
                                    color      = TextPrimary
                                )
                                Text("tracked", fontSize = 10.sp, color = TextSub)
                                Spacer(Modifier.height(4.dp))
                                Icon(
                                    Icons.Filled.AutoFixHigh, null,
                                    tint     = Primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        RamCleanState.Running -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color    = Primary,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Cleaning",
                                    fontSize   = 11.sp,
                                    color      = Warning,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        is RamCleanState.Done -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.CheckCircle, null,
                                    tint     = Success,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                val freed = state.ramFreedMb + state.cacheFreedMb
                                Text(
                                    if (freed > 0) "+$freed MB" else "Done",
                                    fontSize   = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = Success
                                )
                                Text("freed", fontSize = 10.sp, color = TextSub)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Legend
            apps.chunked(2).forEachIndexed { rowIdx, rowApps ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowApps.forEachIndexed { colIdx, app ->
                        val colorIdx = rowIdx * 2 + colIdx
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color    = chartColors.getOrElse(colorIdx) { TextMuted },
                                shape    = RoundedCornerShape(3.dp),
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Spacer(Modifier.width(6.dp))
                            Text(
                                app.packageName.substringAfterLast("."),
                                fontSize = 11.sp, color = TextSub,
                                maxLines = 1, modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${app.ramMb}M",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                    if (rowApps.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// ─── Per-App Card ─────────────────────────────────────────────────────────────

@Composable
private fun RamAppCard(app: AppRamUsage, total: Long, onKill: () -> Unit) {
    val pct = if (total > 0) (app.ramMb * 100f / total).toInt().coerceIn(0, 100) else 0
    val barColor = when {
        pct >= 30 -> Danger
        pct >= 15 -> Warning
        else      -> Primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        app.packageName.substringAfterLast("."),
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                    Text(app.packageName, fontSize = 10.sp, color = TextMuted, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${app.ramMb} MB",
                        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                        color = barColor
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onKill, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Delete, "Kill",
                            tint     = Danger.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress    = { pct / 100f },
                modifier    = Modifier.fillMaxWidth().height(4.dp),
                color       = barColor,
                trackColor  = Border,
                strokeCap   = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(Modifier.height(2.dp))
            Text("$pct% of tracked", fontSize = 10.sp, color = TextMuted)
        }
    }
}
