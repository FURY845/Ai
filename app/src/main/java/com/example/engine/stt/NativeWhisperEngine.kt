package com.example.engine.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class NativeWhisperEngine private constructor() {
    companion object {
        @Volatile private var instance: NativeWhisperEngine? = null
        fun get(context: Context): NativeWhisperEngine =
            instance ?: synchronized(this) {
                instance ?: NativeWhisperEngine().also {
                    System.loadLibrary("thanaweya_whisper")
                    instance = it
                }
            }
    }

    private external fun transcribeNative(modelPath: String, wavPath: String): String?

    suspend fun transcribe(model: File, wav: File): Result<String> =
        withContext(Dispatchers.Default) {
            try {
                require(model.exists()) { "الملف المحلي لنموذج Whisper غير موجود" }
                require(wav.exists() && wav.length() > 44) { "ملف التسجيل الصوتي غير موجود أو فارغ" }
                Result.success(transcribeNative(model.absolutePath, wav.absolutePath).orEmpty())
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }
}