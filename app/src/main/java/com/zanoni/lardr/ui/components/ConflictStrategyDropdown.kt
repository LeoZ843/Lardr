package com.zanoni.lardr.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zanoni.lardr.data.model.ConflictStrategy

private fun ConflictStrategy.label(): String = when (this) {
    ConflictStrategy.ASK -> "Ask every time"
    ConflictStrategy.IGNORE -> "Keep existing"
    ConflictStrategy.INCREASE -> "Add quantities"
    ConflictStrategy.REPLACE -> "Replace with new"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictStrategyDropdown(
    selected: ConflictStrategy,
    onSelected: (ConflictStrategy) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("When already in list") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ConflictStrategy.entries.forEach { strategy ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = strategy.label(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onSelected(strategy)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}