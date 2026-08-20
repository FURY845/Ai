package com.example.engine.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * JNI bridge to llama.cpp. Unlike GGUF inspection, load() allocates a real
 * llama_model and prepare() creates a llama_context and sampler.
 */
class NativeLlamaEngine private constructor(
    private val nativeLibraryDir: String
) {
    companion object {
        @Volatile private var instance: NativeLlamaEngine? = null

        fun get(context: Context): NativeLlamaEngine =
            instance ?: synchronized(this) {
                instance ?: NativeLlamaEngine(context.applicationInfo.nativeLibraryDir)
                    .also { instance = it }
            }
    }

    enum class State { UNINITIALIZED, INITIALIZED, MODEL_READY, GENERATING, ERROR }

    @Volatile var state: State = State.UNINITIALIZED
        private set

    private var initialized = false

    private external fun init(nativeLibDir: String)
    private external fun load(modelPath: String): Int
    private external fun prepare(): Int
    private external fun processSystemPrompt(prompt: String): Int
    private external fun processUserPrompt(prompt: String, predictLength: Int): Int
    private external fun generateNextToken(): String?
    private external fun unload()
    private external fun shutdown()

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!initialized) {
                System.loadLibrary("thanaweya_llama")
                init(nativeLibraryDir)
                initialized = true
                state = State.INITIALIZED
            }
            Result.success(Unit)
        } catch (e: Throwable) {
            state = State.ERROR
            Result.failure(e)
        }
    }

    suspend fun loadModel(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            initialize().getOrThrow()
            require(file.exists() && file.isFile && file.canRead()) { "ملف النموذج غير قابل للقراءة" }
            check(load(file.absolutePath) == 0) { "فشل تحميل GGUF داخل llama.cpp" }
            check(prepare() == 0) { "فشل إنشاء سياق الاستدلال داخل llama.cpp" }
            state = State.MODEL_READY
            Result.success(Unit)
        } catch (e: Throwable) {
            state = State.ERROR
            Result.failure(e)
        }
    }

    fun stream(prompt: String, maxTokens: Int = 384): Flow<String> = flow {
        check(state == State.MODEL_READY) { "محرك llama.cpp غير جاهز" }
        state = State.GENERATING
        try {
            check(processUserPrompt(prompt, maxTokens) == 0) { "فشل ترميز prompt داخل llama.cpp" }
            while (true) {
                val token = generateNextToken() ?: break
                if (token.isNotEmpty()) emit(token)
            }
        } finally {
            state = State.MODEL_READY
        }
    }.flowOn(Dispatchers.IO)

    suspend fun setSystemPrompt(prompt: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            check(state == State.MODEL_READY)
            check(processSystemPrompt(prompt) == 0) { "فشل تجهيز system prompt" }
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun close() {
        if (initialized) {
            runCatching { unload() }
            runCatching { shutdown() }
            initialized = false
            state = State.UNINITIALIZED
        }
    }
}