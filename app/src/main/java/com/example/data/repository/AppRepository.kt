package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.DocumentEntity
import com.example.data.local.MistakeEntity
import com.example.data.local.ModelFileEntity
import com.example.data.local.PageEntity
import com.example.data.local.QuizEntity
import com.example.data.local.QuizQuestionEntity
import com.example.data.local.StudyActivityEntity
import com.example.engine.ai.AiResponse
import com.example.engine.ai.LocalAiEngine
import com.example.engine.model.ModelFileManager
import com.example.engine.ocr.LocalOcrEngine
import com.example.engine.ocr.OcrResult
import com.example.engine.ocr.PageAnswerResult
import com.example.engine.pdf.PdfExtractor
import com.example.engine.quiz.ExamResultSummary
import com.example.engine.quiz.GeneratedQuiz
import com.example.engine.quiz.QuizConfig
import com.example.engine.quiz.QuizEngine
import com.example.engine.search.LocalSearchEngine
import com.example.engine.stt.AudioRecorderHelper
import com.example.engine.stt.LocalSttEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

class AppRepository(
    private val context: Context,
    val db: AppDatabase,
    val aiEngine: LocalAiEngine,
    val sttEngine: LocalSttEngine,
    val ocrEngine: LocalOcrEngine,
    val searchEngine: LocalSearchEngine,
    val quizEngine: QuizEngine,
    val modelFileManager: ModelFileManager,
    val audioRecorderHelper: AudioRecorderHelper
) {

    // --- Documents & PDFs (Subject-based Slots: 📖 الشرح & 📝 الأسئلة) ---
    fun getAllDocuments(): Flow<List<DocumentEntity>> = db.documentDao().getAllDocuments()
    fun getAvailableSubjects(): Flow<List<String>> = db.documentDao().getDistinctSubjects()
    fun getDocumentsBySubject(subject: String): Flow<List<DocumentEntity>> = db.documentDao().getDocumentsBySubject(subject)
    fun getDocumentsBySubjectAndTypeFlow(subject: String, docType: String): Flow<List<DocumentEntity>> =
        db.documentDao().getDocumentsBySubjectAndTypeFlow(subject, docType)
    fun getPagesForDocument(documentId: Long): Flow<List<PageEntity>> = db.pageDao().getPagesForDocument(documentId)

    suspend fun importPdfForSubjectSlot(
        uri: Uri,
        fileName: String,
        subject: String,
        docType: String // "EXPLANATION" or "QUESTIONS"
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        if (subject.isBlank()) {
            return@withContext Result.failure(Exception("اكتب اسم المادة قبل رفع ملف PDF"))
        }
        // Enforce 10-PDF maximum limit across all slots
        val currentDocs = db.documentDao().getAllDocuments().firstOrNull() ?: emptyList()
        val existingSlot = db.documentDao().getDocumentBySubjectAndType(subject, docType)

        if (existingSlot == null && currentDocs.size >= 10) {
            return@withContext Result.failure(Exception("الحد الأقصى هو 10 ملفات PDF."))
        }

        // If slot already exists, replace it
        if (existingSlot != null) {
            deleteDocument(existingSlot.id)
        }

        val extractRes = PdfExtractor.importAndExtractPdf(context, uri, fileName)
        extractRes.mapCatching { (file, extractedDoc) ->
            if (extractedDoc.pages.none { it.text.isNotBlank() }) {
                throw IllegalArgumentException("لم يتم استخراج نص من ملف PDF؛ لم تتم إضافته إلى المنهج")
            }
            val docEntity = DocumentEntity(
                title = extractedDoc.title.ifBlank { fileName.removeSuffix(".pdf") },
                subject = subject,
                docType = docType,
                filePath = file.absolutePath,
                fileSize = file.length(),
                pageCount = extractedDoc.pageCount,
                isIndexed = false
            )
            val docId = db.documentDao().insertDocument(docEntity)

            val pageEntities = extractedDoc.pages.map { page ->
                PageEntity(
                    documentId = docId,
                    pageNumber = page.pageNumber,
                    rawText = page.text,
                    normalizedText = page.normalizedText,
                    charCount = page.text.length
                )
            }
            db.pageDao().insertPages(pageEntities)

            // Index in background search engine
            val savedDoc = docEntity.copy(id = docId)
            searchEngine.indexDocument(savedDoc, pageEntities)

            db.studyActivityDao().logActivity(
                StudyActivityEntity(
                    subject = subject,
                    activityType = "READ",
                    durationMinutes = 5
                )
            )

            savedDoc.copy(isIndexed = true)
        }
    }

    suspend fun reindexDocument(docId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val doc = db.documentDao().getDocumentById(docId) ?: return@withContext Result.failure(Exception("الملف غير موجود"))
            val pages = db.pageDao().getPagesForDocumentSync(docId)
            searchEngine.indexDocument(doc, pages)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDocument(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        db.documentDao().updateDocument(doc)
    }

    suspend fun deleteDocument(docId: Long) = withContext(Dispatchers.IO) {
        val doc = db.documentDao().getDocumentById(docId)
        if (doc != null) {
            val file = File(doc.filePath)
            if (file.exists()) file.delete()
            db.pageDao().deletePagesForDocument(docId)
            db.searchTokenDao().deleteTokensForDocument(docId)
            db.documentDao().deleteDocument(docId)
        }
    }

    // --- Search & Strict AI RAG (Strictly Primary Source: Assigned 📖 PDF الشرح) ---
    suspend fun askCurriculum(
        question: String,
        subject: String? = null,
        documentIds: List<Long>? = null
    ): AiResponse = withContext(Dispatchers.IO) {
        val passages = searchEngine.searchCurriculum(
            question = question,
            subjectFilter = subject,
            documentIds = documentIds,
            docTypeFilter = "EXPLANATION", // Primary knowledge source is 📖 PDF الشرح
            topK = 3
        )
        val response = aiEngine.generateAnswer(question, passages)

        if (subject != null) {
            db.studyActivityDao().logActivity(
                StudyActivityEntity(
                    subject = subject,
                    activityType = "CHAT",
                    durationMinutes = 2
                )
            )
        }

        response
    }

    // --- OCR & Camera ---
    suspend fun processOcrBitmap(bitmap: android.graphics.Bitmap): OcrResult {
        return ocrEngine.extractTextFromBitmap(bitmap)
    }

    suspend fun askQuestionOnOcrPage(question: String, pageText: String): PageAnswerResult {
        return ocrEngine.answerQuestionFromPage(question, pageText)
    }

    // --- STT & Voice ---
    fun startAudioRecording(): Result<File> = audioRecorderHelper.startRecording()
    fun stopAudioRecording(): File? = audioRecorderHelper.stopRecording()
    fun startSpeechRecognition(onResult: (String) -> Unit): Result<Unit> =
        sttEngine.startListening(onResult)
    fun stopSpeechRecognition() = sttEngine.stopListening()

    suspend fun transcribeRecording(audioFile: File): Result<String> {
        return sttEngine.transcribeAudio(audioFile)
    }

    // --- Quizzes & Exams ---
    fun getAllQuizzes(): Flow<List<QuizEntity>> = db.quizDao().getAllQuizzes()
    fun getCompletedQuizzes(): Flow<List<QuizEntity>> = db.quizDao().getCompletedQuizzes()
    fun getQuestionsForQuiz(quizId: Long): Flow<List<QuizQuestionEntity>> = db.quizDao().getQuestionsForQuiz(quizId)

    suspend fun detectUnitsAndLessonsForSubject(subject: String): Map<String, List<String>> {
        return quizEngine.detectUnitsAndLessonsForSubject(subject)
    }

    suspend fun generateConfiguredQuiz(config: QuizConfig): Result<GeneratedQuiz> {
        return quizEngine.generateConfiguredQuiz(config)
    }

    suspend fun submitQuizAnswer(question: QuizQuestionEntity, userAnswer: String): Pair<Boolean, String> {
        return quizEngine.evaluateAnswer(question, userAnswer)
    }

    suspend fun completeExam(quizId: Long, timeSpentSeconds: Int): ExamResultSummary {
        val summary = quizEngine.completeExam(quizId, timeSpentSeconds)
        db.studyActivityDao().logActivity(
            StudyActivityEntity(
                subject = "امتحان تدريبي",
                activityType = "QUIZ",
                durationMinutes = (timeSpentSeconds / 60).coerceAtLeast(1)
            )
        )
        return summary
    }

    // --- Mistakes Notebook ---
    fun getUnresolvedMistakes(): Flow<List<MistakeEntity>> = db.mistakeDao().getUnresolvedMistakes()
    fun getMistakesBySubject(subject: String): Flow<List<MistakeEntity>> = db.mistakeDao().getMistakesBySubject(subject)
    suspend fun resolveMistake(id: Long) = db.mistakeDao().resolveMistake(id)

    // --- Analytics & Stats ---
    fun getCompletedQuizCount(): Flow<Int> = db.quizDao().getCompletedQuizCount()
    fun getAverageQuizScore(): Flow<Float?> = db.quizDao().getAverageQuizScore()
    fun getUnresolvedMistakeCount(): Flow<Int> = db.mistakeDao().getUnresolvedMistakeCount()
    fun getTotalPagesCount(): Flow<Int> = db.pageDao().getTotalPagesCount()
    fun getTotalStudyMinutes(): Flow<Int?> = db.studyActivityDao().getTotalStudyMinutes()
    fun getTotalTestTimeSeconds(): Flow<Int?> = db.quizDao().getTotalTestTimeSeconds()

    // --- Model File Manager ---
    fun getAllModels(): Flow<List<ModelFileEntity>> = db.modelFileDao().getAllModelFiles()
    suspend fun importModel(uri: Uri, name: String, type: String) = modelFileManager.importModelFromUri(uri, name, type)
    suspend fun testModel(model: ModelFileEntity) = modelFileManager.testModel(model)
    suspend fun deleteModel(model: ModelFileEntity) = modelFileManager.deleteModel(model)

    suspend fun seedInitialDataIfEmpty() {
        modelFileManager.seedInitialModelPlaceholdersIfEmpty()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val ai = LocalAiEngine(context.applicationContext)
                val stt = LocalSttEngine(context.applicationContext)
                val ocr = LocalOcrEngine(context.applicationContext)
                val search = LocalSearchEngine(db)
                val quiz = QuizEngine(db)
                val modelManager = ModelFileManager(context, db, ai, stt, ocr)
                val recorder = AudioRecorderHelper(context)

                val instance = AppRepository(
                    context = context.applicationContext,
                    db = db,
                    aiEngine = ai,
                    sttEngine = stt,
                    ocrEngine = ocr,
                    searchEngine = search,
                    quizEngine = quiz,
                    modelFileManager = modelManager,
                    audioRecorderHelper = recorder
                )
                INSTANCE = instance
                instance
            }
        }
    }
}
