package com.zanoni.lardr.data.model

data class User(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val friendIds: List<String> = emptyList()
)