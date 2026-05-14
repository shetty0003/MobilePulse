package com.mobilepulse.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilepulse.app.data.db.entity.ActivityLogEntity
import com.mobilepulse.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogEntryCard(
    entry: ActivityLogEntity,
    modifier: Modifier = Modifier,
    hasAiKey: Boolean = false,
    onAskAi: ((String) -> Unit)? = null
) {
    val color: androidx.compose.ui.graphics.Color = when (entry.type) {
        "ACTION"     -> Success
        "AUTOMATION" -> Accent
        "WARNING"    -> Warning
        "ERROR"      -> Danger
        "SUCCESS"    -> Success
        else         -> Primary
    }

    val icon: ImageVector = when (entry.type) {
        "ACTION"     -> Icons.Filled.PlayArrow
        "AUTOMATION" -> Icons.Filled.Settings
        "WARNING"    -> Icons.Filled.Warning
        "ERROR"      -> Icons.Filled.Close
        "SUCCESS"    -> Icons.Filled.Check
        else         -> Icons.Filled.Info
    }

    val timeStr = remember(entry.timestamp) {
        SimpleDateFormat("dd MMM · HH:mm:ss", Locale.getDefault())
            .format(Date(entry.timestamp))
    }

    val isActionable = entry.type == "ERROR" || entry.type == "WARNING"
    var detailsExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color       = color,
                    start       = Offset(0f, 0f),
                    end         = Offset(0f, size.height),
                    strokeWidth = 6f
                )
            },
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(
            modifier = Modifier.padding(
                start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text          = entry.type,
                        color         = color,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(text = timeStr, color = TextMuted, fontSize = 10.sp)
                }
                entry.affectedApp?.let { app ->
                    Surface(color = Border, shape = MaterialTheme.shapes.small) {
                        Text(
                            text     = app,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color    = TextSub,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text       = entry.message,
                fontSize   = 13.sp,
                lineHeight = 18.sp,
                color      = TextPrimary
            )

            if (isActionable) {
                Spacer(Modifier.height(8.dp))

                if (hasAiKey && onAskAi != null) {
                    // ── Has AI: show Ask AI button ────────────────────────────
                    OutlinedButton(
                        onClick = {
                            onAskAi(
                                "I got this ${entry.type} in MobilePulse: \"${entry.message}\"" +
                                (entry.affectedApp?.let { " (app: $it)" } ?: "") +
                                " — what caused it and how do I fix it?"
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape  = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            width = 1.dp
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome, null,
                            tint     = Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Ask AI to fix this", fontSize = 12.sp, color = Primary)
                    }
                } else {
                    // ── No AI: collapsible details ────────────────────────────
                    TextButton(
                        onClick        = { detailsExpanded = !detailsExpanded },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors         = ButtonDefaults.textButtonColors(contentColor = TextSub)
                    ) {
                        Icon(
                            if (detailsExpanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (detailsExpanded) "Hide details" else "View full info",
                            fontSize = 12.sp
                        )
                    }

                    AnimatedVisibility(
                        visible = detailsExpanded,
                        enter   = expandVertically(),
                        exit    = shrinkVertically()
                    ) {
                        Surface(
                            color  = color.copy(alpha = 0.06f),
                            shape  = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                DetailRow("Type",      entry.type)
                                DetailRow("Time",      SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp)))
                                entry.affectedApp?.let { DetailRow("App", it) }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text       = entry.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = 11.sp,
                                    color      = color,
                                    lineHeight = 15.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Add an AI API key in Settings to get automated fix suggestions.",
                                    fontSize   = 10.sp,
                                    color      = TextMuted,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.padding(bottom = 2.dp)) {
        Text(
            "$label: ",
            fontSize   = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color      = TextSub
        )
        Text(value, fontSize = 10.sp, color = TextPrimary)
    }
}
