package com.mobilepulse.app.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.mobilepulse.app.ui.component.OptimizerSection
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mobilepulse.app.ui.navigation.Screen
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.ui.theme.*
import com.mobilepulse.app.ui.viewmodel.DashboardUiState
import com.mobilepulse.app.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, vm: DashboardViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hasPermission by vm.hasUsageStatsPermission.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MobilePulse", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "System Monitor",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSub
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(Success.copy(alpha = alpha))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE", color = Success, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is DashboardUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

            is DashboardUiState.Error ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(s.message, color = Danger)
                }

            is DashboardUiState.Success -> {
                val currentSettings = settings ?: run {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Scaffold
                }

                val m = s.metrics
                val ramPct = if (m.ramTotalMb > 0)
                    ((m.ramUsedMb * 100) / m.ramTotalMb).toInt() else 0

                val cpuThresh = currentSettings.cpuThreshold
                val ramThresh = currentSettings.ramThreshold
                val batThresh = currentSettings.batteryThreshold

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    item {
                        StatusCard(
                            tier = currentSettings.enforcementTier,
                            isPermissionGranted = hasPermission,
                            onFixPermission = {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                )
                            },
                            onSettings = { navController.navigate(Screen.Settings.route) }
                        )
                    }

                    item {
                        AnimatedVisibility(
                            visible = m.cpuTotal > cpuThresh,
                            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                            exit  = shrinkVertically(tween(200)) + fadeOut(tween(200))
                        ) {
                            AlertCard("CPU high: ${m.cpuTotal}% (threshold: ${cpuThresh}%)", Danger)
                        }
                    }
                    item {
                        AnimatedVisibility(
                            visible = ramPct > ramThresh,
                            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                            exit  = shrinkVertically(tween(200)) + fadeOut(tween(200))
                        ) {
                            AlertCard("RAM high: ${ramPct}% (threshold: ${ramThresh}%)", Warning)
                        }
                    }
                    item {
                        AnimatedVisibility(
                            visible = m.batteryLevel < batThresh && !m.batteryCharging,
                            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                            exit  = shrinkVertically(tween(200)) + fadeOut(tween(200))
                        ) {
                            AlertCard(
                                "Battery low: ${m.batteryLevel}% (threshold: ${batThresh}%)",
                                Danger, Icons.Filled.BatteryAlert
                            )
                        }
                    }
                    item {
                        AnimatedVisibility(
                            visible = m.batteryTemp > 40f,
                            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                            exit  = shrinkVertically(tween(200)) + fadeOut(tween(200))
                        ) {
                            AlertCard("Overheating: ${m.batteryTemp}°C", Warning, Icons.Filled.Thermostat)
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GaugeCard("CPU", m.cpuTotal, Modifier.weight(1f)) {
                                navController.navigate("apps/CPU")
                            }
                            GaugeCard("RAM", ramPct, Modifier.weight(1f)) {
                                navController.navigate("apps/RAM")
                            }
                            GaugeCard("Battery", m.batteryLevel, Modifier.weight(1f))
                        }
                    }

                    item { SectionTitle("CPU Cores") }
                    item {
                        if (m.cpuCores.isEmpty()) {
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CardColor)
                            ) {
                                Text(
                                    "Core data unavailable",
                                    modifier = Modifier.padding(16.dp),
                                    color = TextSub
                                )
                            }
                        } else {
                            MetricCard {
                                m.cpuCores.forEachIndexed { index, usage ->
                                    StatRow(
                                        "Core $index", "$usage%",
                                        color = when {
                                            usage > cpuThresh        -> Danger
                                            usage > cpuThresh / 2    -> Warning
                                            else                     -> Success
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item { SectionTitle("Memory Details") }
                    item {
                        MetricCard {
                            StatRow("Total", "${m.ramTotalMb} MB")
                            StatRow("Used",  "${m.ramUsedMb} MB",
                                color = if (ramPct > ramThresh) Danger else TextPrimary)
                            StatRow("Free",  "${m.ramFreeMb} MB")
                            StatRow("Usage", "$ramPct%",
                                color = if (ramPct > ramThresh) Danger else TextPrimary)
                        }
                    }

                    item {
                        TextButton(
                            onClick  = { navController.navigate(Screen.Ram.route) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Per-App RAM Breakdown", color = Primary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward, null,
                                tint = Primary, modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    item { SectionTitle("Battery Details") }
                    item {
                        MetricCard {
                            StatRow(
                                "Level", "${m.batteryLevel}%",
                                color = when {
                                    m.batteryLevel < batThresh      -> Danger
                                    m.batteryLevel < batThresh + 10 -> Warning
                                    else                            -> Success
                                }
                            )
                            StatRow("Status", if (m.batteryCharging) "Charging" else "Discharging")
                            StatRow(
                                "Temp", "${m.batteryTemp}°C",
                                color = if (m.batteryTemp > 40f) Danger else TextPrimary
                            )
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }

                    item { SectionTitle("Optimizer") }
                    item {
                        OptimizerSection(
                            tier     = currentSettings.enforcementTier,
                            onAskAi  = { query ->
                                navController.navigate("ai?query=${Uri.encode(query)}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GaugeCard(label: String, value: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val targetColor = when {
        value > 75 -> Danger
        value > 50 -> Warning
        else       -> Success
    }
    val color by animateColorAsState(targetColor, tween(500), label = "gauge_color")
    val animVal by animateFloatAsState(
        targetValue = value / 100f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "gauge"
    )
    val animatedValue by animateIntAsState(value, tween(500), label = "counter")
    val scale by animateFloatAsState(
        targetValue = if (value > 80) 1.05f else 1.0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = TextSub, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(10f, cap = StrokeCap.Round)
                    val inset  = 5f
                    val rect   = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(Color.Gray.copy(0.15f), 135f, 270f, false,
                        topLeft = Offset(inset, inset), size = rect, style = stroke)
                    drawArc(color, 135f, 270f * animVal, false,
                        topLeft = Offset(inset, inset), size = rect, style = stroke)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$animatedValue", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = color)
                    Text("%", fontSize = 10.sp, color = TextMuted)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animVal },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = color,
                trackColor = Border
            )
        }
    }
}

@Composable
fun StatusCard(
    tier: EnforcementTier,
    isPermissionGranted: Boolean,
    onFixPermission: () -> Unit,
    onSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Shield, null, tint = Primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Tier: ${tier.name}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                if (!isPermissionGranted) {
                    Text("Usage Access Required", color = Warning,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("All systems nominal", color = Success, fontSize = 12.sp)
                }
            }
            if (!isPermissionGranted) {
                Button(
                    onClick = onFixPermission,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Warning, contentColor = Color.Black
                    )
                ) {
                    Text("FIX", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            } else {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, null, tint = TextSub)
                }
            }
        }
    }
}

@Composable
fun AlertCard(
    msg: String,
    color: Color,
    icon: ImageVector = Icons.Filled.Warning
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Text(msg, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun MetricCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun StatRow(label: String, value: String, color: Color = TextPrimary) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSub, fontSize = 13.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(color = Border, thickness = 0.5.dp)
}
