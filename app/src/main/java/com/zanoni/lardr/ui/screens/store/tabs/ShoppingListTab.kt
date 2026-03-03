package com.zanoni.lardr.ui.screens.store.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.zanoni.lardr.data.model.Ingredient
import com.zanoni.lardr.ui.components.SwipeableIngredientCard

@Composable
fun ShoppingListTab(
    items: List<Ingredient>,
    boughtItems: List<Ingredient>,
    onMarkAsBought: (String) -> Unit,
    onMarkAsNotBought: (String) -> Unit,
    onDeleteIngredient: (String) -> Unit,
    onEditIngredient: (Ingredient) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    val horizontalPadding = when {
        screenWidth >= 900 -> 32.dp
        screenWidth >= 600 -> 24.dp
        else -> 16.dp
    }

    val sortedItems = items.sortedBy { it.name.lowercase() }
    val sortedBoughtItems = boughtItems.sortedBy { it.name.lowercase() }

    if (sortedItems.isEmpty() && sortedBoughtItems.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No items yet\nTap + to add ingredients",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = horizontalPadding,
                vertical = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active items
            items(sortedItems, key = { it.id }) { ingredient ->
                SwipeableIngredientCard(
                    ingredient = ingredient,
                    onSwipeRight = { onMarkAsBought(ingredient.id) },
                    onSwipeLeft = { onDeleteIngredient(ingredient.id) },
                    onEdit = { onEditIngredient(ingredient) }
                )
            }

            // Bought section separator
            if (sortedBoughtItems.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                item {
                    Text(
                        text = "Bought",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // Bought items
            items(sortedBoughtItems, key = { it.id }) { ingredient ->
                SwipeableIngredientCard(
                    ingredient = ingredient,
                    onSwipeRight = { onMarkAsNotBought(ingredient.id) },
                    onSwipeLeft = { onDeleteIngredient(ingredient.id) },
                    onEdit = { onEditIngredient(ingredient) }
                )
            }
        }
    }
}