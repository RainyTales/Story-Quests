package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Story(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val coverImage: String = "preset:mystic_realm",
    val genre: String = "Adventure",
    val openingLine: String = "",
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatMessage(
    val id: String = "",
    val role: String = "user",
    val content: String = "",
    val timestampMs: Long = 0L,
    val options: List<String> = emptyList(),
    val imagePrompt: String = "",
    val sceneImageUrl: String = "",
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "role" to role,
        "content" to content,
        "timestampMs" to timestampMs,
        "options" to options,
        "imagePrompt" to imagePrompt,
        "sceneImageUrl" to sceneImageUrl,
    )
}

@IgnoreExtraProperties
data class StoryChat(
    @DocumentId val id: String = "",
    val userId: String = "",
    val storyId: String = "",
    val storyTitle: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val summary: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
