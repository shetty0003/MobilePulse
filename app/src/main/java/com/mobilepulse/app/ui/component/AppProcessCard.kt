package com.mobilepulse.app.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilepulse.app.data.model.AppProcess
import com.mobilepulse.app.data.model.RiskLevel
import com.mobilepulse.app.ui.theme.*

@Composable
fun AppProcessCard(
    process: AppProcess,
    onWhitelist: (AppProcess) -> Unit,
    onIgnore: (AppProcess) -> Unit,
    onStop: (AppProcess) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val riskColor = when (process.risk) {
        RiskLevel.HIGH   -> Danger
        RiskLevel.MEDIUM -> Warning
        RiskLevel.LOW    -> Success
    }

    val riskLabel = when (process.risk) {
        RiskLevel.HIGH   -> "HIGH"
        RiskLevel.MEDIUM -> "MED"
        RiskLevel.LOW    -> "LOW"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(Modifier.padding(16.dp)) {

            // Header row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Risk indicator dot
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = RoundedCornerShape(5.dp),
                    color = riskColor
                ) {}
                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        process.appName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Text(
                        process.packageName,
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1
                    )
                }

                // Risk badge
                Surface(
                    color = riskColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        riskLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = riskColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Expand toggle
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = TextSub,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // CPU and RAM bars
            MetricBar(
                label = "CPU",
                value = process.cpuPercent,
                max = 100,
                color = when {
                    process.cpuPercent > 60 -> Danger
                    process.cpuPercent > 30 -> Warning
                    else -> Success
                },
                displayText = "${process.cpuPercent}%"
            )
            Spacer(Modifier.height(6.dp))
            MetricBar(
                label = "RAM",
                value = process.ramMb.toInt(),
                max = 1024,
                color = when {
                    process.ramMb > 500 -> Danger
                    process.ramMb > 200 -> Warning
                    else -> Success
                },
                displayText = "${process.ramMb} MB"
            )

            // Expanded actions
            if (expanded) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                Spacer(Modifier.height(10.dp))

                if (process.isSystem) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning, null,
                            tint = Warning,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "System process — actions restricted",
                            fontSize = 11.sp,
                            color = Warning,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onWhitelist(process) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Shield, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Protect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onIgnore(process) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Block, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ignore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onStop(process) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Danger)
                        ) {
                            Icon(Icons.Filled.StopCircle, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBar(
    label: String,
    value: Int,
    max: Int,
    color: Color,
    displayText: String
) {
    val fraction = (value.toFloat() / max).coerceIn(0f, 1f)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = TextSub,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )
        Spacer(Modifier.width(6.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = Border
        )
        Spacer(Modifier.width(8.dp))
        Text(
            displayText,
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(48.dp)
        )
    }
}
