package com.mobilepulse.app.data.repository

import com.mobilepulse.app.data.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AiMessage(val role: String, val content: String)

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<AiMessage>
)

@Serializable
private data class ContentBlock(val type: String, val text: String = "")

@Serializable
private data class ClaudeResponse(val content: List<ContentBlock>)

// DeepSeek request/response models
@Serializable
private data class DsRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<AiMessage>
)

@Serializable
private data class DsChoice(val message: AiMessage)

@Serializable
private data class DsResponse(val choices: List<DsChoice>)

@Singleton
class AiRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val systemPrompt = """
        You are MobilePulse AI, an expert Android device optimization assistant built into the MobilePulse app.
        You help users understand and fix Android performance issues: high CPU/RAM usage, battery drain, slow apps, and optimization errors.
        Keep responses concise and actionable. Use plain language — no unnecessary jargon.
        When the user shares an error message, explain what likely caused it and give 2-3 clear steps to fix it.
    """.trimIndent()

    suspend fun sendMessage(
        provider: AiProvider,
        apiKey: String,
        history: List<AiMessage>
    ): Result<String> = when (provider) {
        AiProvider.CLAUDE   -> sendClaude(apiKey, history)
        AiProvider.DEEPSEEK -> sendDeepSeek(apiKey, history)
    }

    private suspend fun sendClaude(
        apiKey: String,
        history: List<AiMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(
                ClaudeRequest(
                    model     = "claude-haiku-4-5-20251001",
                    maxTokens = 1024,
                    system    = systemPrompt,
                    messages  = history
                )
            )
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(body.toRequestBody(mediaType))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response from Claude API"))

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Claude API ${response.code}: $responseBody"))
            }

            val parsed = json.decodeFromString<ClaudeResponse>(responseBody)
            val text = parsed.content.firstOrNull { it.type == "text" }?.text
                ?: return@withContext Result.failure(Exception("No text in Claude response"))

            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendDeepSeek(
        apiKey: String,
        history: List<AiMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messages = listOf(AiMessage("system", systemPrompt)) + history
            val body = json.encodeToString(
                DsRequest(model = "deepseek-chat", maxTokens = 1024, messages = messages)
            )
            val request = Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body.toRequestBody(mediaType))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response from DeepSeek API"))

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("DeepSeek API ${response.code}: $responseBody"))
            }

            val parsed = json.decodeFromString<DsResponse>(responseBody)
            val text = parsed.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("No text in DeepSeek response"))

            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
