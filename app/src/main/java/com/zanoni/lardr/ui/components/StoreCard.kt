package com.zanoni.lardr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zanoni.lardr.data.model.Store
import com.zanoni.lardr.data.model.User

@Composable
fun StoreCard(
    store: Store,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (String) -> Unit,
    friends: List<User>,
    pendingInviteUserIds: List<String> = emptyList(),
    onShareStore: (List<String>) -> Unit,
    onNavigateToFriends: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showShareDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = store.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${store.shoppingList.size} items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = { showShareDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share store",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = { showEditDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit store",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showShareDialog) {
        ShareStoreDialog(
            storeName = store.name,
            friends = friends,
            existingMemberIds = store.memberIds,
            pendingInviteUserIds = pendingInviteUserIds,
            onDismiss = { showShareDialog = false },
            onConfirm = { friendIds ->
                onShareStore(friendIds)
                showShareDialog = false
            },
            onNavigateToFriends = onNavigateToFriends
        )
    }

    if (showEditDialog) {
        UpdateStoreDialog(
            storeName = store.name,
            onDismiss = { showEditDialog = false },
            onUpdateName = { newName ->
                onUpdate(newName)
                showEditDialog = false
            },
            onDelete = {
                showEditDialog = false
                onDelete()
            }
        )
    }
}