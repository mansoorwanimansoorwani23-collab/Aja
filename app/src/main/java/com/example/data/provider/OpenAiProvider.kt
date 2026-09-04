package com.example.data.provider

import com.example.data.model.AiModel
import com.example.data.model.ChatMessage
import com.example.data.model.ImageAttachment
import com.example.data.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionPool
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
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var activeCall: Call? = null

    override fun cancelActiveCall() {
        try {
            activeCall?.cancel()
        } catch (_: Exception) {}
        activeCall = null
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Curated 2-4 latest useful OpenAI models
    private val defaultModels = listOf(
        AiModel(
            id = "gpt-4o-mini",
            name = "GPT-4o Mini",
            providerId = id,
            supportsVision = true,
            supportsStreaming = true,
            supportsImageGen = false,
            description = "Fast, smart, and multimodal everyday intelligence (Default)"
        ),
        AiModel(
            id = "gpt-4o",
            name = "GPT-4o",
            providerId = id,
            supportsVision = true,
            supportsStreaming = true,
            supportsImageGen = false,
            description = "Flagship high-intelligence multimodal model for coding and reasoning"
        ),
        AiModel(
            id = "o3-mini",
            name = "o3-mini",
            providerId = id,
            supportsVision = false,
            supportsStreaming = true,
            supportsImageGen = false,
            description = "Advanced STEM reasoning, math, and coding"
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

                val availableIds = mutableSetOf<String>()
                for (i in 0 until dataArray.length()) {
                    val m = dataArray.getJSONObject(i)
                    val modelId = m.getString("id")
                    availableIds.add(modelId)
                }

                // Curate strictly to 2-4 latest useful models
                val curated = curateOpenAiModels(availableIds)
                Result.success(curated)
            }
        } catch (e: Exception) {
            Result.success(defaultModels)
        }
    }

    private fun curateOpenAiModels(availableIds: Set<String>): List<AiModel> {
        val list = mutableListOf<AiModel>()

        // 1. Primary/Default: Newest fast model (GPT-4o Mini)
        list.add(
            AiModel(
                id = "gpt-4o-mini",
                name = "GPT-4o Mini",
                providerId = id,
                supportsVision = true,
                supportsStreaming = true,
                supportsImageGen = false,
                description = "Fast, smart, and multimodal everyday intelligence (Default)"
            )
        )

        // 2. High Intelligence Flagship: GPT-4o
        list.add(
            AiModel(
                id = "gpt-4o",
                name = "GPT-4o",
                providerId = id,
                supportsVision = true,
                supportsStreaming = true,
                supportsImageGen = false,
                description = "Flagship high-intelligence multimodal model for coding and reasoning"
            )
        )

        // 3. STEM / Deep Reasoning: o3-mini or o1
        val reasoningId = when {
            availableIds.contains("o3-mini") -> "o3-mini"
            availableIds.contains("o1") -> "o1"
            availableIds.contains("o1-mini") -> "o1-mini"
            else -> "o3-mini"
        }
        val reasoningName = when (reasoningId) {
            "o3-mini" -> "o3-mini"
            "o1" -> "o1"
            "o1-mini" -> "o1-mini"
            else -> reasoningId
        }
        list.add(
            AiModel(
                id = reasoningId,
                name = reasoningName,
                providerId = id,
                supportsVision = reasoningId == "o1",
                supportsStreaming = true,
                supportsImageGen = false,
                description = "Advanced STEM reasoning, math, and deep logic"
            )
        )

        // 4. Image Generation: DALL-E 3
        list.add(
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

        return list.take(4)
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

            val call = client.newCall(request)
            activeCall = call
            try {
                call.execute().use { response ->
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
            } finally {
                activeCall = null
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
