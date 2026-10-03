// Minimal JNI bridge to whisper.cpp for org.sakshi.processing.stt.WhisperNative.
// Never logs transcript text, never touches the network, never writes files.

#include <jni.h>
#include <malloc.h>

#include <atomic>
#include <cstdint>
#include <cstring>
#include <new>
#include <string>
#include <vector>

#include "whisper.h"

namespace {

constexpr int kStatusOk = 0;
constexpr int kStatusFailed = 1;
constexpr int kStatusCancelled = 2;
constexpr int kStatusOutOfMemory = 3;

struct Session {
    whisper_context* context = nullptr;
    std::atomic<bool> cancelled{false};
};

Session* sessionOf(jlong handle) { return reinterpret_cast<Session*>(handle); }

bool abortRequested(void* userData) {
    return static_cast<Session*>(userData)->cancelled.load(std::memory_order_relaxed);
}

std::string stringOf(JNIEnv* env, jstring value) {
    if (value == nullptr) return std::string();
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return std::string();
    std::string copy(chars);
    env->ReleaseStringUTFChars(value, chars);
    return copy;
}

int run(Session* session, std::vector<float>& pcm, const std::string& languageCode, int threads) {
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.translate = false;  // transcribe in the spoken language; never translate
    params.detect_language = false;
    params.language = languageCode.empty() ? "auto" : languageCode.c_str();
    params.no_context = true;
    params.single_segment = false;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.token_timestamps = false;
    params.suppress_nst = true;
    params.abort_callback = abortRequested;
    params.abort_callback_user_data = session;

    const int rc = whisper_full(session->context, params, pcm.data(), static_cast<int>(pcm.size()));
    if (session->cancelled.load(std::memory_order_relaxed)) return kStatusCancelled;
    return rc == 0 ? kStatusOk : kStatusFailed;
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL Java_org_sakshi_processing_stt_WhisperNative_nativeVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF(whisper_version());
}

JNIEXPORT jlong JNICALL Java_org_sakshi_processing_stt_WhisperNative_nativeLoad(JNIEnv* env, jobject, jstring path) {
    const std::string modelPath = stringOf(env, path);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    params.flash_attn = false;
    whisper_context* context = whisper_init_from_file_with_params(modelPath.c_str(), params);
    if (context == nullptr) return 0;
    Session* session = new (std::nothrow) Session();
    if (session == nullptr) {
        whisper_free(context);
        return 0;
    }
    session->context = context;
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL Java_org_sakshi_processing_stt_WhisperNative_nativeFree(JNIEnv*, jobject, jlong handle) {
    Session* session = sessionOf(handle);
    if (session == nullptr) return;
    whisper_free(session->context);
    delete session;
    // Hand the freed model memory back to the system now: allocators otherwise keep it, and "unloaded" would not show.
    mallopt(M_PURGE, 0);
}

JNIEXPORT void JNICALL Java_org_sakshi_processing_stt_WhisperNative_nativeCancel(JNIEnv*, jobject, jlong handle) {
    Session* session = sessionOf(handle);
    if (session != nullptr) session->cancelled.store(true, std::memory_order_relaxed);
}

// language: an ISO 639-1 code, or null for automatic detection. Translation is never requested.
JNIEXPORT jint JNICALL Java_org_sakshi_processing_stt_WhisperNative_nativeTranscribe(
    JNIEnv* env, jobject, jlong handle, jfloatArray samples, jstring language, jint threads) {
    Session* session = sessionOf(handle);
    if (session == nullptr || samples == nullptr) return kStatusFailed;
    session->cancelled.store(false, std::memory_order_relaxed);
    const std::string languageCode = stringOf(env, language);
    try {
        const jsize count = env->GetArrayLength(samples);
        std::vector<float> pcm(static_cast<size_t>(count));
        env->GetFloatArrayRegion(samples, 0, count, pcm.data());
        if (env->ExceptionCheck()) return kStatusFailed;
        return run(session, pcm, languageCode, static_cast<int>(threads));
    } catch (const std::bad_alloc&) {
        return kStatusOutOfMemory;
    } catch (...) {
        return kStatusFailed;
    }
}

JNIEXPORT jint JNICALL Java_org_sakshi_processing_stt_WhisperNative_nativeSegmentCount(JNIEnv*, jobject, jlong handle) {
    return whisper_full_n_segments(sessionOf(handle)->context);
}

// Raw UTF-8 bytes: a segment can end inside a multi-byte character, which NewStringUTF must never be given.
JNIEXPORT jbyteArray JNICALL Java_org_sakshi_processing_stt_WhisperNative_nativeSegmentText(
    JNIEnv* env, jobject, jlong handle, jint index) {
    const char* text = whisper_full_get_segment_text(sessionOf(handle)->context, index);
    const jsize length = text == nullptr ? 0 : static_cast<jsize>(std::strlen(text));
    jbyteArray bytes = env->NewByteArray(length);
    if (bytes != nullptr && length > 0) {
        env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte*>(text));
    }
    return bytes;
}

// Whisper reports segment times in units of 10 ms.
JNIEXPORT jlong JNICALL Java_org_sakshi_processing_stt_WhisperNative_nativeSegmentStartMs(
    JNIEnv*, jobject, jlong handle, jint index) {
    return static_cast<jlong>(whisper_full_get_segment_t0(sessionOf(handle)->context, index)) * 10;
}

JNIEXPORT jlong JNICALL Java_org_sakshi_processing_stt_WhisperNative_nativeSegmentEndMs(
    JNIEnv*, jobject, jlong handle, jint index) {
    return static_cast<jlong>(whisper_full_get_segment_t1(sessionOf(handle)->context, index)) * 10;
}

JNIEXPORT jfloat JNICALL Java_org_sakshi_processing_stt_WhisperNative_nativeSegmentNoSpeechProbability(
    JNIEnv*, jobject, jlong handle, jint index) {
    return whisper_full_get_segment_no_speech_prob(sessionOf(handle)->context, index);
}

// Mean probability of the segment's text tokens (special tokens, whose ids are at or above eot, are skipped).
// Returns -1 when the segment has no text token.
JNIEXPORT jfloat JNICALL Java_org_sakshi_processing_stt_WhisperNative_nativeSegmentMeanTokenProbability(
    JNIEnv*, jobject, jlong handle, jint index) {
    whisper_context* context = sessionOf(handle)->context;
    const whisper_token eot = whisper_token_eot(context);
    const int tokens = whisper_full_n_tokens(context, index);
    double sum = 0.0;
    int used = 0;
    for (int i = 0; i < tokens; ++i) {
        const whisper_token_data data = whisper_full_get_token_data(context, index, i);
        if (data.id >= eot) continue;
        sum += static_cast<double>(data.p);
        ++used;
    }
    return used == 0 ? -1.0f : static_cast<jfloat>(sum / used);
}

// ISO 639-1 code of the language whisper used for the last run, or null.
JNIEXPORT jstring JNICALL Java_org_sakshi_processing_stt_WhisperNative_nativeLanguage(JNIEnv* env, jobject, jlong handle) {
    const int id = whisper_full_lang_id(sessionOf(handle)->context);
    if (id < 0) return nullptr;
    const char* code = whisper_lang_str(id);
    return code == nullptr ? nullptr : env->NewStringUTF(code);
}

}  // extern "C"
