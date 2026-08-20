package com.bitchat.android.stealthmesh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StealthMeshPrivateScreen(
    state: StealthMeshUiState,
    onBack: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val target = state.privateConversation ?: return
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("stealthmesh_private_chat"),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Back to Nearby Mesh"
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    Column {
                        Text(target.nickname, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Private conversation",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            PrivateMessageComposer(
                draft = state.privateDraft,
                canSend = state.canSendPrivate,
                onDraftChanged = onDraftChanged,
                onSend = onSend
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            PrivateSessionSummary(state.privateSessionState)
            state.privateSendError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .semantics { contentDescription = "Private message error: $error" }
                )
            }
            PrivateConversation(
                messages = state.privateMessages,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PrivateSessionSummary(state: PrivateSessionState) {
    val visual = state.toPrivateStatusVisual()
    Surface(
        color = visual.containerColor(),
        contentColor = visual.contentColor(),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = visual.label
                contentDescription = "Private session: ${visual.label}"
            }
            .testTag("private_session_status")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(visual.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                visual.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PrivateConversation(
    messages: List<PrivateChatMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    if (messages.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    "Private messages appear here after the secure session is ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth().testTag("private_messages"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = PrivateChatMessage::id) { message ->
                PrivateMessageBubble(message)
            }
        }
    }
}

@Composable
private fun PrivateMessageBubble(message: PrivateChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .semantics {
                    contentDescription =
                        "Private message ${message.id}: ${message.nickname}, ${message.text}, ${message.formattedTime}"
                }
                .testTag("private_message_${message.id}")
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (message.isMine) "You" else message.nickname,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        message.formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                if (message.isMine) {
                    Text(
                        message.deliveryState.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivateMessageComposer(
    draft: String,
    canSend: Boolean,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChanged,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Private message input" }
                    .testTag("private_message_input"),
                placeholder = { Text("Private message") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() })
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .size(56.dp)
                    .semantics { contentDescription = "Send private message" }
                    .testTag("send_private_message")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
            }
        }
    }
}

private data class PrivateStatusVisual(
    val label: String,
    val icon: ImageVector,
    val positive: Boolean = false,
    val error: Boolean = false
)

private fun PrivateSessionState.toPrivateStatusVisual(): PrivateStatusVisual = when (this) {
    PrivateSessionState.Establishing -> PrivateStatusVisual(
        "Establishing secure session",
        Icons.Rounded.Refresh
    )
    PrivateSessionState.Encrypted -> PrivateStatusVisual(
        "Encrypted",
        Icons.Rounded.Lock,
        positive = true
    )
    PrivateSessionState.Reconnecting -> PrivateStatusVisual("Reconnecting", Icons.Rounded.Refresh)
    PrivateSessionState.Unavailable -> PrivateStatusVisual(
        "Unavailable",
        Icons.Rounded.BluetoothDisabled
    )
    is PrivateSessionState.Error -> PrivateStatusVisual(
        message,
        Icons.Rounded.ErrorOutline,
        error = true
    )
}

@Composable
private fun PrivateStatusVisual.containerColor(): Color = when {
    error -> MaterialTheme.colorScheme.errorContainer
    positive -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun PrivateStatusVisual.contentColor(): Color = when {
    error -> MaterialTheme.colorScheme.onErrorContainer
    positive -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSecondaryContainer
}

private val PrivateChatMessage.formattedTime: String
    get() = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestampMillis))

private val ChatDeliveryState.label: String
    get() = when (this) {
        ChatDeliveryState.Sending -> "Sending"
        ChatDeliveryState.Sent -> "Sent"
        ChatDeliveryState.Delivered -> "Delivered"
        ChatDeliveryState.Read -> "Read"
        ChatDeliveryState.Failed -> "Failed"
        ChatDeliveryState.PartiallyDelivered -> "Partially delivered"
        ChatDeliveryState.Received -> "Received"
    }
