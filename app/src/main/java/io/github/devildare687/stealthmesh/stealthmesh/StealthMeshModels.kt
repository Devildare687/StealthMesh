package io.github.devildare687.stealthmesh.stealthmesh

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

sealed interface PrivateSessionState {
    data object Establishing : PrivateSessionState
    data object Encrypted : PrivateSessionState
    data object Reconnecting : PrivateSessionState
    data object Unavailable : PrivateSessionState
    data class Error(val message: String) : PrivateSessionState
}

data class PrivateConversationTarget(
    val peerId: String,
    val nickname: String
)

data class PrivateChatMessage(
    val id: String,
    val conversationId: String,
    val senderPeerId: String?,
    val nickname: String,
    val text: String,
    val timestampMillis: Long,
    val isMine: Boolean,
    val deliveryState: ChatDeliveryState
)

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
    val isSending: Boolean = false,
    val privateConversation: PrivateConversationTarget? = null,
    val privateSessionState: PrivateSessionState = PrivateSessionState.Unavailable,
    val privateMessages: List<PrivateChatMessage> = emptyList(),
    val privateDraft: String = "",
    val isSendingPrivate: Boolean = false,
    val privateSendError: String? = null
) {
    val canSend: Boolean
        get() = draft.isNotBlank() && !isSending && connectionState.canUseMesh()

    val canSendPrivate: Boolean
        get() = privateDraft.isNotBlank() &&
            !isSendingPrivate &&
            privateSessionState == PrivateSessionState.Encrypted
}

internal fun MeshConnectionState.canUseMesh(): Boolean = when (this) {
    MeshConnectionState.Discovering,
    MeshConnectionState.ActiveNoPeers,
    is MeshConnectionState.ActiveWithPeers,
    MeshConnectionState.Recovering -> true

    else -> false
}

const val NEARBY_MESH_CONVERSATION_ID = "nearby-mesh"
