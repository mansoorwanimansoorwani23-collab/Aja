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

    // Available curated models for active provider
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

    init {
        // Pre-fill from BuildConfig if user hasn't set Gemini key and it's present
        val existingGemini = preferencesManager.getApiKey(PreferencesManager.PROVIDER_GEMINI)
        if (existingGemini.isBlank()) {
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

        refreshModels(autoSelectNewest = true)
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
        viewModelScope.launch {
            _messages.value = repository.getMessagesOnce(conversationId)
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

    private fun selectNewestSuitableModel(provider: String, models: List<AiModel>): AiModel? {
        if (models.isEmpty()) return null
        return if (provider == PreferencesManager.PROVIDER_OPENAI) {
            models.find { it.id == "gpt-4o-mini" }
                ?: models.find { it.id == "gpt-4o" }
                ?: models.firstOrNull { it.supportsStreaming && !it.supportsImageGen }
                ?: models.first()
        } else {
            models.find { it.id == "gemini-3.8-flash" }
                ?: models.find { it.id == "gemini-3.7-flash" }
                ?: models.find { it.id == "gemini-3.5-flash" }
                ?: models.find { it.id == "gemini-3.1-pro-preview" }
                ?: models.find { it.id == "gemini-flash-latest" }
                ?: models.find { it.id == "gemini-2.5-flash" }
                ?: models.firstOrNull { it.supportsStreaming && !it.supportsImageGen }
                ?: models.first()
        }
    }

    fun refreshModels(autoSelectNewest: Boolean = false) {
        viewModelScope.launch {
            _isLoadingModels.value = true
            val provider = preferencesManager.activeProvider
            val res = repository.fetchModelsForProvider(provider)
            res.onSuccess { list ->
                _availableModels.value = list
                val currentModel = preferencesManager.getSelectedModel(provider)
                if (autoSelectNewest || list.none { it.id == currentModel }) {
                    val newest = selectNewestSuitableModel(provider, list)
                    if (newest != null) {
                        preferencesManager.setSelectedModel(provider, newest.id)
                    }
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.message ?: "Failed to refresh models"
            }
            _isLoadingModels.value = false
        }
    }

    fun connectAndAutoDetect(provider: String, key: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            preferencesManager.setApiKey(provider, key)
            preferencesManager.activeProvider = provider
            preferencesManager.isSetupCompleted = true
            repository.clearModelCache(provider)
            _isLoadingModels.value = true

            val res = repository.fetchModelsForProvider(provider, forceRefresh = true)
            _isLoadingModels.value = false

            res.onSuccess { models ->
                _availableModels.value = models
                val newest = selectNewestSuitableModel(provider, models)
                if (newest != null) {
                    preferencesManager.setSelectedModel(provider, newest.id)
                }
                _showSetupDialog.value = false
                onResult(true, "Connected successfully to ${newest?.name ?: provider}")
            }.onFailure { err ->
                onResult(false, err.message ?: "Failed to validate API key")
            }
        }
    }

    fun selectModel(model: AiModel) {
        preferencesManager.setSelectedModel(model.providerId, model.id)
    }

    fun switchProvider(provider: String) {
        preferencesManager.activeProvider = provider
        refreshModels(autoSelectNewest = true)
    }

    fun saveApiKey(provider: String, key: String) {
        preferencesManager.setApiKey(provider, key)
        preferencesManager.isSetupCompleted = true
        repository.clearModelCache(provider)
        refreshModels(autoSelectNewest = true)
    }

    fun removeApiKey(provider: String) {
        preferencesManager.removeApiKey(provider)
        repository.clearModelCache(provider)
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

        val provider = repository.getActiveProvider()
        val selectedModelId = preferencesManager.getSelectedModel(providerId)
        val selectedModel = _availableModels.value.find { it.id == selectedModelId }
            ?: AiModel(id = selectedModelId, name = selectedModelId, providerId = providerId, supportsVision = true, supportsStreaming = true)

        // Strict capability checks
        if (image != null && !selectedModel.supportsVision) {
            _snackbarMessage.value = "Selected model (${selectedModel.name}) does not support image understanding. Please choose a multimodal model."
            return
        }

        if (isImgGen && !selectedModel.supportsImageGen && providerId != PreferencesManager.PROVIDER_OPENAI) {
            _snackbarMessage.value = "Image generation is not supported by ${selectedModel.name}."
            return
        }

        // Clear input box and attachment immediately
        _inputText.value = ""
        _attachedImage.value = null
        _isImageGenMode.value = false

        // Auto-update conversation title if it's the first message
        if (_messages.value.isEmpty()) {
            val autoTitle = text.take(32).ifEmpty { "Image Question" }
            viewModelScope.launch {
                repository.renameConversation(convId, autoTitle)
            }
        }

        // 1. Prepare User Message and Placeholder
        val userMsgId = UUID.randomUUID().toString()
        val userMsg = ChatMessage(
            id = userMsgId,
            conversationId = convId,
            role = MessageRole.USER,
            content = text,
            imagePath = image?.localUri
        )

        val assistantMsgId = UUID.randomUUID().toString()
        val assistantPlaceholder = ChatMessage(
            id = assistantMsgId,
            conversationId = convId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )

        // Update in-memory state immediately for instant responsiveness
        _messages.value = _messages.value + userMsg + assistantPlaceholder
        _isGenerating.value = true

        activeJob = viewModelScope.launch {
            if (isImgGen) {
                val imgGenResult = provider.generateImage(apiKey, text, selectedModel)
                _isGenerating.value = false
                imgGenResult.onSuccess { imageUrl ->
                    val completedMsg = assistantPlaceholder.copy(
                        content = "Generated image for: \"$text\"",
                        generatedImageUrl = imageUrl,
                        isStreaming = false
                    )
                    _messages.value = _messages.value.map {
                        if (it.id == assistantMsgId) completedMsg else it
                    }
                    repository.saveMessage(userMsg)
                    repository.saveMessage(completedMsg)
                }.onFailure { err ->
                    val errorMsg = assistantPlaceholder.copy(
                        content = err.message ?: "Failed to generate image.",
                        isError = true,
                        isStreaming = false
                    )
                    _messages.value = _messages.value.map {
                        if (it.id == assistantMsgId) errorMsg else it
                    }
                    repository.saveMessage(userMsg)
                    repository.saveMessage(errorMsg)
                }
            } else {
                val currentChatHistory = _messages.value.filter { it.id != assistantMsgId }
                val accumulatedContent = StringBuilder()

                val chatResult = provider.generateChat(
                    apiKey = apiKey,
                    model = selectedModel,
                    messages = currentChatHistory,
                    attachment = image,
                    onChunk = { chunk ->
                        accumulatedContent.append(chunk)
                        val textSoFar = accumulatedContent.toString()
                        // Immediate in-memory update on chunk arrival
                        _messages.value = _messages.value.map {
                            if (it.id == assistantMsgId) {
                                it.copy(content = textSoFar)
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
                    _messages.value = _messages.value.map {
                        if (it.id == assistantMsgId) completedMsg else it
                    }
                    repository.saveMessage(userMsg)
                    repository.saveMessage(completedMsg)
                }.onFailure { err ->
                    val errorMsg = assistantPlaceholder.copy(
                        content = err.message ?: "An unexpected error occurred.",
                        isError = true,
                        isStreaming = false
                    )
                    _messages.value = _messages.value.map {
                        if (it.id == assistantMsgId) errorMsg else it
                    }
                    repository.saveMessage(userMsg)
                    repository.saveMessage(errorMsg)
                }
            }
        }
    }

    fun stopGeneration() {
        activeJob?.cancel()
        repository.cancelActiveGeneration()
        _isGenerating.value = false

        val currentStreaming = _messages.value.find { it.isStreaming }
        if (currentStreaming != null) {
            val stopped = currentStreaming.copy(
                content = if (currentStreaming.content.isBlank()) "Generation stopped." else currentStreaming.content,
                isStreaming = false
            )
            _messages.value = _messages.value.map {
                if (it.id == currentStreaming.id) stopped else it
            }
            viewModelScope.launch {
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
            _messages.value = _messages.value.filter { it.id != lastMsg.id }
            viewModelScope.launch {
                repository.deleteMessage(lastMsg.id)
            }
        }

        _inputText.value = lastUserMsg.content
        sendMessage()
    }
}
