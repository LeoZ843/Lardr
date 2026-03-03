package com.zanoni.lardr.ui.screens.store.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zanoni.lardr.data.model.StarredIngredient

@Composable
fun StarredIngredientsTab(
    starredIngredients: List<StarredIngredient>,
    onAddToList: (String) -> Unit,
    onEdit: (StarredIngredient) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    val columns = when {
        screenWidth >= 900 -> 3
        screenWidth >= 600 -> 2
        else -> 1
    }

    val horizontalPadding = when {
        screenWidth >= 900 -> 32.dp
        screenWidth >= 600 -> 24.dp
        else -> 16.dp
    }

    if (starredIngredients.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No starred ingredients yet\nStar items from your shopping list",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = horizontalPadding,
                vertical = 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(starredIngredients.sortedBy { it.name.lowercase() }) { starred ->
                StarredIngredientCard(
                    starredIngredient = starred,
                    onAddToList = { onAddToList(starred.id) },
                    onEdit = { onEdit(starred) }
                )
            }
        }
    }
}

@Composable
private fun StarredIngredientCard(
    starredIngredient: StarredIngredient,
    onAddToList: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = starredIngredient.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (starredIngredient.defaultQuantity.isNotBlank()) {
                Text(
                    text = "Default: ${starredIngredient.defaultQuantity}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (starredIngredient.periodicity != null) {
                Text(
                    text = "Every ${starredIngredient.periodicity} week${if (starredIngredient.periodicity > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                androidx.compose.material3.Button(
                    onClick = onAddToList,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add to list")
                }
            }
        }
    }
}