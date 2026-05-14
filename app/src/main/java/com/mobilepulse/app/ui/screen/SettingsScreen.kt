package com.mobilepulse.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mobilepulse.app.data.model.AiProvider
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.ui.navigation.Screen
import com.mobilepulse.app.ui.theme.*
import com.mobilepulse.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController? = null,
    vm: SettingsViewModel = hiltViewModel()
) {
    val settings        by vm.settings.collectAsStateWithLifecycle()
    val tierCheckResult by vm.tierCheckResult.collectAsStateWithLifecycle()
    val currentTheme    by vm.currentTheme.collectAsStateWithLifecycle()
    val cacheCleared        by vm.cacheCleared.collectAsStateWithLifecycle()
    val claudeApiKey        by vm.claudeApiKey.collectAsStateWithLifecycle()
    val deepseekApiKey      by vm.deepseekApiKey.collectAsStateWithLifecycle()
    val aiProvider          by vm.aiProvider.collectAsStateWithLifecycle()
    val scheduledClean      by vm.scheduledCleanEnabled.collectAsStateWithLifecycle()
    val snackbarHost     = remember { SnackbarHostState() }
    val scope            = rememberCoroutineScope()

    LaunchedEffect(cacheCleared) {
        cacheCleared?.let { bytes ->
            val mb = bytes / 1_048_576L
            val label = if (mb < 1L) "less than 1 MB" else "$mb MB"
            scope.launch { snackbarHost.showSnackbar("Cache cleared — freed $label") }
            vm.dismissCacheResult()
        }
    }

    LaunchedEffect(tierCheckResult) {
        tierCheckResult?.let { (tier, success) ->
            val msg = if (success) {
                when (tier) {
                    EnforcementTier.ROOT     -> "Root access confirmed — full control enabled"
                    EnforcementTier.SHIZUKU  -> "Shizuku connected — elevated access enabled"
                    EnforcementTier.STANDARD -> "Standard tier activated"
                }
            } else {
                when (tier) {
                    EnforcementTier.ROOT    -> "Root not available on this device"
                    EnforcementTier.SHIZUKU -> "Shizuku not running — install & start Shizuku app first"
                    else                    -> "${tier.name} not available"
                }
            }
            scope.launch { snackbarHost.showSnackbar(msg) }
            vm.clearTierCheckResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings", fontWeight = FontWeight.ExtraBold) })
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        val currentSettings = settings ?: run {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── APPEARANCE ───────────────────────────────────────────────────
            item { SettingsSectionTitle("APPEARANCE") }
            item {
                Card(
                    shape  = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("App Theme", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeChip(
                                label    = "Forest",
                                icon     = { Icon(Icons.Default.Forest, null, Modifier.size(15.dp)) },
                                selected = currentTheme == AppTheme.FOREST,
                                onClick  = { vm.setTheme(AppTheme.FOREST) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeChip(
                                label    = "Light",
                                icon     = { Icon(Icons.Default.LightMode, null, Modifier.size(15.dp)) },
                                selected = currentTheme == AppTheme.LIGHT,
                                onClick  = { vm.setTheme(AppTheme.LIGHT) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeChip(
                                label    = "System",
                                icon     = { Icon(Icons.Default.SettingsBrightness, null, Modifier.size(15.dp)) },
                                selected = currentTheme == AppTheme.SYSTEM,
                                onClick  = { vm.setTheme(AppTheme.SYSTEM) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeChip(
                                label    = "Dark",
                                icon     = { Icon(Icons.Default.DarkMode, null, Modifier.size(15.dp)) },
                                selected = currentTheme == AppTheme.DARK,
                                onClick  = { vm.setTheme(AppTheme.DARK) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── ENFORCEMENT TIER ─────────────────────────────────────────────
            item { SettingsSectionTitle("ENFORCEMENT TIER") }
            item {
                Card(
                    shape  = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor)
                ) {
                    Column {
                        EnforcementTier.entries.forEach { tier ->
                            val (icon, title, desc, color) = when (tier) {
                                EnforcementTier.STANDARD -> TierInfo(
                                    Icons.Default.Lock, "Standard",
                                    "Android APIs only — no special access needed",
                                    Success
                                )
                                EnforcementTier.SHIZUKU -> TierInfo(
                                    Icons.Default.Bolt, "Shizuku",
                                    "ADB-level access via Shizuku app — no root needed",
                                    Warning
                                )
                                EnforcementTier.ROOT -> TierInfo(
                                    Icons.Default.LockOpen, "Root",
                                    "Full root access via Magisk — maximum control",
                                    Danger
                                )
                            }
                            ListItem(
                                headlineContent = {
                                    Text(
                                        title,
                                        color = if (currentSettings.enforcementTier == tier)
                                            color else TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                supportingContent = {
                                    Text(desc, fontSize = 12.sp, color = TextSub)
                                },
                                leadingContent  = {
                                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                                },
                                trailingContent = {
                                    RadioButton(
                                        selected = currentSettings.enforcementTier == tier,
                                        onClick  = { vm.setTier(tier) }
                                    )
                                }
                            )
                            if (tier != EnforcementTier.ROOT) {
                                HorizontalDivider(color = Border)
                            }
                        }

                        if (currentSettings.enforcementTier != EnforcementTier.ROOT) {
                            Surface(
                                color = Primary.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(
                                    bottomStart = 24.dp, bottomEnd = 24.dp
                                )
                            ) {
                                Text(
                                    "To use Shizuku: install the Shizuku app from Play Store, " +
                                            "enable Developer Options, then start Shizuku via Wireless " +
                                            "Debugging or ADB. Tap Shizuku above once it's running.",
                                    modifier   = Modifier.padding(16.dp),
                                    fontSize   = 11.sp,
                                    color      = TextSub,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // ── ALERT THRESHOLDS ─────────────────────────────────────────────
            item { SettingsSectionTitle("ALERT THRESHOLDS") }
            item {
                Card(
                    shape  = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ThresholdStepper(
                            label    = "CPU Alert",
                            value    = currentSettings.cpuThreshold,
                            min = 10, max = 95, unit = "%",
                            onChange = { vm.setCpuThreshold(it) }
                        )
                        HorizontalDivider(color = Border)
                        ThresholdStepper(
                            label    = "RAM Alert",
                            value    = currentSettings.ramThreshold,
                            min = 10, max = 95, unit = "%",
                            onChange = { vm.setRamThreshold(it) }
                        )
                        HorizontalDivider(color = Border)
                        ThresholdStepper(
                            label    = "Battery Low",
                            value    = currentSettings.batteryThreshold,
                            min = 5, max = 50, unit = "%",
                            onChange = { vm.setBatteryThreshold(it) }
                        )
                    }
                }
            }

            // ── GENERAL ──────────────────────────────────────────────────────
            item { SettingsSectionTitle("GENERAL") }
            item {
                Card(
                    shape  = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor)
                ) {
                    Column {
                        ListItem(
                            headlineContent = {
                                Text("Notifications", fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = {
                                Text("Alert when thresholds are exceeded",
                                    fontSize = 12.sp, color = TextSub)
                            },
                            trailingContent = {
                                Switch(
                                    checked         = currentSettings.notificationsEnabled,
                                    onCheckedChange = { vm.setNotifications(it) }
                                )
                            }
                        )
                        HorizontalDivider(color = Border)
                        ListItem(
                            headlineContent = {
                                Text("Automation Engine", fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = {
                                Text("Run automated optimizations in background",
                                    fontSize = 12.sp, color = TextSub)
                            },
                            trailingContent = {
                                Switch(
                                    checked         = currentSettings.automationEnabled,
                                    onCheckedChange = { vm.setAutomation(it) }
                                )
                            }
                        )
                        HorizontalDivider(color = Border)
                        ListItem(
                            headlineContent = {
                                Text("Hourly Auto-Clean", fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = {
                                Text(
                                    "Every hour when you're away 5+ min: kills background apps, trims caches, freezes monitoring",
                                    fontSize = 12.sp, color = TextSub, lineHeight = 16.sp
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked         = scheduledClean,
                                    onCheckedChange = { vm.setScheduledClean(it) }
                                )
                            }
                        )
                    }
                }
            }

            // ── STORAGE ──────────────────────────────────────────────────────
            item { SettingsSectionTitle("STORAGE") }
            item {
                Card(
                    shape  = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor)
                ) {
                    ListItem(
                        headlineContent = {
                            Text("Clear App Cache", fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(
                                "Remove temporary files cached by MobilePulse",
                                fontSize = 12.sp, color = TextSub
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.DeleteSweep, null,
                                tint     = Danger,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = { vm.clearAppCache() },
                                border  = BorderStroke(1.dp, Danger),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text("Clear", color = Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }

            // ── AI ASSISTANT ─────────────────────────────────────────────────
            item { SettingsSectionTitle("AI ASSISTANT") }
            item {
                Card(
                    shape  = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        // Provider selector
                        Text("AI Provider", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AiProvider.entries.forEach { provider ->
                                val label = if (provider == AiProvider.CLAUDE) "Claude" else "DeepSeek"
                                FilterChip(
                                    selected = aiProvider == provider,
                                    onClick  = { vm.setAiProvider(provider) },
                                    label    = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment     = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp))
                                            Text(label, style = MaterialTheme.typography.labelMedium)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        HorizontalDivider(color = Border)

                        // Claude key
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Claude API Key", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (aiProvider == AiProvider.CLAUDE) TextPrimary else TextMuted)
                            Text("console.anthropic.com", fontSize = 11.sp, color = TextMuted)
                            ApiKeyField(value = claudeApiKey, onChange = { vm.setClaudeApiKey(it) }, placeholder = "sk-ant-...")
                        }

                        HorizontalDivider(color = Border)

                        // DeepSeek key
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("DeepSeek API Key", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (aiProvider == AiProvider.DEEPSEEK) TextPrimary else TextMuted)
                            Text("platform.deepseek.com", fontSize = 11.sp, color = TextMuted)
                            ApiKeyField(value = deepseekApiKey, onChange = { vm.setDeepseekApiKey(it) }, placeholder = "sk-...")
                        }

                        if (navController != null) {
                            OutlinedButton(
                                onClick  = { navController.navigate("ai") },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open AI Assistant", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // ── DEVELOPER TOOLS ──────────────────────────────────────────────
            item { SettingsSectionTitle("DEVELOPER TOOLS") }
            item {
                Card(
                    shape  = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor)
                ) {
                    ListItem(
                        headlineContent = {
                            Text("Shell Terminal", fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(
                                "Execute commands via Root, Shizuku, or standard shell",
                                fontSize = 12.sp, color = TextSub
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Terminal, null,
                                tint     = Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        trailingContent = {
                            if (navController != null) {
                                IconButton(onClick = { navController.navigate(Screen.Terminal.route) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Primary)
                                }
                            }
                        }
                    )
                }
            }

            // ── ABOUT ────────────────────────────────────────────────────────
            item { SettingsSectionTitle("ABOUT") }
            item {
                Card(
                    shape  = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor)
                ) {
                    Column {
                        ListItem(
                            headlineContent = {
                                Text("Version", fontWeight = FontWeight.SemiBold)
                            },
                            trailingContent = {
                                Text("1.0.0", color = TextSub, fontSize = 13.sp)
                            }
                        )
                        HorizontalDivider(color = Border)
                        ListItem(
                            headlineContent = {
                                Text("Active Tier", fontWeight = FontWeight.SemiBold)
                            },
                            trailingContent = {
                                Text(
                                    currentSettings.enforcementTier.name,
                                    color = when (currentSettings.enforcementTier) {
                                        EnforcementTier.ROOT     -> Danger
                                        EnforcementTier.SHIZUKU  -> Warning
                                        EnforcementTier.STANDARD -> Success
                                    },
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

private data class TierInfo(
    val icon:  ImageVector,
    val title: String,
    val desc:  String,
    val color: androidx.compose.ui.graphics.Color
)

@Composable
private fun ThemeChip(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.fillMaxWidth()
            ) {
                icon()
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        },
        modifier = modifier
    )
}

@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text, fontSize = 11.sp, color = TextMuted,
        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp
    )
}

@Composable
private fun ApiKeyField(value: String, onChange: (String) -> Unit, placeholder: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = { Text(placeholder, fontSize = 13.sp, color = TextMuted) },
        leadingIcon   = { Icon(Icons.Filled.Key, null, modifier = Modifier.size(18.dp)) },
        trailingIcon  = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    null,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine    = true,
        shape         = RoundedCornerShape(14.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Primary,
            unfocusedBorderColor = Border,
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
            cursorColor          = Primary
        )
    )
}

@Composable
fun ThresholdStepper(
    label: String,
    value: Int,
    min: Int = 10,
    max: Int = 95,
    unit: String = "%",
    onChange: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Min: $min$unit  Max: $max$unit", fontSize = 11.sp, color = TextMuted)
        }
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledIconButton(
                onClick  = { if (value > min) onChange(value - 5) },
                modifier = Modifier.size(32.dp),
                enabled  = value > min
            ) {
                Text("−", fontWeight = FontWeight.ExtraBold)
            }
            Text(
                "$value$unit",
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 16.sp,
                color      = Primary
            )
            FilledIconButton(
                onClick  = { if (value < max) onChange(value + 5) },
                modifier = Modifier.size(32.dp),
                enabled  = value < max
            ) {
                Text("+", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
