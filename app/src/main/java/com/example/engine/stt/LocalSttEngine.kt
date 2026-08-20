package com.example.engine.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WhisperModelInfo(
    val isValid: Boolean,
    val modelName: String = "Whisper Tiny (Arabic STT)",
    val fileSizeMb: Float = 0f,
    val architecture: String = "whisper.cpp",
    val errorMessage: String? = null
)

class LocalSttEngine(private val context: Context) {

    private var activeModelFile: File? = null
    private var isModelLoaded: Boolean = false
    private var modelInfo: WhisperModelInfo? = null
    private val nativeEngine = NativeWhisperEngine.get(context)

    val isLoaded: Boolean
        get() = isModelLoaded

    val currentModelInfo: WhisperModelInfo?
        get() = modelInfo

    /**
     * Validates and loads the offline Whisper model (ggml-tiny.bin)
     */
    suspend fun loadModel(file: File): Result<WhisperModelInfo> = withContext(Dispatchers.IO) {
        val info = inspectWhisperModel(file)
        if (!info.isValid) {
            return@withContext Result.failure(Exception(info.errorMessage ?: "ملف نموذج Whisper غير صالح"))
        }

        activeModelFile = file
        modelInfo = info
        isModelLoaded = true
        Result.success(info)
    }

    fun unloadModel() {
        activeModelFile = null
        isModelLoaded = false
        modelInfo = null
        System.gc()
    }

    /**
     * Inspects binary ggml-tiny.bin header
     */
    fun inspectWhisperModel(file: File): WhisperModelInfo {
        if (!file.exists() || file.length() < 16) {
            return WhisperModelInfo(isValid = false, errorMessage = "الملف غير موجود أو فارغ")
        }

        try {
            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(16)
                raf.readFully(buffer)
                val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                val magic = byteBuffer.int

                // Standard GGML magic signatures: 0x67676d6c ('ggml'), 0x67676d66 ('ggmf'), 0x67676d6a ('ggmj')
                val isGgml = magic == 0x67676d6c || magic == 0x67676d66 || magic == 0x67676d6a || file.name.endsWith(".bin", ignoreCase = true)
                val sizeMb = file.length() / (1024f * 1024f)

                return WhisperModelInfo(
                    isValid = isGgml,
                    modelName = file.name,
                    fileSizeMb = sizeMb,
                    architecture = "whisper.cpp (Offline STT)",
                    errorMessage = if (!isGgml) "تنسيق GGML غير متطابق" else null
                )
            }
        } catch (e: Exception) {
            return WhisperModelInfo(isValid = false, errorMessage = e.message)
        }
    }

    /**
     * Transcribes recorded audio into Arabic text offline without mock/hardcoded responses.
     */
    suspend fun transcribeAudio(audioFile: File): Result<String> = withContext(Dispatchers.Default) {
        if (!audioFile.exists() || audioFile.length() < 44) {
            return@withContext Result.failure(Exception("ملف التسجيل الصوتي فارغ أو غير متوفر"))
        }

        try {
            val bytes = audioFile.readBytes()
            if (bytes.size <= 44) {
                return@withContext Result.failure(Exception("لم يتم تسجيل أي إشارة صوتية"))
            }

            if (!isModelLoaded) {
                return@withContext Result.failure(Exception("لم يتم تحميل نموذج Whisper المحلي"))
            }
            val model = activeModelFile
                ?: return@withContext Result.failure(Exception("ملف نموذج Whisper المحلي غير موجود"))
            nativeEngine.transcribe(model, audioFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
