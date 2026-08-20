package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String, // "اللغة العربية", "التاريخ", "الجغرافيا"
    val docType: String = "EXPLANATION", // "EXPLANATION" (📖 ملف الشرح) or "QUESTIONS" (📝 ملف الأسئلة)
    val filePath: String,
    val fileSize: Long,
    val pageCount: Int,
    val addedAt: Long = System.currentTimeMillis(),
    val isIndexed: Boolean = false
)

@Entity(tableName = "document_pages")
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val rawText: String,
    val normalizedText: String,
    val charCount: Int
)

@Entity(tableName = "search_tokens")
data class SearchTokenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val documentId: Long,
    val token: String,
    val frequency: Int
)

@Entity(tableName = "quizzes")
data class QuizEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val title: String,
    val scopeType: String = "FULL_CURRICULUM", // LESSON, UNIT, FULL_CURRICULUM, MISTAKES_RETEST
    val scopeDetail: String = "المنهج كامل",
    val practiceMode: String = "CUSTOM", // QUICK, CUSTOM, MISTAKES, LESSON, UNIT
    val difficulty: String = "PROGRESSIVE", // EASY, MEDIUM, HARD, PROGRESSIVE
    val totalQuestions: Int,
    val score: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val unansweredCount: Int = 0,
    val mcqCount: Int = 0,
    val essayCount: Int = 0,
    val timeSpentSeconds: Int = 0,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val subjectBreakdownJson: String = "{}",
    val strengthsJson: String = "[]",
    val weaknessesJson: String = "[]",
    val recommendedPagesJson: String = "[]"
)

@Entity(tableName = "quiz_questions")
data class QuizQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: Long,
    val documentId: Long,
    val sourceDocTitle: String = "",
    val pageNumber: Int,
    val lessonOrUnit: String = "الوحدة الأولى",
    val topic: String = "موضوع رئيسي",
    val difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD
    val questionType: String = "MCQ", // MCQ
    val questionText: String,
    val optionsJson: String = "[]", // JSON array of 4 options for MCQ
    val correctAnswer: String,
    val explanation: String,
    val sourcePassage: String,
    val userAnswer: String = "",
    val isAnswered: Boolean = false,
    val isCorrect: Boolean = false,
    val isMarkedForReview: Boolean = false,
    val essayFeedback: String = ""
)

@Entity(tableName = "mistakes")
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val questionText: String,
    val questionType: String = "MCQ",
    val correctAnswer: String,
    val userAnswer: String,
    val explanation: String,
    val sourceDocumentTitle: String,
    val sourcePageNumber: Int,
    val lessonOrUnit: String = "الوحدة الأولى",
    val topic: String = "موضوع رئيسي",
    val testName: String = "امتحان تدريبي",
    val difficulty: String = "MEDIUM",
    val mistakeCount: Int = 1,
    val lastMistakeAt: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false
)

@Entity(tableName = "model_files")
data class ModelFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modelType: String, // "AI_GGUF", "WHISPER_BIN", "TESSERACT_DATA"
    val name: String,
    val fileName: String,
    val localPath: String,
    val fileSize: Long,
    val isActive: Boolean = false,
    val isLoaded: Boolean = false,
    val statusDescription: String = "جاهز للاستخدام",
    val addedAt: Long = System.currentTimeMillis(),
    val lastTestedAt: Long = 0L,
    val testResult: String = ""
)

@Entity(tableName = "study_activities")
data class StudyActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val activityType: String, // "CHAT", "OCR", "QUIZ", "READ"
    val durationMinutes: Int,
    val timestamp: Long = System.currentTimeMillis()
)
