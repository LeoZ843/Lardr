package com.zanoni.lardr.data.model

data class Ingredient(
    val id: String = "",
    val name: String = "",
    val quantity: String = "",
    val bought: Boolean = false,
    val addedBy: String = "",
    val addedAt: Long = System.currentTimeMillis()
)