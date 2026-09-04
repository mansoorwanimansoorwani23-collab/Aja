package com.example.data.provider

import com.example.data.model.AiModel
import com.example.data.model.ChatMessage
import com.example.data.model.ImageAttachment
import com.example.data.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OpenAiProvider : AiProvider {
    override val id: String = "openai"
    override val displayName: String = "OpenAI"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val defaultModels = listOf(
        AiModel(
            id = "gpt-4o-mini",
            name = "GPT-4o Mini",
            providerId = id,
            supportsVision = true,
            supportsStreaming = true,
            supportsImageGen = false,
            description = "Affordable, fast flagship-grade intelligence & vision"
        ),
        AiModel(
            id = "gpt-4o",
            name = "GPT-4o",
            providerId = id,
            supportsVision = true,
            supportsStreaming = true,
            supportsImageGen = false,
            description = "High-intelligence flagship multimodal model"
        ),
        AiModel(
            id = "gpt-4.5-preview",
            name = "GPT-4.5 Preview",
            providerId = id,
            supportsVision = true,
            supportsStreaming = true,
            supportsImageGen = false,
            description = "Advanced reasoning & deep conversational fidelity"
        ),
        AiModel(
            id = "dall-e-3",
            name = "DALL·E 3",
            providerId = id,
            supportsVision = false,
            supportsStreaming = false,
            supportsImageGen = true,
            description = "High-fidelity AI image generation"
        )
    )

    override suspend fun fetchModels(apiKey: String): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.success(defaultModels)
        }

        try {
            val request = Request.Builder()
                .url("https://api.openai.com/v1/models")
                .header("Authorization", "Bearer $cleanKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = when (response.code) {
                        401 -> "Invalid OpenAI API Key"
                        429 -> "OpenAI quota or rate limit exceeded"
                        else -> "Failed to fetch OpenAI models (HTTP ${response.code})"
                    }
                    return@withContext Result.failure(Exception(msg))
                }

                val bodyStr = response.body?.string() ?: return@withContext Result.success(defaultModels)
                val json = JSONObject(bodyStr)
                val dataArray = json.optJSONArray("data") ?: return@withContext Result.success(defaultModels)

                val list = mutableListOf<AiModel>()
                for (i in 0 until dataArray.length()) {
                    val m = dataArray.getJSONObject(i)
                    val modelId = m.getString("id")

                    // Filter relevant text/chat/image models and skip embeddings, audio-transcribe, etc.
                    val isRelevant = modelId.startsWith("gpt") ||
                            modelId.startsWith("o1") ||
                            modelId.startsWith("o3") ||
                            modelId.startsWith("chatgpt") ||
                            modelId.startsWith("dall-e")

                    if (isRelevant) {
                        val isImageGen = modelId.startsWith("dall-e")
                        val isVision = modelId.contains("4o") || modelId.contains("vision") || modelId.contains("turbo") || modelId.startsWith("o1")
                        val cleanName = modelId.replace("-", " ").split(" ")
                            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

                        list.add(
                            AiModel(
                                id = modelId,
                                name = cleanName,
                                providerId = id,
                                supportsVision = isVision,
                                supportsStreaming = !isImageGen,
                                supportsImageGen = isImageGen,
                                description = if (isImageGen) "Image generation model" else "Chat & reasoning model"
                            )
                        )
                    }
                }

                // Sort so popular models appear first
                list.sortByDescending {
                    when {
                        it.id.startsWith("gpt-4o-mini") -> 100
                        it.id.startsWith("gpt-4o") -> 90
                        it.id.startsWith("dall-e-3") -> 80
                        it.id.startsWith("o1") -> 70
                        it.id.startsWith("o3") -> 60
                        else -> 10
                    }
                }

                if (list.isNotEmpty()) Result.success(list) else Result.success(defaultModels)
            }
        } catch (e: Exception) {
            Result.success(defaultModels)
        }
    }

    override suspend fun testConnection(apiKey: String, modelId: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(Exception("OpenAI API key cannot be empty"))
        }

        try {
            val targetModel = modelId.ifEmpty { "gpt-4o-mini" }
            val body = JSONObject().apply {
                put("model", targetModel)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "ping")
                    })
                })
                put("max_tokens", 5)
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $cleanKey")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Connected to OpenAI successfully!")
                } else {
                    val msg = when (response.code) {
                        401 -> "Invalid OpenAI API key"
                        429 -> "Rate limit or quota exceeded on OpenAI account"
                        else -> "OpenAI connection failed (HTTP ${response.code})"
                    }
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.localizedMessage ?: "Unable to connect"}"))
        }
    }

    override suspend fun generateChat(
        apiKey: String,
        model: AiModel,
        messages: List<ChatMessage>,
        attachment: ImageAttachment?,
        onChunk: ((String) -> Unit)?
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(Exception("OpenAI API key is missing. Please set your key in Settings."))
        }

        if (attachment != null && !model.supportsVision) {
            return@withContext Result.failure(
                Exception("This model does not support image understanding. Please select a multimodal model (e.g. GPT-4o or GPT-4o-mini).")
            )
        }

        try {
            val useStreaming = model.supportsStreaming && onChunk != null
            val requestJson = JSONObject()
            requestJson.put("model", model.id)
            requestJson.put("stream", useStreaming)

            val messagesArray = JSONArray()

            // System prompt
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", "You are Nova AI, an intelligent, helpful, and precise assistant. Developer: Rauf. Output clean Markdown, proper code formatting, and explanations.")
            })

            val validMessages = messages.filter { !it.isError && it.content.isNotBlank() }
            for (i in validMessages.indices) {
                val msg = validMessages[i]
                val msgObj = JSONObject()
                val roleStr = when (msg.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "system"
                }
                msgObj.put("role", roleStr)

                // Multimodal check on latest user turn
                if (i == validMessages.lastIndex && msg.role == MessageRole.USER && attachment != null) {
                    val contentParts = JSONArray()
                    contentParts.put(JSONObject().apply {
                        put("type", "text")
                        put("text", msg.content)
                    })
                    contentParts.put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:${attachment.mimeType};base64,${attachment.base64Data}")
                        })
                    })
                    msgObj.put("content", contentParts)
                } else {
                    msgObj.put("content", msg.content)
                }

                messagesArray.put(msgObj)
            }

            requestJson.put("messages", messagesArray)

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $cleanKey")
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = when (response.code) {
                        401 -> "Invalid OpenAI API Key"
                        429 -> "OpenAI quota limit reached or rate limit active"
                        else -> "OpenAI error (HTTP ${response.code})"
                    }
                    return@withContext Result.failure(Exception(msg))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))

                if (useStreaming) {
                    val fullText = StringBuilder()
                    val reader = BufferedReader(InputStreamReader(body.byteStream()))
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line?.trim() ?: continue
                        if (currentLine.startsWith("data:")) {
                            val payload = currentLine.removePrefix("data:").trim()
                            if (payload == "[DONE]") break
                            if (payload.isNotEmpty()) {
                                try {
                                    val chunk = JSONObject(payload)
                                    val choices = chunk.optJSONArray("choices")
                                    if (choices != null && choices.length() > 0) {
                                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                                        val text = delta?.optString("content", "") ?: ""
                                        if (text.isNotEmpty()) {
                                            fullText.append(text)
                                            withContext(Dispatchers.Main) {
                                                onChunk(text)
                                            }
                                        }
                                    }
                                } catch (_: Exception) {
                                    // ignore partial malformed chunk
                                }
                            }
                        }
                    }

                    Result.success(fullText.toString())
                } else {
                    val str = body.string()
                    val json = JSONObject(str)
                    val choices = json.optJSONArray("choices")
                    val message = choices?.optJSONObject(0)?.optJSONObject("message")
                    val content = message?.optString("content", "") ?: ""
                    Result.success(content)
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Failed to generate OpenAI response"))
        }
    }

    override suspend fun generateImage(
        apiKey: String,
        prompt: String,
        model: AiModel?
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(Exception("OpenAI API key is missing. Please set your key in Settings."))
        }

        try {
            val targetModel = model?.id?.takeIf { it.startsWith("dall-e") } ?: "dall-e-3"
            val body = JSONObject().apply {
                put("prompt", prompt)
                put("model", targetModel)
                put("n", 1)
                put("size", "1024x1024")
                put("response_format", "b64_json")
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/images/generations")
                .header("Authorization", "Bearer $cleanKey")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = when (response.code) {
                        400 -> "Invalid image generation prompt or parameters"
                        401 -> "Invalid OpenAI API Key"
                        429 -> "OpenAI quota or rate limit exceeded"
                        else -> "OpenAI Image Generation error (HTTP ${response.code})"
                    }
                    return@withContext Result.failure(Exception(msg))
                }

                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val data = json.optJSONArray("data")
                val item = data?.optJSONObject(0)
                val b64 = item?.optString("b64_json", "") ?: ""
                val url = item?.optString("url", "") ?: ""

                if (b64.isNotEmpty()) {
                    Result.success("data:image/png;base64,$b64")
                } else if (url.isNotEmpty()) {
                    Result.success(url)
                } else {
                    Result.failure(Exception("No image returned from OpenAI"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Image generation failed: ${e.localizedMessage}"))
        }
    }
}
