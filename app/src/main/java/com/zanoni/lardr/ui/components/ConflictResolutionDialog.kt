package com.zanoni.lardr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.zanoni.lardr.data.model.ConflictStrategy

@Composable
fun ConflictResolutionDialog(
    ingredientName: String,
    existingQuantity: String,
    newQuantity: String,
    onDismiss: () -> Unit,
    onResolve: (ConflictStrategy, remember: Boolean) -> Unit
) {
    var rememberChoice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ingredient Already Exists") },
        text = {
            Column {
                Text("'$ingredientName' is already in your list")
                Spacer(modifier = Modifier.height(8.dp))
                if (existingQuantity.isNotBlank()) {
                    Text("Current quantity: $existingQuantity", style = MaterialTheme.typography.bodyMedium)
                }
                if (newQuantity.isNotBlank()) {
                    Text("New quantity: $newQuantity", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("What would you like to do?", style = MaterialTheme.typography.titleSmall)
            }
        },
        confirmButton = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { onResolve(ConflictStrategy.IGNORE, rememberChoice) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ignore")
                    }
                    TextButton(
                        onClick = { onResolve(ConflictStrategy.INCREASE, rememberChoice) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Quantities")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { onResolve(ConflictStrategy.REPLACE, rememberChoice) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Replace")
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it }
                    )
                    Text("Remember my choice for this item", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    )
}