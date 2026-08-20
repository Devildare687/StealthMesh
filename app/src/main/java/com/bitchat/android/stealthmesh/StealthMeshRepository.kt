package com.bitchat.android.stealthmesh

import com.bitchat.android.mesh.MeshService
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import com.bitchat.android.noise.NoiseSession
import com.bitchat.android.services.AppStateStore
import com.bitchat.android.services.ContactDirectory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.util.Date
import java.util.UUID

interface StealthMeshRepository {
    val connectionState: Flow<MeshConnectionState>
    val peers: Flow<List<NearbyPeer>>
    val publicMessages: Flow<List<NearbyMeshMessage>>
    val nickname: StateFlow<String>

    fun updateRuntimeState(state: MeshRuntimeState)
    fun sendPublicMessage(content: String): Result<Unit>
    fun privateMessages(peerId: String): Flow<List<PrivateChatMessage>>
    fun privateSessionState(peerId: String): Flow<PrivateSessionState>
    fun openPrivateConversation(peerId: String)
    fun closePrivateConversation()
    suspend fun sendPrivateMessage(
        peerId: String,
        recipientNickname: String,
        content: String
    ): Result<Unit>
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
    val privateMessages: StateFlow<Map<String, List<BitchatMessage>>>
    val nickname: StateFlow<String>

    fun addPublicMessage(message: BitchatMessage)
    fun setSelectedPrivateConversation(conversationId: String?)
    suspend fun addPrivateMessageDurably(
        conversationId: String,
        message: BitchatMessage
    ): Boolean
    fun updatePrivateMessageStatus(messageId: String, status: DeliveryStatus)
}

private object AppStateStealthMeshSource : StealthMeshStateSource {
    override val peers = AppStateStore.peers
    override val directPeers = AppStateStore.directPeers
    override val publicMessages = AppStateStore.publicMessages
    override val privateMessages = AppStateStore.privateMessages
    override val nickname = AppStateStore.nickname

    override fun addPublicMessage(message: BitchatMessage) {
        AppStateStore.addPublicMessage(message)
    }

    override fun setSelectedPrivateConversation(conversationId: String?) {
        AppStateStore.setSelectedPrivateChatPeer(conversationId)
    }

    override suspend fun addPrivateMessageDurably(
        conversationId: String,
        message: BitchatMessage
    ): Boolean = AppStateStore.addPrivateMessageDurably(
        peerID = conversationId,
        msg = message,
        forceRead = true
    )

    override fun updatePrivateMessageStatus(messageId: String, status: DeliveryStatus) {
        AppStateStore.updatePrivateMessageStatus(messageId, status)
    }
}

internal interface StealthMeshGateway {
    val myPeerId: String
    fun peerNicknames(): Map<String, String>
    fun peerRssi(): Map<String, Int>
    fun sendPublicMessage(content: String)
    fun privateSessionState(peerId: String): NoiseSession.NoiseSessionState
    fun initiatePrivateSession(peerId: String)
    fun sendPrivateMessage(
        peerId: String,
        recipientNickname: String,
        content: String,
        messageId: String
    )
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

    override fun privateSessionState(peerId: String): NoiseSession.NoiseSessionState =
        mesh.getSessionState(peerId)

    override fun initiatePrivateSession(peerId: String) {
        mesh.initiateNoiseHandshake(peerId)
    }

    override fun sendPrivateMessage(
        peerId: String,
        recipientNickname: String,
        content: String,
        messageId: String
    ) {
        mesh.sendPrivateMessage(content, peerId, recipientNickname, messageId)
    }
}

class AppStateStealthMeshRepository internal constructor(
    private val gateway: StealthMeshGateway,
    private val source: StealthMeshStateSource,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newMessageId: () -> String = { UUID.randomUUID().toString().uppercase() },
    private val canonicalConversationId: (String) -> String =
        ContactDirectory::canonicalConversationId,
    private val sessionPollMillis: Long = 500L
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

    override fun privateMessages(peerId: String): Flow<List<PrivateChatMessage>> =
        source.privateMessages.map { conversations ->
            val conversationId = canonicalConversationId(peerId)
            val messages = conversations.entries
                .asSequence()
                .filter { (key, _) -> canonicalConversationId(key) == conversationId }
                .flatMap { it.value.asSequence() }
                .toList()
            mapPrivateMessages(messages, gateway.myPeerId, conversationId)
        }

    override fun privateSessionState(peerId: String): Flow<PrivateSessionState> = flow {
        var handshakeAttemptedForCurrentPresence = false
        while (currentCoroutineContext().isActive) {
            val connected = peerId in source.peers.value
            val rawState = runCatching { gateway.privateSessionState(peerId) }
                .getOrElse { NoiseSession.NoiseSessionState.Failed(it) }

            if (!connected) {
                handshakeAttemptedForCurrentPresence = false
            } else if (
                rawState is NoiseSession.NoiseSessionState.Uninitialized &&
                !handshakeAttemptedForCurrentPresence
            ) {
                runCatching { gateway.initiatePrivateSession(peerId) }
                handshakeAttemptedForCurrentPresence = true
            } else if (
                rawState is NoiseSession.NoiseSessionState.Handshaking ||
                rawState is NoiseSession.NoiseSessionState.Established
            ) {
                handshakeAttemptedForCurrentPresence = true
            }

            val conversationId = canonicalConversationId(peerId)
            val hasHistory = source.privateMessages.value.entries.any { (key, messages) ->
                canonicalConversationId(key) == conversationId && messages.isNotEmpty()
            }
            emit(mapPrivateSessionState(rawState, connected, hasHistory))
            delay(sessionPollMillis)
        }
    }.distinctUntilChanged()

    override fun openPrivateConversation(peerId: String) {
        source.setSelectedPrivateConversation(canonicalConversationId(peerId))
        val currentState = runCatching { gateway.privateSessionState(peerId) }
            .getOrElse { NoiseSession.NoiseSessionState.Failed(it) }
        if (peerId in source.peers.value &&
            currentState is NoiseSession.NoiseSessionState.Uninitialized
        ) {
            runCatching { gateway.initiatePrivateSession(peerId) }
        }
    }

    override fun closePrivateConversation() {
        source.setSelectedPrivateConversation(null)
    }

    override suspend fun sendPrivateMessage(
        peerId: String,
        recipientNickname: String,
        content: String
    ): Result<Unit> {
        val normalized = content.trim()
        if (normalized.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty"))
        }
        if (peerId !in source.peers.value) {
            return Result.failure(IllegalStateException("Nearby person is unavailable"))
        }
        if (gateway.privateSessionState(peerId) !is NoiseSession.NoiseSessionState.Established) {
            return Result.failure(IllegalStateException("Secure session is not established"))
        }

        val messageId = newMessageId()
        val conversationId = canonicalConversationId(peerId)
        val message = BitchatMessage(
            id = messageId,
            sender = source.nickname.value.ifBlank { "Me" },
            content = normalized,
            timestamp = Date(nowMillis()),
            isRelay = false,
            isPrivate = true,
            recipientNickname = recipientNickname,
            senderPeerID = gateway.myPeerId,
            deliveryStatus = DeliveryStatus.Sending
        )
        if (!source.addPrivateMessageDurably(conversationId, message)) {
            return Result.failure(IllegalStateException("Message could not be saved"))
        }

        return runCatching {
            gateway.sendPrivateMessage(
                peerId = peerId,
                recipientNickname = recipientNickname,
                content = normalized,
                messageId = messageId
            )
        }.onFailure { failure ->
            source.updatePrivateMessageStatus(
                messageId,
                DeliveryStatus.Failed(
                    failure.message?.takeIf(String::isNotBlank) ?: "Message could not be sent"
                )
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

internal fun mapPrivateMessages(
    messages: List<BitchatMessage>,
    myPeerId: String,
    conversationId: String
): List<PrivateChatMessage> = messages
    .asSequence()
    .filter { it.isPrivate && it.channel == null }
    .distinctBy(BitchatMessage::id)
    .map { message ->
        val isMine = message.senderPeerID == myPeerId
        PrivateChatMessage(
            id = message.id,
            conversationId = conversationId,
            senderPeerId = message.senderPeerID,
            nickname = message.sender.ifBlank { "Unknown" },
            text = message.content,
            timestampMillis = message.timestamp.time,
            isMine = isMine,
            deliveryState = message.deliveryStatus.toChatDeliveryState(isMine)
        )
    }
    .sortedWith(compareBy<PrivateChatMessage> { it.timestampMillis }.thenBy { it.id })
    .toList()

internal fun mapPrivateSessionState(
    state: NoiseSession.NoiseSessionState,
    connected: Boolean,
    hasHistory: Boolean
): PrivateSessionState {
    if (!connected) {
        return if (hasHistory || state is NoiseSession.NoiseSessionState.Established) {
            PrivateSessionState.Reconnecting
        } else {
            PrivateSessionState.Unavailable
        }
    }
    return when (state) {
        is NoiseSession.NoiseSessionState.Uninitialized,
        is NoiseSession.NoiseSessionState.Handshaking -> PrivateSessionState.Establishing
        is NoiseSession.NoiseSessionState.Established -> PrivateSessionState.Encrypted
        is NoiseSession.NoiseSessionState.Failed -> PrivateSessionState.Error(
            state.error.message?.takeIf(String::isNotBlank) ?: "Secure session failed"
        )
    }
}

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
