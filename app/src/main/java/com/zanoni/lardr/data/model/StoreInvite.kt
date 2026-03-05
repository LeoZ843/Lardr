package com.zanoni.lardr.data.model

data class StoreInvite(
    val id: String = "",
    val storeId: String = "",
    val storeName: String = "",
    val ownerId: String = "",
    val ownerUsername: String = "",
    val invitedUserId: String = "",
    val status: String = StoreInviteStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis()
)

enum class StoreInviteStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}