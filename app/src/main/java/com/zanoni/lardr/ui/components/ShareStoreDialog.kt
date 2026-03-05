package com.zanoni.lardr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zanoni.lardr.data.model.StoreInviteStatus
import com.zanoni.lardr.data.model.User

@Composable
fun ShareStoreDialog(
    storeName: String,
    friends: List<User>,
    existingMemberIds: List<String> = emptyList(),
    pendingInviteUserIds: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    onNavigateToFriends: () -> Unit
) {
    val selectableFriends = friends.filter { friend ->
        !existingMemberIds.contains(friend.id) && !pendingInviteUserIds.contains(friend.id)
    }
    val selectedFriendIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Share \"$storeName\"",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (friends.isEmpty()) {
                    Text(
                        text = "You don't have any friends yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { onDismiss(); onNavigateToFriends() }) {
                        Text("Add friends")
                    }
                } else {
                    Text(
                        text = "Select friends to invite",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(friends) { friend ->
                            val isMember = existingMemberIds.contains(friend.id)
                            val isPending = pendingInviteUserIds.contains(friend.id)
                            val isSelectable = !isMember && !isPending

                            FriendInviteItem(
                                friend = friend,
                                isSelected = selectedFriendIds.contains(friend.id),
                                isMember = isMember,
                                isPending = isPending,
                                onSelectionChange = { selected ->
                                    if (!isSelectable) return@FriendInviteItem
                                    if (selected) selectedFriendIds.add(friend.id)
                                    else selectedFriendIds.remove(friend.id)
                                }
                            )
                            if (friend != friends.last()) {
                                HorizontalDivider()
                            }
                        }
                    }

                    TextButton(
                        onClick = { onDismiss(); onNavigateToFriends() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Missing someone? Add friends")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedFriendIds.toList())
                    onDismiss()
                },
                enabled = selectedFriendIds.isNotEmpty()
            ) {
                Text(
                    if (selectedFriendIds.size == 1) "Send invite"
                    else "Send ${selectedFriendIds.size} invites"
                )
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
private fun FriendInviteItem(
    friend: User,
    isSelected: Boolean,
    isMember: Boolean,
    isPending: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    val isSelectable = !isMember && !isPending

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isSelectable) { onSelectionChange(!isSelected) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isSelectable) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = friend.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                isMember -> Text(
                    text = "Already a member",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                isPending -> Text(
                    text = "Invite pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Checkbox(
            checked = isSelected,
            onCheckedChange = if (isSelectable) onSelectionChange else null,
            enabled = isSelectable
        )
    }
}