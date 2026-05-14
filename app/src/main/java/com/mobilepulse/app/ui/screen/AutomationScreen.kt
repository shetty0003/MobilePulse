package com.mobilepulse.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilepulse.app.data.db.entity.AutomationRuleEntity
import com.mobilepulse.app.data.db.entity.BootRestrictionEntity
import com.mobilepulse.app.ui.theme.*
import com.mobilepulse.app.ui.viewmodel.AutomationViewModel
import com.mobilepulse.app.ui.viewmodel.BootRestrictionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    automationVm: AutomationViewModel      = hiltViewModel(),
    bootVm:       BootRestrictionViewModel = hiltViewModel()
) {
    val rules        by automationVm.rules.collectAsStateWithLifecycle()
    val restrictions by bootVm.restrictions.collectAsStateWithLifecycle()
    val installedApps by bootVm.installedApps.collectAsStateWithLifecycle()

    var selectedTab     by remember { mutableIntStateOf(0) }
    var showRuleSheet   by remember { mutableStateOf(false) }
    var showPickerSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Automation", fontWeight = FontWeight.ExtraBold) })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) showRuleSheet = true
                    else {
                        bootVm.loadInstalledApps()
                        showPickerSheet = true
                    }
                },
                containerColor = if (selectedTab == 0) Primary else Accent
            ) {
                Icon(Icons.Filled.Add, "Add", tint = OnPrimary)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    text     = { Text("Metric Rules", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    text     = { Text("Boot Rules", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            when (selectedTab) {
                0 -> MetricRulesTab(rules, automationVm)
                1 -> BootRulesTab(restrictions, bootVm)
            }
        }
    }

    if (showRuleSheet) {
        AddRuleSheet(
            onDismiss = { showRuleSheet = false },
            onSave    = { rule -> automationVm.insertRule(rule); showRuleSheet = false }
        )
    }

    if (showPickerSheet) {
        AppPickerSheet(
            installedApps    = installedApps,
            alreadyRestricted = restrictions.map { it.packageName }.toSet(),
            onAdd            = { pkg, name -> bootVm.addRestriction(pkg, name) },
            onDismiss        = { showPickerSheet = false }
        )
    }
}

// ─── Metric Rules Tab ────────────────────────────────────────────────────────

@Composable
private fun MetricRulesTab(rules: List<AutomationRuleEntity>, vm: AutomationViewModel) {
    if (rules.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Settings, null, tint = TextSub, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("No rules yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Tap + to add your first rule", color = TextSub)
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(rules, key = { _, r -> r.id }) { _, rule ->
                RuleCard(
                    rule     = rule,
                    onDelete = { vm.deleteRule(it) },
                    onToggle = { id, on -> vm.toggleRule(id, on) },
                    modifier = Modifier.animateItem()
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── Boot Rules Tab ──────────────────────────────────────────────────────────

@Composable
private fun BootRulesTab(
    restrictions: List<BootRestrictionEntity>,
    vm: BootRestrictionViewModel
) {
    if (restrictions.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.RocketLaunch, null, tint = TextSub, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("No boot rules yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Tap + to pick apps to block from auto-starting", color = TextSub,
                    fontSize = 13.sp)
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "These apps are blocked from starting on boot. " +
                    "Toggle \"Background\" to also deny background execution.",
                    color = TextSub, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(4.dp))
            }
            items(restrictions, key = { it.packageName }) { rule ->
                BootRestrictionCard(
                    rule     = rule,
                    onToggleBg = { vm.setRestrictBackground(rule.packageName, it) },
                    onDelete   = { vm.removeRestriction(rule.packageName) },
                    modifier   = Modifier.animateItem()
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun BootRestrictionCard(
    rule: BootRestrictionEntity,
    onToggleBg: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.RocketLaunch, null,
                        tint = Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(rule.appName, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Text(rule.packageName, fontSize = 10.sp, color = TextMuted, maxLines = 1)
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, null,
                        tint = Danger.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Border, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Block Background", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        "Deny app from re-launching in background",
                        color = TextSub, fontSize = 10.sp
                    )
                }
                Switch(
                    checked = rule.restrictBackground,
                    onCheckedChange = onToggleBg
                )
            }
        }
    }
}

// ─── App Picker Sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    installedApps: List<com.mobilepulse.app.ui.viewmodel.InstalledAppInfo>,
    alreadyRestricted: Set<String>,
    onAdd: (pkg: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }

    val available = remember(installedApps, alreadyRestricted, search) {
        installedApps
            .filter { it.packageName !in alreadyRestricted }
            .let { list ->
                if (search.isBlank()) list
                else list.filter {
                    it.appName.contains(search, ignoreCase = true) ||
                    it.packageName.contains(search, ignoreCase = true)
                }
            }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface) {
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
            Text("Add Boot Rule", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text("Tap an app to block it from auto-starting on boot",
                color = TextSub, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value         = search,
                onValueChange = { search = it },
                placeholder   = { Text("Search apps...", fontSize = 13.sp) },
                leadingIcon   = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        if (installedApps.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
            }
        } else if (available.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                Text(
                    if (search.isBlank()) "All apps already added" else "No apps match \"$search\"",
                    color = TextSub, fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(Modifier.padding(horizontal = 20.dp)) {
                items(available, key = { it.packageName }) { app ->
                    ListItem(
                        headlineContent = {
                            Text(app.appName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        },
                        supportingContent = {
                            Text(app.packageName, fontSize = 10.sp, color = TextMuted)
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Android, null,
                                tint = TextSub, modifier = Modifier.size(28.dp))
                        },
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onAdd(app.packageName, app.appName); onDismiss() }
                    )
                    HorizontalDivider(color = Border, thickness = 0.5.dp)
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

// ─── Metric Rule Card ────────────────────────────────────────────────────────

@Composable
fun RuleCard(
    rule: AutomationRuleEntity,
    onDelete: (AutomationRuleEntity) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val operatorLabel = when (rule.operator) {
        "GT"  -> ">"
        "LT"  -> "<"
        "GTE" -> ">="
        "LTE" -> "<="
        else  -> rule.operator
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(rule.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(
                        "${rule.metric} $operatorLabel ${rule.threshold.toInt()}%",
                        color = TextSub, fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                }
                Switch(checked = rule.isEnabled, onCheckedChange = { onToggle(rule.id, it) })
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RuleBadge(rule.action, Primary)
                RuleBadge(rule.responseType, Accent)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDelete(rule) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, null,
                        tint = Danger.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun RuleBadge(text: String, color: Color) {
    Surface(color = color.copy(0.12f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold
        )
    }
}

// ─── Add Metric Rule Sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleSheet(onDismiss: () -> Unit, onSave: (AutomationRuleEntity) -> Unit) {
    var step           by remember { mutableIntStateOf(1) }
    var name           by remember { mutableStateOf("") }
    var metric         by remember { mutableStateOf("CPU") }
    var operator       by remember { mutableStateOf("GT") }
    var threshold      by remember { mutableStateOf("70") }
    var action         by remember { mutableStateOf("NOTIFY") }
    var responseType   by remember { mutableStateOf("NOTIFY_ONLY") }
    var notifyOnTrigger by remember { mutableStateOf(true) }

    val metrics   = listOf("CPU", "RAM", "BATTERY", "TEMP")
    val operators = listOf("GT", "LT", "GTE", "LTE")
    val operatorLabels = mapOf(
        "GT" to "> (Above)", "LT" to "< (Below)",
        "GTE" to ">= (At or Above)", "LTE" to "<= (At or Below)"
    )
    val actions   = listOf("NOTIFY", "STOP", "CLEAR_CACHE", "REDUCE_PRIORITY")
    val responses = listOf("NOTIFY_ONLY", "SEMI_AUTO", "FULL_AUTO")

    val thresholdUnit  = if (metric == "TEMP") "°C" else "%"
    val operatorSymbol = when (operator) {
        "GT" -> ">"; "LT" -> "<"; "GTE" -> ">="; "LTE" -> "<="; else -> operator
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface) {
        Column(Modifier.padding(24.dp).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                (1..3).forEach { s ->
                    Surface(
                        shape = CircleShape,
                        color = if (step >= s) Primary else CardColor,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("$s", fontWeight = FontWeight.Bold,
                                color = if (step >= s) OnPrimary else TextMuted)
                        }
                    }
                    if (s < 3) Spacer(Modifier.width(12.dp))
                }
            }
            Spacer(Modifier.height(24.dp))

            when (step) {
                1 -> {
                    Text("Step 1: Metric & Threshold",
                        fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Rule Name") },
                        placeholder = { Text("e.g. High CPU Alert") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Metric", color = TextSub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    ChipRow(metrics, metric) { metric = it }
                    Spacer(Modifier.height(12.dp))
                    Text("Condition", color = TextSub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    ChipRow(operators, operator) { operator = it }
                    Text(operatorLabels[operator] ?: operator,
                        color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = { v ->
                            if (v.isEmpty() || v.all { it.isDigit() || it == '.' }) threshold = v
                        },
                        label = { Text("Threshold ($thresholdUnit)") },
                        suffix = { Text(thresholdUnit) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                2 -> {
                    Text("Step 2: Action", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Action", color = TextSub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    ChipRow(actions, action) { action = it }
                    Spacer(Modifier.height(12.dp))
                    Text("Response Type", color = TextSub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    ChipRow(responses, responseType) { responseType = it }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Notify on Trigger", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Send a notification when this rule fires",
                                color = TextSub, fontSize = 11.sp)
                        }
                        Switch(checked = notifyOnTrigger, onCheckedChange = { notifyOnTrigger = it })
                    }
                }
                3 -> {
                    Text("Step 3: Review", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardColor)) {
                        Column(Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReviewRow("Name",     name.ifEmpty { "$metric Rule" })
                            ReviewRow("Trigger",  "$metric $operatorSymbol $threshold$thresholdUnit")
                            ReviewRow("Action",   action)
                            ReviewRow("Response", responseType)
                            ReviewRow("Notify",   if (notifyOnTrigger) "Yes" else "No")
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { if (step == 1) onDismiss() else step-- },
                    modifier = Modifier.weight(1f)
                ) {
                    if (step > 1) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(if (step == 1) "Cancel" else "Back")
                }
                Button(
                    onClick = {
                        if (step < 3) step++
                        else onSave(AutomationRuleEntity(
                            name            = name.ifEmpty { "$metric Rule" },
                            metric          = metric,
                            operator        = operator,
                            threshold       = threshold.toFloatOrNull() ?: 70f,
                            action          = action,
                            responseType    = responseType,
                            notifyOnTrigger = notifyOnTrigger
                        ))
                    },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(if (step < 3) "Next" else "Save")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (step < 3) Icons.AutoMirrored.Filled.ArrowForward
                        else Icons.Filled.Check,
                        null, modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ReviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSub, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick  = { onSelect(opt) },
                label    = { Text(opt, fontSize = 11.sp) }
            )
        }
    }
}
