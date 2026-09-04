package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.PreferencesManager
import com.example.ui.components.ChatDrawerContent
import com.example.ui.components.MessageBubble
import com.example.ui.components.ModelSelectorDialog
import com.example.ui.components.SettingsSheet
import com.example.ui.components.SetupDialog
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val isLoadingModels by viewModel.isLoadingModels.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val attachedImage by viewModel.attachedImage.collectAsStateWithLifecycle()
    val isImageGenMode by viewModel.isImageGenMode.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val showSetupDialog by viewModel.showSetupDialog.collectAsStateWithLifecycle()

    var showModelSelectorDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Android Photo Picker launcher (zero-permission, fully Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachImageUri(uri)
        }
    }

    // Auto-scroll to bottom when messages update
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Show snackbar message when emitted
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbarMessage()
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total == 0) false
            else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible < total - 2
            }
        }
    }

    val currentProvider = viewModel.preferencesManager.activeProvider
    val currentModelId = viewModel.preferencesManager.getSelectedModel(currentProvider)
    val currentApiKey = viewModel.preferencesManager.getApiKey(currentProvider)
    val currentApiKeyMasked = viewModel.preferencesManager.maskApiKey(currentApiKey)
    val currentTheme = viewModel.preferencesManager.themeMode

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawerContent(
                conversations = conversations,
                activeConversationId = activeConversationId,
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onRenameConversation = { id, title ->
                    viewModel.renameConversation(id, title)
                },
                onDeleteConversation = { id ->
                    viewModel.deleteConversation(id)
                },
                onOpenSettings = {
                    scope.launch {
                        drawerState.close()
                        showSettingsSheet = true
                    }
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Nova AI",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )

                            // Quick Model Badge Button
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showModelSelectorDialog = true }
                                    .testTag("topbar_model_selector_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Switch Model",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = currentModelId,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("open_drawer_button")
                        ) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Open Chat History")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.startNewChat() },
                            modifier = Modifier.testTag("topbar_new_chat_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "New Chat")
                        }
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("topbar_settings_button")
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // Chat Messages or Empty State
                    Box(modifier = Modifier.weight(1f)) {
                        if (messages.isEmpty()) {
                            EmptyChatState(
                                onPromptSelected = { prompt ->
                                    viewModel.setInputText(prompt)
                                    viewModel.sendMessage()
                                },
                                onGenerateImageSelected = { prompt ->
                                    viewModel.setInputText(prompt)
                                    if (!isImageGenMode) viewModel.toggleImageGenMode()
                                    viewModel.sendMessage()
                                }
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("chat_messages_list"),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    MessageBubble(
                                        message = msg,
                                        onRetry = { viewModel.retryLastMessage() }
                                    )
                                }
                            }
                        }

                        // Scroll to bottom floating button
                        if (showScrollToBottom) {
                            FloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        if (messages.isNotEmpty()) {
                                            listState.animateScrollToItem(messages.lastIndex)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .size(40.dp),
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                elevation = FloatingActionButtonDefaults.elevation(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Scroll to bottom",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Input Section
                    ChatInputBar(
                        inputText = inputText,
                        attachedImage = attachedImage,
                        isGenerating = isGenerating,
                        isImageGenMode = isImageGenMode,
                        onInputTextChanged = { viewModel.setInputText(it) },
                        onSendMessage = { viewModel.sendMessage() },
                        onStopGeneration = { viewModel.stopGeneration() },
                        onToggleImageGenMode = { viewModel.toggleImageGenMode() },
                        onAttachImage = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveImage = { viewModel.removeAttachedImage() }
                    )
                }
            }
        }
    }

    // Dialogs & Sheets
    if (showModelSelectorDialog) {
        ModelSelectorDialog(
            models = availableModels,
            selectedModelId = currentModelId,
            isLoading = isLoadingModels,
            onModelSelected = { model ->
                viewModel.selectModel(model)
            },
            onRefresh = { viewModel.refreshModels() },
            onDismiss = { showModelSelectorDialog = false }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            sheetState = settingsSheetState,
            currentProvider = currentProvider,
            currentApiKeyMasked = currentApiKeyMasked,
            currentModelId = currentModelId,
            availableModels = availableModels,
            currentTheme = currentTheme,
            isLoadingModels = isLoadingModels,
            onProviderChanged = { viewModel.switchProvider(it) },
            onApiKeySaved = { prov, key -> viewModel.saveApiKey(prov, key) },
            onApiKeyRemoved = { prov -> viewModel.removeApiKey(prov) },
            onModelSelected = { model -> viewModel.selectModel(model) },
            onRefreshModels = { viewModel.refreshModels() },
            onThemeChanged = { mode -> viewModel.preferencesManager.themeMode = mode },
            onTestConnection = { prov, key, cb -> viewModel.testConnection(prov, key, cb) },
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showSetupDialog) {
        SetupDialog(
            initialProvider = currentProvider,
            onConnectAndDetect = { prov, key, cb ->
                viewModel.connectAndAutoDetect(prov, key, cb)
            },
            onDismiss = { viewModel.dismissSetupDialog() }
        )
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    attachedImage: com.example.data.model.ImageAttachment?,
    isGenerating: Boolean,
    isImageGenMode: Boolean,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onToggleImageGenMode: () -> Unit,
    onAttachImage: () -> Unit,
    onRemoveImage: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Attached Image preview
            attachedImage?.let { img ->
                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(File(img.localUri)).build(),
                        contentDescription = "Attached thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = img.fileName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = "Ready for multimodal analysis",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onRemoveImage,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove image",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Quick Tools Bar: Attach Image + Generate Image Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Attach image chip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onAttachImage() }
                        .testTag("attach_image_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Attach image",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Attach Image",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Generate Image Toggle Chip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isImageGenMode) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = if (isImageGenMode) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary) else null,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleImageGenMode() }
                        .testTag("toggle_image_gen_mode_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Generate Image",
                            modifier = Modifier.size(14.dp),
                            tint = if (isImageGenMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isImageGenMode) "Image Mode: ON" else "Generate Image",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isImageGenMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    placeholder = {
                        Text(
                            text = if (isImageGenMode) "Describe an image to generate..." else "Ask Nova AI anything...",
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_textfield"),
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isImageGenMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() && !isGenerating) {
                            onSendMessage()
                        }
                    })
                )

                // Send or Stop Button
                if (isGenerating) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onStopGeneration() }
                            .testTag("stop_generation_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop generation",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    val canSend = inputText.isNotBlank() || attachedImage != null
                    Surface(
                        shape = CircleShape,
                        color = if (canSend) {
                            if (isImageGenMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canSend) { onSendMessage() }
                            .testTag("send_message_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                tint = if (canSend) {
                                    if (isImageGenMode) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyChatState(
    onPromptSelected: (String) -> Unit,
    onGenerateImageSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Nova AI",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Multi-Model AI Assistant • Developer: Rauf",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Suggested Prompts:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        val prompts = listOf(
            "Write a Python script to scrape web articles cleanly",
            "Explain quantum superposition with a real-life analogy",
            "Find and fix common bugs in asynchronous Kotlin coroutines",
            "Create an image of a futuristic city with neon lights at night"
        )

        prompts.forEach { p ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (p.startsWith("Create an image")) {
                            onGenerateImageSelected(p)
                        } else {
                            onPromptSelected(p)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (p.startsWith("Create an image")) Icons.Default.Image else Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = p,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
