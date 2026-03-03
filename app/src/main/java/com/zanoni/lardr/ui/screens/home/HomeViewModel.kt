package com.zanoni.lardr.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zanoni.lardr.data.model.Store
import com.zanoni.lardr.data.repository.AuthRepository
import com.zanoni.lardr.data.repository.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.zanoni.lardr.data.model.StoreInvite
import com.zanoni.lardr.data.repository.UserRepository
import com.zanoni.lardr.data.model.User

data class HomeUiState(
    val stores: List<Store> = emptyList(),
    val friends: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOfflineMode: Boolean = false,
    val storeInvites: List<StoreInvite> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val storeRepository: StoreRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadStores()
        checkOfflineMode()
        loadStoreInvites()
        loadFriends()
    }

    private fun checkOfflineMode() {
        viewModelScope.launch {
            authRepository.isSkippedLogin().collect { isSkipped ->
                _uiState.value = _uiState.value.copy(isOfflineMode = isSkipped)
            }
        }
    }

    private fun loadStoreInvites() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId != null) {
                userRepository.getPendingStoreInvites(userId).collect { invites ->
                    _uiState.value = _uiState.value.copy(storeInvites = invites)
                }
            }
        }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId != null) {
                userRepository.observeFriends(userId).collect { friends ->
                    _uiState.value = _uiState.value.copy(friends = friends)
                }
            }
        }
    }

    fun shareStore(storeId: String, friendIds: List<String>) {
        viewModelScope.launch {
            val store = _uiState.value.stores.find { it.id == storeId } ?: return@launch

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
        viewModelScope.launch {
            userRepository.acceptStoreInvite(inviteId)
        }
    }

    fun declineStoreInvite(inviteId: String) {
        viewModelScope.launch {
            userRepository.declineStoreInvite(inviteId)
        }
    }

    private fun loadStores() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userId = authRepository.getCurrentUserId()
            if (userId != null) {
                storeRepository.getStoresForUser(userId).collect { stores ->
                    _uiState.value = _uiState.value.copy(
                        stores = stores,
                        isLoading = false,
                        error = null
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Not logged in"
                )
            }
        }
    }

    fun createStore(name: String) {
        if (name.isBlank()) return

        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            storeRepository.createStore(name, userId)
        }
    }

    fun deleteStore(storeId: String) {
        viewModelScope.launch {
            storeRepository.deleteStore(storeId)
        }
    }

    fun updateStoreName(storeId: String, newName: String) {
        if (newName.isBlank()) return

        viewModelScope.launch {
            storeRepository.updateStoreName(storeId, newName)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}