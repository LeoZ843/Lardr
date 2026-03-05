package com.zanoni.lardr.data.model

data class Recipe(
    val id: String = "",
    val name: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val periodicity: Int? = null,
    val conflictStrategy: String? = null,
    val lastAddedWeek: Int? = null
)

data class RecipeIngredient(
    val name: String = "",
    val quantity: String? = null
)