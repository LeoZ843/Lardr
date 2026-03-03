package com.zanoni.lardr.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zanoni.lardr.data.model.User
import com.zanoni.lardr.data.repository.AuthRepository
import com.zanoni.lardr.data.repository.Result
import com.zanoni.lardr.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsUiState(
    val isLoading: Boolean = true,
    val friends: List<User> = emptyList(),
    val pendingInvites: List<User> = emptyList(),
    val sentRequests: List<User> = emptyList(),
    val error: String? = null,
    val isOfflineMode: Boolean = false
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        loadFriends()
        checkOfflineMode()
    }

    private fun checkOfflineMode() {
        viewModelScope.launch {
            authRepository.isSkippedLogin().collect { isSkipped ->
                _uiState.value = _uiState.value.copy(isOfflineMode = isSkipped)
            }
        }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userId = authRepository.getCurrentUserId()
            if (userId != null) {
                // Observe friends in separate coroutine
                launch {
                    userRepository.observeFriends(userId).collect { friends ->
                        _uiState.value = _uiState.value.copy(
                            friends = friends,
                            isLoading = false
                        )
                    }
                }

                // Observe pending invites in separate coroutine
                launch {
                    userRepository.observePendingInvites(userId).collect { invites ->
                        _uiState.value = _uiState.value.copy(
                            pendingInvites = invites
                        )
                    }
                }

                // Observe sent friend requests in separate coroutine
                launch {
                    userRepository.observeSentFriendRequests(userId).collect { sentRequests ->
                        _uiState.value = _uiState.value.copy(
                            sentRequests = sentRequests
                        )
                    }
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isOfflineMode = true
                )
            }
        }
    }

    fun sendFriendRequest(email: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                _uiState.value = _uiState.value.copy(
                    error = "You must be logged in to send friend requests"
                )
                return@launch
            }

            when (val result = userRepository.sendFriendRequest(userId, email)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Friend request sent to $email"
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.exception.message
                    )
                }
                is Result.Loading -> { }
            }
        }
    }

    fun acceptFriendRequest(friendId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) return@launch

            when (userRepository.acceptFriendRequest(userId, friendId)) {
                is Result.Success -> {
                    // Friends list will update via Flow
                }
                is Result.Error -> { }
                is Result.Loading -> { }
            }
        }
    }

    fun rejectFriendRequest(friendId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) return@launch

            when (userRepository.rejectFriendRequest(userId, friendId)) {
                is Result.Success -> {
                    // Invites list will update via Flow
                }
                is Result.Error -> { }
                is Result.Loading -> { }
            }
        }
    }

    fun removeFriend(friendId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) return@launch

            when (userRepository.removeFriend(userId, friendId)) {
                is Result.Success -> {
                    // Friends list will update via Flow
                }
                is Result.Error -> { }
                is Result.Loading -> { }
            }
        }
    }

    fun cancelFriendRequest(toUserId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) return@launch

            when (userRepository.cancelFriendRequest(userId, toUserId)) {
                is Result.Success -> {
                    // Sent requests will update via Flow
                }
                is Result.Error -> { }
                is Result.Loading -> { }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}