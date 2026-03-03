package com.zanoni.lardr.ui.screens.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zanoni.lardr.ui.components.LoadingScreen

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LoadingScreen(message = "Loading...")

    LaunchedEffect(uiState.isCheckingAuth, uiState.isAuthenticated) {
        if (!uiState.isCheckingAuth) {
            if (uiState.isAuthenticated) {
                onNavigateToHome()
            } else {
                onNavigateToLogin()
            }
        }
    }
}