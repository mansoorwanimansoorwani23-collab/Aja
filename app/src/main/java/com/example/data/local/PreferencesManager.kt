package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nova_ai_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_OPENAI = "openai"

        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_GEMINI_SELECTED_MODEL = "gemini_selected_model"
        private const val KEY_OPENAI_SELECTED_MODEL = "openai_selected_model"
        private const val KEY_THEME_MODE = "theme_mode" // "system", "dark", "light"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
    }

    var activeProvider: String
        get() = prefs.getString(KEY_ACTIVE_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI
        set(value) = prefs.edit().putString(KEY_ACTIVE_PROVIDER, value).apply()

    fun getApiKey(provider: String): String {
        return when (provider) {
            PROVIDER_OPENAI -> prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
            else -> prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        }
    }

    fun setApiKey(provider: String, key: String) {
        val editor = prefs.edit()
        when (provider) {
            PROVIDER_OPENAI -> editor.putString(KEY_OPENAI_API_KEY, key.trim())
            else -> editor.putString(KEY_GEMINI_API_KEY, key.trim())
        }
        editor.apply()
    }

    fun removeApiKey(provider: String) {
        val editor = prefs.edit()
        when (provider) {
            PROVIDER_OPENAI -> editor.remove(KEY_OPENAI_API_KEY)
            else -> editor.remove(KEY_GEMINI_API_KEY)
        }
        editor.apply()
    }

    fun getSelectedModel(provider: String): String {
        return when (provider) {
            PROVIDER_OPENAI -> prefs.getString(KEY_OPENAI_SELECTED_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
            else -> prefs.getString(KEY_GEMINI_SELECTED_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash"
        }
    }

    fun setSelectedModel(provider: String, modelId: String) {
        val editor = prefs.edit()
        when (provider) {
            PROVIDER_OPENAI -> editor.putString(KEY_OPENAI_SELECTED_MODEL, modelId)
            else -> editor.putString(KEY_GEMINI_SELECTED_MODEL, modelId)
        }
        editor.apply()
    }

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var isSetupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()

    fun maskApiKey(key: String): String {
        if (key.isBlank()) return "Not configured"
        if (key.length <= 8) return "••••••••"
        val prefix = key.take(4)
        val suffix = key.takeLast(4)
        return "$prefix••••••••$suffix"
    }
}
