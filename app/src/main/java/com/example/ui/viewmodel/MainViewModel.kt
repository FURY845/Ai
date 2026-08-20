package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DocumentEntity
import com.example.data.local.MistakeEntity
import com.example.data.local.ModelFileEntity
import com.example.data.local.PageEntity
import com.example.data.local.QuizEntity
import com.example.data.local.QuizQuestionEntity
import com.example.data.repository.AppRepository
import com.example.engine.ocr.ImagePreprocessor
import com.example.engine.ocr.OcrResult
import com.example.engine.quiz.ExamResultSummary
import com.example.engine.quiz.GeneratedQuiz
import com.example.engine.quiz.QuizConfig
import com.example.engine.search.SearchResultPassage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen(val title: String, val iconName: String) {
    CHAT("المحادثة الذكية", "chat"),
    QUIZ("الاختبارات", "quiz"),
    LEVEL_AND_MISTAKES("مستواي وأخطائي", "analytics"),
    SETTINGS("الإعدادات", "settings")
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "AI"
    val text: String,
    val isRefusal: Boolean = false,
    val sourcePassage: SearchResultPassage? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class SmartRecommendation(
    val title: String,
    val description: String,
    val targetSubject: String,
    val recommendedPages: List<String>,
    val iconType: String // "WEAKNESS", "STRENGTH", "REPETITION"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository.getInstance(application)

    // Navigation State - Defaults directly to CHAT (ChatGPT/Claude/Gemini style)
    private val _currentScreen = MutableStateFlow(AppScreen.CHAT)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    // Live Stats & Analytics
    val totalDocuments = repository.db.documentDao().getDocumentCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalPages = repository.getTotalPagesCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val completedQuizzesCount = repository.getCompletedQuizCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val avgQuizScore = repository.getAverageQuizScore()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val totalTestTimeSeconds = repository.getTotalTestTimeSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unresolvedMistakesCount = repository.getUnresolvedMistakeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalStudyMinutes = repository.getTotalStudyMinutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Models State
    val modelFiles = repository.getAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Documents State (Subject slots)
    val allDocuments = repository.getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableSubjects = repository.getAvailableSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun importPdfForSlot(
        uri: Uri,
        fileName: String,
        subject: String,
        docType: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.importPdfForSubjectSlot(uri, fileName, subject, docType)
            result.onSuccess {
                onResult(true, "تم حفظ وفهرسة الملف بنجاح (${it.pageCount} صفحة)")
            }.onFailure {
                onResult(false, it.message ?: "حدث خطأ أثناء استيراد الملف")
            }
        }
    }

    fun reindexDocument(docId: Long, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.reindexDocument(docId)
            result.onSuccess {
                onResult(true, "تمت إعادة بناء الفهرس المعكوس للملف بنجاح")
            }.onFailure {
                onResult(false, it.message ?: "فشلت إعادة الفهرسة")
            }
        }
    }

    fun deleteDocument(docId: Long) {
        viewModelScope.launch {
            repository.deleteDocument(docId)
        }
    }

    // --- Main AI Chat State (Starts completely clean and empty) ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun askQuestion(
        questionText: String,
        subject: String? = null,
        documentIds: List<Long>? = null
    ) {
        if (questionText.isBlank()) return

        val userMsg = ChatMessage(sender = "USER", text = questionText)
        _chatMessages.value = _chatMessages.value + userMsg
        _isChatLoading.value = true

        viewModelScope.launch {
            val response = repository.askCurriculum(
                question = questionText,
                subject = subject,
                documentIds = documentIds
            )
            val aiMsg = ChatMessage(
                sender = "AI",
                text = response.answer,
                isRefusal = response.isRefusal,
                sourcePassage = response.sourcePassage
            )
            _chatMessages.value = _chatMessages.value + aiMsg
            _isChatLoading.value = false
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
    }

    // --- Voice Recording & Whisper STT ---
    val isRecording = repository.audioRecorderHelper.isRecording
    val audioAmplitude = repository.audioRecorderHelper.amplitude

    fun startVoiceRecording(): Boolean {
        val res = repository.startAudioRecording()
        return res.isSuccess
    }

    fun stopVoiceRecordingAndTranscribe(onTranscribed: (String) -> Unit) {
        val audioFile = repository.stopAudioRecording()
        if (audioFile != null) {
            viewModelScope.launch {
                repository.transcribeRecording(audioFile).onSuccess { text ->
                    if (text.isNotBlank()) onTranscribed(text)
                }
            }
        }
    }

    // --- Camera & OCR Text Extraction ---
    private val _isOcrProcessing = MutableStateFlow(false)
    val isOcrProcessing: StateFlow<Boolean> = _isOcrProcessing.asStateFlow()

    fun extractTextFromCameraBitmap(bitmap: Bitmap, onExtracted: (String) -> Unit) {
        viewModelScope.launch {
            _isOcrProcessing.value = true
            val preprocessed = ImagePreprocessor.preprocessForOcr(bitmap)
            val ocr = repository.processOcrBitmap(preprocessed)
            _isOcrProcessing.value = false
            if (ocr.extractedText.isNotBlank()) {
                onExtracted(ocr.extractedText)
            }
        }
    }

    // --- Exam & Quiz State ---
    private val _activeQuiz = MutableStateFlow<QuizEntity?>(null)
    val activeQuiz: StateFlow<QuizEntity?> = _activeQuiz.asStateFlow()

    private val _quizQuestions = MutableStateFlow<List<QuizQuestionEntity>>(emptyList())
    val quizQuestions: StateFlow<List<QuizQuestionEntity>> = _quizQuestions.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    private val _isQuizLoading = MutableStateFlow(false)
    val isQuizLoading: StateFlow<Boolean> = _isQuizLoading.asStateFlow()

    private val _quizErrorMessage = MutableStateFlow<String?>(null)
    val quizErrorMessage: StateFlow<String?> = _quizErrorMessage.asStateFlow()

    fun clearQuizError() {
        _quizErrorMessage.value = null
    }

    private val _examSummaryReport = MutableStateFlow<ExamResultSummary?>(null)
    val examSummaryReport: StateFlow<ExamResultSummary?> = _examSummaryReport.asStateFlow()

    private val _detectedUnitsAndLessons = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val detectedUnitsAndLessons: StateFlow<Map<String, List<String>>> = _detectedUnitsAndLessons.asStateFlow()

    // State for visual feedback on current question
    private val _selectedChoiceState = MutableStateFlow<String?>(null)
    val selectedChoiceState: StateFlow<String?> = _selectedChoiceState.asStateFlow()

    private val _isAnswerFeedbackShowing = MutableStateFlow(false)
    val isAnswerFeedbackShowing: StateFlow<Boolean> = _isAnswerFeedbackShowing.asStateFlow()

    val allQuizzes = repository.getAllQuizzes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedQuizzes = repository.getCompletedQuizzes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadUnitsForSubject(subject: String) {
        viewModelScope.launch {
            val structure = repository.detectUnitsAndLessonsForSubject(subject)
            _detectedUnitsAndLessons.value = structure
        }
    }

    fun startConfiguredQuiz(config: QuizConfig) {
        viewModelScope.launch {
            _isQuizLoading.value = true
            _quizErrorMessage.value = null
            _examSummaryReport.value = null
            _selectedChoiceState.value = null
            _isAnswerFeedbackShowing.value = false
            val result = repository.generateConfiguredQuiz(config)
            result.onSuccess { generated ->
                _activeQuiz.value = generated.quiz
                _quizQuestions.value = generated.questions
                _currentQuestionIndex.value = 0
            }.onFailure { error ->
                _quizErrorMessage.value = error.message ?: "فشل إنشاء الامتحان"
            }
            _isQuizLoading.value = false
        }
    }

    fun startMistakesRetest(subject: String = "الكل") {
        val config = QuizConfig(
            subject = subject,
            scopeType = "MISTAKES_RETEST",
            scopeDetail = "تدريب علاجي من أخطائي",
            practiceMode = "MISTAKES",
            questionCount = 10,
            durationMinutes = 15
        )
        startConfiguredQuiz(config)
    }

    /**
     * Evaluates selected MCQ option, triggers Green (correct) / Red (incorrect) visual feedback,
     * and automatically moves to the next question.
     */
    fun selectAnswerAndAdvance(index: Int, selectedOption: String, timeSpentSeconds: Int) {
        if (_isAnswerFeedbackShowing.value) return

        val questions = _quizQuestions.value.toMutableList()
        if (index !in questions.indices) return

        val currentQ = questions[index]
        _selectedChoiceState.value = selectedOption
        _isAnswerFeedbackShowing.value = true

        viewModelScope.launch {
            val (isCorrect, _) = repository.submitQuizAnswer(currentQ, selectedOption)
            questions[index] = currentQ.copy(
                userAnswer = selectedOption,
                isAnswered = true,
                isCorrect = isCorrect
            )
            _quizQuestions.value = questions

            // Brief pause (1.2s) so student observes Green/Red feedback
            delay(1200)
            _isAnswerFeedbackShowing.value = false
            _selectedChoiceState.value = null

            if (index < questions.size - 1) {
                _currentQuestionIndex.value = index + 1
            } else {
                // Last question answered -> Finish exam automatically
                finishAndGradeExam(timeSpentSeconds)
            }
        }
    }

    fun jumpToQuestion(index: Int) {
        if (index in _quizQuestions.value.indices) {
            _currentQuestionIndex.value = index
        }
    }

    fun finishAndGradeExam(timeSpentSeconds: Int) {
        val quiz = _activeQuiz.value ?: return
        viewModelScope.launch {
            _isQuizLoading.value = true
            val summary = repository.completeExam(quiz.id, timeSpentSeconds)
            _examSummaryReport.value = summary
            _activeQuiz.value = null
            _isQuizLoading.value = false
        }
    }

    fun exitQuiz() {
        _activeQuiz.value = null
        _quizQuestions.value = emptyList()
        _examSummaryReport.value = null
        _selectedChoiceState.value = null
        _isAnswerFeedbackShowing.value = false
    }

    // --- Mistakes State ---
    val unresolvedMistakes = repository.getUnresolvedMistakes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun resolveMistake(id: Long) {
        viewModelScope.launch {
            repository.resolveMistake(id)
        }
    }

    // --- Model Manager State ---
    private val _modelTestStatus = MutableStateFlow<String?>(null)
    val modelTestStatus: StateFlow<String?> = _modelTestStatus.asStateFlow()

    fun testModel(model: ModelFileEntity) {
        viewModelScope.launch {
            _modelTestStatus.value = "جاري فحص النموذج محلياً..."
            val res = repository.testModel(model)
            _modelTestStatus.value = res
        }
    }

    fun importModelFile(uri: Uri, name: String, type: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.importModel(uri, name, type)
            result.onSuccess {
                onResult(true, "تم استيراد ${it.name} بنجاح وحفظه محلياً.")
            }.onFailure {
                onResult(false, it.message ?: "فشل استيراد النموذج")
            }
        }
    }

    fun deleteModelFile(model: ModelFileEntity) {
        viewModelScope.launch {
            repository.deleteModel(model)
        }
    }

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }
    }
}
