package com.zanoni.lardr.data.model

data class FriendRequest(
    val id: String = "",
    val senderId: String = "",
    val senderEmail: String = "",
    val senderUsername: String = "",
    val receiverId: String = "",
    val status: String = FriendRequestStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis()
)

enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}