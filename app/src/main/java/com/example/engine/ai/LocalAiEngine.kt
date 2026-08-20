package com.example.engine.ai

import com.example.engine.arabic.ArabicTextProcessor
import com.example.engine.search.SearchResultPassage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import android.content.Context
import java.io.File

data class AiResponse(
    val answer: String,
    val isRefusal: Boolean,
    val sourcePassage: SearchResultPassage?,
    val modelUsed: String = "استخراج وتركيب من المنهج (Offline RAG)",
    val inferenceTimeMs: Long = 0L
)

class LocalAiEngine(context: Context) {

    private var activeModelFile: File? = null
    private var isModelLoaded: Boolean = false
    private var modelMetadata: GgufMetadata? = null
    private val nativeRuntime = NativeLlamaEngine.get(context)

    val isLoaded: Boolean
        get() = isModelLoaded

    val currentMetadata: GgufMetadata?
        get() = modelMetadata

    /**
     * Validates a GGUF file. Inference is deliberately not reported as
     * available until a native runtime is present; the answer path below is
     * a grounded extractive RAG engine and never pretends to run GGUF.
     */
    suspend fun loadModel(modelFile: File): Result<GgufMetadata> = withContext(Dispatchers.IO) {
        val inspection = GgufParser.inspectGgufFile(modelFile)
        if (!inspection.isValidGguf) {
            return@withContext Result.failure(Exception(inspection.errorMessage ?: "ملف GGUF غير صالح"))
        }

        nativeRuntime.loadModel(modelFile).map {
            nativeRuntime.setSystemPrompt(
                "أنت مدرس مصري للمرحلة الثانوية. أجب بالعربية بوضوح، ولا تخترع معلومات خارج سياق المنهج."
            ).getOrThrow()
            activeModelFile = modelFile
            modelMetadata = inspection
            isModelLoaded = true
            inspection
        }
    }

    /**
     * Unloads model to free memory (RAM optimization)
     */
    fun unloadModel() {
        activeModelFile = null
        isModelLoaded = false
        modelMetadata = null
        System.gc()
    }

    /**
     * Generates a strict PDF-only answer based on question and retrieved passages
     */
    suspend fun generateAnswer(
        question: String,
        passages: List<SearchResultPassage>
    ): AiResponse = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        if (passages.isEmpty() || passages.first().confidenceScore < 0.20f) {
            return@withContext AiResponse(
                answer = buildRefusalAnswer(question),
                isRefusal = true,
                sourcePassage = null,
                modelUsed = "استخراج وتركيب من المنهج (Offline RAG)",
                inferenceTimeMs = System.currentTimeMillis() - startTime
            )
        }

        if (!isModelLoaded) {
            return@withContext AiResponse(
                answer = "ملف النموذج موجود، لكن runtime المحلي لم يكتمل تحميله. افتح الإعدادات وأعد تحميل نموذج GGUF صالح.",
                isRefusal = true,
                sourcePassage = passages.first(),
                modelUsed = "llama.cpp (غير جاهز)",
                inferenceTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val bestPassage = passages.first()
        val context = passages.joinToString("\n\n") {
            "[${it.documentTitle}، صفحة ${it.pageNumber}]\n${it.matchedPassage}"
        }
        val prompt = """
            أنت مدرس مصري للمرحلة الثانوية. أجب بالعربية باستخدام سياق المنهج المرفق فقط.
            لا تخترع معلومات غير موجودة في السياق. إذا لم توجد الإجابة، قل بوضوح إنها غير موجودة في المنهج المتاح.

            سياق المنهج:
            $context

            سؤال الطالب:
            $question

            الإجابة:
        """.trimIndent()

        val synthesizedAnswer = try {
            nativeRuntime.stream(prompt).toList().joinToString("").trim()
        } catch (e: Throwable) {
            return@withContext AiResponse(
                answer = "فشل الاستدلال المحلي: ${e.message ?: "خطأ غير معروف"}",
                isRefusal = true,
                sourcePassage = bestPassage,
                modelUsed = "llama.cpp (خطأ أثناء الاستدلال)",
                inferenceTimeMs = System.currentTimeMillis() - startTime
            )
        }
        if (synthesizedAnswer.isBlank()) {
            return@withContext AiResponse(
                answer = "لم يُرجع النموذج إجابة. تحقق من توافق ملف GGUF مع llama.cpp.",
                isRefusal = true,
                sourcePassage = bestPassage,
                modelUsed = "llama.cpp",
                inferenceTimeMs = System.currentTimeMillis() - startTime
            )
        }

        AiResponse(
            answer = synthesizedAnswer,
            isRefusal = false,
            sourcePassage = bestPassage,
            modelUsed = "llama.cpp + GGUF (Offline RAG)",
            inferenceTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Streams synthesized answer token by token for smooth UI animation
     */
    fun streamAnswer(
        question: String,
        passages: List<SearchResultPassage>
    ): Flow<String> = flow {
        if (passages.isEmpty() || passages.first().confidenceScore < 0.10f) {
            val refusal = buildRefusalAnswer(question)
            for (word in refusal.split(" ")) {
                emit(word + " ")
                delay(20)
            }
            return@flow
        }

        if (!isModelLoaded) {
            emit("محرك llama.cpp غير جاهز. حمّل نموذج GGUF صالحاً من الإعدادات.")
            return@flow
        }
        val context = passages.joinToString("\n\n") {
            "[${it.documentTitle}، صفحة ${it.pageNumber}]\n${it.matchedPassage}"
        }
        val prompt = "سياق المنهج:\n$context\n\nسؤال الطالب:\n$question\n\nالإجابة بالعربية:"
        val sb = StringBuilder()
        nativeRuntime.stream(prompt).collect { token ->
            sb.append(token)
            emit(sb.toString())
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Builds structured, helpful refusal adhering strictly to curriculum boundary
     */
    private fun buildRefusalAnswer(question: String): String {
        return buildString {
            append("⚠️ لم يرد هذا السؤال في المنهج المقرّر (الملفات المرفوعة).\n\n")
            append("وفقاً لضوابط التطبيق الصارمة، لا يقدّم المساعد أي إجابات من خارج مقرّرك الدراسي لضمان عدم تشتيتك أو تزويدك بمعلومات غير مقررة.\n\n")
            append("💡 نصائح:\n")
            append("• تأكد من رفع كتاب الشرح المناسب للمادة من خانة المناهج.\n")
            append("• تأكد من كتابة مصطلحات السؤال بدقة كما وردت في كتاب الوزارة.")
        }
    }

    /**
     * Strict PDF Answer Synthesizer adhering strictly to source context
     */
    private fun synthesizeStrictAnswer(question: String, passage: SearchResultPassage): String {
        val rawPassage = passage.matchedPassage.trim()
        val normQuestion = ArabicTextProcessor.normalize(question)

        // Analyze question type
        val isCauseQuestion = normQuestion.contains("اسباب") || normQuestion.contains("بم تفسر") || normQuestion.contains("علل") || normQuestion.contains("لماذا") || normQuestion.contains("سبب")
        val isResultQuestion = normQuestion.contains("نتائج") || normQuestion.contains("ما النتائج") || normQuestion.contains("اثر") || normQuestion.contains("ترتب على")
        val isDefineQuestion = normQuestion.contains("ما المقصود") || normQuestion.contains("عرف") || normQuestion.contains("ما هو") || normQuestion.contains("ما هي") || normQuestion.contains("مفهوم")
        val isCompareQuestion = normQuestion.contains("قارن") || normQuestion.contains("الفرق بين") || normQuestion.contains("مقارنه")

        val sb = StringBuilder()

        // Extract structured sentences or numbered points from the passage
        val sentences = rawPassage.split(Regex("[.،؟!\n]+"))
            .map { it.trim() }
            .filter { it.length > 5 }

        val contentList = if (sentences.isNotEmpty()) sentences else listOf(rawPassage)

        when {
            isCauseQuestion -> {
                sb.append("📋 بناءً على المنهج المقرر في (").append(passage.documentTitle).append("):\n\n")
                contentList.forEachIndexed { i, s ->
                    sb.append("${i + 1}. ").append(s).append("\n")
                }
            }
            isResultQuestion -> {
                sb.append("📌 النتائج والآثار الواردة في المنهج (").append(passage.documentTitle).append("):\n\n")
                contentList.forEachIndexed { i, s ->
                    sb.append("• ").append(s).append("\n")
                }
            }
            isDefineQuestion -> {
                sb.append("📖 المفهوم والتعريف وفقاً لكتاب المنهج (ص ").append(passage.pageNumber).append("):\n\n")
                sb.append(contentList.joinToString("\n"))
            }
            isCompareQuestion -> {
                sb.append("⚖️ أوجه المقارنة وفقاً للمنهج المقرّر (").append(passage.documentTitle).append("):\n\n")
                sb.append(contentList.joinToString("\n• ", prefix = "• "))
            }
            else -> {
                sb.append("📚 الإجابة المباشرة من المنهج المقرر (ص ").append(passage.pageNumber).append("):\n\n")
                sb.append(contentList.joinToString("\n\n"))
            }
        }

        return sb.toString().trim()
    }
}
