package com.mobilepulse.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilepulse.app.ui.theme.*
import com.mobilepulse.app.ui.viewmodel.WhitelistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(vm: WhitelistViewModel = hiltViewModel()) {
    val whitelist  by vm.whitelist.collectAsStateWithLifecycle()
    val ignoreList by vm.ignoreList.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Whitelist (${whitelist.size})",
        "Ignore List (${ignoreList.size})")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("App Lists", fontWeight = FontWeight.ExtraBold) })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text     = { Text(title, fontSize = 12.sp) })
                }
            }
            when (selectedTab) {
                0 -> ExemptionList(
                    items = whitelist.map { it.packageName to it.appName },
                    badgeColor = Success, badgeLabel = "PROTECTED",
                    onRemove = { vm.removeFromWhitelist(it) }
                )
                1 -> ExemptionList(
                    items = ignoreList.map { it.packageName to it.appName },
                    badgeColor = Warning, badgeLabel = "IGNORED",
                    onRemove = { vm.removeFromIgnoreList(it) }
                )
            }
        }
    }
}

@Composable
fun ExemptionList(
    items: List<Pair<String, String>>,
    badgeColor: Color,
    badgeLabel: String,
    onRemove: (String) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No apps in this list", color = TextSub)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.first }) { (pkg, name) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor)
                ) {
                    Row(Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text(pkg, fontSize = 11.sp, color = TextMuted)
                        }
                        Surface(
                            color = badgeColor.copy(0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(badgeLabel,
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = badgeColor, fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { onRemove(pkg) }) {
                            Icon(Icons.Filled.Delete, null, tint = Danger.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}
