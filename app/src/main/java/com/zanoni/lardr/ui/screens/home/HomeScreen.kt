package com.zanoni.lardr.ui.screens.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.zanoni.lardr.data.model.Store
import com.zanoni.lardr.ui.components.BottomNavBar
import com.zanoni.lardr.ui.components.LoadingButton
import com.zanoni.lardr.ui.components.StoreInviteCard
import com.zanoni.lardr.data.model.StoreInvite
import com.zanoni.lardr.ui.components.StoreCard

@Composable
fun HomeScreen(
    onNavigateToStore: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }

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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.tertiary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create store"
                )
            }
        }
    ) { paddingValues ->
        HomeContent(
            stores = uiState.stores,
            isLoading = uiState.isLoading,
            isOfflineMode = uiState.isOfflineMode,
            onStoreClick = onNavigateToStore,
            onDeleteStore = { viewModel.deleteStore(it) },
            onUpdateStore = { storeId, newName -> viewModel.updateStoreName(storeId, newName) },
            storeInvites = uiState.storeInvites,
            onAcceptInvite = { viewModel.acceptStoreInvite(it) },
            onDeclineInvite = { viewModel.declineStoreInvite(it) },
            onNavigate = onNavigate,
            viewModel = viewModel,
            uiState = uiState,
            modifier = Modifier.padding(paddingValues)
        )
    }

    if (showCreateDialog) {
        CreateStoreDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { storeName ->
                viewModel.createStore(storeName)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun HomeContent(
    stores: List<Store>,
    isLoading: Boolean,
    isOfflineMode: Boolean,
    onStoreClick: (String) -> Unit,
    onDeleteStore: (String) -> Unit,
    onUpdateStore: (String, String) -> Unit,
    storeInvites: List<StoreInvite>,
    onAcceptInvite: (String) -> Unit,
    onDeclineInvite: (String) -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    uiState: HomeUiState,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    val columns = when {
        screenWidth >= 900 -> 3
        screenWidth >= 600 -> 2
        else -> 1
    }

    val maxWidth = when {
        screenWidth >= 900 -> 800.dp
        screenWidth >= 600 -> 600.dp
        else -> 600.dp
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            stores.isEmpty() -> {
                EmptyStoresView(isOfflineMode = isOfflineMode)
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = maxWidth)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                        Text(
                            text = "Lardr",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (storeInvites.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                            Column(
                                modifier = Modifier.padding(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                storeInvites.forEach { invite ->
                                    StoreInviteCard(
                                        invite = invite,
                                        onAccept = { onAcceptInvite(invite.id) },
                                        onDecline = { onDeclineInvite(invite.id) }
                                    )
                                }
                            }
                        }
                    }

                    items(stores) { store ->
                        StoreCard(
                            store = store,
                            onClick = { onStoreClick(store.id) },
                            onDelete = { onDeleteStore(store.id) },
                            onUpdate = { newName -> onUpdateStore(store.id, newName) },
                            friends = uiState.friends,
                            onShareStore = { friendIds -> viewModel.shareStore(store.id, friendIds) },
                            onNavigateToFriends = { onNavigate("friends") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStoresView(
    isOfflineMode: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No stores yet",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isOfflineMode) {
                "Tap the + button to create your first store\n(Offline mode - data stored locally)"
            } else {
                "Tap the + button to create your first store"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun CreateStoreDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var storeName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Store") },
        text = {
            Column {
                Text("Enter a name for your new store")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Store name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            LoadingButton(
                text = "Create",
                onClick = { onConfirm(storeName) },
                enabled = storeName.isNotBlank()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
