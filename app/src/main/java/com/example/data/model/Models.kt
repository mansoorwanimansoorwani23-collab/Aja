package com.example.data.model

data class AiModel(
    val id: String,
    val name: String,
    val providerId: String,
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    val supportsImageGen: Boolean = false,
    val description: String = ""
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val imagePath: String? = null,
    val generatedImageUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val isStreaming: Boolean = false
)

data class ImageAttachment(
    val localUri: String,
    val base64Data: String,
    val mimeType: String = "image/jpeg",
    val fileName: String = "image.jpg"
)
