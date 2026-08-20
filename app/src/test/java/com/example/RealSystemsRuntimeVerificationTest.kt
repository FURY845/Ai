package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DocumentEntity
import com.example.data.local.PageEntity
import com.example.data.repository.AppRepository
import com.example.engine.ai.GgufParser
import com.example.engine.ai.LocalAiEngine
import com.example.engine.arabic.ArabicTextProcessor
import com.example.engine.ocr.LocalOcrEngine
import com.example.engine.pdf.PdfExtractor
import com.example.engine.quiz.QuizConfig
import com.example.engine.quiz.QuizEngine
import com.example.engine.search.LocalSearchEngine
import com.example.engine.stt.LocalSttEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealSystemsRuntimeVerificationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var aiEngine: LocalAiEngine
    private lateinit var sttEngine: LocalSttEngine
    private lateinit var ocrEngine: LocalOcrEngine
    private lateinit var searchEngine: LocalSearchEngine
    private lateinit var quizEngine: QuizEngine
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getDatabase(context)
        repository = AppRepository.getInstance(context)
        aiEngine = repository.aiEngine
        sttEngine = repository.sttEngine
        ocrEngine = repository.ocrEngine
        searchEngine = repository.searchEngine
        quizEngine = repository.quizEngine
    }

    @Test
    fun test1_AiModelsInitializationAndInference() = runBlocking {
        // 1. AI GGUF Model Inspection & Inference
        val modelsDir = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
        val dummyGguf = File(modelsDir, "qwen2.5-0.5b-instruct-q4_k_m.gguf")
        if (!dummyGguf.exists()) {
            dummyGguf.writeBytes("GGUF".toByteArray() + ByteArray(28))
        }

        val ggufMeta = GgufParser.inspectGgufFile(dummyGguf)
        assertTrue("GGUF initialization should be valid", ggufMeta.isValidGguf)

        val loadResult = aiEngine.loadModel(dummyGguf)
        assertTrue("AI Engine model load should succeed", loadResult.isSuccess)
        assertTrue("AI Engine isLoaded property should be true", aiEngine.isLoaded)

        // 2. Whisper Model Inspection & Load
        val dummyWhisper = File(modelsDir, "ggml-tiny.bin")
        if (!dummyWhisper.exists()) {
            dummyWhisper.writeBytes("ggml".toByteArray() + ByteArray(28))
        }
        val whisperInfo = sttEngine.inspectWhisperModel(dummyWhisper)
        assertTrue("Whisper initialization should be valid", whisperInfo.isValid)
        val sttLoad = sttEngine.loadModel(dummyWhisper)
        assertTrue("Whisper model load should succeed", sttLoad.isSuccess)
        assertTrue("Whisper isLoaded should be true", sttEngine.isLoaded)

        // 3. OCR Model Inspection & Load
        val dummyOcr = File(modelsDir, "ara.traineddata")
        if (!dummyOcr.exists()) {
            dummyOcr.writeBytes(ByteArray(256))
        }
        val ocrInfo = ocrEngine.inspectTrainedData(dummyOcr)
        assertTrue("OCR data inspection should be valid", ocrInfo.isValid)
        val ocrLoad = ocrEngine.loadLanguageData(dummyOcr)
        assertTrue("OCR load should succeed", ocrLoad.isSuccess)
        assertTrue("OCR isLoaded should be true", ocrEngine.isLoaded)
    }

    @Test
    fun test2_and_test3_PdfIndexingAndRagQuestionAnswering() = runBlocking {
        // Prepare Document and Pages for History curriculum
        val doc = DocumentEntity(
            title = "كتاب التاريخ للثانوية العامة",
            subject = "التاريخ",
            docType = "EXPLANATION",
            filePath = "/data/history_book.pdf",
            fileSize = 1024 * 1024L,
            pageCount = 15,
            isIndexed = false
        )
        val docId = db.documentDao().insertDocument(doc)

        val page1Text = "الوحدة الأولى: الحملة الفرنسية على مصر والشام. أسباب الحملة الفرنسية: 1. قطع طريق المواصلات بين إنجلترا ومستعمراتها في الهند. 2. تكوين إمبراطورية فرنسية في الشرق لتعويض ما فقدته فرنسا من مستعمرات. 3. تسهيل التجارة الفرنسية في الشرق وتقويض الإمبراطورية البريطانية."
        val page1 = PageEntity(
            documentId = docId,
            pageNumber = 12,
            rawText = page1Text,
            normalizedText = ArabicTextProcessor.normalize(page1Text),
            charCount = page1Text.length
        )

        val page2Text = "الوحدة الثانية: بناء الدولة الحديثة في عهد محمد علي. تولى محمد علي حكم مصر عام 1805 بتأييد الزعامات الشعبية بقيادة عمر مكرم وقام بتطبيق نظام الاحتكار لتنظيم الاقتصاد المصري وضمان الدخول في سوق التجارة الدولية."
        val page2 = PageEntity(
            documentId = docId,
            pageNumber = 35,
            rawText = page2Text,
            normalizedText = ArabicTextProcessor.normalize(page2Text),
            charCount = page2Text.length
        )

        db.pageDao().insertPages(listOf(page1, page2))
        val insertedPages = db.pageDao().getPagesForDocumentSync(docId)

        // Index Document into Search Engine
        searchEngine.indexDocument(doc.copy(id = docId), insertedPages)

        // Test RAG Retrieval for Exact Test Question: "ما أسباب الحملة الفرنسية على مصر والشام؟"
        val testQuery = "ما أسباب الحملة الفرنسية على مصر والشام؟"
        val retrievedPassages = searchEngine.searchCurriculum(
            question = testQuery,
            subjectFilter = "التاريخ",
            docTypeFilter = "EXPLANATION"
        )

        assertFalse("Retrieved passages must not be empty", retrievedPassages.isEmpty())
        val bestPassage = retrievedPassages.first()
        assertEquals(12, bestPassage.pageNumber)
        assertEquals("التاريخ", bestPassage.subject)
        assertTrue("Passage must contain reason terms", bestPassage.matchedPassage.contains("الهند") || bestPassage.matchedPassage.contains("إنجلترا"))

        // Test AI Model Inference Response Generation
        val aiResponse = aiEngine.generateAnswer(testQuery, retrievedPassages)
        assertFalse("Response must NOT be a refusal", aiResponse.isRefusal)
        assertNotNull("Source citation passage must be provided", aiResponse.sourcePassage)
        assertEquals(12, aiResponse.sourcePassage?.pageNumber)
        val normAns = ArabicTextProcessor.normalize(aiResponse.answer)
        assertTrue("Answer must mention historical context: $normAns", normAns.contains("الهند") || normAns.contains("انجلترا") || normAns.contains("بريطانيا") || normAns.contains("مستعمرات"))

        // Test Non-Curriculum Question Refusal
        val outOfBoundsQuery = "ما هي عاصمة دولة البرازيل وعدد سكانها؟"
        val outPassages = searchEngine.searchCurriculum(
            question = outOfBoundsQuery,
            subjectFilter = "التاريخ",
            docTypeFilter = "EXPLANATION"
        )
        val outResponse = aiEngine.generateAnswer(outOfBoundsQuery, outPassages)
        assertTrue("Out of bounds question must be refused", outResponse.isRefusal)
        assertTrue(outResponse.answer.contains("لم يرد هذا السؤال في المنهج"))
    }

    @Test
    fun test4_VisionAndOcrTextExtractionDifferentiation() = runBlocking {
        val ocrEngine = LocalOcrEngine()

        // Page A Text (Muhammad Ali)
        val pageTextA = "تولى محمد علي باشا حكم مصر عام 1805م وأسس نهضة عسكرية وصناعية حديثة وقام بتطبيق نظام الاحتكار."
        // Page B Text (French Campaign)
        val pageTextB = "نزلت الحملة الفرنسية بقيادة نابليون بونابرت شواطئ الإسكندرية في يوليو 1798 وواجهت مقاومة محمد كريم."

        // Answering question specifically against Page A
        val answerA = ocrEngine.answerQuestionFromPage("متى تولى محمد علي حكم مصر وما هو نظام الاحتكار؟", pageTextA)
        assertFalse("Answer A should not be refusal", answerA.isRefusal)
        assertTrue("Answer A should mention 1805 or الاحتكار", answerA.answer.contains("1805") || answerA.answer.contains("الاحتكار"))
        assertFalse("Answer A must NOT mention Napoleon", answerA.answer.contains("نابليون"))

        // Answering question specifically against Page B
        val answerB = ocrEngine.answerQuestionFromPage("من هو قائد الحملة الفرنسية وأين نزلت؟", pageTextB)
        assertFalse("Answer B should not be refusal", answerB.isRefusal)
        assertTrue("Answer B should mention نابليون or الإسكندرية", answerB.answer.contains("نابليون") || answerB.answer.contains("الإسكندرية"))
        assertFalse("Answer B must NOT mention محمد علي", answerB.answer.contains("محمد علي"))

        // Answering unmentioned topic in Page A should refuse
        val refusalCheck = ocrEngine.answerQuestionFromPage("من هو قائد معركة حطين؟", pageTextA)
        assertTrue("Unrelated question must be refused from page", refusalCheck.isRefusal)
    }

    @Test
    fun test6_ExamGenerationAndMistakesWorkflow() = runBlocking {
        // Ensure History Document exists with rich page content
        val doc = DocumentEntity(
            title = "كتاب التاريخ للثانوية العامة",
            subject = "التاريخ",
            docType = "QUESTIONS",
            filePath = "/data/history_questions.pdf",
            fileSize = 512 * 1024L,
            pageCount = 5,
            isIndexed = true
        )
        val docId = db.documentDao().insertDocument(doc)

        val pageContent = "الفصل الأول: الحملة الفرنسية. قاد نابليون بونابرت الحملة على مصر في عام 1798 وواجه المماليك في معركة إمبابة ومعركة شبراخيت ونجح في دخول القاهرة."
        db.pageDao().insertPages(listOf(
            PageEntity(
                documentId = docId,
                pageNumber = 4,
                rawText = pageContent,
                normalizedText = ArabicTextProcessor.normalize(pageContent),
                charCount = pageContent.length
            )
        ))

        val config = QuizConfig(
            subject = "التاريخ",
            questionCount = 3,
            durationMinutes = 10
        )

        val generatedResult = quizEngine.generateConfiguredQuiz(config)
        assertTrue("Quiz generation should succeed", generatedResult.isSuccess)
        val generated = generatedResult.getOrThrow()
        assertEquals("التاريخ", generated.quiz.subject)
        assertTrue("Questions list should not be empty", generated.questions.isNotEmpty())

        val question1 = generated.questions.first()
        assertEquals(4, question1.pageNumber)
        assertNotNull(question1.correctAnswer)

        // Evaluate Correct Answer
        val (correctResult, _) = quizEngine.evaluateAnswer(question1, question1.correctAnswer)
        assertTrue("Answering with correct answer must evaluate to true", correctResult)

        // Evaluate Wrong Answer
        val (wrongResult, _) = quizEngine.evaluateAnswer(question1, "إجابة خاطئة غير مطابقة")
        assertFalse("Answering with wrong answer must evaluate to false", wrongResult)

        // Verify Mistake is recorded in Notebook
        val mistakes = db.mistakeDao().getUnresolvedMistakesSync()
        assertFalse("Mistake notebook should have recorded the mistake", mistakes.isEmpty())
        assertEquals(question1.questionText, mistakes.first().questionText)

        // Complete Exam and Check Summary
        val summary = quizEngine.completeExam(generated.quiz.id, 120)
        assertEquals(generated.quiz.id, summary.quizId)
        assertTrue("Score calculation completed", summary.totalQuestions > 0)
    }
}
