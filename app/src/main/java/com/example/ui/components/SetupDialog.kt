package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun SetupDialog(
    initialProvider: String = PreferencesManager.PROVIDER_GEMINI,
    onConnectAndDetect: (provider: String, apiKey: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var apiKeyInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isConnecting) onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = if (selectedProvider == null) "Connect your AI" else "Set ${if (selectedProvider == PreferencesManager.PROVIDER_GEMINI) "Gemini" else "OpenAI"} API Key",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (selectedProvider == null) "Choose an AI provider to get started" else "Auto-detects & selects the newest model",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (selectedProvider == null) {
                    // Screen 1: Simple Provider Choice
                    Text(
                        text = "Select your preferred AI provider. You can switch, test, or add keys anytime in Settings.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            selectedProvider = PreferencesManager.PROVIDER_GEMINI
                            apiKeyInput = ""
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("setup_set_gemini_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Set Gemini API Key",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            selectedProvider = PreferencesManager.PROVIDER_OPENAI
                            apiKeyInput = ""
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("setup_set_openai_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Set OpenAI API Key",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Screen 2: Enter Key, Test, Detect Models, and Auto-Select Newest
                    val prov = selectedProvider!!
                    val isGemini = prov == PreferencesManager.PROVIDER_GEMINI

                    Text(
                        text = if (isGemini) {
                            "Enter your Google Gemini API key. Nova AI will test the key, identify available models, and automatically select the newest Gemini model."
                        } else {
                            "Enter your OpenAI API key. Nova AI will test the key, identify available models, and automatically select the newest GPT model."
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            errorMessage = null
                        },
                        label = {
                            Text(if (isGemini) "Gemini API Key" else "OpenAI API Key")
                        },
                        placeholder = {
                            Text(if (isGemini) "AIzaSy..." else "sk-...")
                        },
                        singleLine = true,
                        enabled = !isConnecting,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide key" else "Show key"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {}),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_api_key_input")
                    )

                    AnimatedVisibility(visible = errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = errorMessage ?: "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    if (isConnecting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Testing key & selecting newest model...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "🔒 Security: Your API key is stored locally on this device and is never logged or shared.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        },
        confirmButton = {
            if (selectedProvider != null) {
                Button(
                    onClick = {
                        val cleanKey = apiKeyInput.trim()
                        if (cleanKey.isNotBlank()) {
                            isConnecting = true
                            errorMessage = null
                            onConnectAndDetect(selectedProvider!!, cleanKey) { success, msg ->
                                isConnecting = false
                                if (!success) {
                                    errorMessage = msg
                                }
                            }
                        }
                    },
                    enabled = apiKeyInput.trim().isNotBlank() && !isConnecting,
                    modifier = Modifier.testTag("setup_connect_button")
                ) {
                    Text("Connect & Start Chat")
                }
            }
        },
        dismissButton = {
            if (selectedProvider != null) {
                TextButton(
                    onClick = {
                        if (!isConnecting) {
                            selectedProvider = null
                            errorMessage = null
                        }
                    },
                    enabled = !isConnecting
                ) {
                    Text("Back")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}
