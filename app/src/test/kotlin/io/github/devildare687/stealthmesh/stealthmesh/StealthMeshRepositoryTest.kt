package io.github.devildare687.stealthmesh.stealthmesh

import androidx.lifecycle.SavedStateHandle
import io.github.devildare687.stealthmesh.model.BitchatMessage
import io.github.devildare687.stealthmesh.model.DeliveryStatus
import io.github.devildare687.stealthmesh.noise.NoiseSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class StealthMeshRepositoryTest {
    @Test
    fun `same packet arriving over multiple paths renders once`() {
        val packet = message(id = "packet-1", text = "hello")

        val mapped = mapPublicMessages(listOf(packet, packet.copy()), MY_PEER_ID)

        assertEquals(listOf("packet-1"), mapped.map(NearbyMeshMessage::id))
    }

    @Test
    fun `intentional identical messages with different ids both render`() {
        val first = message(id = "packet-1", text = "same text")
        val second = first.copy(id = "packet-2")

        val mapped = mapPublicMessages(listOf(first, second), MY_PEER_ID)

        assertEquals(listOf("packet-1", "packet-2"), mapped.map(NearbyMeshMessage::id))
    }

    @Test
    fun `messages use deterministic timestamp then id ordering`() {
        val later = message(id = "z", text = "later", timestamp = 20)
        val sameTimeSecond = message(id = "b", text = "second", timestamp = 10)
        val sameTimeFirst = message(id = "a", text = "first", timestamp = 10)

        val mapped = mapPublicMessages(
            listOf(later, sameTimeSecond, sameTimeFirst),
            MY_PEER_ID
        )

        assertEquals(listOf("a", "b", "z"), mapped.map(NearbyMeshMessage::id))
    }

    @Test
    fun `peer disappears cleanly and reconnects with same identity`() {
        val connected = mapNearbyPeers(
            peerIds = listOf("peer-a"),
            directPeerIds = setOf("peer-a"),
            nicknames = mapOf("peer-a" to "Alice"),
            rssiByPeer = mapOf("peer-a" to -55)
        )
        val disconnected = mapNearbyPeers(emptyList(), emptySet(), emptyMap(), emptyMap())
        val reconnected = mapNearbyPeers(
            peerIds = listOf("peer-a"),
            directPeerIds = setOf("peer-a"),
            nicknames = mapOf("peer-a" to "Alice"),
            rssiByPeer = mapOf("peer-a" to -70)
        )

        assertEquals("peer-a", connected.single().peerId)
        assertTrue(disconnected.isEmpty())
        assertEquals(connected.single().peerId, reconnected.single().peerId)
        assertEquals(connected.single().nickname, reconnected.single().nickname)
    }

    @Test
    fun `runtime states never report stale active peers`() {
        assertEquals(
            MeshConnectionState.ActiveWithPeers(1),
            MeshRuntimeState.Running.toConnectionState(peerCount = 1)
        )
        assertEquals(
            MeshConnectionState.ActiveNoPeers,
            MeshRuntimeState.Running.toConnectionState(peerCount = 0)
        )
        assertEquals(
            MeshConnectionState.PermissionRequired,
            MeshRuntimeState.PermissionRequired.toConnectionState(peerCount = 1)
        )
        assertEquals(
            MeshConnectionState.BluetoothOff,
            MeshRuntimeState.BluetoothOff.toConnectionState(peerCount = 1)
        )
    }

    @Test
    fun `outgoing public message is handed to mesh and reflected once`() = runTest {
        val source = FakeStateSource(nicknameValue = "Me")
        val gateway = FakeGateway()
        val repository = AppStateStealthMeshRepository(
            gateway = gateway,
            source = source,
            nowMillis = { 42L },
            newMessageId = { "local-message" }
        )

        val result = repository.sendPublicMessage("  hello nearby  ")

        assertTrue(result.isSuccess)
        assertEquals(listOf("hello nearby"), gateway.sent)
        val rendered = repository.publicMessages.first()
        assertEquals(1, rendered.size)
        assertEquals("local-message", rendered.single().id)
        assertTrue(rendered.single().isMine)
        assertEquals(ChatDeliveryState.Sent, rendered.single().deliveryState)
    }

    @Test
    fun `send failure is observable and does not create a local message`() = runTest {
        val source = FakeStateSource(nicknameValue = "Me")
        val repository = AppStateStealthMeshRepository(
            gateway = FakeGateway(sendFailure = IllegalStateException("radio unavailable")),
            source = source
        )

        val result = repository.sendPublicMessage("hello")

        assertTrue(result.isFailure)
        assertTrue(source.publicMessages.value.isEmpty())
        assertEquals(
            MeshConnectionState.Error("radio unavailable"),
            repository.connectionState.first()
        )
    }

    @Test
    fun `valid display name is trimmed and sent through existing nickname updater`() {
        val source = FakeStateSource(nicknameValue = "anon1234")
        val updates = mutableListOf<String>()
        val repository = AppStateStealthMeshRepository(
            gateway = FakeGateway(),
            source = source,
            nicknameUpdater = { nickname ->
                updates += nickname
                source.nickname.value = nickname
            }
        )

        val result = repository.setDisplayName("  Nova  ")

        assertTrue(result.isSuccess)
        assertEquals("Nova", result.getOrNull())
        assertEquals(listOf("Nova"), updates)
        assertEquals("Nova", repository.nickname.value)
    }

    @Test
    fun `empty and over-limit display names are rejected without mutation`() {
        val source = FakeStateSource(nicknameValue = "anon1234")
        val updates = mutableListOf<String>()
        val repository = AppStateStealthMeshRepository(
            gateway = FakeGateway(),
            source = source,
            nicknameUpdater = updates::add
        )

        val empty = repository.setDisplayName("   ")
        val overLimit = repository.setDisplayName("x".repeat(16))

        assertTrue(empty.isFailure)
        assertTrue(overLimit.isFailure)
        assertTrue(updates.isEmpty())
        assertEquals("anon1234", repository.nickname.value)
    }

    @Test
    fun `delivery status mapping preserves valid transitions`() {
        val sending = message("1", "hello").copy(deliveryStatus = DeliveryStatus.Sending)
        val delivered = sending.copy(
            deliveryStatus = DeliveryStatus.Delivered("Alice", Date(2))
        )
        val read = sending.copy(
            deliveryStatus = DeliveryStatus.Read("Alice", Date(3))
        )

        assertEquals(
            ChatDeliveryState.Sending,
            mapPublicMessages(listOf(sending), MY_PEER_ID).single().deliveryState
        )
        assertEquals(
            ChatDeliveryState.Delivered,
            mapPublicMessages(listOf(delivered), MY_PEER_ID).single().deliveryState
        )
        assertEquals(
            ChatDeliveryState.Read,
            mapPublicMessages(listOf(read), MY_PEER_ID).single().deliveryState
        )
    }

    @Test
    fun `private and public messages stay in separate timelines`() {
        val public = message("public", "nearby")
        val private = message("private", "secret").copy(isPrivate = true)

        val publicTimeline = mapPublicMessages(listOf(public, private), MY_PEER_ID)
        val privateTimeline = mapPrivateMessages(
            listOf(public, private),
            MY_PEER_ID,
            "peer-a"
        )

        assertEquals(listOf("public"), publicTimeline.map(NearbyMeshMessage::id))
        assertEquals(listOf("private"), privateTimeline.map(PrivateChatMessage::id))
    }

    @Test
    fun `private conversations do not leak messages between peers`() = runTest {
        val source = FakeStateSource()
        source.privateMessages.value = mapOf(
            "peer-a" to listOf(message("a", "for a").copy(isPrivate = true)),
            "peer-b" to listOf(message("b", "for b").copy(isPrivate = true))
        )
        val repository = AppStateStealthMeshRepository(
            gateway = FakeGateway(),
            source = source,
            canonicalConversationId = { it },
            sessionPollMillis = 1
        )

        assertEquals(listOf("a"), repository.privateMessages("peer-a").first().map { it.id })
        assertEquals(listOf("b"), repository.privateMessages("peer-b").first().map { it.id })
    }

    @Test
    fun `duplicate private id renders once while intentional identical messages remain distinct`() {
        val first = message("one", "same").copy(isPrivate = true)
        val duplicate = first.copy()
        val second = first.copy(id = "two")

        val mapped = mapPrivateMessages(
            listOf(first, duplicate, second),
            MY_PEER_ID,
            "peer-a"
        )

        assertEquals(listOf("one", "two"), mapped.map(PrivateChatMessage::id))
    }

    @Test
    fun `private session UI state reflects real Noise and connection state`() {
        assertEquals(
            PrivateSessionState.Establishing,
            mapPrivateSessionState(NoiseSession.NoiseSessionState.Handshaking, true, false)
        )
        assertEquals(
            PrivateSessionState.Encrypted,
            mapPrivateSessionState(NoiseSession.NoiseSessionState.Established, true, false)
        )
        assertEquals(
            PrivateSessionState.Reconnecting,
            mapPrivateSessionState(NoiseSession.NoiseSessionState.Uninitialized, false, true)
        )
        assertEquals(
            PrivateSessionState.Unavailable,
            mapPrivateSessionState(NoiseSession.NoiseSessionState.Uninitialized, false, false)
        )
        assertTrue(
            mapPrivateSessionState(
                NoiseSession.NoiseSessionState.Failed(IllegalStateException("handshake failed")),
                true,
                false
            ) is PrivateSessionState.Error
        )
    }

    @Test
    fun `opening connected private conversation selects it and starts Noise`() {
        val source = FakeStateSource()
        source.peers.value = listOf("peer-a")
        val gateway = FakeGateway()
        val repository = AppStateStealthMeshRepository(
            gateway = gateway,
            source = source,
            canonicalConversationId = { it }
        )

        repository.openPrivateConversation("peer-a")

        assertEquals("peer-a", source.selectedConversationValue)
        assertEquals(listOf("peer-a"), gateway.handshakes)
    }

    @Test
    fun `private send persists before handing encrypted message to mesh`() = runTest {
        val source = FakeStateSource(nicknameValue = "Me")
        source.peers.value = listOf("peer-a")
        val gateway = FakeGateway().apply {
            sessionStates["peer-a"] = NoiseSession.NoiseSessionState.Established
        }
        val repository = AppStateStealthMeshRepository(
            gateway = gateway,
            source = source,
            nowMillis = { 42L },
            newMessageId = { "private-local" },
            canonicalConversationId = { it }
        )

        val result = repository.sendPrivateMessage("peer-a", "Alice", "  secret  ")

        assertTrue(result.isSuccess)
        assertEquals(listOf("private-local"), source.privateMessages.value["peer-a"]?.map { it.id })
        assertEquals(listOf("secret"), gateway.privateSent.map { it.content })
        assertEquals(DeliveryStatus.Sending, source.privateMessages.value["peer-a"]?.single()?.deliveryStatus)
    }

    @Test
    fun `private send is rejected until Noise is established`() = runTest {
        val source = FakeStateSource()
        source.peers.value = listOf("peer-a")
        val gateway = FakeGateway().apply {
            sessionStates["peer-a"] = NoiseSession.NoiseSessionState.Handshaking
        }
        val repository = AppStateStealthMeshRepository(
            gateway = gateway,
            source = source,
            canonicalConversationId = { it }
        )

        val result = repository.sendPrivateMessage("peer-a", "Alice", "secret")

        assertTrue(result.isFailure)
        assertTrue(source.privateMessages.value.isEmpty())
        assertTrue(gateway.privateSent.isEmpty())
    }

    private fun message(
        id: String,
        text: String,
        timestamp: Long = 10
    ) = BitchatMessage(
        id = id,
        sender = "Alice",
        content = text,
        timestamp = Date(timestamp),
        senderPeerID = "peer-a"
    )

    private class FakeGateway(
        private val sendFailure: Throwable? = null
    ) : StealthMeshGateway {
        override val myPeerId: String = MY_PEER_ID
        val sent = mutableListOf<String>()
        val privateSent = mutableListOf<PrivateSend>()
        val handshakes = mutableListOf<String>()
        val sessionStates = mutableMapOf<String, NoiseSession.NoiseSessionState>()

        override fun peerNicknames(): Map<String, String> = mapOf("peer-a" to "Alice")
        override fun peerRssi(): Map<String, Int> = mapOf("peer-a" to -55)

        override fun sendPublicMessage(content: String) {
            sendFailure?.let { throw it }
            sent += content
        }

        override fun privateSessionState(peerId: String): NoiseSession.NoiseSessionState =
            sessionStates[peerId] ?: NoiseSession.NoiseSessionState.Uninitialized

        override fun initiatePrivateSession(peerId: String) {
            handshakes += peerId
        }

        override fun sendPrivateMessage(
            peerId: String,
            recipientNickname: String,
            content: String,
            messageId: String
        ) {
            sendFailure?.let { throw it }
            privateSent += PrivateSend(peerId, recipientNickname, content, messageId)
        }
    }

    private data class PrivateSend(
        val peerId: String,
        val nickname: String,
        val content: String,
        val messageId: String
    )

    private class FakeStateSource(
        nicknameValue: String = ""
    ) : StealthMeshStateSource {
        override val peers = MutableStateFlow<List<String>>(emptyList())
        override val directPeers = MutableStateFlow<Set<String>>(emptySet())
        override val publicMessages = MutableStateFlow<List<BitchatMessage>>(emptyList())
        override val privateMessages =
            MutableStateFlow<Map<String, List<BitchatMessage>>>(emptyMap())
        override val nickname = MutableStateFlow(nicknameValue)
        var selectedConversationValue: String? = null

        override fun addPublicMessage(message: BitchatMessage) {
            if (publicMessages.value.none { it.id == message.id }) {
                publicMessages.value = publicMessages.value + message
            }
        }

        override fun setSelectedPrivateConversation(conversationId: String?) {
            selectedConversationValue = conversationId
        }

        override suspend fun addPrivateMessageDurably(
            conversationId: String,
            message: BitchatMessage
        ): Boolean {
            if (privateMessages.value.values.flatten().any { it.id == message.id }) return false
            privateMessages.value = privateMessages.value +
                (conversationId to (privateMessages.value[conversationId].orEmpty() + message))
            return true
        }

        override fun updatePrivateMessageStatus(messageId: String, status: DeliveryStatus) {
            privateMessages.value = privateMessages.value.mapValues { (_, messages) ->
                messages.map { message ->
                    if (message.id == messageId) message.copy(deliveryStatus = status) else message
                }
            }
        }
    }

    companion object {
        private const val MY_PEER_ID = "my-peer"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StealthMeshViewModelTest {
    @Test
    fun `recreated viewmodel observes one canonical copy of every message`() = runTest {
        val repository = FakeRepository()
        repository.messages.value = listOf(domainMessage("message-1"))

        val first = StealthMeshViewModel(repository, SavedStateHandle())
        val firstSnapshot = first.uiState.first { it.messages.isNotEmpty() }

        val second = StealthMeshViewModel(repository, SavedStateHandle())
        val secondSnapshot = second.uiState.first { it.messages.isNotEmpty() }

        assertEquals(1, firstSnapshot.messages.size)
        assertEquals(1, secondSnapshot.messages.size)
    }

    @Test
    fun `draft survives viewmodel recreation through saved state`() = runTest {
        val handle = SavedStateHandle()
        val first = StealthMeshViewModel(FakeRepository(), handle)

        first.updateDraft("unfinished thought")
        val recreated = StealthMeshViewModel(FakeRepository(), handle)

        assertEquals("unfinished thought", recreated.uiState.value.draft)
    }

    @Test
    fun `accepted send clears draft without duplicating repository state`() = runTest {
        val repository = FakeRepository()
        val viewModel = StealthMeshViewModel(repository, SavedStateHandle())
        viewModel.onMeshRunning()
        viewModel.updateDraft("hello")

        viewModel.sendMessage()

        assertEquals(listOf("hello"), repository.sent)
        assertEquals("", viewModel.uiState.first { it.draft.isEmpty() }.draft)
    }

    @Test
    fun `display name update is reflected in viewmodel state`() = runTest {
        val repository = FakeRepository()
        val viewModel = StealthMeshViewModel(repository, SavedStateHandle())

        val result = viewModel.setDisplayName("Nova")
        val state = viewModel.uiState.first { it.nickname == "Nova" }

        assertTrue(result.isSuccess)
        assertEquals("Nova", state.nickname)
    }

    @Test
    fun `selecting nearby peer opens only that private conversation`() = runTest {
        val repository = FakeRepository()
        repository.privateSessions["peer-a"] = MutableStateFlow(PrivateSessionState.Encrypted)
        repository.privateMessagesByPeer["peer-a"] = MutableStateFlow(
            listOf(domainPrivateMessage("private-1"))
        )
        val viewModel = StealthMeshViewModel(repository, SavedStateHandle())

        viewModel.openPrivateConversation(
            NearbyPeer("peer-a", "Alice", SignalStrength.Strong, isDirect = true)
        )
        val state = viewModel.uiState.first { it.privateMessages.isNotEmpty() }

        assertEquals("peer-a", state.privateConversation?.peerId)
        assertEquals(listOf("private-1"), state.privateMessages.map { it.id })
        assertEquals(listOf("peer-a"), repository.opened)
    }

    @Test
    fun `encrypted private send clears its own draft`() = runTest {
        val repository = FakeRepository()
        repository.privateSessions["peer-a"] = MutableStateFlow(PrivateSessionState.Encrypted)
        val viewModel = StealthMeshViewModel(repository, SavedStateHandle())
        viewModel.openPrivateConversation(
            NearbyPeer("peer-a", "Alice", SignalStrength.Strong, isDirect = true)
        )
        viewModel.uiState.first { it.privateSessionState == PrivateSessionState.Encrypted }

        viewModel.updatePrivateDraft("secret")
        viewModel.uiState.first {
            it.privateDraft == "secret" &&
                it.privateSessionState == PrivateSessionState.Encrypted
        }

        viewModel.sendPrivateMessage()
        val clearedState = viewModel.uiState.first { it.privateDraft.isEmpty() }

        assertEquals(listOf("secret"), repository.privateSent)
        assertEquals("", clearedState.privateDraft)
    }

    @Test
    fun `recreated viewmodel preserves selected private peer without duplicating messages`() = runTest {
        val handle = SavedStateHandle()
        val repository = FakeRepository()
        repository.privateSessions["peer-a"] = MutableStateFlow(PrivateSessionState.Encrypted)
        repository.privateMessagesByPeer["peer-a"] = MutableStateFlow(
            listOf(domainPrivateMessage("private-1"))
        )
        val first = StealthMeshViewModel(repository, handle)
        first.openPrivateConversation(
            NearbyPeer("peer-a", "Alice", SignalStrength.Strong, isDirect = true)
        )
        first.uiState.first { it.privateMessages.isNotEmpty() }

        val recreated = StealthMeshViewModel(repository, handle)
        val state = recreated.uiState.first { it.privateMessages.isNotEmpty() }

        assertEquals("peer-a", state.privateConversation?.peerId)
        assertEquals(listOf("private-1"), state.privateMessages.map { it.id })
    }

    private class FakeRepository : StealthMeshRepository {
        val connection = MutableStateFlow<MeshConnectionState>(MeshConnectionState.Starting)
        val nearbyPeers = MutableStateFlow<List<NearbyPeer>>(emptyList())
        val messages = MutableStateFlow<List<NearbyMeshMessage>>(emptyList())
        val privateMessagesByPeer =
            mutableMapOf<String, MutableStateFlow<List<PrivateChatMessage>>>()
        val privateSessions = mutableMapOf<String, MutableStateFlow<PrivateSessionState>>()
        override val nickname = MutableStateFlow("Me")
        val sent = mutableListOf<String>()
        val privateSent = mutableListOf<String>()
        val opened = mutableListOf<String>()

        override val connectionState: Flow<MeshConnectionState> = connection
        override val peers: Flow<List<NearbyPeer>> = nearbyPeers
        override val publicMessages: Flow<List<NearbyMeshMessage>> = messages

        override fun updateRuntimeState(state: MeshRuntimeState) {
            connection.value = state.toConnectionState(nearbyPeers.value.size)
        }

        override fun setDisplayName(displayName: String): Result<String> {
            nickname.value = displayName
            return Result.success(displayName)
        }

        override fun sendPublicMessage(content: String): Result<Unit> {
            sent += content
            return Result.success(Unit)
        }

        override fun privateMessages(peerId: String): Flow<List<PrivateChatMessage>> =
            privateMessagesByPeer.getOrPut(peerId) { MutableStateFlow(emptyList()) }

        override fun privateSessionState(peerId: String): Flow<PrivateSessionState> =
            privateSessions.getOrPut(peerId) {
                MutableStateFlow(PrivateSessionState.Unavailable)
            }

        override fun openPrivateConversation(peerId: String) {
            opened += peerId
        }

        override fun closePrivateConversation() = Unit

        override suspend fun sendPrivateMessage(
            peerId: String,
            recipientNickname: String,
            content: String
        ): Result<Unit> {
            privateSent += content
            return Result.success(Unit)
        }
    }

    private fun domainMessage(id: String) = NearbyMeshMessage(
        id = id,
        senderPeerId = "peer-a",
        nickname = "Alice",
        text = "hello",
        timestampMillis = 1,
        isMine = false,
        isPrivate = false,
        deliveryState = ChatDeliveryState.Received
    )

    private fun domainPrivateMessage(id: String) = PrivateChatMessage(
        id = id,
        conversationId = "peer-a",
        senderPeerId = "peer-a",
        nickname = "Alice",
        text = "secret",
        timestampMillis = 1,
        isMine = false,
        deliveryState = ChatDeliveryState.Received
    )
}
