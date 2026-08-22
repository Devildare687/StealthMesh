package io.github.devildare687.stealthmesh.stealthmesh

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StealthMeshViewModel(
    private val repository: StealthMeshRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val draft = MutableStateFlow(savedStateHandle[DRAFT_KEY] ?: "")
    private val isSending = MutableStateFlow(false)
    private val selectedPrivateConversation = MutableStateFlow(
        savedStateHandle.get<String>(PRIVATE_PEER_ID_KEY)?.let { peerId ->
            PrivateConversationTarget(
                peerId = peerId,
                nickname = savedStateHandle[PRIVATE_PEER_NICKNAME_KEY] ?: "Nearby person"
            )
        }
    )
    private val privateDraft = MutableStateFlow(savedStateHandle[PRIVATE_DRAFT_KEY] ?: "")
    private val isSendingPrivate = MutableStateFlow(false)
    private val privateSendError = MutableStateFlow<String?>(null)

    private val repositoryState = combine(
        repository.connectionState,
        repository.peers,
        repository.publicMessages,
        repository.nickname
    ) { connectionState, peers, messages, nickname ->
        RepositoryState(connectionState, peers, messages, nickname)
    }

    private val privateConversationState = selectedPrivateConversation.flatMapLatest { target ->
        if (target == null) {
            flowOf(PrivateConversationState())
        } else {
            combine(
                repository.privateSessionState(target.peerId),
                repository.privateMessages(target.peerId)
            ) { sessionState, messages ->
                PrivateConversationState(
                    target = target,
                    sessionState = sessionState,
                    messages = messages
                )
            }
        }
    }

    private val privatePresentation = combine(
        privateConversationState,
        privateDraft,
        isSendingPrivate,
        privateSendError
    ) { conversation, currentDraft, sending, error ->
        PrivatePresentation(conversation, currentDraft, sending, error)
    }

    val uiState: StateFlow<StealthMeshUiState> = combine(
        repositoryState,
        draft,
        isSending,
        privatePresentation
    ) { state, currentDraft, sending, privateState ->
        StealthMeshUiState(
            connectionState = state.connectionState,
            peers = state.peers,
            messages = state.messages,
            nickname = state.nickname,
            draft = currentDraft,
            isSending = sending,
            privateConversation = privateState.conversation.target,
            privateSessionState = privateState.conversation.sessionState,
            privateMessages = privateState.conversation.messages,
            privateDraft = privateState.draft,
            isSendingPrivate = privateState.isSending,
            privateSendError = privateState.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StealthMeshUiState(draft = draft.value)
    )

    init {
        selectedPrivateConversation.value?.let { target ->
            repository.openPrivateConversation(target.peerId)
        }
    }

    fun updateDraft(value: String) {
        draft.value = value
        savedStateHandle[DRAFT_KEY] = value
    }

    fun sendMessage() {
        val content = draft.value
        if (content.isBlank() || isSending.value) return

        isSending.value = true
        val result = repository.sendPublicMessage(content)
        if (result.isSuccess) {
            updateDraft("")
        }
        isSending.value = false
    }

    fun openPrivateConversation(peer: NearbyPeer) {
        val target = PrivateConversationTarget(peer.peerId, peer.nickname)
        privateSendError.value = null
        selectedPrivateConversation.value = target
        savedStateHandle[PRIVATE_PEER_ID_KEY] = target.peerId
        savedStateHandle[PRIVATE_PEER_NICKNAME_KEY] = target.nickname
        repository.openPrivateConversation(target.peerId)
    }

    fun closePrivateConversation() {
        repository.closePrivateConversation()
        selectedPrivateConversation.value = null
        privateSendError.value = null
        updatePrivateDraft("")
        savedStateHandle[PRIVATE_PEER_ID_KEY] = null
        savedStateHandle[PRIVATE_PEER_NICKNAME_KEY] = null
    }

    fun updatePrivateDraft(value: String) {
        privateDraft.value = value
        privateSendError.value = null
        savedStateHandle[PRIVATE_DRAFT_KEY] = value
    }

    fun sendPrivateMessage() {
        val target = selectedPrivateConversation.value ?: return
        val content = privateDraft.value
        if (
            content.isBlank() ||
            isSendingPrivate.value ||
            uiState.value.privateSessionState != PrivateSessionState.Encrypted
        ) return

        viewModelScope.launch {
            isSendingPrivate.value = true
            privateSendError.value = null
            val result = repository.sendPrivateMessage(
                peerId = target.peerId,
                recipientNickname = target.nickname,
                content = content
            )
            if (result.isSuccess) {
                updatePrivateDraft("")
            } else {
                privateSendError.value = result.exceptionOrNull()?.message
                    ?.takeIf(String::isNotBlank)
                    ?: "Private message could not be sent"
            }
            isSendingPrivate.value = false
        }
    }

    fun onStarting() = repository.updateRuntimeState(MeshRuntimeState.Starting)

    fun onDiscovering() = repository.updateRuntimeState(MeshRuntimeState.Discovering)

    fun onMeshRunning() = repository.updateRuntimeState(MeshRuntimeState.Running)

    fun onRecovering() = repository.updateRuntimeState(MeshRuntimeState.Recovering)

    fun onBluetoothOff() = repository.updateRuntimeState(MeshRuntimeState.BluetoothOff)

    fun onPermissionRequired() = repository.updateRuntimeState(MeshRuntimeState.PermissionRequired)

    fun onError(message: String) = repository.updateRuntimeState(MeshRuntimeState.Error(message))

    companion object {
        private const val DRAFT_KEY = "stealthmesh_nearby_mesh_draft"
        private const val PRIVATE_DRAFT_KEY = "stealthmesh_private_draft"
        private const val PRIVATE_PEER_ID_KEY = "stealthmesh_private_peer_id"
        private const val PRIVATE_PEER_NICKNAME_KEY = "stealthmesh_private_peer_nickname"
    }

    private data class RepositoryState(
        val connectionState: MeshConnectionState,
        val peers: List<NearbyPeer>,
        val messages: List<NearbyMeshMessage>,
        val nickname: String
    )

    private data class PrivateConversationState(
        val target: PrivateConversationTarget? = null,
        val sessionState: PrivateSessionState = PrivateSessionState.Unavailable,
        val messages: List<PrivateChatMessage> = emptyList()
    )

    private data class PrivatePresentation(
        val conversation: PrivateConversationState,
        val draft: String,
        val isSending: Boolean,
        val error: String?
    )
}
