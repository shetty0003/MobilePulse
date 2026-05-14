package com.mobilepulse.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mobilepulse.app.data.model.EnforcementTier
import com.mobilepulse.app.ui.theme.*
import com.mobilepulse.app.ui.viewmodel.CommandSuggestion
import com.mobilepulse.app.ui.viewmodel.TerminalLine
import com.mobilepulse.app.ui.viewmodel.TerminalViewModel

private val TerminalBg   = Color(0xFF050D08)
private val TerminalText = Color(0xFF39D353)
private val TerminalErr  = Color(0xFFFF6B6B)
private val TerminalInput = Color(0xFFCFAE6E)
private val TerminalDim  = Color(0xFF2A5A35)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    navController: NavController,
    vm: TerminalViewModel = hiltViewModel()
) {
    val lines       by vm.lines.collectAsStateWithLifecycle()
    val isBusy      by vm.isBusy.collectAsStateWithLifecycle()
    val tier        by vm.tier.collectAsStateWithLifecycle()
    val suggestions by vm.allSuggestions.collectAsStateWithLifecycle()
    val listState   = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Terminal, null,
                            tint     = TerminalText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Terminal", fontWeight = FontWeight.ExtraBold)
                            Text(
                                when (tier) {
                                    EnforcementTier.ROOT     -> "root shell"
                                    EnforcementTier.SHIZUKU  -> "adb shell (Shizuku)"
                                    EnforcementTier.STANDARD -> "restricted shell"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (tier) {
                                    EnforcementTier.ROOT     -> Danger
                                    EnforcementTier.SHIZUKU  -> Warning
                                    EnforcementTier.STANDARD -> TextSub
                                }
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.clearLines() }) {
                        Icon(Icons.Filled.DeleteSweep, "Clear", tint = TextSub)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TerminalBg)
            )
        },
        containerColor = TerminalBg,
        bottomBar = {
            TerminalInputBar(
                value       = input,
                onChange    = { input = it },
                isBusy      = isBusy,
                onSend      = { vm.execute(input); input = "" },
                suggestions = suggestions,
                onSuggestion = { input = it }
            )
        }
    ) { padding ->
        LazyColumn(
            state   = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(lines) { line -> TerminalLineView(line) }

            if (isBusy) {
                item {
                    Text(
                        "▌",
                        color    = TerminalText,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun TerminalLineView(line: TerminalLine) {
    val color = when {
        line.isInput -> TerminalInput
        line.isError -> TerminalErr
        else         -> TerminalText
    }
    Text(
        text       = line.text,
        color      = color,
        fontSize   = 13.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 18.sp,
        modifier   = Modifier.horizontalScroll(rememberScrollState())
    )
}

@Composable
private fun TerminalInputBar(
    value: String,
    onChange: (String) -> Unit,
    isBusy: Boolean,
    onSend: () -> Unit,
    suggestions: List<CommandSuggestion>,
    onSuggestion: (String) -> Unit
) {
    val filtered = remember(value, suggestions) {
        if (value.isBlank()) suggestions.take(8)
        else suggestions.filter {
            it.command.contains(value, ignoreCase = true) ||
            it.label.contains(value, ignoreCase = true)
        }.take(8)
    }

    Surface(color = Color(0xFF0A1A0F), tonalElevation = 0.dp) {
        Column {
            if (filtered.isNotEmpty()) {
                SuggestionsRow(filtered, onSuggestion)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "$", color = TerminalText, fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value         = value,
                    onValueChange = onChange,
                    modifier      = Modifier.weight(1f),
                    placeholder   = {
                        Text(
                            "enter command or /help...", color = TerminalDim,
                            fontSize = 13.sp, fontFamily = FontFamily.Monospace
                        )
                    },
                    shape         = RoundedCornerShape(8.dp),
                    singleLine    = true,
                    textStyle     = TextStyle(
                        color      = TerminalText,
                        fontSize   = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!isBusy) onSend() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = TerminalText.copy(alpha = 0.5f),
                        unfocusedBorderColor = TerminalDim,
                        cursorColor          = TerminalText
                    )
                )
                FilledIconButton(
                    onClick  = onSend,
                    enabled  = value.isNotBlank() && !isBusy,
                    modifier = Modifier.size(40.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(containerColor = TerminalText)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, "Run",
                        tint     = Color(0xFF050D08),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionsRow(
    suggestions: List<CommandSuggestion>,
    onSuggestion: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        suggestions.forEach { s ->
            SuggestionChip(
                onClick = { onSuggestion(s.command) },
                label = {
                    Text(s.label, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Color(0xFF0D2B14),
                    labelColor     = TerminalText
                ),
                border = SuggestionChipDefaults.suggestionChipBorder(
                    enabled     = true,
                    borderColor = TerminalDim
                )
            )
        }
    }
}
