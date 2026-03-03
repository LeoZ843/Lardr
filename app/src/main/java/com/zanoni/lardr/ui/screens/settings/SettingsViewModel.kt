package com.zanoni.lardr.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zanoni.lardr.data.local.PreferencesManager
import com.zanoni.lardr.data.model.User
import com.zanoni.lardr.data.repository.AuthRepository
import com.zanoni.lardr.data.repository.UserRepository
import com.zanoni.lardr.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: User? = null,
    val email: String = "",
    val username: String = "",
    val currentTheme: ThemeMode = ThemeMode.SYSTEM,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOfflineMode: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserData()
        loadTheme()
        checkOfflineMode()
    }

    private fun checkOfflineMode() {
        viewModelScope.launch {
            authRepository.isSkippedLogin().collect { isSkipped ->
                _uiState.value = _uiState.value.copy(isOfflineMode = isSkipped)
            }
        }
    }

    private fun loadUserData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userId = authRepository.getCurrentUserId()
            val email = authRepository.getCurrentUserEmail()

            if (userId != null) {
                userRepository.getUserById(userId).collect { result ->
                    when (result) {
                        is com.zanoni.lardr.data.repository.Result.Success -> {
                            _uiState.value = _uiState.value.copy(
                                user = result.data,
                                email = email ?: "",
                                username = result.data?.username ?: "",
                                isLoading = false
                            )
                        }
                        is com.zanoni.lardr.data.repository.Result.Error -> {
                            _uiState.value = _uiState.value.copy(
                                error = result.exception.message,
                                isLoading = false
                            )
                        }
                        is com.zanoni.lardr.data.repository.Result.Loading -> {
                            _uiState.value = _uiState.value.copy(isLoading = true)
                        }
                    }
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    email = "Offline Mode",
                    username = "Guest",
                    isLoading = false
                )
            }
        }
    }

    private fun loadTheme() {
        viewModelScope.launch {
            preferencesManager.themeMode.collect { theme ->
                _uiState.value = _uiState.value.copy(currentTheme = theme)
            }
        }
    }

    fun updateUsername(newUsername: String) {
        if (newUsername.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Username cannot be empty")
            return
        }

        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            val result = userRepository.updateUsername(userId, newUsername)
            when (result) {
                is com.zanoni.lardr.data.repository.Result.Success -> {
                    _uiState.value = _uiState.value.copy(username = newUsername)
                }
                is com.zanoni.lardr.data.repository.Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.exception.message
                    )
                }
                else -> {}
            }
        }
    }

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(theme)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}