package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.ChatScreen
import com.example.ui.ChatViewModel
import com.example.ui.theme.NovaAiTheme

class MainActivity : ComponentActivity() {
  private val viewModel: ChatViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val themeMode = viewModel.preferencesManager.themeMode
      NovaAiTheme(themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
          ChatScreen(viewModel = viewModel)
        }
      }
    }
  }
}
