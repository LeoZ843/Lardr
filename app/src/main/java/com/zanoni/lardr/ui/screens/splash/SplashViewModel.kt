package com.zanoni.lardr.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zanoni.lardr.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplashUiState(
    val isCheckingAuth: Boolean = true,
    val isAuthenticated: Boolean = false
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            // Small delay to prevent flash
            delay(300)

            val userId = authRepository.getCurrentUserId()
            val isSkipped = authRepository.isSkippedLogin()

            // Check if user is authenticated or skipped login
            isSkipped.collect { skipped ->
                val isAuth = userId != null || skipped

                _uiState.value = _uiState.value.copy(
                    isCheckingAuth = false,
                    isAuthenticated = isAuth
                )
            }
        }
    }
}