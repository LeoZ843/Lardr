package com.zanoni.lardr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddRecipeDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, ingredients: List<Pair<String, String>>, periodicity: Int?) -> Unit
) {
    var recipeName by remember { mutableStateOf("") }
    var periodicity by remember { mutableStateOf("") }
    val ingredientNames = remember { mutableStateListOf("") }
    val ingredientQuantities = remember { mutableStateListOf("") }
    var recipeNameError by remember { mutableStateOf<String?>(null) }
    var periodicityError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        var isValid = true

        if (recipeName.isBlank()) {
            recipeNameError = "Recipe name is required"
            isValid = false
        } else {
            recipeNameError = null
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

        if (ingredientNames.none { it.isNotBlank() }) {
            isValid = false
        }

        return isValid
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Recipe") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = recipeName,
                    onValueChange = {
                        recipeName = it
                        recipeNameError = null
                    },
                    label = { Text("Recipe Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = recipeNameError != null
                )
                if (recipeNameError != null) {
                    Text(
                        text = recipeNameError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ingredients",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = {
                            ingredientNames.add("")
                            ingredientQuantities.add("")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add ingredient",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ingredientNames.forEachIndexed { index, _ ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = ingredientNames[index],
                                    onValueChange = { ingredientNames[index] = it },
                                    label = { Text("Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = ingredientQuantities[index],
                                    onValueChange = { ingredientQuantities[index] = it },
                                    label = { Text("Quantity") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("e.g., 2kg") }
                                )
                            }

                            if (ingredientNames.size > 1) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        ingredientNames.removeAt(index)
                                        ingredientQuantities.removeAt(index)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove ingredient",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validate()) {
                        val ingredientsList = ingredientNames.indices
                            .filter { ingredientNames[it].isNotBlank() }
                            .map { ingredientNames[it] to ingredientQuantities[it] }
                        val periodicityValue = periodicity.toIntOrNull()
                        onAdd(recipeName, ingredientsList, periodicityValue)
                    }
                }
            ) {
                Text("Add Recipe")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}