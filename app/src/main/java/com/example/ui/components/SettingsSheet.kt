package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PreferencesManager
import com.example.data.model.AiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    sheetState: SheetState,
    currentProvider: String,
    currentApiKeyMasked: String,
    currentModelId: String,
    availableModels: List<AiModel>,
    currentTheme: String,
    isLoadingModels: Boolean,
    onProviderChanged: (String) -> Unit,
    onApiKeySaved: (provider: String, newKey: String) -> Unit,
    onApiKeyRemoved: (provider: String) -> Unit,
    onModelSelected: (AiModel) -> Unit,
    onRefreshModels: () -> Unit,
    onThemeChanged: (String) -> Unit,
    onTestConnection: (provider: String, key: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var newKeyInput by remember { mutableStateOf("") }
    var isKeyVisible by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure AI Provider, Keys & Models",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }

            HorizontalDivider()

            // 1. AI Provider Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "AI Provider",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val geminiSelected = currentProvider == PreferencesManager.PROVIDER_GEMINI
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (geminiSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (geminiSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onProviderChanged(PreferencesManager.PROVIDER_GEMINI)
                                newKeyInput = ""
                                testResult = null
                            }
                            .testTag("settings_gemini_tab")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Google Gemini",
                                fontWeight = FontWeight.SemiBold,
                                color = if (geminiSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "REST & Vision",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val openaiSelected = currentProvider == PreferencesManager.PROVIDER_OPENAI
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (openaiSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (openaiSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onProviderChanged(PreferencesManager.PROVIDER_OPENAI)
                                newKeyInput = ""
                                testResult = null
                            }
                            .testTag("settings_openai_tab")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "OpenAI",
                                fontWeight = FontWeight.SemiBold,
                                color = if (openaiSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "GPT-4o & DALL·E",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Active Model Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selected Model",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    IconButton(
                        onClick = onRefreshModels,
                        enabled = !isLoadingModels,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isLoadingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh models",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showModelPicker = true }
                        .testTag("settings_model_selector_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val activeModelObj = availableModels.find { it.id == currentModelId }
                            Text(
                                text = activeModelObj?.name ?: currentModelId,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = activeModelObj?.description?.ifEmpty { "Click to change model" } ?: "Click to change model",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Change",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 3. API Key Management
            val isKeyConfigured = currentApiKeyMasked != "Not configured"
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "API Key Management",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }

                // Currently saved masked key
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (currentProvider == PreferencesManager.PROVIDER_GEMINI) "Google Gemini API Key:" else "OpenAI API Key:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currentApiKeyMasked,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (isKeyConfigured) {
                                OutlinedButton(
                                    onClick = { onApiKeyRemoved(currentProvider) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.testTag("remove_api_key_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove Key", fontSize = 12.sp)
                                }
                            }
                        }

                        if (isKeyConfigured) {
                            OutlinedButton(
                                onClick = {
                                    isTesting = true
                                    testResult = null
                                    onTestConnection(currentProvider, "") { success, msg ->
                                        isTesting = false
                                        testResult = Pair(success, msg)
                                    }
                                },
                                enabled = !isTesting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("test_saved_key_button")
                            ) {
                                if (isTesting && newKeyInput.isBlank()) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testing Saved Key...", fontSize = 12.sp)
                                } else {
                                    Text("Test Saved API Key", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Enter new key input (Set / Change)
                OutlinedTextField(
                    value = newKeyInput,
                    onValueChange = {
                        newKeyInput = it
                        testResult = null
                    },
                    label = { Text(if (isKeyConfigured) "Change API Key" else "Set API Key") },
                    placeholder = {
                        Text(if (currentProvider == PreferencesManager.PROVIDER_GEMINI) "AIzaSy..." else "sk-...")
                    },
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isKeyVisible) "Hide key" else "Show key"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_api_key_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val keyToTest = newKeyInput.ifBlank { "" }
                            if (keyToTest.isNotBlank()) {
                                isTesting = true
                                testResult = null
                                onTestConnection(currentProvider, keyToTest.trim()) { success, msg ->
                                    isTesting = false
                                    testResult = Pair(success, msg)
                                }
                            }
                        },
                        enabled = newKeyInput.isNotBlank() && !isTesting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTesting && newKeyInput.isNotBlank()) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...")
                        } else {
                            Text("Test New Key")
                        }
                    }

                    Button(
                        onClick = {
                            if (newKeyInput.isNotBlank()) {
                                onApiKeySaved(currentProvider, newKeyInput.trim())
                                newKeyInput = ""
                                testResult = null
                            }
                        },
                        enabled = newKeyInput.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isKeyConfigured) "Change Key" else "Set Key")
                    }
                }

                // Test result feedback
                testResult?.let { (success, msg) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (success) Color(0xFF14532D).copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (success) Color(0xFF22C55E) else MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (success) Color(0xFF22C55E) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                color = if (success) Color(0xFF22C55E) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 4. Theme Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Appearance / Theme",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
                    themes.forEach { (mode, label) ->
                        val isSelected = currentTheme == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onThemeChanged(mode) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // 5. About & Developer Info
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Nova AI — Multi-Model AI Chatbot",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Developer: Rauf",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Modular provider architecture ready for Google Gemini, OpenAI, and future AI models with vision, streaming, and image generation.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showModelPicker) {
        ModelSelectorDialog(
            models = availableModels,
            selectedModelId = currentModelId,
            isLoading = isLoadingModels,
            onModelSelected = onModelSelected,
            onRefresh = onRefreshModels,
            onDismiss = { showModelPicker = false }
        )
    }
}
