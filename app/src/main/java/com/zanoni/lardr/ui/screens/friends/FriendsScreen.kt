package com.zanoni.lardr.ui.screens.friends

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import com.zanoni.lardr.data.model.User
import com.zanoni.lardr.ui.components.BottomNavBar

@Composable
fun FriendsScreen(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddFriendDialog by remember { mutableStateOf(false) }

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
            if (!uiState.isOfflineMode) {
                FloatingActionButton(
                    onClick = { showAddFriendDialog = true },
                    containerColor = MaterialTheme.colorScheme.tertiary
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Add friend"
                    )
                }
            }
        }
    ) { paddingValues ->
        FriendsContent(
            uiState = uiState,
            onAcceptFriend = { viewModel.acceptFriendRequest(it) },
            onRejectFriend = { viewModel.rejectFriendRequest(it) },
            onRemoveFriend = { viewModel.removeFriend(it) },
            onCancelRequest = { viewModel.cancelFriendRequest(it) },
            modifier = Modifier.padding(paddingValues)
        )
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            onDismiss = { showAddFriendDialog = false },
            onConfirm = { email ->
                viewModel.sendFriendRequest(email)
                showAddFriendDialog = false
            }
        )
    }
}

@Composable
private fun FriendsContent(
    uiState: FriendsUiState,
    onAcceptFriend: (String) -> Unit,
    onRejectFriend: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onCancelRequest: (String) -> Unit,
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
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.widthIn(max = maxWidth),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Friends",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (uiState.pendingInvites.isNotEmpty()) {
                        item {
                            PendingInvitesSection(
                                invites = uiState.pendingInvites,
                                onAccept = onAcceptFriend,
                                onReject = onRejectFriend
                            )
                        }
                    }

                    if (uiState.sentRequests.isNotEmpty()) {
                        item {
                            SentRequestsSection(
                                sentRequests = uiState.sentRequests,
                                onCancel = onCancelRequest
                            )
                        }
                    }

                    item {
                        FriendsListSection(
                            friends = uiState.friends,
                            onRemoveFriend = onRemoveFriend
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingInvitesSection(
    invites: List<User>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
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
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = "Pending Invites",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${invites.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            invites.forEach { user ->
                PendingInviteItem(
                    user = user,
                    onAccept = { onAccept(user.id) },
                    onReject = { onReject(user.id) }
                )
                if (user != invites.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun PendingInviteItem(
    user: User,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row {
            IconButton(onClick = onAccept) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Accept",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onReject) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Reject",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SentRequestsSection(
    sentRequests: List<User>,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = "Sent Requests",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${sentRequests.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            sentRequests.forEach { user ->
                SentRequestItem(
                    user = user,
                    onCancel = { onCancel(user.id) }
                )
                if (user != sentRequests.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SentRequestItem(
    user: User,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = "Pending...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
private fun FriendsListSection(
    friends: List<User>,
    onRemoveFriend: (String) -> Unit,
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
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = "My Friends",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${friends.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (friends.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No friends yet. Tap + to add friends!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))

                friends.forEach { user ->
                    FriendItem(
                        user = user,
                        onRemove = { onRemoveFriend(user.id) }
                    )
                    if (user != friends.last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendItem(
    user: User,
    onRemove: () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = { showRemoveDialog = true }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remove friend",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove Friend?") },
            text = { Text("Are you sure you want to remove ${user.username} from your friends?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove()
                        showRemoveDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AddFriendDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Friend") },
        text = {
            Column {
                Text("Enter your friend's email address")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(email) },
                enabled = email.isNotBlank()
            ) {
                Text("Send Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}