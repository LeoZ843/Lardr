package com.zanoni.lardr.data.model

data class StarredIngredient(
    val id: String = "",
    val name: String = "",
    val periodicity: Int? = null,
    val defaultQuantity: String = "",
    val conflictStrategy: ConflictStrategy = ConflictStrategy.ASK,
    val lastAddedWeek: Int? = null
)