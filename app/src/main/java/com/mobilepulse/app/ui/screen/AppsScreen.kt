package com.mobilepulse.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilepulse.app.ui.component.AppProcessCard
import com.mobilepulse.app.ui.theme.*
import com.mobilepulse.app.ui.viewmodel.AppsViewModel
import com.mobilepulse.app.ui.viewmodel.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    vm:     AppsViewModel = hiltViewModel(),
    filter: String        = "NONE"
) {
    val apps    by vm.sortedApps.collectAsStateWithLifecycle()
    val sortBy  by vm.sortBy.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error   by vm.error.collectAsStateWithLifecycle()

    LaunchedEffect(filter) {
        when (filter) {
            "CPU" -> vm.setSortOrder(SortOrder.CPU)
            "RAM" -> vm.setSortOrder(SortOrder.RAM)
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                title = {
                    Column {
                        Text("Background Processes", fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (loading) "Loading..."
                            else "${apps.size} processes",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSub
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = Primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Sort chips ──────────────────────────────────────────────
            LazyRow(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SortOrder.entries) { order ->
                    FilterChip(
                        selected = sortBy == order,
                        onClick  = { vm.setSortOrder(order) },
                        label    = { Text(order.name, fontSize = 11.sp) }
                    )
                }
            }

            // ── Error banner ────────────────────────────────────────────
            AnimatedVisibility(visible = error != null) {
                error?.let { msg ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Warning.copy(alpha = 0.15f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(msg, color = Warning, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.refresh() }) {
                            Text("Retry", color = Warning, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Loading ─────────────────────────────────────────────────
            if (loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Reading processes...", color = TextSub, fontSize = 13.sp)
                    }
                }
                return@Scaffold
            }

            // ── Empty state ─────────────────────────────────────────────
            if (apps.isEmpty() && error == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhoneAndroid, null,
                            tint = TextSub,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No processes found", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Grant Usage Access permission in Settings,\nor switch to Shizuku/Root tier for real data",
                            color     = TextSub,
                            fontSize  = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier  = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { vm.refresh() },
                            colors  = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) { Text("Refresh") }
                    }
                }
                return@Scaffold
            }

            // ── Process list ────────────────────────────────────────────
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                val userApps   = apps.filter { !it.isSystem }
                val systemApps = apps.filter { it.isSystem }

                if (userApps.isNotEmpty()) {
                    item { SectionHeader("User Apps (${userApps.size})") }
                    items(userApps, key = { it.packageName }) { process ->
                        AppProcessCard(
                            process     = process,
                            onWhitelist = { vm.addToWhitelist(it) },
                            onIgnore    = { vm.addToIgnoreList(it) },
                            onStop      = { vm.stopApp(it) },
                            modifier    = Modifier.animateItem()
                        )
                    }
                }

                if (systemApps.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader("System Processes (${systemApps.size})")
                    }
                    items(systemApps, key = { it.packageName }) { process ->
                        AppProcessCard(
                            process     = process,
                            onWhitelist = { vm.addToWhitelist(it) },
                            onIgnore    = { vm.addToIgnoreList(it) },
                            onStop      = { vm.stopApp(it) },
                            modifier    = Modifier.animateItem()
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text          = title,
        fontSize      = 11.sp,
        color         = TextMuted,
        fontWeight    = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        modifier      = Modifier.padding(vertical = 6.dp)
    )
}
