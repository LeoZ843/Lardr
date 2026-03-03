package com.zanoni.lardr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zanoni.lardr.data.model.ConflictStrategy

@Composable
fun ConflictDialog(
    ingredientName: String,
    existingQuantity: String,
    newQuantity: String,
    onDismiss: () -> Unit,
    onResolve: (strategy: ConflictStrategy, remember: Boolean) -> Unit
) {
    var rememberChoice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicate Ingredient") },
        text = {
            Column {
                Text(
                    text = "\"$ingredientName\" is already in your shopping list.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Current: ${existingQuantity.ifBlank { "no quantity" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Adding: ${newQuantity.ifBlank { "no quantity" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "What would you like to do?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                ConflictOption(
                    title = "Ignore",
                    description = "Keep current, don't add new",
                    onClick = { onResolve(ConflictStrategy.IGNORE, rememberChoice) }
                )

                if (existingQuantity.isNotBlank() && newQuantity.isNotBlank()) {
                    ConflictOption(
                        title = "Increase Quantity",
                        description = "Try to combine quantities",
                        onClick = { onResolve(ConflictStrategy.INCREASE, rememberChoice) }
                    )
                }

                ConflictOption(
                    title = "Replace",
                    description = "Remove current and add new",
                    onClick = { onResolve(ConflictStrategy.REPLACE, rememberChoice) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Remember my choice",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConflictOption(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}