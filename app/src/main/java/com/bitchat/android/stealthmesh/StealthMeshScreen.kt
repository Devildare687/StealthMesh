package com.bitchat.android.stealthmesh

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material.icons.rounded.SignalCellularNull
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StealthMeshScreen(
    viewModel: StealthMeshViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("stealthmesh_chat_shell"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "StealthMesh",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Offline-first local chat",
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
            NearbyPeople(peers = state.peers)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NearbyMeshConversation(
                messages = state.messages,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ConnectionSummary(connectionState: MeshConnectionState) {
    val visual = connectionState.toStatusVisual()
    Surface(
        color = visual.containerColor(),
        contentColor = visual.contentColor(),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = visual.label
                contentDescription = "Mesh status: ${visual.label}"
            }
            .testTag("mesh_status")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = visual.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun NearbyPeople(peers: List<NearbyPeer>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Nearby People",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
        }
        Spacer(Modifier.height(10.dp))

        if (peers.isEmpty()) {
            Text(
                text = "No one nearby yet. Keep StealthMesh open while it looks for people.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("nearby_people")
            ) {
                items(peers, key = NearbyPeer::peerId) { peer ->
                    PeerCard(peer)
                }
            }
        }
    }
}

@Composable
private fun PeerCard(peer: NearbyPeer) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.semantics {
            contentDescription = buildString {
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
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.nickname.firstOrNull()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = peer.nickname,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = peer.connectionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = peer.signalStrength.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
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
        Text(
            text = "Nearby Mesh",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .semantics { heading() }
                .testTag("nearby_mesh_title")
        )
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Messages stay on the local mesh.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Say hello when someone appears nearby.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("nearby_mesh_messages"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .semantics {
                    contentDescription = "${message.nickname}, ${message.text}, ${message.formattedTime}"
                }
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (message.isMine) "You" else message.nickname,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = message.formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge
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
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChanged,
                modifier = Modifier.weight(1f).testTag("message_input"),
                placeholder = { Text("Message Nearby Mesh") },
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
                    .semantics { contentDescription = "Send message" }
                    .testTag("send_message")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null
                )
            }
        }
    }
}

private data class StatusVisual(
    val label: String,
    val icon: ImageVector,
    val isPositive: Boolean = false,
    val isError: Boolean = false
)

@Composable
private fun StatusVisual.containerColor(): Color = when {
    isError -> MaterialTheme.colorScheme.errorContainer
    isPositive -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun StatusVisual.contentColor(): Color = when {
    isError -> MaterialTheme.colorScheme.onErrorContainer
    isPositive -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSecondaryContainer
}

private fun MeshConnectionState.toStatusVisual(): StatusVisual = when (this) {
    MeshConnectionState.Stopped -> StatusVisual("Mesh stopped", Icons.Rounded.SignalCellularNull)
    MeshConnectionState.PermissionRequired -> StatusVisual(
        "Nearby permission required",
        Icons.Rounded.ErrorOutline,
        isError = true
    )
    MeshConnectionState.BluetoothOff -> StatusVisual(
        "Bluetooth off",
        Icons.Rounded.BluetoothDisabled,
        isError = true
    )
    MeshConnectionState.Starting -> StatusVisual("Starting mesh", Icons.Rounded.Refresh)
    MeshConnectionState.Discovering -> StatusVisual("Looking nearby", Icons.Rounded.WifiTethering)
    MeshConnectionState.ActiveNoPeers -> StatusVisual("Mesh active", Icons.Rounded.WifiTethering, isPositive = true)
    is MeshConnectionState.ActiveWithPeers -> StatusVisual(
        if (count == 1) "1 person nearby" else "$count people nearby",
        Icons.Rounded.WifiTethering,
        isPositive = true
    )
    MeshConnectionState.Recovering -> StatusVisual("Reconnecting", Icons.Rounded.Refresh)
    is MeshConnectionState.Error -> StatusVisual(message, Icons.Rounded.ErrorOutline, isError = true)
}

private val NearbyPeer.connectionLabel: String
    get() = if (isDirect) "Direct · ${signalStrength.accessibilityLabel}" else signalStrength.accessibilityLabel

private val SignalStrength.accessibilityLabel: String
    get() = when (this) {
        SignalStrength.Strong -> "Strong signal"
        SignalStrength.Nearby -> "Nearby signal"
        SignalStrength.Weak -> "Weak signal"
        SignalStrength.Unknown -> "Signal unavailable"
    }

private val SignalStrength.icon: ImageVector
    get() = when (this) {
        SignalStrength.Strong -> Icons.Rounded.SignalCellularAlt
        SignalStrength.Nearby -> Icons.Rounded.SignalCellularConnectedNoInternet0Bar
        SignalStrength.Weak,
        SignalStrength.Unknown -> Icons.Rounded.SignalCellularNull
    }

private val NearbyMeshMessage.formattedTime: String
    get() = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestampMillis))
