package com.example.data.provider

object ProviderRegistry {
    private val providers = mutableMapOf<String, AiProvider>(
        "gemini" to GeminiProvider(),
        "openai" to OpenAiProvider()
    )

    fun getProvider(id: String): AiProvider {
        return providers[id] ?: providers["gemini"]!!
    }

    fun getAllProviders(): List<AiProvider> {
        return providers.values.toList()
    }

    /**
     * Extension point for future providers (e.g. Anthropic, DeepSeek, Groq, Ollama)
     */
    fun registerProvider(provider: AiProvider) {
        providers[provider.id] = provider
    }
}
