package io.github.devildare687.stealthmesh.stealthmesh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        modifier = modifier
            .fillMaxSize()
            .testTag("stealthmesh_private_chat"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Back to Nearby Mesh"
                        }
                    ) {
                        Text(
                            text = "← Nearby",
                            fontFamily = StealthMeshChatDisplayFont,
                            color = StealthMeshChatAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            target.nickname,
                            fontFamily = StealthMeshChatDisplayFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Direct private chat",
                            fontFamily = StealthMeshChatDisplayFont,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontFamily = StealthMeshChatDisplayFont,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(12.dp)
                            .semantics { contentDescription = "Private message error: $error" }
                    )
                }
            }
            PrivateConversation(
                messages = state.privateMessages,
                sessionState = state.privateSessionState,
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
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .semantics {
                stateDescription = visual.label
                contentDescription = "Private session: ${visual.label}"
            }
            .testTag("private_session_status")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(visual.contentColor())
            )
            Text(
                visual.label,
                fontFamily = StealthMeshChatDisplayFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PrivateConversation(
    messages: List<PrivateChatMessage>,
    sessionState: PrivateSessionState,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    if (messages.isEmpty()) {
        val emptyCopy = sessionState.toPrivateEmptyStateCopy()
        Box(
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    StealthMeshBadge(emptyCopy.badge)
                    Text(
                        emptyCopy.title,
                        fontFamily = StealthMeshChatDisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        emptyCopy.body,
                        fontFamily = StealthMeshChatDisplayFont,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxWidth()
                .testTag("private_messages"),
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
                StealthMeshChatAccent.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
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
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (message.isMine) "You" else message.nickname,
                        fontFamily = StealthMeshChatDisplayFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = StealthMeshChatAccent
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        message.formattedTime,
                        fontFamily = StealthMeshChatCodeFont,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    message.text,
                    fontFamily = StealthMeshChatDisplayFont,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
                if (message.isMine) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        message.deliveryState.label.uppercase(),
                        fontFamily = StealthMeshChatCodeFont,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
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
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChanged,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Private message input" }
                    .testTag("private_message_input"),
                placeholder = {
                    Text(
                        "Private message",
                        fontFamily = StealthMeshChatDisplayFont
                    )
                },
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StealthMeshChatAccent,
                    cursorColor = StealthMeshChatAccent
                ),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() })
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSend,
                enabled = canSend,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StealthMeshChatAccent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .height(56.dp)
                    .semantics { contentDescription = "Send private message" }
                    .testTag("send_private_message")
            ) {
                Text(
                    "Send",
                    fontFamily = StealthMeshChatDisplayFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private enum class PrivateStatusTone { Neutral, Positive, Error }

private data class PrivateStatusVisual(
    val label: String,
    val tone: PrivateStatusTone = PrivateStatusTone.Neutral
)

private data class PrivateEmptyStateCopy(
    val badge: String,
    val title: String,
    val body: String
)

private fun PrivateSessionState.toPrivateEmptyStateCopy(): PrivateEmptyStateCopy = when (this) {
    PrivateSessionState.Encrypted -> PrivateEmptyStateCopy(
        badge = "Ready",
        title = "Private line is ready.",
        body = "Only the two of you are in here. Say something suspiciously normal."
    )
    PrivateSessionState.Establishing -> PrivateEmptyStateCopy(
        badge = "Securing",
        title = "Locking things down.",
        body = "Building the encrypted direct link. This should be quick."
    )
    PrivateSessionState.Reconnecting -> PrivateEmptyStateCopy(
        badge = "Retrying",
        title = "Finding your private line again.",
        body = "The encrypted link blinked for a sec. Keeping both devices nearby helps."
    )
    PrivateSessionState.Unavailable -> PrivateEmptyStateCopy(
        badge = "Offline",
        title = "Private line is taking a break.",
        body = "Keep the other device nearby and StealthMesh will keep trying."
    )
    is PrivateSessionState.Error -> PrivateEmptyStateCopy(
        badge = "Oops",
        title = "Private line hit a snag.",
        body = "Give it a moment and try again. Your public Nearby Mesh can keep running meanwhile."
    )
}

private fun PrivateSessionState.toPrivateStatusVisual(): PrivateStatusVisual = when (this) {
    PrivateSessionState.Establishing -> PrivateStatusVisual("Securing direct link")
    PrivateSessionState.Encrypted -> PrivateStatusVisual(
        "Encrypted direct chat",
        PrivateStatusTone.Positive
    )
    PrivateSessionState.Reconnecting -> PrivateStatusVisual("Reconnecting private link")
    PrivateSessionState.Unavailable -> PrivateStatusVisual("Private link unavailable")
    is PrivateSessionState.Error -> PrivateStatusVisual(message, PrivateStatusTone.Error)
}

@Composable
private fun PrivateStatusVisual.containerColor(): Color = when (tone) {
    PrivateStatusTone.Error -> MaterialTheme.colorScheme.errorContainer
    PrivateStatusTone.Positive -> StealthMeshChatAccent.copy(alpha = 0.13f)
    PrivateStatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
}

@Composable
private fun PrivateStatusVisual.contentColor(): Color = when (tone) {
    PrivateStatusTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    PrivateStatusTone.Positive -> StealthMeshChatAccent
    PrivateStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
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
