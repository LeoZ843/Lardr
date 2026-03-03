package com.zanoni.lardr.data.repository

import com.zanoni.lardr.data.model.FriendRequest
import com.zanoni.lardr.data.model.FriendRequestStatus
import com.zanoni.lardr.data.model.StoreInvite
import com.zanoni.lardr.data.model.StoreInviteStatus
import com.zanoni.lardr.data.model.User
import com.zanoni.lardr.data.remote.FirebaseDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val dataSource: FirebaseDataSource
) : UserRepository {

    override suspend fun getUserById(userId: String): Flow<Result<User?>> = flow {
        try {
            emit(Result.Loading)
            val user = dataSource.getDocument("users", userId, User::class.java)
            emit(Result.Success(user))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    override suspend fun updateUsername(userId: String, newUsername: String): Result<Unit> {
        return try {
            dataSource.updateDocument("users", userId, mapOf("username" to newUsername))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun searchUserByEmail(email: String): Result<User?> {
        return try {
            val users = dataSource.queryDocuments(
                collection = "users",
                field = "email",
                value = email,
                clazz = User::class.java
            )
            Result.Success(users.firstOrNull())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun sendFriendRequest(fromUserId: String, toEmail: String): Result<Unit> {
        return try {
            val searchResult = searchUserByEmail(toEmail)
            val toUser = when (searchResult) {
                is Result.Success -> searchResult.data
                is Result.Error -> return Result.Error(searchResult.exception)
                else -> null
            }

            if (toUser == null) {
                return Result.Error(Exception("User not found"))
            }

            // Prevent sending friend request to yourself
            if (toUser.id == fromUserId) {
                return Result.Error(Exception("You cannot send a friend request to yourself"))
            }

            val fromUser = dataSource.getDocument("users", fromUserId, User::class.java)
                ?: return Result.Error(Exception("Sender not found"))

            // Check if already friends
            if (fromUser.friendIds.contains(toUser.id)) {
                return Result.Error(Exception("You are already friends with this user"))
            }

            // Check if request already exists
            val existingRequests = dataSource.queryDocuments(
                collection = "friendRequests",
                field = "senderId",
                value = fromUserId,
                clazz = FriendRequest::class.java
            )

            val alreadyRequested = existingRequests.any {
                it.receiverId == toUser.id && it.status == FriendRequestStatus.PENDING
            }

            if (alreadyRequested) {
                return Result.Error(Exception("Friend request already sent to this user"))
            }

            val requestId = UUID.randomUUID().toString()
            val friendRequest = FriendRequest(
                id = requestId,
                senderId = fromUserId,
                senderEmail = fromUser.email,
                senderUsername = fromUser.username,
                receiverId = toUser.id,
                status = FriendRequestStatus.PENDING,
                createdAt = System.currentTimeMillis()
            )

            dataSource.setDocument("friendRequests", requestId, friendRequest)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun acceptFriendRequest(userId: String, friendId: String): Result<Unit> {
        return try {
            android.util.Log.d("UserRepository", "acceptFriendRequest: userId=$userId, friendId=$friendId")

            // Verify both users exist
            val receiverUser = dataSource.getDocument("users", userId, User::class.java)
            if (receiverUser == null) {
                android.util.Log.e("UserRepository", "Receiver user not found: $userId")
                return Result.Error(Exception("Receiver user not found"))
            }

            val senderUser = dataSource.getDocument("users", friendId, User::class.java)
            if (senderUser == null) {
                android.util.Log.e("UserRepository", "Sender user not found: $friendId")
                return Result.Error(Exception("Sender user not found"))
            }

            // Query friend requests
            val requests = dataSource.queryDocuments(
                collection = "friendRequests",
                field = "receiverId",
                value = userId,
                clazz = FriendRequest::class.java
            )

            android.util.Log.d("UserRepository", "Found ${requests.size} requests for receiver $userId")

            val request = requests.find {
                it.senderId == friendId && it.status == FriendRequestStatus.PENDING
            }

            if (request == null) {
                android.util.Log.e("UserRepository", "Friend request not found: sender=$friendId, receiver=$userId")
                return Result.Error(Exception("Friend request not found"))
            }

            android.util.Log.d("UserRepository", "Found friend request: ${request.id}")

            // Add to receiver's friend list
            android.util.Log.d("UserRepository", "Adding sender $friendId to receiver $userId friendIds")
            try {
                dataSource.addArrayValueWithMerge("users", userId, "friendIds", friendId)
                android.util.Log.d("UserRepository", "Successfully added to receiver's friendIds")
            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "Failed to add to receiver's friendIds: ${e.message}", e)
                return Result.Error(Exception("Failed to add friend to receiver: ${e.message}"))
            }

            // Add to sender's friend list
            android.util.Log.d("UserRepository", "Adding receiver $userId to sender $friendId friendIds")
            try {
                dataSource.addArrayValueWithMerge("users", friendId, "friendIds", userId)
                android.util.Log.d("UserRepository", "Successfully added to sender's friendIds")
            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "Failed to add to sender's friendIds: ${e.message}", e)
                // Try to rollback receiver's change
                try {
                    dataSource.removeArrayValue("users", userId, "friendIds", friendId)
                } catch (rollbackError: Exception) {
                    android.util.Log.e("UserRepository", "Rollback failed: ${rollbackError.message}")
                }
                return Result.Error(Exception("Failed to add friend to sender: ${e.message}"))
            }

            // Delete the friend request
            android.util.Log.d("UserRepository", "Deleting friend request: ${request.id}")
            try {
                dataSource.deleteDocument("friendRequests", request.id)
                android.util.Log.d("UserRepository", "Successfully deleted friend request")
            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "Failed to delete friend request: ${e.message}", e)
                // Friend relationship is already created, so this is less critical
                android.util.Log.w("UserRepository", "Friend request not deleted but relationship created")
            }

            android.util.Log.d("UserRepository", "Friend request accepted successfully")

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error accepting friend request", e)
            Result.Error(e)
        }
    }

    override suspend fun rejectFriendRequest(userId: String, friendId: String): Result<Unit> {
        return try {
            val requests = dataSource.queryDocuments(
                collection = "friendRequests",
                field = "receiverId",
                value = userId,
                clazz = FriendRequest::class.java
            )

            val request = requests.find {
                it.senderId == friendId && it.status == FriendRequestStatus.PENDING
            }

            if (request == null) {
                return Result.Error(Exception("Friend request not found"))
            }

            // Delete the friend request
            dataSource.deleteDocument("friendRequests", request.id)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun removeFriend(userId: String, friendId: String): Result<Unit> {
        return try {
            dataSource.removeArrayValue("users", userId, "friendIds", friendId)
            dataSource.removeArrayValue("users", friendId, "friendIds", userId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override fun observeFriends(userId: String): Flow<List<User>> {
        return dataSource.observeArrayContains(
            collection = "users",
            field = "friendIds",
            value = userId,
            clazz = User::class.java
        )
    }

    override fun observePendingInvites(userId: String): Flow<List<User>> {
        return flow {
            dataSource.observeQuery(
                collection = "friendRequests",
                field = "receiverId",
                value = userId,
                clazz = FriendRequest::class.java
            ).collect { requests ->
                val pendingRequests = requests.filter { it.status == FriendRequestStatus.PENDING }

                val senderUsers = pendingRequests.mapNotNull { request ->
                    try {
                        dataSource.getDocument("users", request.senderId, User::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }

                emit(senderUsers)
            }
        }
    }

    override fun observeSentFriendRequests(userId: String): Flow<List<User>> {
        return flow {
            dataSource.observeQuery(
                collection = "friendRequests",
                field = "senderId",
                value = userId,
                clazz = FriendRequest::class.java
            ).collect { requests ->
                val pendingRequests = requests.filter { it.status == FriendRequestStatus.PENDING }

                val receiverUsers = pendingRequests.mapNotNull { request ->
                    try {
                        dataSource.getDocument("users", request.receiverId, User::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }

                emit(receiverUsers)
            }
        }
    }

    override suspend fun cancelFriendRequest(fromUserId: String, toUserId: String): Result<Unit> {
        return try {
            val requests = dataSource.queryDocuments(
                collection = "friendRequests",
                field = "senderId",
                value = fromUserId,
                clazz = FriendRequest::class.java
            )

            val request = requests.find {
                it.receiverId == toUserId && it.status == FriendRequestStatus.PENDING
            }

            if (request == null) {
                return Result.Error(Exception("Friend request not found"))
            }

            dataSource.deleteDocument("friendRequests", request.id)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun sendStoreInvite(
        storeId: String,
        storeName: String,
        friendId: String
    ): Result<Unit> {
        return try {
            val currentUser = dataSource.getCurrentUser()
                ?: return Result.Error(Exception("Not logged in"))

            val user = dataSource.getDocument("users", currentUser.uid, User::class.java)
                ?: return Result.Error(Exception("User not found"))

            val inviteId = UUID.randomUUID().toString()
            val storeInvite = StoreInvite(
                id = inviteId,
                storeId = storeId,
                storeName = storeName,
                ownerId = currentUser.uid,
                ownerUsername = user.username,
                invitedUserId = friendId,
                status = StoreInviteStatus.PENDING,
                createdAt = System.currentTimeMillis()
            )

            dataSource.setDocument("storeInvites", inviteId, storeInvite)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun acceptStoreInvite(inviteId: String): Result<Unit> {
        return try {
            val invite = dataSource.getDocument("storeInvites", inviteId, StoreInvite::class.java)
                ?: return Result.Error(Exception("Store invite not found"))

            dataSource.addArrayValue("stores", invite.storeId, "memberIds", invite.invitedUserId)
            dataSource.updateDocument(
                "storeInvites",
                inviteId,
                mapOf("status" to StoreInviteStatus.ACCEPTED.name)
            )

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun declineStoreInvite(inviteId: String): Result<Unit> {
        return try {
            dataSource.updateDocument(
                "storeInvites",
                inviteId,
                mapOf("status" to StoreInviteStatus.DECLINED.name)
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override fun getPendingStoreInvites(userId: String): Flow<List<StoreInvite>> {
        return dataSource.observeQuery(
            collection = "storeInvites",
            field = "invitedUserId",
            value = userId,
            clazz = StoreInvite::class.java
        )
    }
}