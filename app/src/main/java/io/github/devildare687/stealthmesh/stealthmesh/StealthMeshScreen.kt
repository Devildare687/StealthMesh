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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.devildare687.stealthmesh.R
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StealthMeshScreen(
    viewModel: StealthMeshViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    if (state.privateConversation != null) {
        StealthMeshPrivateScreen(
            state = state,
            onBack = viewModel::closePrivateConversation,
            onDraftChanged = viewModel::updatePrivateDraft,
            onSend = viewModel::sendPrivateMessage,
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("stealthmesh_chat_shell"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.stealthmesh_mark),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = "StealthMesh",
                                fontFamily = StealthMeshChatDisplayFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                            Text(
                                text = "Nearby. Private. No server required.",
                                fontFamily = StealthMeshChatDisplayFont,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.testTag("open_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Open settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        bottomBar = {
            MessageComposer(
                draft = state.draft,
                canSend = state.canSend,
                onDraftChanged = viewModel::updateDraft,
                onSend = viewModel::sendMessage
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            ConnectionSummary(state.connectionState)
            NearbyPeople(
                peers = state.peers,
                onPeerSelected = viewModel::openPrivateConversation
            )
            NearbyMeshConversation(
                messages = state.messages,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showSettings) {
        StealthMeshSettingsSheet(
            currentDisplayName = state.nickname,
            onDismiss = { showSettings = false },
            onSaveDisplayName = viewModel::setDisplayName
        )
    }
}

@Composable
private fun ConnectionSummary(connectionState: MeshConnectionState) {
    val visual = connectionState.toStatusVisual()
    Surface(
        color = visual.containerColor(),
        contentColor = visual.contentColor(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .semantics {
                stateDescription = visual.label
                contentDescription = "Mesh status: ${visual.label}"
            }
            .testTag("mesh_status")
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
                text = visual.label,
                fontFamily = StealthMeshChatDisplayFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun NearbyPeople(
    peers: List<NearbyPeer>,
    onPeerSelected: (NearbyPeer) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp)
    ) {
        StealthMeshSectionLabel(
            text = "Nearby",
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "People around you",
            fontFamily = StealthMeshChatDisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .semantics { heading() }
        )
        Spacer(Modifier.height(11.dp))

        if (peers.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Quiet around here",
                        fontFamily = StealthMeshChatDisplayFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Keep StealthMesh open for a moment while nearby devices show up.",
                        fontFamily = StealthMeshChatDisplayFont,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("nearby_people")
            ) {
                items(peers, key = NearbyPeer::peerId) { peer ->
                    PeerCard(peer, onClick = { onPeerSelected(peer) })
                }
            }
        }
    }
}

@Composable
private fun PeerCard(
    peer: NearbyPeer,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
        ),
        modifier = Modifier.semantics {
            contentDescription = buildString {
                append("Open private chat with ")
                append(peer.nickname)
                append(", ")
                append(peer.signalStrength.accessibilityLabel)
                if (peer.isDirect) append(", direct connection")
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StealthMeshChatAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.nickname.firstOrNull()?.uppercase() ?: "?",
                    color = StealthMeshChatAccent,
                    fontFamily = StealthMeshChatDisplayFont,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = peer.nickname,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = StealthMeshChatDisplayFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = peer.connectionLabel,
                    fontFamily = StealthMeshChatCodeFont,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NearbyMeshConversation(
    messages: List<NearbyMeshMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        StealthMeshSectionLabel(
            text = "Local room",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Text(
            text = "Nearby Mesh",
            fontFamily = StealthMeshChatDisplayFont,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .semantics { heading() }
                .testTag("nearby_mesh_title")
        )

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
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
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        StealthMeshBadge("Local")
                        Text(
                            text = "This room exists only around you.",
                            fontFamily = StealthMeshChatDisplayFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "When someone appears nearby, say hi. Nothing needs a central chat server.",
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
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("nearby_mesh_messages"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = NearbyMeshMessage::id) { message ->
                    MessageBubble(message)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: NearbyMeshMessage) {
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
                    contentDescription = "${message.nickname}, ${message.text}, ${message.formattedTime}"
                }
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (message.isMine) "You" else message.nickname,
                        fontFamily = StealthMeshChatDisplayFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = StealthMeshChatAccent
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = message.formattedTime,
                        fontFamily = StealthMeshChatCodeFont,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message.text,
                    fontFamily = StealthMeshChatDisplayFont,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun MessageComposer(
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
                    .testTag("message_input"),
                placeholder = {
                    Text(
                        "Message the local room",
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
                    .semantics { contentDescription = "Send message" }
                    .testTag("send_message")
            ) {
                Text(
                    text = "Send",
                    fontFamily = StealthMeshChatDisplayFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private enum class StatusTone { Neutral, Positive, Error }

private data class StatusVisual(
    val label: String,
    val tone: StatusTone = StatusTone.Neutral
)

@Composable
private fun StatusVisual.containerColor(): Color = when (tone) {
    StatusTone.Error -> MaterialTheme.colorScheme.errorContainer
    StatusTone.Positive -> StealthMeshChatAccent.copy(alpha = 0.13f)
    StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
}

@Composable
private fun StatusVisual.contentColor(): Color = when (tone) {
    StatusTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    StatusTone.Positive -> StealthMeshChatAccent
    StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun MeshConnectionState.toStatusVisual(): StatusVisual = when (this) {
    MeshConnectionState.Stopped -> StatusVisual("Mesh offline")
    MeshConnectionState.PermissionRequired -> StatusVisual(
        "Nearby access needed",
        StatusTone.Error
    )
    MeshConnectionState.BluetoothOff -> StatusVisual(
        "Bluetooth is off",
        StatusTone.Error
    )
    MeshConnectionState.Starting -> StatusVisual("Starting local mesh")
    MeshConnectionState.Discovering -> StatusVisual("Looking for nearby people")
    MeshConnectionState.ActiveNoPeers -> StatusVisual(
        "Ready — no one nearby yet",
        StatusTone.Positive
    )
    is MeshConnectionState.ActiveWithPeers -> StatusVisual(
        if (count == 1) "1 person in range" else "$count people in range",
        StatusTone.Positive
    )
    MeshConnectionState.Recovering -> StatusVisual("Reconnecting nearby links")
    is MeshConnectionState.Error -> StatusVisual(message, StatusTone.Error)
}

private val NearbyPeer.connectionLabel: String
    get() = if (isDirect) "DIRECT · ${signalStrength.accessibilityLabel.uppercase()}" else signalStrength.accessibilityLabel.uppercase()

private val SignalStrength.accessibilityLabel: String
    get() = when (this) {
        SignalStrength.Strong -> "Strong"
        SignalStrength.Nearby -> "Nearby"
        SignalStrength.Weak -> "Weak"
        SignalStrength.Unknown -> "Signal unknown"
    }

private val NearbyMeshMessage.formattedTime: String
    get() = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestampMillis))
