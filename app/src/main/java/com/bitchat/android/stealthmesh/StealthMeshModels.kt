package com.bitchat.android.stealthmesh

sealed interface MeshConnectionState {
    data object Stopped : MeshConnectionState
    data object PermissionRequired : MeshConnectionState
    data object BluetoothOff : MeshConnectionState
    data object Starting : MeshConnectionState
    data object Discovering : MeshConnectionState
    data object ActiveNoPeers : MeshConnectionState
    data class ActiveWithPeers(val count: Int) : MeshConnectionState
    data object Recovering : MeshConnectionState
    data class Error(val message: String) : MeshConnectionState
}

enum class SignalStrength {
    Strong,
    Nearby,
    Weak,
    Unknown
}

data class NearbyPeer(
    val peerId: String,
    val nickname: String,
    val signalStrength: SignalStrength,
    val isDirect: Boolean
)

enum class ChatDeliveryState {
    Sending,
    Sent,
    Delivered,
    Read,
    Failed,
    PartiallyDelivered,
    Received
}

data class NearbyMeshMessage(
    val id: String,
    val senderPeerId: String?,
    val nickname: String,
    val conversationId: String = NEARBY_MESH_CONVERSATION_ID,
    val text: String,
    val timestampMillis: Long,
    val isMine: Boolean,
    val isPrivate: Boolean,
    val deliveryState: ChatDeliveryState
)

data class StealthMeshUiState(
    val connectionState: MeshConnectionState = MeshConnectionState.Starting,
    val peers: List<NearbyPeer> = emptyList(),
    val messages: List<NearbyMeshMessage> = emptyList(),
    val nickname: String = "",
    val draft: String = "",
    val isSending: Boolean = false
) {
    val canSend: Boolean
        get() = draft.isNotBlank() && !isSending && connectionState.canUseMesh()
}

internal fun MeshConnectionState.canUseMesh(): Boolean = when (this) {
    MeshConnectionState.Discovering,
    MeshConnectionState.ActiveNoPeers,
    is MeshConnectionState.ActiveWithPeers,
    MeshConnectionState.Recovering -> true

    else -> false
}

const val NEARBY_MESH_CONVERSATION_ID = "nearby-mesh"
