package com.example.engine.ai

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufMetadata(
    val isValidGguf: Boolean,
    val version: Int = 0,
    val tensorCount: Long = 0,
    val metadataKvCount: Long = 0,
    val architecture: String = "qwen2",
    val contextLength: Int = 4096,
    val quantizationType: String = "Q4_K_M",
    val modelName: String = "Qwen 2.5 0.5B Instruct",
    val estimatedRamMb: Int = 450,
    val errorMessage: String? = null
)

object GgufParser {
    // Magic: 'G' 'G' 'U' 'F' -> 0x46554747
    private const val GGUF_MAGIC = 0x46554747

    fun inspectGgufFile(file: File): GgufMetadata {
        if (!file.exists() || file.length() < 32) {
            return GgufMetadata(isValidGguf = false, errorMessage = "الملف غير موجود أو فارغ")
        }

        try {
            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(32)
                raf.readFully(buffer)
                val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)

                val magic = byteBuffer.int
                if (magic != GGUF_MAGIC) {
                    return GgufMetadata(isValidGguf = false, errorMessage = "تنسيق الملف ليس GGUF صحيح")
                }

                val version = byteBuffer.int
                val tensorCount = byteBuffer.long
                val metadataKvCount = byteBuffer.long

                val fileSizeMb = (file.length() / (1024 * 1024)).toInt()
                val estimatedRam = (fileSizeMb * 1.25).toInt().coerceAtLeast(350)

                return GgufMetadata(
                    isValidGguf = true,
                    version = version,
                    tensorCount = tensorCount,
                    metadataKvCount = metadataKvCount,
                    architecture = if (file.name.contains("qwen", ignoreCase = true)) "Qwen 2.5" else "Llama",
                    quantizationType = if (file.name.contains("q4_k_m", ignoreCase = true)) "Q4_K_M" else "GGUF Quantized",
                    modelName = file.name,
                    estimatedRamMb = estimatedRam
                )
            }
        } catch (e: Exception) {
            return GgufMetadata(isValidGguf = false, errorMessage = e.message ?: "خطأ أثناء فحص ملف GGUF")
        }
    }
}
