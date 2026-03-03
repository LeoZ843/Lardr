package com.zanoni.lardr.data.repository

import android.content.Context
import com.zanoni.lardr.data.model.User
import com.zanoni.lardr.data.remote.FirebaseDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val dataSource: FirebaseDataSource,
    @ApplicationContext private val context: Context
) : AuthRepository {

    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val SKIP_LOGIN_KEY = "skip_login"

    override suspend fun login(email: String, password: String): Result<User> {
        return try {
            val firebaseUser = dataSource.signIn(email, password)
                ?: return Result.Error(Exception("Login failed"))

            setSkipLogin(false)

            val user = dataSource.getDocument("users", firebaseUser.uid, User::class.java)
                ?: return Result.Error(Exception("User not found"))

            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun loginWithGoogle(idToken: String): Result<User> {
        return try {
            val firebaseUser = dataSource.signInWithGoogle(idToken)
                ?: return Result.Error(Exception("Google sign-in failed"))

            setSkipLogin(false)

            var user = dataSource.getDocument("users", firebaseUser.uid, User::class.java)

            if (user == null) {
                user = User(
                    id = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    username = firebaseUser.displayName ?: "",
                    friendIds = emptyList()
                )
                dataSource.setDocument("users", firebaseUser.uid, user)
            }

            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun register(email: String, password: String, username: String): Result<User> {
        return try {
            val firebaseUser = dataSource.signUp(email, password)
                ?: return Result.Error(Exception("Registration failed"))

            setSkipLogin(false)

            val user = User(
                id = firebaseUser.uid,
                email = email,
                username = username,
                friendIds = emptyList()
            )

            dataSource.setDocument("users", firebaseUser.uid, user)

            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun skipLogin(): Result<Unit> {
        return try {
            setSkipLogin(true)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            dataSource.signOut()
            setSkipLogin(false)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override fun getCurrentUser(): Flow<User?> {
        return dataSource.observeAuthState().map { firebaseUser ->
            firebaseUser?.let {
                dataSource.getDocument("users", it.uid, User::class.java)
            }
        }
    }

    override fun isLoggedIn(): Flow<Boolean> {
        return dataSource.observeAuthState().map { it != null }
    }

    override fun isSkippedLogin(): Flow<Boolean> = flow {
        emit(prefs.getBoolean(SKIP_LOGIN_KEY, false))
    }

    override suspend fun updateUsername(username: String): Result<Unit> {
        return try {
            val userId = dataSource.getCurrentUser()?.uid
                ?: return Result.Error(Exception("Not logged in"))

            dataSource.updateDocument("users", userId, mapOf("username" to username))

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getCurrentUserId(): String? {
        return dataSource.getCurrentUser()?.uid
    }

    override fun getCurrentUserEmail(): String? {
        return dataSource.getCurrentUser()?.email
    }

    override suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            dataSource.sendPasswordResetEmail(email)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun setSkipLogin(skipped: Boolean) {
        prefs.edit().putBoolean(SKIP_LOGIN_KEY, skipped).apply()
    }
}