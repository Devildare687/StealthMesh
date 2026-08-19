package com.bitchat.android.stealthmesh

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class StealthMeshViewModel(
    private val repository: StealthMeshRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val draft = MutableStateFlow(savedStateHandle[DRAFT_KEY] ?: "")
    private val isSending = MutableStateFlow(false)

    private val repositoryState = combine(
        repository.connectionState,
        repository.peers,
        repository.publicMessages,
        repository.nickname
    ) { connectionState, peers, messages, nickname ->
        RepositoryState(connectionState, peers, messages, nickname)
    }

    val uiState: StateFlow<StealthMeshUiState> = combine(
        repositoryState,
        draft,
        isSending
    ) { state, currentDraft, sending ->
        StealthMeshUiState(
            connectionState = state.connectionState,
            peers = state.peers,
            messages = state.messages,
            nickname = state.nickname,
            draft = currentDraft,
            isSending = sending
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StealthMeshUiState(draft = draft.value)
    )

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

    fun onStarting() = repository.updateRuntimeState(MeshRuntimeState.Starting)

    fun onDiscovering() = repository.updateRuntimeState(MeshRuntimeState.Discovering)

    fun onMeshRunning() = repository.updateRuntimeState(MeshRuntimeState.Running)

    fun onRecovering() = repository.updateRuntimeState(MeshRuntimeState.Recovering)

    fun onBluetoothOff() = repository.updateRuntimeState(MeshRuntimeState.BluetoothOff)

    fun onPermissionRequired() = repository.updateRuntimeState(MeshRuntimeState.PermissionRequired)

    fun onError(message: String) = repository.updateRuntimeState(MeshRuntimeState.Error(message))

    companion object {
        private const val DRAFT_KEY = "stealthmesh_nearby_mesh_draft"
    }

    private data class RepositoryState(
        val connectionState: MeshConnectionState,
        val peers: List<NearbyPeer>,
        val messages: List<NearbyMeshMessage>,
        val nickname: String
    )
}
