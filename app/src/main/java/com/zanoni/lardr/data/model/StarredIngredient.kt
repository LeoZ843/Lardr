package com.zanoni.lardr.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class StarredIngredient(
    val id: String = "",
    val name: String = "",
    val periodicity: Int? = null,
    val defaultQuantity: String = "",
    val conflictStrategy: String = ConflictStrategy.ASK.name,
    val lastAddedWeek: Int? = null
)