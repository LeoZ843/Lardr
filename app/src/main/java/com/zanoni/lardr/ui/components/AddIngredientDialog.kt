package com.zanoni.lardr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun AddIngredientDialog(
    initialIsStarred: Boolean = false,
    existingNames: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onAdd: (name: String, quantity: String, periodicity: Int?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var isStarred by remember { mutableStateOf(initialIsStarred) }
    var periodicity by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var periodicityError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        var isValid = true

        if (name.isBlank()) {
            nameError = "Ingredient name is required"
            isValid = false
        } else if (existingNames.any { it.equals(name.trim(), ignoreCase = true) }) {
            nameError = "An ingredient with this name already exists"
            isValid = false
        } else {
            nameError = null
        }

        if (isStarred && periodicity.isNotBlank()) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Ingredient") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = null
                        },
                        label = { Text("Ingredient Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = nameError != null
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconToggleButton(
                        checked = isStarred,
                        onCheckedChange = { isStarred = it }
                    ) {
                        Icon(
                            imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isStarred) "Starred" else "Not starred",
                            tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

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
                    label = { Text("Quantity (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g., 2kg, 500g, 3 items") }
                )

                if (isStarred) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Periodicity",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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
                                Text("Leave empty for one-time addition")
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validate()) {
                        val periodicityValue = if (isStarred && periodicity.isNotBlank()) {
                            periodicity.toIntOrNull()
                        } else {
                            null
                        }
                        onAdd(name, quantity, periodicityValue)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}