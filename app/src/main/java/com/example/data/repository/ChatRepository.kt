package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.ChatDao
import com.example.data.model.*
import com.example.data.remote.GeminiApiService
import kotlinx.coroutines.flow.Flow
import java.io.IOException

class ChatRepository(
    private val chatDao: ChatDao,
    private val apiService: GeminiApiService
) {
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun createSession(title: String, systemInstruction: String? = null): Long {
        return chatDao.insertSession(ChatSession(title = title, systemInstruction = systemInstruction))
    }

    suspend fun updateSession(session: ChatSession) {
        chatDao.updateSession(session)
    }

    suspend fun deleteSession(session: ChatSession) {
        chatDao.deleteSession(session)
    }

    suspend fun clearAll() {
        chatDao.deleteAllSessions()
    }

    suspend fun saveMessage(sessionId: Long, isUser: Boolean, text: String): Long {
        return chatDao.insertMessage(ChatMessage(sessionId = sessionId, isUser = isUser, text = text))
    }

    suspend fun deleteMessagesForSession(sessionId: Long) {
        chatDao.deleteMessagesForSession(sessionId)
    }

    /**
     * Sends the entire chat history for a session to the Gemini API and returns the response.
     */
    suspend fun generateChatResponse(
        messages: List<ChatMessage>,
        systemInstructionText: String? = null
    ): Result<String> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return Result.failure(
                Exception(
                    "مفتاح API الخاص بـ Gemini غير متوفر. الرجاء إدخال GEMINI_API_KEY في لوحة Secrets في غوغل AI Studio لتتمكن من التحدث مع الذكاء الاصطناعي."
                )
            )
        }

        // Convert ChatMessage to Gemini API Content objects
        // The API expects historical turns to alternate between user and model roles.
        val apiContents = messages.map { message ->
            Content(
                role = if (message.isUser) "user" else "model",
                parts = listOf(Part(text = message.text))
            )
        }

        val systemContent = if (!systemInstructionText.isNullOrBlank()) {
            Content(
                parts = listOf(Part(text = systemInstructionText))
            )
        } else {
            // Default friendly Arabic AI assistant helper
            Content(
                parts = listOf(Part(text = "أنت مساعد ذكاء اصطناعي مفيد ودود ولطيف ومحنك، تتحدث باللغة العربية بدقة ووضوح."))
            )
        }

        val model = "gemini-3.5-flash"

        val request = GeminiRequest(
            contents = apiContents,
            systemInstruction = systemContent,
            generationConfig = GenerationConfig(temperature = 0.7f)
        )

        return try {
            val response = apiService.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (responseText != null) {
                Result.success(responseText)
            } else {
                Result.failure(Exception("لم نحصل على رد صالح من مجمع نموذج الذكاء الاصطناعي."))
            }
        } catch (e: IOException) {
            Result.failure(Exception("تعذر الاتصال بالإنترنت. يرجى التحقق من اتصال الشبكة وإعادة المحاولة. التفاصيل: ${e.localizedMessage}"))
        } catch (e: Exception) {
            Result.failure(Exception("حدث خطأ غير متوقع أثناء الاتصال بـ Gemini API: ${e.localizedMessage}"))
        }
    }
}
