#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <fstream>
#include <string>
#include <vector>
#include "whisper.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ThanaweyaWhisper", __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_engine_stt_NativeWhisperEngine_transcribeNative(
        JNIEnv *env, jobject, jstring model_j, jstring wav_j) {
    const char *model_path = env->GetStringUTFChars(model_j, nullptr);
    const char *wav_path = env->GetStringUTFChars(wav_j, nullptr);

    whisper_context *ctx = whisper_init_from_file(model_path);
    if (!ctx) {
        env->ReleaseStringUTFChars(model_j, model_path);
        env->ReleaseStringUTFChars(wav_j, wav_path);
        return nullptr;
    }

    std::ifstream input(wav_path, std::ios::binary);
    if (!input) {
        whisper_free(ctx);
        env->ReleaseStringUTFChars(model_j, model_path);
        env->ReleaseStringUTFChars(wav_j, wav_path);
        return nullptr;
    }
    input.seekg(0, std::ios::end);
    const auto size = input.tellg();
    input.seekg(44, std::ios::beg);
    if (size <= 44) {
        whisper_free(ctx);
        env->ReleaseStringUTFChars(model_j, model_path);
        env->ReleaseStringUTFChars(wav_j, wav_path);
        return nullptr;
    }

    const size_t samples = static_cast<size_t>(size - 44) / 2;
    std::vector<int16_t> pcm(samples);
    input.read(reinterpret_cast<char *>(pcm.data()), samples * sizeof(int16_t));
    std::vector<float> audio(samples);
    for (size_t i = 0; i < samples; ++i) audio[i] = pcm[i] / 32768.0f;

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.single_segment = false;
    params.no_context = true;
    params.language = "ar";
    params.translate = false;
    params.n_threads = 2;

    std::string result;
    if (whisper_full(ctx, params, audio.data(), static_cast<int>(audio.size())) == 0) {
        const int count = whisper_full_n_segments(ctx);
        for (int i = 0; i < count; ++i) result += whisper_full_get_segment_text(ctx, i);
    } else {
        LOGE("whisper_full failed");
    }

    whisper_free(ctx);
    env->ReleaseStringUTFChars(model_j, model_path);
    env->ReleaseStringUTFChars(wav_j, wav_path);
    return env->NewStringUTF(result.c_str());
}