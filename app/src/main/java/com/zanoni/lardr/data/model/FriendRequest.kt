package com.zanoni.lardr.data.model

data class FriendRequest(
    val id: String = "",
    val senderId: String = "",
    val senderEmail: String = "",
    val senderUsername: String = "",
    val receiverId: String = "",
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}