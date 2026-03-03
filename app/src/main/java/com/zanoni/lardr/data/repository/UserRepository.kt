package com.zanoni.lardr.data.repository

import com.zanoni.lardr.data.model.FriendRequest
import com.zanoni.lardr.data.model.StoreInvite
import com.zanoni.lardr.data.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    // User operations
    suspend fun getUserById(userId: String): Flow<Result<User?>>
    suspend fun updateUsername(userId: String, newUsername: String): Result<Unit>

    // Friend requests
    suspend fun searchUserByEmail(email: String): Result<User?>
    suspend fun sendFriendRequest(fromUserId: String, toEmail: String): Result<Unit>
    suspend fun acceptFriendRequest(userId: String, friendId: String): Result<Unit>
    suspend fun rejectFriendRequest(userId: String, friendId: String): Result<Unit>
    suspend fun removeFriend(userId: String, friendId: String): Result<Unit>
    suspend fun cancelFriendRequest(fromUserId: String, toUserId: String): Result<Unit>

    // Observe friends and invites
    fun observeFriends(userId: String): Flow<List<User>>
    fun observePendingInvites(userId: String): Flow<List<User>>
    fun observeSentFriendRequests(userId: String): Flow<List<User>>

    // Store invites
    suspend fun sendStoreInvite(storeId: String, storeName: String, friendId: String): Result<Unit>
    suspend fun acceptStoreInvite(inviteId: String): Result<Unit>
    suspend fun declineStoreInvite(inviteId: String): Result<Unit>
    fun getPendingStoreInvites(userId: String): Flow<List<StoreInvite>>
}