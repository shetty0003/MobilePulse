package com.mobilepulse.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mobilepulse.app.data.repository.AiMessage
import com.mobilepulse.app.ui.theme.*
import com.mobilepulse.app.ui.viewmodel.AiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(
    navController: NavController,
    vm: AiViewModel = hiltViewModel()
) {
    val messages   by vm.messages.collectAsStateWithLifecycle()
    val isLoading  by vm.isLoading.collectAsStateWithLifecycle()
    val error      by vm.error.collectAsStateWithLifecycle()
    val listState  = rememberLazyListState()
    val snackbar   = remember { SnackbarHostState() }

    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AutoAwesome, null,
                            tint     = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("MobilePulse AI", fontWeight = FontWeight.ExtraBold)
                            Text(
                                "Powered by Claude",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSub
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
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { vm.clearChat() }) {
                            Icon(Icons.Filled.DeleteSweep, "Clear chat", tint = TextSub)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            ChatInput(
                value     = inputText,
                onChange  = { inputText = it },
                onSend    = {
                    vm.send(inputText)
                    inputText = ""
                },
                isLoading = isLoading
            )
        }
    ) { padding ->
        if (messages.isEmpty() && !isLoading) {
            EmptyState(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                state   = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                items(messages, key = { "${it.role}_${messages.indexOf(it)}" }) { msg ->
                    MessageBubble(msg)
                }

                if (isLoading) {
                    item { TypingIndicator() }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier              = modifier,
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.AutoAwesome, null,
            tint     = Primary.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Ask me anything",
            fontWeight = FontWeight.Bold,
            fontSize   = 20.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "High CPU? RAM full? App crashing?\nDescribe the problem and I'll help fix it.",
            color    = TextSub,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun MessageBubble(msg: AiMessage) {
    val isUser   = msg.role == "user"
    val bubbleColor  = if (isUser) Primary else CardColor
    val textColor    = if (isUser) OnPrimary else TextPrimary
    val alignment    = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text      = msg.content,
                color     = textColor,
                fontSize  = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp))
            .background(CardColor)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        CircularProgressIndicator(
            modifier    = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color       = Primary
        )
        Spacer(Modifier.width(8.dp))
        Text("Thinking...", color = TextSub, fontSize = 13.sp)
    }
}

@Composable
private fun ChatInput(
    value: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    val canSend = value.isNotBlank() && !isLoading

    Surface(
        color     = Surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value         = value,
                onValueChange = onChange,
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Ask about your device...", color = TextMuted, fontSize = 14.sp) },
                shape         = RoundedCornerShape(20.dp),
                maxLines      = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Primary,
                    unfocusedBorderColor = Border,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary,
                    cursorColor          = Primary
                )
            )
            FilledIconButton(
                onClick  = onSend,
                enabled  = canSend,
                modifier = Modifier.size(48.dp),
                colors   = IconButtonDefaults.filledIconButtonColors(containerColor = Primary)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send, "Send",
                    tint     = OnPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
