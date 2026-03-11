package com.zanoni.lardr.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zanoni.lardr.data.local.PendingWritesManager
import com.zanoni.lardr.data.local.StoreCache
import com.zanoni.lardr.data.model.Store
import com.zanoni.lardr.data.model.StoreInvite
import com.zanoni.lardr.data.model.User
import com.zanoni.lardr.data.repository.AuthRepository
import com.zanoni.lardr.data.repository.StoreRepository
import com.zanoni.lardr.data.repository.UserRepository
import com.zanoni.lardr.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val stores: List<Store> = emptyList(),
    val friends: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOfflineMode: Boolean = false,
    val storeInvites: List<StoreInvite> = emptyList(),
    val sentStoreInvites: List<StoreInvite> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val storeRepository: StoreRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val storeCache: StoreCache,
    private val pendingWritesManager: PendingWritesManager,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Kept in memory to filter cache entries for this user.
    private var currentUserId: String? = null

    init {
        loadStores()
        checkOfflineMode()
        loadReceivedStoreInvites()
        loadSentStoreInvites()
        loadFriends()
    }

    private fun checkOfflineMode() {
        viewModelScope.launch {
            authRepository.isSkippedLogin().collect { isSkipped ->
                _uiState.value = _uiState.value.copy(isOfflineMode = isSkipped)
            }
        }
    }

    private fun loadReceivedStoreInvites() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            userRepository.getPendingStoreInvites(userId).collect { invites ->
                _uiState.value = _uiState.value.copy(storeInvites = invites)
            }
        }
    }

    private fun loadSentStoreInvites() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            userRepository.getSentStoreInvites(userId).collect { invites ->
                _uiState.value = _uiState.value.copy(sentStoreInvites = invites)
            }
        }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            userRepository.observeFriends(userId).collect { friends ->
                _uiState.value = _uiState.value.copy(friends = friends)
            }
        }
    }

    private fun loadStores() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Not logged in")
                return@launch
            }
            currentUserId = userId

            // Push Firestore → cache (additive merge only — never removes entries).
            // WAL name overrides are applied so a pending rename survives restart.
            launch {
                storeRepository.getStoresForUser(userId).collect { stores ->
                    val corrected = stores.map { store ->
                        val pendingName = pendingWritesManager.queue.getPendingName(store.id)
                        if (pendingName != null) store.copy(name = pendingName) else store
                    }
                    storeCache.merge(corrected)
                }
            }

            // Pull cache → UI. Filter to stores the current user is a member of.
            storeCache.flow.collect { cacheMap ->
                val userStores = cacheMap.values
                    .filter { it.memberIds.contains(userId) }
                    .sortedBy { it.name.lowercase() }
                _uiState.value = _uiState.value.copy(
                    stores = userStores,
                    isLoading = false,
                    error = null
                )
            }
        }
    }

    fun shareStore(storeId: String, friendIds: List<String>) {
        val store = _uiState.value.stores.find { it.id == storeId } ?: return
        applicationScope.launch {
            friendIds.forEach { friendId ->
                userRepository.sendStoreInvite(
                    storeId = store.id,
                    storeName = store.name,
                    friendId = friendId
                )
            }
        }
    }

    fun acceptStoreInvite(inviteId: String) {
        _uiState.value = _uiState.value.copy(
            storeInvites = _uiState.value.storeInvites.filter { it.id != inviteId }
        )
        applicationScope.launch { userRepository.acceptStoreInvite(inviteId) }
    }

    fun declineStoreInvite(inviteId: String) {
        _uiState.value = _uiState.value.copy(
            storeInvites = _uiState.value.storeInvites.filter { it.id != inviteId }
        )
        applicationScope.launch { userRepository.declineStoreInvite(inviteId) }
    }

    fun createStore(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                _uiState.value = _uiState.value.copy(error = "Not logged in")
                return@launch
            }
            val store = com.zanoni.lardr.data.model.Store(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                ownerId = userId,
                memberIds = listOf(userId)
            )
            storeCache.put(store)
            // Fire in applicationScope so the write survives navigation,
            // but surface any error back to the UI via a shared error channel.
            applicationScope.launch {
                try {
                    storeRepository.createStore(store.name, store.ownerId).let { result ->
                        if (result is com.zanoni.lardr.data.repository.Result.Error) {
                            android.util.Log.e("HomeViewModel", "createStore failed: ${result.exception.message}", result.exception)
                            storeCache.remove(store.id)
                            _uiState.value = _uiState.value.copy(error = "Failed to create store: ${result.exception.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "createStore exception: ${e.message}", e)
                    storeCache.remove(store.id)
                    _uiState.value = _uiState.value.copy(error = "Failed to create store: ${e.message}")
                }
            }
        }
    }

    fun deleteStore(storeId: String) {
        // Remove immediately from cache and UI — this is an explicit user action,
        // not a Firestore snapshot, so it is safe to remove from the cache.
        storeCache.remove(storeId)
        applicationScope.launch { storeRepository.deleteStore(storeId) }
    }

    fun updateStoreName(storeId: String, newName: String) {
        if (newName.isBlank()) return
        val existing = storeCache.get(storeId)
        if (existing != null) storeCache.put(existing.copy(name = newName))
        pendingWritesManager.updateStoreName(storeId, newName)
    }

    fun getPendingInviteUserIdsForStore(storeId: String): List<String> =
        _uiState.value.sentStoreInvites
            .filter { it.storeId == storeId }
            .map { it.invitedUserId }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}