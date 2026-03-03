package com.zanoni.lardr.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zanoni.lardr.data.model.User
import com.zanoni.lardr.ui.components.BottomNavBar
import com.zanoni.lardr.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showUsernameDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = onNavigate
            )
        }
    ) { paddingValues ->
        SettingsContent(
            uiState = uiState,
            onEditUsername = { showUsernameDialog = true },
            onChangeTheme = { showThemeDialog = true },
            onLogout = { showLogoutDialog = true },
            modifier = Modifier.padding(paddingValues)
        )
    }

    if (showUsernameDialog) {
        EditUsernameDialog(
            currentUsername = uiState.username,
            onDismiss = { showUsernameDialog = false },
            onConfirm = { newUsername ->
                viewModel.updateUsername(newUsername)
                showUsernameDialog = false
            }
        )
    }

    if (showThemeDialog) {
        ThemeDialog(
            currentTheme = uiState.currentTheme,
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { theme ->
                viewModel.setTheme(theme)
                showThemeDialog = false
            }
        )
    }

    if (showLogoutDialog) {
        LogoutDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                viewModel.logout()
                showLogoutDialog = false
                onLogout()
            }
        )
    }
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onEditUsername: () -> Unit,
    onChangeTheme: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    val maxWidth = when {
        screenWidth >= 900 -> 800.dp
        screenWidth >= 600 -> 600.dp
        else -> 600.dp
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = maxWidth),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                ProfileSection(
                    email = uiState.email,
                    username = uiState.username,
                    isOfflineMode = uiState.isOfflineMode,
                    onEditUsername = onEditUsername
                )
            }

            item {
                ThemeSection(
                    currentTheme = uiState.currentTheme,
                    onChangeTheme = onChangeTheme
                )
            }

            item {
                LogoutSection(onLogout = onLogout)
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProfileSection(
    email: String,
    username: String,
    isOfflineMode: Boolean,
    onEditUsername: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Email",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Username",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (!isOfflineMode) {
                    IconButton(onClick = onEditUsername) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit username",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSection(
    currentTheme: ThemeMode,
    onChangeTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (currentTheme) {
                        ThemeMode.LIGHT -> Icons.Default.LightMode
                        ThemeMode.DARK -> Icons.Default.DarkMode
                        ThemeMode.SYSTEM -> Icons.Default.LightMode
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = when (currentTheme) {
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.SYSTEM -> "System default"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(onClick = onChangeTheme) {
                Text("Change")
            }
        }
    }
}

@Composable
private fun LogoutSection(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onLogout,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        )
    ) {
        Text("Logout")
    }
}

@Composable
private fun EditUsernameDialog(
    currentUsername: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var username by remember { mutableStateOf(currentUsername) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Username") },
        text = {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(username) },
                enabled = username.isNotBlank() && username != currentUsername
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ThemeDialog(
    currentTheme: ThemeMode,
    onDismiss: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            Column {
                ThemeMode.values().forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == theme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (theme) {
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.SYSTEM -> "System default"
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun LogoutDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logout?") },
        text = { Text("Are you sure you want to logout?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Logout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}