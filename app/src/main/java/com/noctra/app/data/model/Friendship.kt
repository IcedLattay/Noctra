package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Friendship(
    val id: String? = null,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("receiver_id") val receiverId: String,
    val status: String = "PENDING",
    @SerialName("created_at") val createdAt: String? = null
)
