package com.zanoni.lardr.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val friendIds: List<String> = emptyList()
)