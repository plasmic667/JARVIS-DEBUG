package com.example.core.network

import android.util.Log
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
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed class ValidationResult {
    data class Success(
        val model: String,
        val latencyMs: Long,
        val sample: String,
        val message: String = "Gemini API Connected Successfully"
    ) : ValidationResult()

    data class Error(
        val code: String,
        val message: String,
        val hint: String? = null
    ) : ValidationResult()
}

data class GeminiModelItem(
    val id: String,
    val displayName: String,
    val description: String
)

data class ToolCall(
    val name: String,
    val args: Map<String, Any?>
)

data class ChatStreamResult(
    val fullText: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val error: String? = null
)

class GeminiClient {
    // Dedicated client for key validation with 20s timeout
    private val validationClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Streaming client for conversation exchanges
    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"

    /**
     * Validates an API key with a live handshake against the Gemini API.
     * Enforces strict 20-25s timeout and returns the exact required status messages.
     * No arbitrary prefix restriction (e.g. AIza) is imposed.
     */
    suspend fun validateApiKey(apiKey: String, model: String = "gemini-2.5-flash"): ValidationResult =
        withContext(Dispatchers.IO) {
            val trimmedKey = apiKey.trim()
            if (trimmedKey.isEmpty()) {
                return@withContext ValidationResult.Error(
                    code = "empty_key",
                    message = "Invalid API Key. Please check your key and try again.",
                    hint = "Key cannot be empty. Get a key from Google AI Studio (aistudio.google.com/app/apikey)."
                )
            }
            if (trimmedKey.contains(" ")) {
                return@withContext ValidationResult.Error(
                    code = "contains_spaces",
                    message = "Invalid API Key. Please check your key and try again.",
                    hint = "API keys cannot contain spaces. Paste the complete key from Google AI Studio."
                )
            }

            val startedAt = System.currentTimeMillis()
            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Reply with exactly one word: online")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 24)
                    put("temperature", 0.0)
                })
            }

            val request = Request.Builder()
                .url("$baseUrl/models/$model:generateContent")
                .addHeader("Content-Type", "application/json")
                .addHeader("x-goog-api-key", trimmedKey)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            try {
                validationClient.newCall(request).execute().use { response ->
                    val latencyMs = System.currentTimeMillis() - startedAt
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        var sample = "online"
                        try {
                            val parsed = JSONObject(responseBody)
                            val candidates = parsed.optJSONArray("candidates")
                            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            val text = parts?.optJSONObject(0)?.optString("text")
                            if (!text.isNullOrEmpty()) {
                                sample = text.trim()
                            }
                        } catch (_: Exception) {}

                        ValidationResult.Success(
                            model = model,
                            latencyMs = latencyMs,
                            sample = sample,
                            message = "Gemini API Connected Successfully"
                        )
                    } else {
                        mapError(response.code, responseBody)
                    }
                }
            } catch (e: SocketTimeoutException) {
                Log.w("GeminiClient", "Validation socket timeout: ${e.message}")
                ValidationResult.Error(
                    code = "timeout",
                    message = "Connection timed out. Please try again.",
                    hint = "The server took too long to respond. Please check your connection or retry."
                )
            } catch (e: InterruptedIOException) {
                Log.w("GeminiClient", "Validation interrupted/timeout: ${e.message}")
                ValidationResult.Error(
                    code = "timeout",
                    message = "Connection timed out. Please try again.",
                    hint = "The request timed out. Please verify your internet connection and try again."
                )
            } catch (e: UnknownHostException) {
                Log.w("GeminiClient", "Validation unknown host: ${e.message}")
                ValidationResult.Error(
                    code = "network_error",
                    message = "Network error. Please check your internet connection.",
                    hint = "Could not resolve generativelanguage.googleapis.com."
                )
            } catch (e: ConnectException) {
                Log.w("GeminiClient", "Validation connect exception: ${e.message}")
                ValidationResult.Error(
                    code = "network_error",
                    message = "Network error. Please check your internet connection.",
                    hint = "Could not connect to the Gemini service. Check your connection or firewall."
                )
            } catch (e: Exception) {
                Log.w("GeminiClient", "Validation unexpected exception: ${e.javaClass.simpleName} - ${e.message}")
                ValidationResult.Error(
                    code = "server_error",
                    message = "Gemini service error. Please try again later.",
                    hint = e.localizedMessage
                )
            }
        }

    suspend fun listModels(apiKey: String): List<GeminiModelItem> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val request = Request.Builder()
            .url("$baseUrl/models?pageSize=100")
            .addHeader("x-goog-api-key", trimmedKey)
            .get()
            .build()

        try {
            validationClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = JSONObject(response.body?.string() ?: "{}")
                val modelsArray = json.optJSONArray("models") ?: return@withContext emptyList()
                val list = mutableListOf<GeminiModelItem>()
                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.optJSONObject(i) ?: continue
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var canGenerate = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.optString(j) == "generateContent") {
                                canGenerate = true
                                break
                            }
                        }
                    }
                    if (canGenerate) {
                        val rawName = m.optString("name", "")
                        val id = rawName.removePrefix("models/")
                        if (id.isNotEmpty() && !id.contains("embedding") && !id.contains("aqa")) {
                            list.add(
                                GeminiModelItem(
                                    id = id,
                                    displayName = m.optString("displayName", id),
                                    description = m.optString("description", "")
                                )
                            )
                        }
                    }
                }
                list.sortedBy { it.id }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun streamChat(
        apiKey: String,
        model: String,
        contentsJson: JSONArray,
        systemInstruction: String,
        temperature: Float = 0.8f,
        maxOutputTokens: Int = 1536,
        toolsJson: JSONArray? = null,
        onToken: (String) -> Unit
    ): ChatStreamResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val root = JSONObject().apply {
            put("contents", contentsJson)
            if (systemInstruction.isNotBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    })
                })
            }
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature.toDouble())
                put("maxOutputTokens", maxOutputTokens)
            })
            if (toolsJson != null && toolsJson.length() > 0) {
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("functionDeclarations", toolsJson)
                    })
                })
            }
        }

        val request = Request.Builder()
            .url("$baseUrl/models/$model:streamGenerateContent?alt=sse")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", trimmedKey)
            .post(root.toString().toRequestBody(jsonMediaType))
            .build()

        val fullTextBuilder = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()

        try {
            streamClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    val err = mapError(response.code, errBody)
                    return@withContext ChatStreamResult(
                        fullText = "",
                        error = err.message + (err.hint?.let { " ($it)" } ?: "")
                    )
                }

                val source = response.body?.byteStream() ?: return@withContext ChatStreamResult("", error = "Empty body")
                val reader = BufferedReader(InputStreamReader(source, Charsets.UTF_8))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val current = line?.trim() ?: continue
                    if (current.startsWith("data:")) {
                        val payload = current.removePrefix("data:").trim()
                        if (payload.isEmpty() || payload == "[DONE]") continue
                        try {
                            val chunk = JSONObject(payload)
                            val candidates = chunk.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val firstCandidate = candidates.optJSONObject(0)
                                val content = firstCandidate?.optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val part = parts.optJSONObject(i) ?: continue
                                        if (part.has("text")) {
                                            val text = part.optString("text")
                                            fullTextBuilder.append(text)
                                            withContext(Dispatchers.Main) {
                                                onToken(text)
                                            }
                                        }
                                        if (part.has("functionCall")) {
                                            val fc = part.getJSONObject("functionCall")
                                            val name = fc.optString("name")
                                            val argsObj = fc.optJSONObject("args")
                                            val argsMap = mutableMapOf<String, Any?>()
                                            if (argsObj != null) {
                                                val keys = argsObj.keys()
                                                while (keys.hasNext()) {
                                                    val k = keys.next()
                                                    argsMap[k] = argsObj.opt(k)
                                                }
                                            }
                                            toolCalls.add(ToolCall(name, argsMap))
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            ChatStreamResult(fullText = fullTextBuilder.toString(), toolCalls = toolCalls)
        } catch (e: Exception) {
            ChatStreamResult(
                fullText = fullTextBuilder.toString(),
                error = "Connection lost: ${e.localizedMessage ?: "request failed"}"
            )
        }
    }

    suspend fun generateContent(
        apiKey: String,
        model: String,
        prompt: String,
        systemInstruction: String = ""
    ): String = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            if (systemInstruction.isNotBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    })
                })
            }
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 2048)
                put("temperature", 0.4)
            })
        }

        val request = Request.Builder()
            .url("$baseUrl/models/$model:generateContent")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", apiKey.trim())
            .post(root.toString().toRequestBody(jsonMediaType))
            .build()

        validationClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val err = mapError(response.code, body)
                throw RuntimeException(err.message + (err.hint?.let { " ($it)" } ?: ""))
            }
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates")
            val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            parts?.optJSONObject(0)?.optString("text") ?: ""
        }
    }

    private fun mapError(status: Int, body: String): ValidationResult.Error {
        val lower = body.lowercase()
        return when {
            lower.contains("api key not valid") || lower.contains("api_key_invalid") || status == 401 || (status == 400 && lower.contains("key")) -> {
                ValidationResult.Error(
                    code = "invalid_key",
                    message = "Invalid API Key. Please check your key and try again.",
                    hint = "Verify that you copied the complete key from Google AI Studio > Get API key."
                )
            }
            status == 403 || lower.contains("permission_denied") || lower.contains("access_token_scope_insufficient") -> {
                ValidationResult.Error(
                    code = "permission_denied",
                    message = "Gemini API access is unavailable for this key.",
                    hint = "Ensure the Generative Language API is enabled for the key's Google Cloud project."
                )
            }
            status == 404 || lower.contains("model not found") || lower.contains("is not found") || lower.contains("models/") -> {
                ValidationResult.Error(
                    code = "model_unavailable",
                    message = "The selected Gemini model is unavailable. Please select a supported model.",
                    hint = "Switch to gemini-2.5-flash or gemini-2.5-pro in the model dropdown."
                )
            }
            status == 429 -> {
                ValidationResult.Error(
                    code = "quota_exceeded",
                    message = "API quota or rate limit exceeded. Please try again later.",
                    hint = "Check your quota or plan in Google AI Studio, or retry when the window resets."
                )
            }
            status >= 500 -> {
                ValidationResult.Error(
                    code = "server_error",
                    message = "Gemini service error. Please try again later.",
                    hint = "This is on Google's side. Please retry in a moment."
                )
            }
            else -> {
                var message = "Invalid API Key. Please check your key and try again."
                try {
                    val obj = JSONObject(body)
                    val err = obj.optJSONObject("error")
                    val msg = err?.optString("message")
                    if (!msg.isNullOrBlank()) message = msg
                } catch (_: Exception) {}
                ValidationResult.Error(code = "bad_request", message = message)
            }
        }
    }
}
