package com.zanoni.lardr.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Recipe(
    val id: String = "",
    val name: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val periodicity: Int? = null,
    val conflictStrategy: String? = null,
    val lastAddedWeek: Int? = null
)

@IgnoreExtraProperties
data class RecipeIngredient(
    val name: String = "",
    val quantity: String? = null
)