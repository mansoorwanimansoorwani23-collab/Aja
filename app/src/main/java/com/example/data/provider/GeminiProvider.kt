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

class GeminiProvider : AiProvider {
    override val id: String = "gemini"
    override val displayName: String = "Google Gemini"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val defaultModels = listOf(
        AiModel(
            id = "gemini-2.5-flash",
            name = "Gemini 2.5 Flash",
            providerId = id,
            supportsVision = true,
            supportsStreaming = true,
            supportsImageGen = false,
            description = "Fast, multimodal intelligence for everyday tasks & coding"
        ),
        AiModel(
            id = "gemini-3.5-flash",
            name = "Gemini 3.5 Flash",
            providerId = id,
            supportsVision = true,
            supportsStreaming = true,
            supportsImageGen = false,
            description = "High-speed reasoning, coding, and general tasks"
        ),
        AiModel(
            id = "gemini-3.1-pro-preview",
            name = "Gemini 3.1 Pro",
            providerId = id,
            supportsVision = true,
            supportsStreaming = true,
            supportsImageGen = false,
            description = "Advanced reasoning, complex STEM, and deep coding"
        ),
        AiModel(
            id = "gemini-2.5-flash-image",
            name = "Gemini 2.5 Flash Image",
            providerId = id,
            supportsVision = true,
            supportsStreaming = false,
            supportsImageGen = true,
            description = "Multimodal image generation and visual synthesis"
        )
    )

    override suspend fun fetchModels(apiKey: String): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.trim()}"
            val request = Request.Builder().url(url).get().build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorMsg = when (code) {
                        400 -> "Invalid API key or request parameters"
                        403 -> "API key unauthorized or Gemini API not enabled"
                        429 -> "Gemini rate limit exceeded. Please wait a moment."
                        else -> "Failed to load models (HTTP $code)"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val bodyStr = response.body?.string() ?: return@withContext Result.success(defaultModels)
                val json = JSONObject(bodyStr)
                val modelsArray = json.optJSONArray("models") ?: return@withContext Result.success(defaultModels)

                val parsedModels = mutableListOf<AiModel>()
                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.getJSONObject(i)
                    val rawName = m.getString("name") // e.g. "models/gemini-2.5-flash"
                    val cleanId = rawName.removePrefix("models/")
                    val dispName = m.optString("displayName", cleanId)
                    val desc = m.optString("description", "")
                    val methods = m.optJSONArray("supportedGenerationMethods")

                    var canGenerate = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.getString(j) == "generateContent") {
                                canGenerate = true
                                break
                            }
                        }
                    }

                    if (canGenerate) {
                        val isImageGen = cleanId.contains("image") || cleanId.contains("imagen")
                        val isVision = !cleanId.contains("embedding") && !cleanId.contains("aqa")
                        parsedModels.add(
                            AiModel(
                                id = cleanId,
                                name = dispName,
                                providerId = id,
                                supportsVision = isVision,
                                supportsStreaming = !isImageGen,
                                supportsImageGen = isImageGen,
                                description = desc
                            )
                        )
                    }
                }

                if (parsedModels.isNotEmpty()) {
                    Result.success(parsedModels)
                } else {
                    Result.success(defaultModels)
                }
            }
        } catch (e: Exception) {
            // Return defaults if offline or network error, but allow user to use
            Result.success(defaultModels)
        }
    }

    override suspend fun testConnection(apiKey: String, modelId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanKey = apiKey.trim()
            if (cleanKey.isEmpty()) {
                return@withContext Result.failure(Exception("API key cannot be empty"))
            }

            val targetModel = modelId.ifEmpty { "gemini-2.5-flash" }
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$cleanKey"

            val bodyJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val userContent = JSONObject().apply {
                        put("role", "user")
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", "Ping test. Reply with 'OK'.") })
                        }
                        put("parts", parts)
                    }
                    put(userContent)
                }
                put("contents", contents)
            }

            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Connection successful! Provider & model are ready.")
                } else {
                    val errorBody = response.body?.string() ?: ""
                    val msg = when (response.code) {
                        400 -> "Invalid API key format or parameters"
                        403 -> "Invalid API key or Gemini API not enabled"
                        429 -> "Rate limit reached (HTTP 429)"
                        else -> "Connection failed (HTTP ${response.code})"
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
            return@withContext Result.failure(Exception("Gemini API key is missing. Please set your key in Settings."))
        }

        // Multimodal capability check
        if (attachment != null && !model.supportsVision) {
            return@withContext Result.failure(
                Exception("This model does not support image understanding. Please select a multimodal model (e.g., Gemini 2.5 Flash).")
            )
        }

        try {
            val useStreaming = model.supportsStreaming && onChunk != null
            val endpoint = if (useStreaming) "streamGenerateContent?key=$cleanKey&alt=sse" else "generateContent?key=$cleanKey"
            val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.id}:$endpoint"

            val requestJson = JSONObject()
            val contentsArray = JSONArray()

            // System instructions
            requestJson.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put(
                            "text",
                            "You are Nova AI, a helpful, intelligent, and precise AI assistant. Developer: Rauf. Provide clear, well-structured, formatted responses with Markdown and properly labeled code blocks where appropriate."
                        )
                    )
                )
            )

            // Convert conversation history
            // For Gemini, alternating user and model turns are required
            val nonErrorMessages = messages.filter { !it.isError && it.content.isNotBlank() }
            for (i in nonErrorMessages.indices) {
                val msg = nonErrorMessages[i]
                val contentObj = JSONObject()
                contentObj.put("role", if (msg.role == MessageRole.USER) "user" else "model")

                val partsArray = JSONArray()
                partsArray.put(JSONObject().put("text", msg.content))

                // If this is the last user message and an attachment is provided
                if (i == nonErrorMessages.lastIndex && msg.role == MessageRole.USER && attachment != null) {
                    val inlineData = JSONObject().apply {
                        put("mimeType", attachment.mimeType)
                        put("data", attachment.base64Data)
                    }
                    partsArray.put(JSONObject().put("inlineData", inlineData))
                }

                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
            }

            requestJson.put("contents", contentsArray)

            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    val friendlyMsg = when (response.code) {
                        400 -> "Invalid request or malformed image data"
                        403 -> "Invalid Gemini API key or access forbidden"
                        404 -> "Model '${model.id}' not found on Gemini API"
                        429 -> "Gemini API rate limit reached. Please wait a moment."
                        else -> "Gemini error (HTTP ${response.code})"
                    }
                    return@withContext Result.failure(Exception(friendlyMsg))
                }

                val responseBody = response.body ?: return@withContext Result.failure(Exception("Empty response body"))

                if (useStreaming) {
                    val fullText = StringBuilder()
                    val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line?.trim() ?: continue
                        if (currentLine.startsWith("data:")) {
                            val dataJsonStr = currentLine.removePrefix("data:").trim()
                            if (dataJsonStr.isEmpty() || dataJsonStr == "[DONE]") continue
                            try {
                                val chunkJson = JSONObject(dataJsonStr)
                                val candidates = chunkJson.optJSONArray("candidates")
                                if (candidates != null && candidates.length() > 0) {
                                    val candidate = candidates.getJSONObject(0)
                                    val content = candidate.optJSONObject("content")
                                    val parts = content?.optJSONArray("parts")
                                    if (parts != null && parts.length() > 0) {
                                        val text = parts.getJSONObject(0).optString("text", "")
                                        if (text.isNotEmpty()) {
                                            fullText.append(text)
                                            withContext(Dispatchers.Main) {
                                                onChunk(text)
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                // Ignore partial malformed SSE chunk
                            }
                        }
                    }

                    if (fullText.isNotEmpty()) {
                        Result.success(fullText.toString())
                    } else {
                        Result.success("I processed your request, but received no text response.")
                    }
                } else {
                    val bodyString = responseBody.string()
                    val json = JSONObject(bodyString)
                    val candidates = json.optJSONArray("candidates")
                    val candidate = candidates?.optJSONObject(0)
                    val content = candidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text", "") ?: ""

                    if (text.isNotEmpty()) {
                        Result.success(text)
                    } else {
                        Result.success("No response generated.")
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Failed to generate response"))
        }
    }

    override suspend fun generateImage(
        apiKey: String,
        prompt: String,
        model: AiModel?
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(Exception("Gemini API key is missing. Please set your key in Settings."))
        }

        val targetModel = model?.id?.takeIf { it.contains("image") || it.contains("imagen") } ?: "gemini-2.5-flash-image"

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$cleanKey"

            val bodyJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val userContent = JSONObject().apply {
                        put("role", "user")
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", parts)
                    }
                    put(userContent)
                }
                put("contents", contents)

                val genConfig = JSONObject().apply {
                    val modalities = JSONArray().apply {
                        put("IMAGE")
                        put("TEXT")
                    }
                    put("responseModalities", modalities)
                    val imgConfig = JSONObject().apply {
                        put("aspectRatio", "1:1")
                    }
                    put("imageConfig", imgConfig)
                }
                put("generationConfig", genConfig)
            }

            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val friendlyMsg = when (response.code) {
                        400 -> "Invalid image generation prompt or parameters"
                        403 -> "Gemini API key not permitted for image generation"
                        404 -> "Model '$targetModel' not found. Please try with an active multimodal/image model."
                        429 -> "Rate limit reached for image generation"
                        else -> "Image generation failed (HTTP ${response.code})"
                    }
                    return@withContext Result.failure(Exception(friendlyMsg))
                }

                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val candidates = json.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")

                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val data = inlineData.optString("data", "")
                            val mime = inlineData.optString("mimeType", "image/png")
                            if (data.isNotEmpty()) {
                                return@withContext Result.success("data:$mime;base64,$data")
                            }
                        }
                    }
                }

                Result.failure(Exception("Image generation completed, but no image data was returned."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Image generation error: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }
}
