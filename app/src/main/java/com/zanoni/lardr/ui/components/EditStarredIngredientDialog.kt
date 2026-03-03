package com.zanoni.lardr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zanoni.lardr.data.model.StarredIngredient

@Composable
fun EditStarredIngredientDialog(
    starred: StarredIngredient,
    onDismiss: () -> Unit,
    onUpdate: (name: String, quantity: String, periodicity: Int?) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(starred.name) }
    var quantity by remember { mutableStateOf(starred.defaultQuantity) }
    var periodicity by remember { mutableStateOf(starred.periodicity?.toString() ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var periodicityError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        var isValid = true

        if (name.isBlank()) {
            nameError = "Ingredient name is required"
            isValid = false
        } else {
            nameError = null
        }

        if (periodicity.isNotBlank()) {
            val weeks = periodicity.toIntOrNull()
            if (weeks == null || weeks < 1) {
                periodicityError = "Must be a positive number"
                isValid = false
            } else {
                periodicityError = null
            }
        } else {
            periodicityError = null
        }

        return isValid
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Starred Ingredient?") },
            text = { Text("This will remove it from your starred ingredients. Items already in your shopping list won't be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit Starred Ingredient") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = null
                        },
                        label = { Text("Ingredient Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = nameError != null
                    )
                    if (nameError != null) {
                        Text(
                            text = nameError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Default Quantity (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g., 2kg, 500g") }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = periodicity,
                        onValueChange = {
                            periodicity = it
                            periodicityError = null
                        },
                        label = { Text("Repeat every N weeks (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g., 1, 2, 4") },
                        isError = periodicityError != null,
                        supportingText = {
                            if (periodicityError != null) {
                                Text(
                                    text = periodicityError!!,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("Leave empty for one-time use")
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete Starred Ingredient")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (validate()) {
                            val periodicityValue = periodicity.toIntOrNull()
                            onUpdate(name, quantity, periodicityValue)
                        }
                    }
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
}