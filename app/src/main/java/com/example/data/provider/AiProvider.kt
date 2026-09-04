package com.example.data.provider

import com.example.data.model.AiModel
import com.example.data.model.ChatMessage
import com.example.data.model.ImageAttachment

interface AiProvider {
    val id: String
    val displayName: String

    suspend fun fetchModels(apiKey: String): Result<List<AiModel>>

    suspend fun testConnection(apiKey: String, modelId: String): Result<String>

    suspend fun generateChat(
        apiKey: String,
        model: AiModel,
        messages: List<ChatMessage>,
        attachment: ImageAttachment?,
        onChunk: ((String) -> Unit)?
    ): Result<String>

    suspend fun generateImage(
        apiKey: String,
        prompt: String,
        model: AiModel?
    ): Result<String>
}
