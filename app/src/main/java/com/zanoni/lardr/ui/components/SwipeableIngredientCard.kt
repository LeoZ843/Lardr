package com.zanoni.lardr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zanoni.lardr.data.model.Ingredient
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeableIngredientCard(
    ingredient: Ingredient,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val swipeThreshold = 200f

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Background actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (offsetX.value > 0)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.error
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (offsetX.value > 0) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Mark as bought",
                    tint = MaterialTheme.colorScheme.onTertiary
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }

        // Card content
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value > swipeThreshold) {
                                    onSwipeRight()
                                } else if (offsetX.value < -swipeThreshold) {
                                    onSwipeLeft()
                                }
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val newValue = offsetX.value + dragAmount
                                offsetX.snapTo(newValue.coerceIn(-300f, 300f))
                            }
                        }
                    )
                },
            colors = CardDefaults.cardColors(
                containerColor = if (ingredient.bought)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ingredient.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    color = if (ingredient.bought)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (ingredient.bought)
                        TextDecoration.LineThrough
                    else
                        TextDecoration.None
                )

                if (ingredient.quantity.isNotBlank()) {
                    Text(
                        text = ingredient.quantity,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit ingredient",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}