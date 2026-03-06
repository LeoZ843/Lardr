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
import androidx.compose.material3.ButtonDefaults
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
import com.zanoni.lardr.data.model.ConflictStrategy
import com.zanoni.lardr.data.model.Ingredient
import com.zanoni.lardr.data.model.StarredIngredient

@Composable
fun EditIngredientDialog(
    ingredient: Ingredient? = null,
    starredIngredient: StarredIngredient? = null,
    initialStarred: Boolean = false,
    initialPeriodicity: Int? = null,
    initialConflictStrategy: ConflictStrategy = ConflictStrategy.ASK,
    onDismiss: () -> Unit,
    onUpdate: (name: String, quantity: String, isStarred: Boolean, periodicity: Int?, conflictStrategy: ConflictStrategy) -> Unit,
    onDelete: () -> Unit
) {
    val isEditingStarred = starredIngredient != null
    val initialName = starredIngredient?.name ?: ingredient?.name ?: ""
    val initialQuantity = starredIngredient?.defaultQuantity ?: ingredient?.quantity ?: ""

    var name by remember { mutableStateOf(initialName) }
    var quantity by remember { mutableStateOf(initialQuantity) }
    var starred by remember { mutableStateOf(initialStarred || isEditingStarred) }
    var periodicity by remember { mutableStateOf((starredIngredient?.periodicity ?: initialPeriodicity)?.toString() ?: "") }
    var conflictStrategy by remember { mutableStateOf(initialConflictStrategy) }
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

        if (starred && periodicity.isNotBlank()) {
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
            title = { Text(if (isEditingStarred) "Delete Starred Ingredient?" else "Delete Ingredient?") },
            text = {
                Text(
                    if (isEditingStarred)
                        "This will remove it from your starred ingredients. Items already in your shopping list won't be affected."
                    else
                        "Are you sure you want to delete this ingredient?"
                )
            },
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
            title = { Text(if (isEditingStarred) "Edit Starred Ingredient" else "Edit Ingredient") },
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

                        if (!isEditingStarred) {
                            Spacer(modifier = Modifier.width(8.dp))

                            IconToggleButton(
                                checked = starred,
                                onCheckedChange = { starred = it }
                            ) {
                                Icon(
                                    imageVector = if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (starred) "Starred" else "Not starred",
                                    tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                        label = { Text(if (isEditingStarred) "Default Quantity (optional)" else "Quantity (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g., 2kg, 500g") }
                    )

                    if (starred || isEditingStarred) {
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
                                    Text("Leave empty for one-time")
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        ConflictStrategyDropdown(
                            selected = conflictStrategy,
                            onSelected = { conflictStrategy = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(if (isEditingStarred) "Delete Starred Ingredient" else "Delete Ingredient")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (validate()) {
                            val periodicityValue = if ((starred || isEditingStarred) && periodicity.isNotBlank()) {
                                periodicity.toIntOrNull()
                            } else {
                                null
                            }
                            onUpdate(name, quantity, starred || isEditingStarred, periodicityValue, conflictStrategy)
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