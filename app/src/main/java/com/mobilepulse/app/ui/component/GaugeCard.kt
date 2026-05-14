package com.mobilepulse.app.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilepulse.app.ui.theme.*

@Composable
fun GaugeCard(
    label: String,
    value: Int,
    unit: String = "%",
    modifier: Modifier = Modifier,
    colorOverride: Color? = null
) {
    val gaugeColor = colorOverride ?: when {
        value > 75 -> Danger
        value > 50 -> Warning
        else       -> Success
    }

    val animatedSweep by animateFloatAsState(
        targetValue   = (value / 100f).coerceIn(0f, 1f) * 270f,
        animationSpec = tween(durationMillis = 600),
        label         = "sweep"
    )

    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(
            modifier            = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSub)
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 9f
                    val stroke      = Stroke(strokeWidth, cap = StrokeCap.Round)
                    val inset       = strokeWidth / 2f
                    val arcRect     = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft     = Offset(inset, inset)
                    drawArc(color = Color.Gray.copy(alpha = 0.20f), startAngle = 135f,
                        sweepAngle = 270f, useCenter = false,
                        topLeft = topLeft, size = arcRect, style = stroke)
                    drawArc(color = gaugeColor, startAngle = 135f,
                        sweepAngle = animatedSweep, useCenter = false,
                        topLeft = topLeft, size = arcRect, style = stroke)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "$value", fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp, color = gaugeColor)
                    Text(text = unit, fontSize = 9.sp, color = TextMuted)
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress   = { (value / 100f).coerceIn(0f, 1f) },
                modifier   = Modifier.fillMaxWidth().height(4.dp),
                color      = gaugeColor,
                trackColor = Border
            )
        }
    }
}

