package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.ConversationEntity
import com.example.data.local.PreferencesManager
import com.example.data.model.AiModel
import com.example.data.model.ChatMessage
import com.example.data.model.ImageAttachment
import com.example.data.model.MessageRole
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    val preferencesManager = PreferencesManager(application)
    val repository = ChatRepository(application, database, preferencesManager)

    // Conversations from Room
    val conversations: StateFlow<List<ConversationEntity>> = repository.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Conversation
    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    // Messages for current conversation
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Available models for active provider
    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    val availableModels: StateFlow<List<AiModel>> = _availableModels.asStateFlow()

    // Loading states
    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Input state
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _attachedImage = MutableStateFlow<ImageAttachment?>(null)
    val attachedImage: StateFlow<ImageAttachment?> = _attachedImage.asStateFlow()

    private val _isImageGenMode = MutableStateFlow(false)
    val isImageGenMode: StateFlow<Boolean> = _isImageGenMode.asStateFlow()

    // Snackbar / Alert Message
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Setup dialog state
    private val _showSetupDialog = MutableStateFlow(false)
    val showSetupDialog: StateFlow<Boolean> = _showSetupDialog.asStateFlow()

    private var activeJob: Job? = null
    private var messagesCollectJob: Job? = null

    init {
        // Pre-fill from BuildConfig if user hasn't set Gemini key and it's present
        val existingGemini = preferencesManager.getApiKey(PreferencesManager.PROVIDER_GEMINI)
        if (existingGemini.isBlank() && try { BuildConfig.GEMINI_API_KEY.isNotEmpty() } catch (_: Throwable) { false }) {
            try {
                if (BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
                    preferencesManager.setApiKey(PreferencesManager.PROVIDER_GEMINI, BuildConfig.GEMINI_API_KEY)
                    preferencesManager.isSetupCompleted = true
                }
            } catch (_: Throwable) {}
        }

        // Show setup on first launch if no key configured
        val activeKey = preferencesManager.getApiKey(preferencesManager.activeProvider)
        if (activeKey.isBlank() && !preferencesManager.isSetupCompleted) {
            _showSetupDialog.value = true
        }

        refreshModels()
        loadOrCreateInitialChat()
    }

    private fun loadOrCreateInitialChat() {
        viewModelScope.launch {
            conversations.collect { list ->
                if (_activeConversationId.value == null) {
                    if (list.isNotEmpty()) {
                        selectConversation(list.first().id)
                    } else {
                        val newId = repository.createNewConversation(title = "Welcome to Nova AI")
                        selectConversation(newId)
                    }
                }
            }
        }
    }

    fun selectConversation(conversationId: String) {
        _activeConversationId.value = conversationId
        messagesCollectJob?.cancel()
        messagesCollectJob = viewModelScope.launch {
            repository.getMessages(conversationId).collect { msgList ->
                _messages.value = msgList
            }
        }
    }

    fun startNewChat() {
        viewModelScope.launch {
            val provider = preferencesManager.activeProvider
            val model = preferencesManager.getSelectedModel(provider)
            val newId = repository.createNewConversation(
                title = "New Chat",
                providerId = provider,
                modelId = model
            )
            selectConversation(newId)
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameConversation(id, newTitle)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = null
                _messages.value = emptyList()
            }
        }
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun toggleImageGenMode() {
        _isImageGenMode.value = !_isImageGenMode.value
    }

    fun attachImageUri(uri: Uri) {
        viewModelScope.launch {
            val attachment = repository.processImageUri(uri)
            if (attachment != null) {
                _attachedImage.value = attachment
            } else {
                _snackbarMessage.value = "Failed to process selected image."
            }
        }
    }

    fun removeAttachedImage() {
        _attachedImage.value = null
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun dismissSetupDialog() {
        _showSetupDialog.value = false
        preferencesManager.isSetupCompleted = true
    }

    fun refreshModels() {
        viewModelScope.launch {
            _isLoadingModels.value = true
            val provider = preferencesManager.activeProvider
            val res = repository.fetchModelsForProvider(provider)
            res.onSuccess { list ->
                _availableModels.value = list
                // If currently selected model is not in list, select the first valid model
                val currentModel = preferencesManager.getSelectedModel(provider)
                if (list.isNotEmpty() && list.none { it.id == currentModel }) {
                    preferencesManager.setSelectedModel(provider, list.first().id)
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.message ?: "Failed to refresh models"
            }
            _isLoadingModels.value = false
        }
    }

    fun selectModel(model: AiModel) {
        preferencesManager.setSelectedModel(model.providerId, model.id)
    }

    fun switchProvider(provider: String) {
        preferencesManager.activeProvider = provider
        refreshModels()
    }

    fun saveApiKey(provider: String, key: String) {
        preferencesManager.setApiKey(provider, key)
        preferencesManager.isSetupCompleted = true
        refreshModels()
    }

    fun removeApiKey(provider: String) {
        preferencesManager.removeApiKey(provider)
        refreshModels()
    }

    fun testConnection(provider: String, key: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val modelId = preferencesManager.getSelectedModel(provider)
            val result = repository.testConnection(provider, key, modelId)
            result.onSuccess { msg ->
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "Connection test failed")
            }
        }
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        val image = _attachedImage.value
        val isImgGen = _isImageGenMode.value

        if (text.isEmpty() && image == null) return

        var convId = _activeConversationId.value
        if (convId == null) {
            viewModelScope.launch {
                convId = repository.createNewConversation(title = text.take(30).ifEmpty { "Chat" })
                selectConversation(convId!!)
                proceedSending(convId!!, text, image, isImgGen)
            }
        } else {
            proceedSending(convId, text, image, isImgGen)
        }
    }

    private fun proceedSending(
        convId: String,
        text: String,
        image: ImageAttachment?,
        isImgGen: Boolean
    ) {
        val providerId = preferencesManager.activeProvider
        val apiKey = preferencesManager.getApiKey(providerId)

        if (apiKey.isBlank()) {
            _snackbarMessage.value = "Please configure your $providerId API Key first."
            _showSetupDialog.value = true
            return
        }

        // Clear input box and attachment immediately
        _inputText.value = ""
        _attachedImage.value = null
        _isImageGenMode.value = false

        val provider = repository.getActiveProvider()
        val selectedModelId = preferencesManager.getSelectedModel(providerId)
        val selectedModel = _availableModels.value.find { it.id == selectedModelId }
            ?: AiModel(id = selectedModelId, name = selectedModelId, providerId = providerId, supportsVision = true, supportsStreaming = true)

        // Capability checks
        if (image != null && !selectedModel.supportsVision) {
            _snackbarMessage.value = "This model does not support image understanding. Please select a multimodal model."
            return
        }

        // Auto-update conversation title if it's the first message
        if (_messages.value.isEmpty()) {
            val autoTitle = text.take(32).ifEmpty { "Image Question" }
            viewModelScope.launch {
                repository.renameConversation(convId, autoTitle)
            }
        }

        // 1. Insert User Message into Room
        val userMsgId = UUID.randomUUID().toString()
        val userMsg = ChatMessage(
            id = userMsgId,
            conversationId = convId,
            role = MessageRole.USER,
            content = text,
            imagePath = image?.localUri
        )

        // 2. Prepare Assistant Placeholder Message
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantPlaceholder = ChatMessage(
            id = assistantMsgId,
            conversationId = convId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )

        viewModelScope.launch {
            repository.saveMessage(userMsg)
            repository.saveMessage(assistantPlaceholder)
        }

        _isGenerating.value = true

        activeJob = viewModelScope.launch {
            if (isImgGen) {
                // Image Generation Flow
                val imgGenResult = provider.generateImage(apiKey, text, selectedModel)
                _isGenerating.value = false
                imgGenResult.onSuccess { imageUrl ->
                    val completedMsg = assistantPlaceholder.copy(
                        content = "Generated image for: \"$text\"",
                        generatedImageUrl = imageUrl,
                        isStreaming = false
                    )
                    repository.updateMessage(completedMsg)
                }.onFailure { err ->
                    val errorMsg = assistantPlaceholder.copy(
                        content = err.message ?: "Failed to generate image.",
                        isError = true,
                        isStreaming = false
                    )
                    repository.updateMessage(errorMsg)
                }
            } else {
                // Text / Multimodal Chat Flow
                val currentChatHistory = _messages.value + userMsg
                val accumulatedContent = StringBuilder()

                val chatResult = provider.generateChat(
                    apiKey = apiKey,
                    model = selectedModel,
                    messages = currentChatHistory,
                    attachment = image,
                    onChunk = { chunk ->
                        accumulatedContent.append(chunk)
                        // Live update in memory for responsive smooth typing
                        _messages.value = _messages.value.map {
                            if (it.id == assistantMsgId) {
                                it.copy(content = accumulatedContent.toString())
                            } else it
                        }
                    }
                )

                _isGenerating.value = false

                chatResult.onSuccess { finalText ->
                    val completedMsg = assistantPlaceholder.copy(
                        content = finalText,
                        isStreaming = false
                    )
                    repository.updateMessage(completedMsg)
                }.onFailure { err ->
                    val errorMsg = assistantPlaceholder.copy(
                        content = err.message ?: "An unexpected error occurred.",
                        isError = true,
                        isStreaming = false
                    )
                    repository.updateMessage(errorMsg)
                }
            }
        }
    }

    fun stopGeneration() {
        activeJob?.cancel()
        _isGenerating.value = false
        // Update any currently streaming message in room to not streaming
        val currentStreaming = _messages.value.find { it.isStreaming }
        if (currentStreaming != null) {
            viewModelScope.launch {
                val stopped = currentStreaming.copy(
                    content = if (currentStreaming.content.isBlank()) "Generation stopped." else currentStreaming.content,
                    isStreaming = false
                )
                repository.updateMessage(stopped)
            }
        }
    }

    fun retryLastMessage() {
        val lastUserMsg = _messages.value.lastOrNull { it.role == MessageRole.USER } ?: return
        val convId = _activeConversationId.value ?: return

        // Remove trailing assistant/error message if any
        val lastMsg = _messages.value.lastOrNull()
        if (lastMsg != null && lastMsg.role == MessageRole.ASSISTANT) {
            viewModelScope.launch {
                repository.deleteMessage(lastMsg.id)
            }
        }

        _inputText.value = lastUserMsg.content
        sendMessage()
    }
}
