package com.bitchat.android.stealthmesh

import com.bitchat.android.mesh.MeshService
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import com.bitchat.android.services.AppStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID

interface StealthMeshRepository {
    val connectionState: Flow<MeshConnectionState>
    val peers: Flow<List<NearbyPeer>>
    val publicMessages: Flow<List<NearbyMeshMessage>>
    val nickname: StateFlow<String>

    fun updateRuntimeState(state: MeshRuntimeState)
    fun sendPublicMessage(content: String): Result<Unit>
}

sealed interface MeshRuntimeState {
    data object Stopped : MeshRuntimeState
    data object PermissionRequired : MeshRuntimeState
    data object BluetoothOff : MeshRuntimeState
    data object Starting : MeshRuntimeState
    data object Discovering : MeshRuntimeState
    data object Running : MeshRuntimeState
    data object Recovering : MeshRuntimeState
    data class Error(val message: String) : MeshRuntimeState
}

internal interface StealthMeshStateSource {
    val peers: StateFlow<List<String>>
    val directPeers: StateFlow<Set<String>>
    val publicMessages: StateFlow<List<BitchatMessage>>
    val nickname: StateFlow<String>

    fun addPublicMessage(message: BitchatMessage)
}

private object AppStateStealthMeshSource : StealthMeshStateSource {
    override val peers = AppStateStore.peers
    override val directPeers = AppStateStore.directPeers
    override val publicMessages = AppStateStore.publicMessages
    override val nickname = AppStateStore.nickname

    override fun addPublicMessage(message: BitchatMessage) {
        AppStateStore.addPublicMessage(message)
    }
}

internal interface StealthMeshGateway {
    val myPeerId: String
    fun peerNicknames(): Map<String, String>
    fun peerRssi(): Map<String, Int>
    fun sendPublicMessage(content: String)
}

private class ProductionStealthMeshGateway(
    private val mesh: MeshService
) : StealthMeshGateway {
    override val myPeerId: String
        get() = mesh.myPeerID

    override fun peerNicknames(): Map<String, String> = mesh.getPeerNicknames()

    override fun peerRssi(): Map<String, Int> = mesh.getPeerRSSI()

    override fun sendPublicMessage(content: String) {
        mesh.sendMessage(content, emptyList(), null)
    }
}

class AppStateStealthMeshRepository internal constructor(
    private val gateway: StealthMeshGateway,
    private val source: StealthMeshStateSource,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newMessageId: () -> String = { UUID.randomUUID().toString().uppercase() }
) : StealthMeshRepository {
    constructor(mesh: MeshService) : this(
        gateway = ProductionStealthMeshGateway(mesh),
        source = AppStateStealthMeshSource
    )

    private val runtimeState = MutableStateFlow<MeshRuntimeState>(MeshRuntimeState.Starting)

    override val connectionState: Flow<MeshConnectionState> = combine(
        runtimeState,
        source.peers
    ) { runtime, peerIds ->
        runtime.toConnectionState(peerIds.size)
    }

    override val peers: Flow<List<NearbyPeer>> = combine(
        source.peers,
        source.directPeers
    ) { peerIds, directPeerIds ->
        mapNearbyPeers(
            peerIds = peerIds,
            directPeerIds = directPeerIds,
            nicknames = gateway.peerNicknames(),
            rssiByPeer = gateway.peerRssi()
        )
    }

    override val publicMessages: Flow<List<NearbyMeshMessage>> = source.publicMessages.map { messages ->
        mapPublicMessages(messages, gateway.myPeerId)
    }

    override val nickname: StateFlow<String> = source.nickname

    override fun updateRuntimeState(state: MeshRuntimeState) {
        runtimeState.value = state
    }

    override fun sendPublicMessage(content: String): Result<Unit> {
        val normalized = content.trim()
        if (normalized.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty"))
        }

        return runCatching {
            gateway.sendPublicMessage(normalized)
            source.addPublicMessage(
                BitchatMessage(
                    id = newMessageId(),
                    sender = source.nickname.value.ifBlank { "Me" },
                    content = normalized,
                    timestamp = Date(nowMillis()),
                    isRelay = false,
                    isPrivate = false,
                    senderPeerID = gateway.myPeerId,
                    deliveryStatus = DeliveryStatus.Sent
                )
            )
        }.onFailure { failure ->
            runtimeState.value = MeshRuntimeState.Error(
                failure.message?.takeIf(String::isNotBlank) ?: "Message could not be sent"
            )
        }
    }
}

internal fun MeshRuntimeState.toConnectionState(peerCount: Int): MeshConnectionState = when (this) {
    MeshRuntimeState.Stopped -> MeshConnectionState.Stopped
    MeshRuntimeState.PermissionRequired -> MeshConnectionState.PermissionRequired
    MeshRuntimeState.BluetoothOff -> MeshConnectionState.BluetoothOff
    MeshRuntimeState.Starting -> MeshConnectionState.Starting
    MeshRuntimeState.Discovering -> if (peerCount == 0) {
        MeshConnectionState.Discovering
    } else {
        MeshConnectionState.ActiveWithPeers(peerCount)
    }
    MeshRuntimeState.Running -> if (peerCount == 0) {
        MeshConnectionState.ActiveNoPeers
    } else {
        MeshConnectionState.ActiveWithPeers(peerCount)
    }
    MeshRuntimeState.Recovering -> if (peerCount == 0) {
        MeshConnectionState.Recovering
    } else {
        MeshConnectionState.ActiveWithPeers(peerCount)
    }
    is MeshRuntimeState.Error -> MeshConnectionState.Error(message)
}

internal fun mapNearbyPeers(
    peerIds: List<String>,
    directPeerIds: Set<String>,
    nicknames: Map<String, String>,
    rssiByPeer: Map<String, Int>
): List<NearbyPeer> = peerIds
    .distinct()
    .mapIndexed { index, peerId ->
        NearbyPeer(
            peerId = peerId,
            nickname = nicknames[peerId]?.takeIf(String::isNotBlank) ?: "Nearby person ${index + 1}",
            signalStrength = rssiByPeer[peerId].toSignalStrength(),
            isDirect = peerId in directPeerIds
        )
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, NearbyPeer::nickname).thenBy(NearbyPeer::peerId))

internal fun mapPublicMessages(
    messages: List<BitchatMessage>,
    myPeerId: String
): List<NearbyMeshMessage> = messages
    .asSequence()
    .filter { !it.isPrivate && it.channel == null }
    .distinctBy(BitchatMessage::id)
    .map { message ->
        NearbyMeshMessage(
            id = message.id,
            senderPeerId = message.senderPeerID,
            nickname = message.sender.ifBlank { "Unknown" },
            text = message.content,
            timestampMillis = message.timestamp.time,
            isMine = message.senderPeerID == myPeerId,
            isPrivate = false,
            deliveryState = message.deliveryStatus.toChatDeliveryState(message.senderPeerID == myPeerId)
        )
    }
    .sortedWith(compareBy<NearbyMeshMessage> { it.timestampMillis }.thenBy { it.id })
    .toList()

private fun Int?.toSignalStrength(): SignalStrength = when {
    this == null -> SignalStrength.Unknown
    this >= -60 -> SignalStrength.Strong
    this >= -78 -> SignalStrength.Nearby
    else -> SignalStrength.Weak
}

private fun DeliveryStatus?.toChatDeliveryState(isMine: Boolean): ChatDeliveryState = when (this) {
    DeliveryStatus.Sending -> ChatDeliveryState.Sending
    DeliveryStatus.Sent -> ChatDeliveryState.Sent
    is DeliveryStatus.Delivered -> ChatDeliveryState.Delivered
    is DeliveryStatus.Read -> ChatDeliveryState.Read
    is DeliveryStatus.Failed -> ChatDeliveryState.Failed
    is DeliveryStatus.PartiallyDelivered -> ChatDeliveryState.PartiallyDelivered
    null -> if (isMine) ChatDeliveryState.Sent else ChatDeliveryState.Received
}
