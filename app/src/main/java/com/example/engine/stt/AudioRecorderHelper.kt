package com.example.engine.stt

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.sqrt

class AudioRecorderHelper(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var currentAudioFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    companion object {
        const val SAMPLE_RATE = 16000 // 16 kHz required by Whisper
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Result<File> {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return Result.failure(Exception("تعذر تهيئة الميكروفون"))
            }

            val audioDir = File(context.cacheDir, "audio_recordings").apply { if (!exists()) mkdirs() }
            val outputFile = File(audioDir, "recorded_voice_${System.currentTimeMillis()}.wav")
            currentAudioFile = outputFile

            audioRecord?.startRecording()
            _isRecording.value = true

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val tempPcmFile = File(audioDir, "temp_record.pcm")
                val buffer = ShortArray(bufferSize)

                FileOutputStream(tempPcmFile).use { fos ->
                    while (isActive && _isRecording.value) {
                        val readShorts = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readShorts > 0) {
                            // Calculate RMS amplitude for UI waveform
                            var sum = 0.0
                            for (i in 0 until readShorts) {
                                sum += buffer[i] * buffer[i]
                            }
                            val rms = sqrt(sum / readShorts).toFloat()
                            _amplitude.value = (rms / 32767f).coerceIn(0f, 1f)

                            // Write raw PCM bytes
                            val byteBuffer = ByteArray(readShorts * 2)
                            for (i in 0 until readShorts) {
                                val s = buffer[i].toInt()
                                byteBuffer[i * 2] = (s and 0x00FF).toByte()
                                byteBuffer[i * 2 + 1] = ((s shr 8) and 0x00FF).toByte()
                            }
                            fos.write(byteBuffer)
                        }
                    }
                }

                // Convert PCM to proper 16-bit 16kHz WAV
                writeWavHeader(tempPcmFile, outputFile)
                tempPcmFile.delete()
            }

            return Result.success(outputFile)
        } catch (e: Exception) {
            _isRecording.value = false
            return Result.failure(e)
        }
    }

    fun stopRecording(): File? {
        try {
            _isRecording.value = false
            _amplitude.value = 0f
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingJob?.cancel()
            runBlocking(Dispatchers.IO) { recordingJob?.join() }
            return currentAudioFile
        } catch (e: Exception) {
            return null
        }
    }

    private fun writeWavHeader(pcmFile: File, wavFile: File) {
        val pcmSize = pcmFile.length()
        val totalDataLen = pcmSize + 36
        val sampleRate = SAMPLE_RATE.toLong()
        val channels = 1
        val byteRate = (SAMPLE_RATE * channels * 16 / 8).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmSize and 0xff).toByte()
        header[41] = ((pcmSize shr 8) and 0xff).toByte()
        header[42] = ((pcmSize shr 16) and 0xff).toByte()
        header[43] = ((pcmSize shr 24) and 0xff).toByte()

        FileOutputStream(wavFile).use { fos ->
            fos.write(header)
            pcmFile.inputStream().use { fis ->
                fis.copyTo(fos)
            }
        }
    }
}
