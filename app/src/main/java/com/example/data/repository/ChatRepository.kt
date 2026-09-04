package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.data.local.AppDatabase
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.PreferencesManager
import com.example.data.model.AiModel
import com.example.data.model.ChatMessage
import com.example.data.model.ImageAttachment
import com.example.data.model.MessageRole
import com.example.data.provider.AiProvider
import com.example.data.provider.ProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ChatRepository(
    private val context: Context,
    private val database: AppDatabase,
    val preferencesManager: PreferencesManager
) {
    private val chatDao = database.chatDao()

    private val modelCache = java.util.concurrent.ConcurrentHashMap<String, List<AiModel>>()

    val allConversations: Flow<List<ConversationEntity>> = chatDao.getAllConversations()

    fun getMessages(conversationId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getMessagesOnce(conversationId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        chatDao.getMessagesOnce(conversationId).map { it.toDomain() }
    }

    suspend fun createNewConversation(
        title: String = "New Chat",
        providerId: String = preferencesManager.activeProvider,
        modelId: String = preferencesManager.getSelectedModel(providerId)
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val conversation = ConversationEntity(
            id = id,
            title = title,
            providerId = providerId,
            modelId = modelId
        )
        chatDao.insertConversation(conversation)
        id
    }

    suspend fun renameConversation(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        chatDao.renameConversation(id, newTitle)
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        chatDao.deleteConversationById(id)
    }

    suspend fun saveMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message.toEntity())
        chatDao.touchConversation(message.conversationId)
    }

    suspend fun updateMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        chatDao.updateMessage(message.toEntity())
        chatDao.touchConversation(message.conversationId)
    }

    suspend fun deleteMessage(id: String) = withContext(Dispatchers.IO) {
        chatDao.deleteMessageById(id)
    }

    fun getActiveProvider(): AiProvider {
        return ProviderRegistry.getProvider(preferencesManager.activeProvider)
    }

    suspend fun fetchModelsForProvider(providerId: String, forceRefresh: Boolean = false): Result<List<AiModel>> {
        if (!forceRefresh) {
            val cached = modelCache[providerId]
            if (!cached.isNullOrEmpty()) {
                return Result.success(cached)
            }
        }
        val provider = ProviderRegistry.getProvider(providerId)
        val apiKey = preferencesManager.getApiKey(providerId)
        val result = provider.fetchModels(apiKey)
        result.onSuccess { models ->
            if (models.isNotEmpty()) {
                modelCache[providerId] = models
            }
        }
        return result
    }

    fun clearModelCache(providerId: String? = null) {
        if (providerId != null) {
            modelCache.remove(providerId)
        } else {
            modelCache.clear()
        }
    }

    fun cancelActiveGeneration(providerId: String = preferencesManager.activeProvider) {
        try {
            ProviderRegistry.getProvider(providerId).cancelActiveCall()
        } catch (_: Exception) {}
    }

    suspend fun testConnection(providerId: String, apiKey: String, modelId: String): Result<String> {
        val provider = ProviderRegistry.getProvider(providerId)
        return provider.testConnection(apiKey, modelId)
    }

    suspend fun processImageUri(uri: Uri): ImageAttachment? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Scale down if image is very large (max 1536px width/height) to save bandwidth & memory
            val maxDimension = 1536
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (width * ratio).toInt(),
                    (height * ratio).toInt(),
                    true
                )
            } else {
                originalBitmap
            }

            // Save to internal storage cache file
            val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val localFile = File(attachmentsDir, fileName)

            val outputStream = FileOutputStream(localFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()

            // Base64 encode for API
            val byteStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteStream)
            val base64 = Base64.encodeToString(byteStream.toByteArray(), Base64.NO_WRAP)

            ImageAttachment(
                localUri = localFile.absolutePath,
                base64Data = base64,
                mimeType = "image/jpeg",
                fileName = fileName
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun MessageEntity.toDomain(): ChatMessage {
        return ChatMessage(
            id = id,
            conversationId = conversationId,
            role = when (role.lowercase()) {
                "user" -> MessageRole.USER
                "assistant" -> MessageRole.ASSISTANT
                else -> MessageRole.SYSTEM
            },
            content = content,
            imagePath = imagePath,
            generatedImageUrl = generatedImageUrl,
            timestamp = timestamp,
            isError = isError,
            isStreaming = false
        )
    }

    private fun ChatMessage.toEntity(): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            role = when (role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            },
            content = content,
            imagePath = imagePath,
            generatedImageUrl = generatedImageUrl,
            timestamp = timestamp,
            isError = isError
        )
    }
}
