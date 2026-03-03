package com.zanoni.lardr.data.repository

import com.zanoni.lardr.data.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun loginWithGoogle(idToken: String): Result<User>
    suspend fun register(email: String, password: String, username: String): Result<User>
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun skipLogin(): Result<Unit>
    suspend fun logout(): Result<Unit>
    fun getCurrentUser(): Flow<User?>
    fun isLoggedIn(): Flow<Boolean>
    fun isSkippedLogin(): Flow<Boolean>
    suspend fun updateUsername(username: String): Result<Unit>
    suspend fun getCurrentUserId(): String?
    fun getCurrentUserEmail(): String?
}