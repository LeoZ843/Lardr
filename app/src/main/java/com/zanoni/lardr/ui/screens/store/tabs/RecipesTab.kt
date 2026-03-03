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
import androidx.compose.material.icons.filled.MoreVert
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
import com.zanoni.lardr.data.model.Recipe

@Composable
fun RecipesTab(
    recipes: List<Recipe>,
    onRecipeClick: (String) -> Unit,
    onAddRecipeToList: (String) -> Unit,
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

    if (recipes.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No recipes yet\nTap + to create a recipe",
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
            items(recipes.sortedBy { it.name.lowercase() }) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    onClick = { onRecipeClick(recipe.id) },
                    onAddToList = { onAddRecipeToList(recipe.id) }
                )
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: Recipe,
    onClick: () -> Unit,
    onAddToList: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
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
                    text = recipe.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Recipe options",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${recipe.ingredients.size} ingredient${if (recipe.ingredients.size != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (recipe.periodicity != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Every ${recipe.periodicity} week${if (recipe.periodicity > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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