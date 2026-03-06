package com.zanoni.lardr.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Store(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val memberIds: List<String> = emptyList(),
    val shoppingList: List<Ingredient> = emptyList(),
    val starredIngredients: List<StarredIngredient> = emptyList(),
    val recipes: List<Recipe> = emptyList()
)