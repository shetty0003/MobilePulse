package com.mobilepulse.app.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.optimization.StorageInfo
import com.mobilepulse.app.ui.theme.*
import com.mobilepulse.app.ui.viewmodel.BootState
import com.mobilepulse.app.ui.viewmodel.OptimizerState
import com.mobilepulse.app.ui.viewmodel.OptimizerViewModel

@Composable
fun OptimizerSection(
    tier: EnforcementTier,
    onAskAi: ((String) -> Unit)? = null,
    vm: OptimizerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val storage by vm.storageInfo.collectAsStateWithLifecycle()

    val bootState by vm.bootState.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        storage?.let { StorageCard(it) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardColor)
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "RAM Optimizer",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            when (tier) {
                                EnforcementTier.ROOT     -> "Root — full cache + process kill"
                                EnforcementTier.SHIZUKU  -> "Shizuku — full cache + process kill"
                                EnforcementTier.STANDARD -> "Standard — soft kill + own cache only"
                            },
                            color = when (tier) {
                                EnforcementTier.ROOT     -> Danger
                                EnforcementTier.SHIZUKU  -> Warning
                                EnforcementTier.STANDARD -> Success
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    val rotation by rememberInfiniteTransition(label = "spin")
                        .animateFloat(
                            initialValue = 0f,
                            targetValue  = 360f,
                            animationSpec = infiniteRepeatable(
                                tween(1000, easing = LinearEasing)
                            ),
                            label = "rotation"
                        )

                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier
                                .size(26.dp)
                                .then(
                                    if (state is OptimizerState.Running)
                                        Modifier.rotate(rotation) else Modifier
                                )
                        )
                    }
                }

                AnimatedContent(targetState = state, label = "optimizer_state") { s ->
                    when (s) {
                        is OptimizerState.Idle -> {
                            Text(
                                "Tap Optimize to kill background processes and clear app caches.",
                                color = TextSub,
                                fontSize = 12.sp
                            )
                        }
                        is OptimizerState.Running -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Primary
                                )
                                Text(
                                    "Optimizing...",
                                    color = Primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        is OptimizerState.Done -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ResultRow("Apps killed",   "${s.result.appsKilled}",       Success)
                                ResultRow("Cache freed",   "${s.result.cacheCleanedMb} MB", Primary)
                                if (s.result.errors.isNotEmpty()) {
                                    ResultRow("Errors", "${s.result.errors.size}", Danger)
                                }
                            }
                        }
                        is OptimizerState.Error -> {
                            ErrorCard(message = s.message, onAskAi = onAskAi)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (state is OptimizerState.Done || state is OptimizerState.Error)
                                vm.reset()
                            else
                                vm.optimizeNow()
                        },
                        modifier = Modifier.weight(1f),
                        enabled  = state !is OptimizerState.Running,
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        when (state) {
                            is OptimizerState.Done, is OptimizerState.Error -> {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Optimize Again", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            is OptimizerState.Running -> {
                                Text("Optimizing...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            else -> {
                                Icon(Icons.Filled.Bolt, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Optimize Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (tier == EnforcementTier.STANDARD) {
                    Surface(
                        color = Warning.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Warning, null,
                                tint     = Warning,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Standard tier: only soft process trimming and own-app cache " +
                                        "cleaning available. Grant Shizuku or Root for full optimization.",
                                color      = Warning,
                                fontSize   = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        if (tier != EnforcementTier.STANDARD) {
            BootBoostCard(tier = tier, state = bootState, vm = vm, onAskAi = onAskAi)
        }
    }
}

@Composable
private fun BootBoostCard(
    tier: EnforcementTier,
    state: BootState,
    vm: OptimizerViewModel,
    onAskAi: ((String) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Boot Boost",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Prevents apps auto-starting on reboot",
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.RocketLaunch,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            AnimatedContent(targetState = state, label = "boot_state") { s ->
                when (s) {
                    is BootState.Idle -> Text(
                        "Strips auto-start and background permissions from social media and " +
                                "other known battery drainers. Whitelisted apps are skipped.",
                        color = TextSub,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    is BootState.Running -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Accent
                        )
                        Text(
                            "Applying restrictions...",
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    is BootState.Done -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResultRow("Apps restricted", "${s.result.restrictedCount}", Accent)
                        if (s.result.errors.isNotEmpty()) {
                            ResultRow("Errors", "${s.result.errors.size}", Danger)
                        }
                        Text(
                            "Changes persist until you tap Reset or reinstall the app.",
                            color = TextMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    is BootState.Error -> ErrorCard(message = s.message, onAskAi = onAskAi)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        when (state) {
                            is BootState.Done, is BootState.Error -> vm.resetBootState()
                            else -> vm.bootOptimize()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled  = state !is BootState.Running,
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    when (state) {
                        is BootState.Done -> {
                            Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Applied", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        is BootState.Error -> {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        is BootState.Running -> Text(
                            "Applying...", fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        else -> {
                            Icon(Icons.Filled.RocketLaunch, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Apply Boot Boost", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                if (state is BootState.Done) {
                    OutlinedButton(
                        onClick = { vm.resetBoot() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.RestartAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onAskAi: ((String) -> Unit)?) {
    Surface(
        color = Danger.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Warning, null,
                    tint     = Danger,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp)
                )
                Text(message, color = Danger, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
            if (onAskAi != null) {
                OutlinedButton(
                    onClick  = { onAskAi("I got this error in MobilePulse: $message — what caused it and how do I fix it?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ask AI to fix this", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StorageCard(info: StorageInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Storage", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(
                    "${info.usedPercent}% used",
                    color = when {
                        info.usedPercent > 85 -> Danger
                        info.usedPercent > 65 -> Warning
                        else -> Success
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { info.usedPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    info.usedPercent > 85 -> Danger
                    info.usedPercent > 65 -> Warning
                    else -> Success
                },
                trackColor = Border
            )
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StorageStat("Used",  "%.1f GB".format(info.usedGb),  TextPrimary)
                StorageStat("Free",  "%.1f GB".format(info.freeGb),  Success)
                StorageStat("Total", "%.1f GB".format(info.totalGb), TextSub)
            }
        }
    }
}

@Composable
private fun StorageStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = color)
        Text(label, fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
private fun ResultRow(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = TextSub)
        Text(value, fontSize = 13.sp, color = color, fontWeight = FontWeight.ExtraBold)
    }
}
