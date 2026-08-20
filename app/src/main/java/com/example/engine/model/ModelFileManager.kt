package com.example.engine.model

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.ModelFileEntity
import com.example.engine.ai.GgufParser
import com.example.engine.ai.LocalAiEngine
import com.example.engine.ocr.LocalOcrEngine
import com.example.engine.stt.LocalSttEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ModelFileManager(
    private val context: Context,
    private val db: AppDatabase,
    private val aiEngine: LocalAiEngine,
    private val sttEngine: LocalSttEngine,
    private val ocrEngine: LocalOcrEngine
) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    /**
     * Imports a model file (GGUF / Whisper / Tesseract) from SAF Uri
     */
    suspend fun importModelFromUri(
        uri: Uri,
        fileName: String,
        modelType: String // "AI_GGUF", "WHISPER_BIN", "TESSERACT_DATA"
    ): Result<ModelFileEntity> = withContext(Dispatchers.IO) {
        try {
            val destFile = File(modelsDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("تعذر نسخ الملف من الذاكرة"))

            // Validate based on type
            var statusDesc = "تم الاستيراد بنجاح"
            var isValid = true

            when (modelType) {
                "AI_GGUF" -> {
                    val metadata = GgufParser.inspectGgufFile(destFile)
                    if (metadata.isValidGguf) {
                         statusDesc = "تم التحقق من ${metadata.modelName} (${metadata.quantizationType})؛ يلزم محرك GGUF أصلي للاستدلال"
                    } else {
                        statusDesc = metadata.errorMessage ?: "تنسيق GGUF غير صالح"
                        isValid = false
                    }
                }
                "WHISPER_BIN" -> {
                    val info = sttEngine.inspectWhisperModel(destFile)
                    if (info.isValid) {
                         statusDesc = "تم التحقق من ملف Whisper؛ يلزم محرك whisper.cpp أصلي للاستدلال"
                    } else {
                        statusDesc = info.errorMessage ?: "تنسيق Whisper غير صالح"
                        isValid = false
                    }
                }
                "TESSERACT_DATA" -> {
                    val info = ocrEngine.inspectTrainedData(destFile)
                    if (info.isValid) {
                        statusDesc = "بيانات التعرف الضوئي ara.traineddata جاهزة"
                    } else {
                        statusDesc = info.errorMessage ?: "بيانات OCR غير صالحة"
                        isValid = false
                    }
                }
            }

            val modelDao = db.modelFileDao()
            // Deactivate existing of same type if valid
            if (isValid) {
                modelDao.deactivateAllModelsOfType(modelType)
            }

            val entity = ModelFileEntity(
                modelType = modelType,
                name = when (modelType) {
                    "AI_GGUF" -> "نموذج الذكاء الاصطناعي (Qwen 2.5 GGUF)"
                    "WHISPER_BIN" -> "نموذج تحويل الصوت (Whisper ggml-tiny)"
                    "TESSERACT_DATA" -> "بيانات القراءة الضوئية (ara.traineddata)"
                    else -> fileName
                },
                fileName = destFile.name,
                localPath = destFile.absolutePath,
                fileSize = destFile.length(),
                isActive = isValid,
                isLoaded = false,
                statusDescription = statusDesc
            )

            val id = modelDao.insertModel(entity)
            var saved = entity.copy(id = id)
            if (modelType == "AI_GGUF" && isValid) {
                val runtimeResult = aiEngine.loadModel(destFile)
                saved = if (runtimeResult.isSuccess) {
                    saved.copy(
                        isLoaded = true,
                        statusDescription = "AI Ready: llama.cpp native runtime + inference context جاهزان"
                    )
                } else {
                    saved.copy(
                        isLoaded = false,
                        statusDescription = "تم حفظ الملف، لكن فشل تشغيل llama.cpp: ${runtimeResult.exceptionOrNull()?.message}"
                    )
                }
                modelDao.updateModel(saved)
            }
            if (modelType == "TESSERACT_DATA" && isValid) {
                val result = ocrEngine.loadLanguageData(destFile)
                saved = saved.copy(
                    isLoaded = result.isSuccess,
                    statusDescription = if (result.isSuccess) {
                        "OCR Ready: Tesseract + ara.traineddata جاهزان"
                    } else {
                        "تم حفظ الملف، لكن فشل تشغيل Tesseract: ${result.exceptionOrNull()?.message}"
                    }
                )
                modelDao.updateModel(saved)
            }
            if (modelType == "WHISPER_BIN" && isValid) {
                // The native Whisper runtime is initialized on first
                // transcription; keep the file persistent and mark only
                // validated storage here.
                saved = saved.copy(
                    isLoaded = false,
                    statusDescription = "تم حفظ نموذج Whisper المحلي؛ سيُستخدم عبر whisper.cpp عند التسجيل"
                )
                modelDao.updateModel(saved)
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Diagnostic Test for an imported model
     */
    suspend fun testModel(modelEntity: ModelFileEntity): String = withContext(Dispatchers.IO) {
        val file = File(modelEntity.localPath)
        if (!file.exists()) {
            return@withContext "الملف غير موجود على مسار التخزين الداخلي"
        }

        val resultStr = when (modelEntity.modelType) {
            "AI_GGUF" -> {
                val metadata = GgufParser.inspectGgufFile(file)
                if (metadata.isValidGguf) {
                    "✅ فحص ناجح: معمارية ${metadata.architecture}، تكميم ${metadata.quantizationType}، استهلاك الذاكرة التقريبي: ${metadata.estimatedRamMb} ميجابايت."
                } else {
                    "❌ فشل الفحص: ${metadata.errorMessage}"
                }
            }
            "WHISPER_BIN" -> {
                val info = sttEngine.inspectWhisperModel(file)
                if (info.isValid) {
                    "✅ فحص ناجح: نموذج Whisper بحجم ${String.format("%.2f", info.fileSizeMb)} ميجابايت جاهز لتحويل الصوت إلى نص."
                } else {
                    "❌ فشل الفحص: ${info.errorMessage}"
                }
            }
            "TESSERACT_DATA" -> {
                val info = ocrEngine.inspectTrainedData(file)
                if (info.isValid) {
                    "✅ فحص ناجح: حزمة لغة ${info.language} جاهزة للاستخراج الضوئي للنصوص العربية."
                } else {
                    "❌ فشل الفحص: ${info.errorMessage}"
                }
            }
            else -> "نوع النموذج غير مدعوم"
        }

        db.modelFileDao().updateModel(
            modelEntity.copy(
                lastTestedAt = System.currentTimeMillis(),
                testResult = resultStr
            )
        )
        resultStr
    }

    /**
     * Deletes a model file
     */
    suspend fun deleteModel(modelEntity: ModelFileEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(modelEntity.localPath)
            if (file.exists()) file.delete()
            db.modelFileDao().deleteModel(modelEntity.id)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Do not seed model placeholders. A file that only has a GGUF/Whisper
     * extension is not a model and must never be presented as ready.
     */
    suspend fun seedInitialModelPlaceholdersIfEmpty() = withContext(Dispatchers.IO) {
        // Restore a real previously imported model on the next app launch.
        val active = db.modelFileDao().getActiveModel("AI_GGUF")
        if (active != null) {
            val file = File(active.localPath)
            val result = aiEngine.loadModel(file)
            db.modelFileDao().updateModel(
                active.copy(
                    isLoaded = result.isSuccess,
                    statusDescription = if (result.isSuccess) {
                        "AI Ready: llama.cpp native runtime + inference context جاهزان"
                    } else {
                        "ملف النموذج موجود لكن runtime غير جاهز: ${result.exceptionOrNull()?.message}"
                    }
                )
            )
        }
        val activeOcr = db.modelFileDao().getActiveModel("TESSERACT_DATA")
        if (activeOcr != null) {
            val result = ocrEngine.loadLanguageData(File(activeOcr.localPath))
            db.modelFileDao().updateModel(
                activeOcr.copy(
                    isLoaded = result.isSuccess,
                    statusDescription = if (result.isSuccess) "OCR Ready: Tesseract + ara.traineddata جاهزان"
                    else "ملف OCR موجود لكن تعذر تشغيل Tesseract المحلي"
                )
            )
        }
    }
}
