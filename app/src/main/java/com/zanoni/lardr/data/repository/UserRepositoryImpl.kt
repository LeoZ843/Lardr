package com.zanoni.lardr.data.repository

import com.zanoni.lardr.data.model.FriendRequest
import com.zanoni.lardr.data.model.FriendRequestStatus
import com.zanoni.lardr.data.model.StoreInvite
import com.zanoni.lardr.data.model.StoreInviteStatus
import com.zanoni.lardr.data.model.User
import com.zanoni.lardr.data.remote.FirebaseDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
            val toUser = when (val r = searchUserByEmail(toEmail)) {
                is Result.Success -> r.data
                is Result.Error -> return Result.Error(r.exception)
                else -> null
            } ?: return Result.Error(Exception("User not found"))

            if (toUser.id == fromUserId) {
                return Result.Error(Exception("You cannot send a friend request to yourself"))
            }

            val fromUser = dataSource.getDocument("users", fromUserId, User::class.java)
                ?: return Result.Error(Exception("Sender not found"))

            if (fromUser.friendIds.contains(toUser.id)) {
                return Result.Error(Exception("You are already friends with this user"))
            }

            val existing = dataSource.queryDocuments(
                collection = "friendRequests",
                field = "senderId",
                value = fromUserId,
                clazz = FriendRequest::class.java
            )
            if (existing.any { it.receiverId == toUser.id && it.status == FriendRequestStatus.PENDING.name }) {
                return Result.Error(Exception("Friend request already sent to this user"))
            }

            val requestId = UUID.randomUUID().toString()
            dataSource.setDocument(
                "friendRequests", requestId,
                FriendRequest(
                    id = requestId,
                    senderId = fromUserId,
                    senderEmail = fromUser.email,
                    senderUsername = fromUser.username,
                    receiverId = toUser.id,
                    status = FriendRequestStatus.PENDING.name,
                    createdAt = System.currentTimeMillis()
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun acceptFriendRequest(userId: String, friendId: String): Result<Unit> {
        return try {
            val requests = dataSource.queryDocuments(
                collection = "friendRequests",
                field = "receiverId",
                value = userId,
                clazz = FriendRequest::class.java
            )
            val request = requests.find {
                it.senderId == friendId && it.status == FriendRequestStatus.PENDING.name
            } ?: return Result.Error(Exception("Friend request not found"))

            dataSource.addArrayValueWithMerge("users", userId, "friendIds", friendId)
            dataSource.addArrayValueWithMerge("users", friendId, "friendIds", userId)
            dataSource.deleteDocument("friendRequests", request.id)
            Result.Success(Unit)
        } catch (e: Exception) {
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
                it.senderId == friendId && it.status == FriendRequestStatus.PENDING.name
            } ?: return Result.Error(Exception("Friend request not found"))

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

    override fun observePendingInvites(userId: String): Flow<List<User>> = flow {
        dataSource.observeQuery(
            collection = "friendRequests",
            field = "receiverId",
            value = userId,
            clazz = FriendRequest::class.java
        ).collect { requests ->
            val pending = requests.filter { it.status == FriendRequestStatus.PENDING.name }
            val senders = pending.mapNotNull { req ->
                try { dataSource.getDocument("users", req.senderId, User::class.java) }
                catch (e: Exception) { null }
            }
            emit(senders)
        }
    }

    override fun observeSentFriendRequests(userId: String): Flow<List<User>> = flow {
        dataSource.observeQuery(
            collection = "friendRequests",
            field = "senderId",
            value = userId,
            clazz = FriendRequest::class.java
        ).collect { requests ->
            val pending = requests.filter { it.status == FriendRequestStatus.PENDING.name }
            val receivers = pending.mapNotNull { req ->
                try { dataSource.getDocument("users", req.receiverId, User::class.java) }
                catch (e: Exception) { null }
            }
            emit(receivers)
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
                it.receiverId == toUserId && it.status == FriendRequestStatus.PENDING.name
            } ?: return Result.Error(Exception("Friend request not found"))

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
            dataSource.setDocument(
                "storeInvites", inviteId,
                StoreInvite(
                    id = inviteId,
                    storeId = storeId,
                    storeName = storeName,
                    ownerId = currentUser.uid,
                    ownerUsername = user.username,
                    invitedUserId = friendId,
                    status = StoreInviteStatus.PENDING.name,
                    createdAt = System.currentTimeMillis()
                )
            )
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
            dataSource.deleteDocument("storeInvites", inviteId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun declineStoreInvite(inviteId: String): Result<Unit> {
        return try {
            dataSource.deleteDocument("storeInvites", inviteId)
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
        ).map { invites -> invites.filter { it.status == StoreInviteStatus.PENDING.name } }
    }

    override fun getSentStoreInvites(ownerId: String): Flow<List<StoreInvite>> {
        return dataSource.observeQuery(
            collection = "storeInvites",
            field = "ownerId",
            value = ownerId,
            clazz = StoreInvite::class.java
        ).map { invites -> invites.filter { it.status == StoreInviteStatus.PENDING.name } }
    }
}