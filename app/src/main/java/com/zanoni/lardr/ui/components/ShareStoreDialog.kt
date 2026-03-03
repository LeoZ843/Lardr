package com.zanoni.lardr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.zanoni.lardr.data.model.User

@Composable
fun ShareStoreDialog(
    storeName: String,
    friends: List<User>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    onNavigateToFriends: () -> Unit
) {
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
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (friends.isEmpty()) {
                    Text(
                        text = "You don't have any friends yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        onDismiss()
                        onNavigateToFriends()
                    }) {
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
                            .height(300.dp)
                    ) {
                        items(friends) { friend ->
                            FriendCheckboxItem(
                                friend = friend,
                                isSelected = selectedFriendIds.contains(friend.id),
                                onSelectionChange = { isSelected ->
                                    if (isSelected) {
                                        selectedFriendIds.add(friend.id)
                                    } else {
                                        selectedFriendIds.remove(friend.id)
                                    }
                                }
                            )
                            if (friend != friends.last()) {
                                HorizontalDivider()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            onDismiss()
                            onNavigateToFriends()
                        },
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
                Text(if (selectedFriendIds.size == 1) "Send invite" else "Send ${selectedFriendIds.size} invites")
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
private fun FriendCheckboxItem(
    friend: User,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectionChange(!isSelected) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = friend.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Checkbox(
            checked = isSelected,
            onCheckedChange = onSelectionChange
        )
    }
}