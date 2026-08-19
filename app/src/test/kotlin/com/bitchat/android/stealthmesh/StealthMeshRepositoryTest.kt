package com.bitchat.android.stealthmesh

import androidx.lifecycle.SavedStateHandle
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
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

        override fun peerNicknames(): Map<String, String> = mapOf("peer-a" to "Alice")
        override fun peerRssi(): Map<String, Int> = mapOf("peer-a" to -55)

        override fun sendPublicMessage(content: String) {
            sendFailure?.let { throw it }
            sent += content
        }
    }

    private class FakeStateSource(
        nicknameValue: String = ""
    ) : StealthMeshStateSource {
        override val peers = MutableStateFlow<List<String>>(emptyList())
        override val directPeers = MutableStateFlow<Set<String>>(emptySet())
        override val publicMessages = MutableStateFlow<List<BitchatMessage>>(emptyList())
        override val nickname = MutableStateFlow(nicknameValue)

        override fun addPublicMessage(message: BitchatMessage) {
            if (publicMessages.value.none { it.id == message.id }) {
                publicMessages.value = publicMessages.value + message
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

    private class FakeRepository : StealthMeshRepository {
        val connection = MutableStateFlow<MeshConnectionState>(MeshConnectionState.Starting)
        val nearbyPeers = MutableStateFlow<List<NearbyPeer>>(emptyList())
        val messages = MutableStateFlow<List<NearbyMeshMessage>>(emptyList())
        override val nickname = MutableStateFlow("Me")
        val sent = mutableListOf<String>()

        override val connectionState: Flow<MeshConnectionState> = connection
        override val peers: Flow<List<NearbyPeer>> = nearbyPeers
        override val publicMessages: Flow<List<NearbyMeshMessage>> = messages

        override fun updateRuntimeState(state: MeshRuntimeState) {
            connection.value = state.toConnectionState(nearbyPeers.value.size)
        }

        override fun sendPublicMessage(content: String): Result<Unit> {
            sent += content
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
}
