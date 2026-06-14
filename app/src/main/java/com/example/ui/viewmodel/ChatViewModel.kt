package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.remote.GeminiApiService
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository

    // List of all conversations
    val allSessions: StateFlow<List<ChatSession>>

    // Current active session
    private val _selectedSession = MutableStateFlow<ChatSession?>(null)
    val selectedSession: StateFlow<ChatSession?> = _selectedSession.asStateFlow()

    // Messages for the active session
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Loading & state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // UI Input field state
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        val apiService = GeminiApiService.create()
        repository = ChatRepository(database.chatDao, apiService)

        allSessions = repository.allSessions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Automatically load messages when selected session changes
        viewModelScope.launch {
            _selectedSession.collectLatest { session ->
                if (session != null) {
                    repository.getMessagesForSession(session.id).collect { msgs ->
                        _messages.value = msgs
                    }
                } else {
                    _messages.value = emptyList()
                }
            }
        }

        // Auto-create a default session if list is empty after loading
        viewModelScope.launch {
            allSessions.collect { sessions ->
                if (sessions.isEmpty() && !_isLoading.value) {
                    // Wait slightly to ensure it's actually empty and not just loading
                    createDefaultSession()
                } else if (_selectedSession.value == null && sessions.isNotEmpty()) {
                    // Auto-select latest session
                    _selectedSession.value = sessions.first()
                }
            }
        }
    }

    private fun createDefaultSession() {
        viewModelScope.launch {
            val id = repository.createSession(
                title = "محادثة ذكية جديدة",
                systemInstruction = "أنت مساعد ذكاء اصطناعي مفيد ودود ولطيف ومحنك، تتحدث باللغة العربية بدقة ووضوح."
            )
            val defSession = ChatSession(
                id = id,
                title = "محادثة ذكية جديدة",
                systemInstruction = "أنت مساعد ذكاء اصطناعي مفيد ودود ولطيف ومحنك، تتحدث باللغة العربية بدقة ووضوح."
            )
            _selectedSession.value = defSession
            // Save a welcoming starting message
            repository.saveMessage(
                sessionId = id,
                isUser = false,
                text = "مرحباً بك! أنا مساعدك الذكي الخاص. كيف يمكنني مساعدتك اليوم؟ يمكنك سؤالي عن أي شيء وسأجيبك بكل سرور."
            )
        }
    }

    fun selectSession(session: ChatSession) {
        _selectedSession.value = session
        _errorMessage.value = null
    }

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun deleteSession(session: ChatSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
            if (_selectedSession.value?.id == session.id) {
                _selectedSession.value = null
            }
        }
    }

    fun createNewSession(title: String, systemInstruction: String?) {
        viewModelScope.launch {
            val actualTitle = title.ifBlank { "محادثة جديدة" }
            val actualInstruction = systemInstruction?.ifBlank { null }
            val id = repository.createSession(actualTitle, actualInstruction)
            val newSess = ChatSession(id = id, title = actualTitle, systemInstruction = actualInstruction)
            _selectedSession.value = newSess
            _inputText.value = ""
            _errorMessage.value = null
            
            // Add initial greeting message based on custom instruction if available
            val welcomeText = if (!actualInstruction.isNullOrBlank()) {
                "مرحباً! لقد تم إنشاء هذه المحادثة بالتعليمات الخاصة بك. كيف يمكنني خدمتك بصفتي مساعدك المخصص؟"
            } else {
                "مرحباً بك في هذه المحادثة الجديدة! كيف يمكنني مساعدتك اليوم؟"
            }
            repository.saveMessage(id, isUser = false, text = welcomeText)
        }
    }

    fun renameSession(session: ChatSession, newTitle: String) {
        viewModelScope.launch {
            val updated = session.copy(title = newTitle.ifBlank { session.title })
            repository.updateSession(updated)
            if (_selectedSession.value?.id == session.id) {
                _selectedSession.value = updated
            }
        }
    }

    fun clearActiveChatHistory() {
        val current = _selectedSession.value ?: return
        viewModelScope.launch {
            repository.deleteMessagesForSession(current.id)
            repository.saveMessage(
                sessionId = current.id,
                isUser = false,
                text = "لقد تم مسح سجل هذه المحادثة. كيف يمكنني مساعدتك مجدداً؟"
            )
        }
    }

    fun sendMessage() {
        val textToSend = _inputText.value.trim()
        val currentSession = _selectedSession.value ?: return

        if (textToSend.isEmpty()) return

        _inputText.value = ""
        _errorMessage.value = null

        viewModelScope.launch {
            // 1. Save local user message
            repository.saveMessage(currentSession.id, isUser = true, text = textToSend)

            // 2. Set Loading and prepare query
            _isLoading.value = true

            // Fetch updated messages list (including the one just added) to send as context
            val currentMessages = _messages.value
            
            // 3. Request Gemini Response
            val result = repository.generateChatResponse(
                messages = currentMessages,
                systemInstructionText = currentSession.systemInstruction
            )

            result.onSuccess { reply ->
                // Save model reply
                repository.saveMessage(currentSession.id, isUser = false, text = reply)
                
                // If the session was previously named "محادثة ذكية جديدة" or "محادثة جديدة", auto-rename it 
                // using a brief summarization of the user's first prompt to make the UI look highly polished.
                if (currentSession.title == "محادثة ذكية جديدة" || currentSession.title == "محادثة جديدة") {
                    val briefTitle = if (textToSend.length > 25) {
                        textToSend.take(22) + "..."
                    } else {
                        textToSend
                    }
                    renameSession(currentSession, briefTitle)
                }
            }.onFailure { exception ->
                _errorMessage.value = exception.localizedMessage ?: "حدث خطأ غير معروف"
            }

            _isLoading.value = false
        }
    }

    fun updateSession(session: ChatSession) {
        viewModelScope.launch {
            repository.updateSession(session)
            if (_selectedSession.value?.id == session.id) {
                _selectedSession.value = session
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
            _selectedSession.value = null
        }
    }
}
