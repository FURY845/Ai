package com.example.engine.quiz

import com.example.data.local.AppDatabase
import com.example.data.local.DocumentEntity
import com.example.data.local.MistakeEntity
import com.example.data.local.PageEntity
import com.example.data.local.QuizEntity
import com.example.data.local.QuizQuestionEntity
import com.example.engine.arabic.ArabicTextProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class GeneratedQuiz(
    val quiz: QuizEntity,
    val questions: List<QuizQuestionEntity>
)

data class QuizConfig(
    val subject: String = "",
    val scopeType: String = "FULL_CURRICULUM", // LESSON, UNIT, FULL_CURRICULUM, MISTAKES_RETEST
    val scopeDetail: String = "المنهج كامل",
    val targetDocId: Long? = null,
    val unitName: String = "",
    val lessonName: String = "",
    val questionCount: Int = 10,
    val durationMinutes: Int = 15,
    val questionFormat: String = "MCQ_ONLY",
    val difficulty: String = "PROGRESSIVE",
    val practiceMode: String = "CUSTOM"
)

data class ExamResultSummary(
    val quizId: Long,
    val score: Int,
    val totalQuestions: Int,
    val percentage: Int,
    val timeSpentSeconds: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val unansweredCount: Int,
    val subjectBreakdown: Map<String, Int>,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val recommendedPages: List<String>
)

class QuizEngine(private val db: AppDatabase) {

    /**
     * Dynamically extracts detected Units and Lessons from actual assigned subject PDFs
     */
    suspend fun detectUnitsAndLessonsForSubject(subject: String): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val documentDao = db.documentDao()
        val pageDao = db.pageDao()

        val explanationDoc = documentDao.getDocumentBySubjectAndType(subject, "EXPLANATION")
        val questionsDoc = documentDao.getDocumentBySubjectAndType(subject, "QUESTIONS")

        val targetDoc = explanationDoc ?: questionsDoc
        if (targetDoc == null) {
            return@withContext emptyMap()
        }

        val pages = pageDao.getPagesForDocumentSync(targetDoc.id)
        if (pages.isEmpty()) {
            return@withContext emptyMap()
        }

        val structure = mutableMapOf<String, MutableList<String>>()

        val unitRegex = Regex("(?:الوحدة|الفصل|الباب)\\s+([الأول|الثاني|الثالث|الرابع|الخامس|السادس|السابع|الثامن|\\d]+)[:\\s]*(.{0,40})")
        val lessonRegex = Regex("(?:الدرس|الموضوع|الفصل الفرعي)\\s+([الأول|الثاني|الثالث|الرابع|الخامس|\\d]+)[:\\s]*(.{0,40})")

        var currentUnit = "الفصل الأول"

        for (page in pages) {
            val text = page.rawText
            val unitMatch = unitRegex.find(text)
            if (unitMatch != null) {
                val num = unitMatch.groupValues[1]
                val name = unitMatch.groupValues[2].trim().take(30)
                currentUnit = "الوحدة $num" + if (name.isNotBlank()) ": $name" else ""
                if (!structure.containsKey(currentUnit)) {
                    structure[currentUnit] = mutableListOf()
                }
            }

            val lessonMatch = lessonRegex.find(text)
            if (lessonMatch != null) {
                val lNum = lessonMatch.groupValues[1]
                val lName = lessonMatch.groupValues[2].trim().take(30)
                val lessonTitle = "الدرس $lNum" + if (lName.isNotBlank()) ": $lName" else ""
                val list = structure.getOrPut(currentUnit) { mutableListOf() }
                if (!list.contains(lessonTitle)) {
                    list.add(lessonTitle)
                }
            }
        }

        // If no explicit unit headings were found in text, create dynamic sections based on page spans
        if (structure.isEmpty()) {
            val totalPages = pages.size
            val pageSizePerUnit = (totalPages / 4).coerceAtLeast(1)
            for (i in 0 until totalPages step pageSizePerUnit) {
                val endPage = (i + pageSizePerUnit).coerceAtMost(totalPages)
                val unitTitle = "الجزء ${i / pageSizePerUnit + 1} (ص ${i + 1} - ${endPage})"
                structure[unitTitle] = mutableListOf("الدرس الأساسي", "تدريبات وتطبيقات")
            }
        }

        structure.mapValues { entry ->
            if (entry.value.isEmpty()) listOf("الدرس الأول", "الدرس الثاني") else entry.value.distinct()
        }
    }

    /**
     * Generates a strictly 4-choice Multiple Choice Exam from actual indexed curriculum data
     */
    suspend fun generateConfiguredQuiz(config: QuizConfig): Result<GeneratedQuiz> = withContext(Dispatchers.IO) {
        val documentDao = db.documentDao()
        val pageDao = db.pageDao()

        // 1. Check if this is a Mistakes Retest
        if (config.scopeType == "MISTAKES_RETEST" || config.practiceMode == "MISTAKES") {
            return@withContext generateMistakesRetestQuiz(config)
        }

        // 2. Retrieve Candidate Pages from Subject's Question / Explanation PDFs
        val questionsDoc = documentDao.getDocumentBySubjectAndType(config.subject, "QUESTIONS")
        val explanationDoc = documentDao.getDocumentBySubjectAndType(config.subject, "EXPLANATION")

        val candidatePages = mutableListOf<PageEntity>()
        if (questionsDoc != null) {
            candidatePages.addAll(pageDao.getPagesForDocumentSync(questionsDoc.id))
        }
        if (explanationDoc != null) {
            candidatePages.addAll(pageDao.getPagesForDocumentSync(explanationDoc.id))
        }

        // If no docs for this subject, fail safely with informative message
        if (candidatePages.isEmpty()) {
            return@withContext Result.failure(
                Exception("لا توجد ملفات PDF أو بيانات منهج مضافة لمادة «${config.subject}». يرجى رفع ملف المنهج من شاشة الإعدادات أولاً.")
            )
        }

        val finalCount = config.questionCount.coerceIn(1, 50)
        val questions = mutableListOf<QuizQuestionEntity>()
        val usedQuestionTexts = mutableSetOf<String>()

        // Gather candidate distractors from across all pages of this subject
        val allSentences = candidatePages.flatMap { page ->
            ArabicTextProcessor.splitIntoPassages(page.rawText).flatMap { p ->
                p.split(Regex("[.،؟!\\n]+")).map { it.trim() }.filter { it.length in 10..80 }
            }
        }

        val shuffledPages = candidatePages.shuffled()
        var pageIdx = 0
        var attempts = 0
        val maxAttempts = candidatePages.size * 5

        while (questions.size < finalCount && attempts < maxAttempts && shuffledPages.isNotEmpty()) {
            val page = shuffledPages[pageIdx % shuffledPages.size]
            pageIdx++
            attempts++

            val docTitle = if (questionsDoc?.id == page.documentId) questionsDoc.title else (explanationDoc?.title ?: "كتاب ${config.subject}")
            val q = buildFourChoiceMcqFromPage(
                page = page,
                docTitle = docTitle,
                subject = config.subject,
                unitName = config.unitName.ifBlank { "الوحدة الأولى" },
                lessonName = config.lessonName.ifBlank { "الدرس الأول" },
                difficulty = calculateDifficultyForIndex(config.difficulty, questions.size, finalCount),
                existing = usedQuestionTexts,
                distractorPool = allSentences
            )
            if (q != null && !usedQuestionTexts.contains(q.questionText)) {
                usedQuestionTexts.add(q.questionText)
                questions.add(q)
            }
        }

        if (questions.isEmpty()) {
            return@withContext Result.failure(
                Exception("لم يتم العثور على نصوص كافية في ملفات مادة «${config.subject}» لتوليد أسئلة. تأكد من أن ملف الـ PDF يحتوي على نصوص نصية واضحة.")
            )
        }

        val examTitle = buildExamTitle(config)

        val quizEntity = QuizEntity(
            subject = config.subject,
            title = examTitle,
            scopeType = config.scopeType,
            scopeDetail = "${config.scopeDetail} (${questions.size} أسئلة - ${config.durationMinutes} دقيقة)",
            practiceMode = config.practiceMode,
            difficulty = config.difficulty,
            totalQuestions = questions.size,
            mcqCount = questions.size,
            essayCount = 0,
            score = 0,
            isCompleted = false
        )

        val quizId = db.quizDao().insertQuiz(quizEntity)
        val savedQuestions = questions.map { it.copy(quizId = quizId) }
        db.quizDao().insertQuestions(savedQuestions)

        Result.success(GeneratedQuiz(quizEntity.copy(id = quizId), savedQuestions))
    }

    /**
     * Generates a Mistakes Retest Quiz (100% 4-Choice MCQs)
     */
    private suspend fun generateMistakesRetestQuiz(config: QuizConfig): Result<GeneratedQuiz> {
        val mistakeDao = db.mistakeDao()
        val unresolved = if (config.subject.isNotBlank() && config.subject != "المنهج كامل" && config.subject != "الكل") {
            mistakeDao.getUnresolvedMistakesSync().filter { it.subject == config.subject }
        } else {
            mistakeDao.getUnresolvedMistakesSync()
        }

        if (unresolved.isEmpty()) {
            return Result.failure(Exception("كشكول الأخطاء فارغ حالياً! لم تقم بأي أخطاء مسجلة بعد."))
        }

        val targetMistakes = unresolved.shuffled().take(config.questionCount)
        val questions = mutableListOf<QuizQuestionEntity>()

        for (mistake in targetMistakes) {
            val optionsJson = generateDistractorsForMistake(mistake)
            val q = QuizQuestionEntity(
                quizId = 0,
                documentId = 1,
                sourceDocTitle = mistake.sourceDocumentTitle,
                pageNumber = mistake.sourcePageNumber,
                lessonOrUnit = mistake.lessonOrUnit,
                topic = mistake.topic,
                difficulty = mistake.difficulty,
                questionType = "MCQ",
                questionText = "إعادة تقييم: ${mistake.questionText}",
                optionsJson = optionsJson,
                correctAnswer = mistake.correctAnswer,
                explanation = mistake.explanation,
                sourcePassage = "سؤال معالجة من كشكول الأخطاء السابقة (ص ${mistake.sourcePageNumber})."
            )
            questions.add(q)
        }

        val quizEntity = QuizEntity(
            subject = if (config.subject.isBlank() || config.subject == "الكل") "كشكول أخطائي" else "أخطاء ${config.subject}",
            title = "🎯 تدريب معالجة الأخطاء السابقة",
            scopeType = "MISTAKES_RETEST",
            scopeDetail = "أسئلة مخصصة من نقاط الضعف السابقة",
            practiceMode = "MISTAKES",
            difficulty = "PROGRESSIVE",
            totalQuestions = questions.size,
            mcqCount = questions.size,
            essayCount = 0,
            score = 0,
            isCompleted = false
        )

        val quizId = db.quizDao().insertQuiz(quizEntity)
        val savedQuestions = questions.map { it.copy(quizId = quizId) }
        db.quizDao().insertQuestions(savedQuestions)

        return Result.success(GeneratedQuiz(quizEntity.copy(id = quizId), savedQuestions))
    }

    private fun generateDistractorsForMistake(mistake: MistakeEntity): String {
        val correct = mistake.correctAnswer
        val distractors = mutableListOf<String>()
        if (mistake.userAnswer.isNotBlank() && mistake.userAnswer != correct) {
            distractors.add(mistake.userAnswer)
        }
        while (distractors.size < 3) {
            distractors.add("خيار غير مطابق للعبارة الصحيحة ${distractors.size + 1}")
        }
        val options = (listOf(correct) + distractors.take(3)).shuffled()
        return JSONArray(options).toString()
    }

    /**
     * Evaluates selected answer: Correct -> True, Incorrect -> False and logs mistake
     */
    suspend fun evaluateAnswer(
        question: QuizQuestionEntity,
        userAnswer: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val normUser = ArabicTextProcessor.normalize(userAnswer.trim())
        val normCorrect = ArabicTextProcessor.normalize(question.correctAnswer.trim())

        val isMatch = normUser.equals(normCorrect, ignoreCase = true) ||
                (normUser.length > 2 && normCorrect.contains(normUser)) ||
                (normCorrect.length > 2 && normUser.contains(normCorrect))

        val updatedQuestion = question.copy(
            userAnswer = userAnswer,
            isAnswered = true,
            isCorrect = isMatch
        )
        db.quizDao().updateQuestion(updatedQuestion)

        if (!isMatch) {
            val mistakeDao = db.mistakeDao()
            val existing = mistakeDao.findMistakeByQuestion(question.questionText)
            if (existing != null) {
                mistakeDao.updateMistake(
                    existing.copy(
                        mistakeCount = existing.mistakeCount + 1,
                        userAnswer = userAnswer,
                        lastMistakeAt = System.currentTimeMillis(),
                        isResolved = false
                    )
                )
            } else {
                mistakeDao.insertMistake(
                    MistakeEntity(
                        subject = question.sourceDocTitle.ifBlank { "المنهج" },
                        questionText = question.questionText,
                        questionType = "MCQ",
                        correctAnswer = question.correctAnswer,
                        userAnswer = userAnswer,
                        explanation = question.explanation,
                        sourceDocumentTitle = question.sourceDocTitle,
                        sourcePageNumber = question.pageNumber,
                        lessonOrUnit = question.lessonOrUnit,
                        topic = question.topic,
                        difficulty = question.difficulty,
                        mistakeCount = 1,
                        lastMistakeAt = System.currentTimeMillis()
                    )
                )
            }
        }

        val feedback = if (isMatch) "✅ إجابة صحيحة!" else "❌ إجابة خاطئة. الإجابة الصحيحة هي: ${question.correctAnswer}"
        Pair(isMatch, feedback)
    }

    /**
     * Finalizes exam, calculates analytics, returns summary
     */
    suspend fun completeExam(quizId: Long, timeSpentSeconds: Int): ExamResultSummary = withContext(Dispatchers.IO) {
        val quiz = db.quizDao().getQuizById(quizId) ?: return@withContext emptySummary(quizId)
        val questions = db.quizDao().getQuestionsForQuizSync(quizId)

        val total = questions.size
        val answered = questions.filter { it.isAnswered }
        val correct = answered.count { it.isCorrect }
        val wrong = answered.count { !it.isCorrect }
        val unanswered = total - answered.size
        val score = correct
        val percentage = if (total > 0) ((score.toFloat() / total.toFloat()) * 100).roundToInt() else 0

        val breakdown = questions.groupBy { it.sourceDocTitle.ifBlank { quiz.subject } }
            .mapValues { (_, list) ->
                val c = list.count { it.isCorrect }
                if (list.isNotEmpty()) ((c.toFloat() / list.size.toFloat()) * 100).roundToInt() else 0
            }

        val wrongQuestions = questions.filter { !it.isCorrect && it.isAnswered }
        val weaknesses = wrongQuestions.map { "${it.lessonOrUnit} (${it.topic})" }.distinct().take(4)
        val strengths = questions.filter { it.isCorrect }.map { "${it.lessonOrUnit} (${it.topic})" }.distinct().take(4)
        val recommendedPages = wrongQuestions.map { "كتاب ${it.sourceDocTitle} - ص ${it.pageNumber}" }.distinct().take(5)

        val updatedQuiz = quiz.copy(
            score = score,
            correctCount = correct,
            wrongCount = wrong,
            unansweredCount = unanswered,
            timeSpentSeconds = timeSpentSeconds,
            isCompleted = true,
            subjectBreakdownJson = JSONObject(breakdown as Map<*, *>).toString(),
            strengthsJson = JSONArray(strengths).toString(),
            weaknessesJson = JSONArray(weaknesses).toString(),
            recommendedPagesJson = JSONArray(recommendedPages).toString()
        )
        db.quizDao().updateQuiz(updatedQuiz)

        ExamResultSummary(
            quizId = quizId,
            score = score,
            totalQuestions = total,
            percentage = percentage,
            timeSpentSeconds = timeSpentSeconds,
            correctCount = correct,
            wrongCount = wrong,
            unansweredCount = unanswered,
            subjectBreakdown = breakdown,
            strengths = strengths,
            weaknesses = weaknesses,
            recommendedPages = recommendedPages
        )
    }

    private fun buildFourChoiceMcqFromPage(
        page: PageEntity,
        docTitle: String,
        subject: String,
        unitName: String,
        lessonName: String,
        difficulty: String,
        existing: Set<String>,
        distractorPool: List<String>
    ): QuizQuestionEntity? {
        val passages = ArabicTextProcessor.splitIntoPassages(page.rawText)
        if (passages.isEmpty()) return null

        val passage = passages.firstOrNull { it.length > 40 } ?: passages.firstOrNull() ?: return null
        val sentences = passage.split(Regex("[.،؟!\\n]+")).map { it.trim() }.filter { it.length > 20 }
        if (sentences.isEmpty()) return null

        val targetSentence = sentences.shuffled().firstOrNull() ?: return null
        val words = targetSentence.split(" ").filter { it.length > 2 }
        if (words.size < 4) return null

        val keyPhrase = words.takeLast((words.size / 3).coerceIn(2, 4)).joinToString(" ")
        val questionStem = targetSentence.replace(keyPhrase, "............")

        val qText = "وفقاً لما ورد في المنهج: أكمل العبارة الآتية بدقة:\n\"$questionStem\""
        if (existing.contains(qText)) return null

        // Generate 3 distractors from distractor pool or other words
        val distractors = mutableListOf<String>()
        val poolCandidates = distractorPool.filter { it != targetSentence && it.length > 10 }.shuffled()
        for (c in poolCandidates) {
            val cWords = c.split(" ").filter { it.length > 2 }
            if (cWords.size >= 2) {
                val phrase = cWords.takeLast(keyPhrase.split(" ").size.coerceAtLeast(2)).joinToString(" ")
                if (phrase != keyPhrase && !distractors.contains(phrase)) {
                    distractors.add(phrase)
                }
            }
            if (distractors.size >= 3) break
        }

        var fillerIdx = 1
        while (distractors.size < 3) {
            distractors.add("عبارة غير واردة بالنص $fillerIdx")
            fillerIdx++
        }

        val optionsList = (listOf(keyPhrase) + distractors.take(3)).shuffled()

        return QuizQuestionEntity(
            quizId = 0,
            documentId = page.documentId,
            sourceDocTitle = docTitle,
            pageNumber = page.pageNumber,
            lessonOrUnit = unitName,
            topic = lessonName,
            difficulty = difficulty,
            questionType = "MCQ",
            questionText = qText,
            optionsJson = JSONArray(optionsList).toString(),
            correctAnswer = keyPhrase,
            explanation = "مذكورة نصاً في كتاب المنهج ($docTitle) ص ${page.pageNumber}.",
            sourcePassage = passage
        )
    }

    private fun calculateDifficultyForIndex(overall: String, index: Int, total: Int): String {
        return when (overall) {
            "EASY" -> "EASY"
            "HARD" -> "HARD"
            "MEDIUM" -> "MEDIUM"
            else -> { // PROGRESSIVE
                val ratio = index.toFloat() / total.toFloat()
                when {
                    ratio < 0.35f -> "EASY"
                    ratio < 0.75f -> "MEDIUM"
                    else -> "HARD"
                }
            }
        }
    }

    private fun buildExamTitle(config: QuizConfig): String {
        return when (config.scopeType) {
            "LESSON" -> "📝 اختبار: ${config.lessonName} (${config.subject})"
            "UNIT" -> "📑 اختبار: ${config.unitName} (${config.subject})"
            "MISTAKES_RETEST" -> "🎯 اختبار معالجة نقاط الضعف (${config.subject})"
            else -> "🏆 اختبار شامل: منهج ${config.subject}"
        }
    }

    private fun emptySummary(quizId: Long): ExamResultSummary {
        return ExamResultSummary(
            quizId = quizId,
            score = 0,
            totalQuestions = 0,
            percentage = 0,
            timeSpentSeconds = 0,
            correctCount = 0,
            wrongCount = 0,
            unansweredCount = 0,
            subjectBreakdown = emptyMap(),
            strengths = emptyList(),
            weaknesses = emptyList(),
            recommendedPages = emptyList()
        )
    }
}
