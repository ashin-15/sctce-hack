#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <stdexcept>
#include <thread>
#include <unordered_map>
#include <utility>
#include <vector>

#include "llama.h"
#include "model_identity.h"
#include "prompt_escape.h"

namespace {

constexpr jint kSuccess = 0;
constexpr jint kRuntimeError = 1;
constexpr jint kTruncated = 2;
constexpr jint kCancelled = 3;
constexpr jint kModelLoadFailed = 2;
constexpr jint kModelIdentityMismatch = 3;
constexpr jint kContextInitFailed = 4;
static_assert(LLAMA_FTYPE_MOSTLY_Q4_K_M == 15, "Update the pinned GGUF file-type identity contract");
constexpr int32_t kMaxPromptTokens = 1792;
constexpr int32_t kMaxOutputTokens = 96;
constexpr size_t kMaxTextBytes = 64 * 1024;

struct Session {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    std::mutex request_mutex;
    std::string active_request;
    std::atomic<bool> cancelled{false};
    std::atomic<bool> closing{false};

    ~Session() {
        if (context != nullptr) llama_free(context);
        if (model != nullptr) llama_model_free(model);
    }
};

std::mutex g_sessions_mutex;
std::unordered_map<jlong, std::shared_ptr<Session>> g_sessions;
std::atomic<jlong> g_next_handle{1};
std::once_flag g_backend_once;

void suppress_log(enum ggml_log_level, const char *, void *) {}

bool abort_decode(void * data) {
    const auto * session = static_cast<const Session *>(data);
    return session->cancelled.load(std::memory_order_relaxed) || session->closing.load(std::memory_order_relaxed);
}

std::string from_java(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    const jchar * chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string output;
    output.reserve(static_cast<size_t>(length) * 3U);
    for (jsize index = 0; index < length; ++index) {
        uint32_t codepoint = chars[index];
        if (codepoint >= 0xd800U && codepoint <= 0xdbffU && index + 1 < length) {
            const uint32_t low = chars[index + 1];
            if (low >= 0xdc00U && low <= 0xdfffU) {
                codepoint = 0x10000U + ((codepoint - 0xd800U) << 10U) + (low - 0xdc00U);
                ++index;
            } else {
                codepoint = 0xfffdU;
            }
        } else if (codepoint >= 0xdc00U && codepoint <= 0xdfffU) {
            codepoint = 0xfffdU;
        }
        if (codepoint <= 0x7fU) {
            output.push_back(static_cast<char>(codepoint));
        } else if (codepoint <= 0x7ffU) {
            output.push_back(static_cast<char>(0xc0U | (codepoint >> 6U)));
            output.push_back(static_cast<char>(0x80U | (codepoint & 0x3fU)));
        } else if (codepoint <= 0xffffU) {
            output.push_back(static_cast<char>(0xe0U | (codepoint >> 12U)));
            output.push_back(static_cast<char>(0x80U | ((codepoint >> 6U) & 0x3fU)));
            output.push_back(static_cast<char>(0x80U | (codepoint & 0x3fU)));
        } else {
            output.push_back(static_cast<char>(0xf0U | (codepoint >> 18U)));
            output.push_back(static_cast<char>(0x80U | ((codepoint >> 12U) & 0x3fU)));
            output.push_back(static_cast<char>(0x80U | ((codepoint >> 6U) & 0x3fU)));
            output.push_back(static_cast<char>(0x80U | (codepoint & 0x3fU)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return output;
}

jstring to_java(JNIEnv * env, const std::string & value) {
    std::vector<jchar> utf16;
    utf16.reserve(value.size());
    for (size_t i = 0; i < value.size();) {
        const auto first = static_cast<uint8_t>(value[i]);
        uint32_t codepoint = 0xfffdU;
        size_t count = 1;
        if (first <= 0x7fU) {
            codepoint = first;
        } else if ((first & 0xe0U) == 0xc0U && i + 1 < value.size()) {
            codepoint = ((first & 0x1fU) << 6U) | (static_cast<uint8_t>(value[i + 1]) & 0x3fU);
            count = 2;
        } else if ((first & 0xf0U) == 0xe0U && i + 2 < value.size()) {
            codepoint = ((first & 0x0fU) << 12U) |
                ((static_cast<uint8_t>(value[i + 1]) & 0x3fU) << 6U) |
                (static_cast<uint8_t>(value[i + 2]) & 0x3fU);
            count = 3;
        } else if ((first & 0xf8U) == 0xf0U && i + 3 < value.size()) {
            codepoint = ((first & 0x07U) << 18U) |
                ((static_cast<uint8_t>(value[i + 1]) & 0x3fU) << 12U) |
                ((static_cast<uint8_t>(value[i + 2]) & 0x3fU) << 6U) |
                (static_cast<uint8_t>(value[i + 3]) & 0x3fU);
            count = 4;
        }
        i += count;
        if (codepoint > 0x10ffffU || (codepoint >= 0xd800U && codepoint <= 0xdfffU)) codepoint = 0xfffdU;
        if (codepoint <= 0xffffU) {
            utf16.push_back(static_cast<jchar>(codepoint));
        } else {
            codepoint -= 0x10000U;
            utf16.push_back(static_cast<jchar>(0xd800U + (codepoint >> 10U)));
            utf16.push_back(static_cast<jchar>(0xdc00U + (codepoint & 0x3ffU)));
        }
    }
    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

std::shared_ptr<Session> find_session(jlong handle) {
    std::lock_guard<std::mutex> guard(g_sessions_mutex);
    const auto found = g_sessions.find(handle);
    return found == g_sessions.end() ? nullptr : found->second;
}

jlongArray init_result(JNIEnv * env, jlong handle, jint status, uint64_t parameters = 0, long file_type = -1, bool qwen_architecture = false) {
    jlong values[5] = {handle, static_cast<jlong>(status), static_cast<jlong>(parameters),
        static_cast<jlong>(file_type), qwen_architecture ? 1L : 0L};
    jlongArray result = env->NewLongArray(5);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 5, values);
    return result;
}

jobjectArray generation_result(JNIEnv * env, jint status, const std::string & text) {
    jclass string_class = env->FindClass("java/lang/String");
    if (string_class == nullptr) return nullptr;
    jobjectArray result = env->NewObjectArray(2, string_class, nullptr);
    if (result == nullptr) return nullptr;
    jstring status_value = env->NewStringUTF(std::to_string(status).c_str());
    jstring text_value = to_java(env, text);
    env->SetObjectArrayElement(result, 0, status_value);
    env->SetObjectArrayElement(result, 1, text_value);
    env->DeleteLocalRef(status_value);
    env->DeleteLocalRef(text_value);
    env->DeleteLocalRef(string_class);
    return result;
}

bool tokenize(const llama_vocab * vocab, const std::string & prompt, std::vector<llama_token> * tokens) {
    const int32_t capacity = kMaxPromptTokens + 1;
    tokens->resize(static_cast<size_t>(capacity));
    const int32_t count = llama_tokenize(vocab, prompt.data(), static_cast<int32_t>(prompt.size()), tokens->data(), capacity, true, true);
    if (count < 0 || count > kMaxPromptTokens) return false;
    tokens->resize(static_cast<size_t>(count));
    return !tokens->empty();
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *) {
    std::call_once(g_backend_once, [] {
        llama_backend_init();
        llama_log_set(suppress_log, nullptr);
    });
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_org_sakshi_processing_llm_engine_NativeLlmBridge_nativeInitModel(JNIEnv * env, jclass, jstring path, jint context_size) {
    if (path == nullptr || context_size < 512 || context_size > 4096) return init_result(env, 0L, kRuntimeError);
    try {
        auto session = std::make_shared<Session>();
        const std::string model_path = from_java(env, path);
        auto model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0;
        model_params.use_mmap = true;
        model_params.check_tensors = true;
        session->model = llama_model_load_from_file(model_path.c_str(), model_params);
        if (session->model == nullptr) return init_result(env, 0L, kModelLoadFailed);
        char architecture[64] = {};
        char file_type[32] = {};
        const int32_t architecture_size = llama_model_meta_val_str(
            session->model, "general.architecture", architecture, sizeof(architecture));
        const int32_t file_type_size = llama_model_meta_val_str(
            session->model, "general.file_type", file_type, sizeof(file_type));
        const uint64_t parameter_count = llama_model_n_params(session->model);
        char * file_type_end = nullptr;
        const long observed_file_type = std::strtol(file_type, &file_type_end, 10);
        const bool valid_file_type = file_type_end != file_type && *file_type_end == '\0';
        const bool qwen_architecture = std::string(architecture) == "qwen2";
        if (architecture_size < 0 || architecture_size >= static_cast<int32_t>(sizeof(architecture)) ||
            file_type_size < 0 || file_type_size >= static_cast<int32_t>(sizeof(file_type)) ||
            !sakshi::is_supported_qwen(architecture, file_type, parameter_count)) {
            return init_result(env, 0L, kModelIdentityMismatch, parameter_count,
                valid_file_type ? observed_file_type : -1, qwen_architecture);
        }
        auto context_params = llama_context_default_params();
        context_params.n_ctx = static_cast<uint32_t>(context_size);
        context_params.n_batch = 128;
        context_params.n_ubatch = 128;
        const auto core_count = std::max(1U, std::thread::hardware_concurrency());
        context_params.n_threads = static_cast<int32_t>(std::min(core_count, 4U));
        context_params.n_threads_batch = context_params.n_threads;
        context_params.abort_callback = abort_decode;
        context_params.abort_callback_data = session.get();
        session->context = llama_init_from_model(session->model, context_params);
        if (session->context == nullptr) return init_result(env, 0L, kContextInitFailed, parameter_count,
            observed_file_type, qwen_architecture);
        const jlong handle = g_next_handle.fetch_add(1, std::memory_order_relaxed);
        {
            std::lock_guard<std::mutex> guard(g_sessions_mutex);
            g_sessions.emplace(handle, std::move(session));
        }
        return init_result(env, handle, kSuccess, parameter_count, observed_file_type, qwen_architecture);
    } catch (...) {
        return init_result(env, 0L, kRuntimeError);
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_sakshi_processing_llm_engine_NativeLlmBridge_nativeGenerate(
    JNIEnv * env, jclass, jlong handle, jstring system, jstring user, jstring request_id,
    jint max_tokens, jstring grammar, jobjectArray stop_sequences) {
    const auto session = find_session(handle);
    if (session == nullptr || system == nullptr || user == nullptr || request_id == nullptr || max_tokens < 1 || max_tokens > kMaxOutputTokens) {
        return generation_result(env, kRuntimeError, "");
    }
    const std::string system_text = from_java(env, system);
    std::string user_text = from_java(env, user);
    const std::string request_key = from_java(env, request_id);
    if (system_text.size() + user_text.size() > kMaxTextBytes || request_key.empty()) return generation_result(env, kRuntimeError, "");
    {
        std::lock_guard<std::mutex> guard(session->request_mutex);
        session->active_request = request_key;
        session->cancelled.store(false, std::memory_order_relaxed);
    }

    jint status = kRuntimeError;
    std::string output;
    llama_sampler * sampler = nullptr;
    try {
        const std::string escaped_user = sakshi::escape_chat_control_tokens(user_text);
        llama_chat_message messages[2] = {{"system", system_text.c_str()}, {"user", escaped_user.c_str()}};
        const char * template_text = llama_model_chat_template(session->model, nullptr);
        const int32_t template_capacity = static_cast<int32_t>(std::min(kMaxTextBytes, (system_text.size() + escaped_user.size()) * 4U + 1024U));
        std::vector<char> formatted(static_cast<size_t>(template_capacity));
        const int32_t formatted_size = llama_chat_apply_template(template_text, messages, 2U, true, formatted.data(), template_capacity);
        if (formatted_size <= 0 || formatted_size >= template_capacity) throw std::runtime_error("chat template unavailable or too large");
        std::vector<llama_token> tokens;
        if (!tokenize(llama_model_get_vocab(session->model), std::string(formatted.data(), static_cast<size_t>(formatted_size)), &tokens)) {
            throw std::runtime_error("prompt exceeds context budget");
        }
        sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        if (sampler == nullptr) throw std::runtime_error("sampler creation failed");
        const std::string grammar_text = from_java(env, grammar);
        if (!grammar_text.empty()) {
            llama_sampler * grammar_sampler = llama_sampler_init_grammar(llama_model_get_vocab(session->model), grammar_text.c_str(), "root");
            if (grammar_sampler == nullptr) throw std::runtime_error("grammar rejected");
            llama_sampler_chain_add(sampler, grammar_sampler);
        }
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
        int32_t position = 0;
        while (position < static_cast<int32_t>(tokens.size())) {
            if (session->cancelled.load(std::memory_order_relaxed) || session->closing.load(std::memory_order_relaxed)) {
                status = kCancelled;
                break;
            }
            const int32_t count = std::min(128, static_cast<int32_t>(tokens.size()) - position);
            auto batch = llama_batch_get_one(tokens.data() + position, count);
            if (llama_decode(session->context, batch) != 0) throw std::runtime_error("prompt decode failed");
            position += count;
        }
        if (position == static_cast<int32_t>(tokens.size())) {
            status = kTruncated;
            std::vector<std::string> stops;
            const jsize stop_count = stop_sequences == nullptr ? 0 : env->GetArrayLength(stop_sequences);
            for (jsize index = 0; index < stop_count && index < 8; ++index) {
                auto stop = static_cast<jstring>(env->GetObjectArrayElement(stop_sequences, index));
                if (stop != nullptr) {
                    stops.push_back(from_java(env, stop));
                    env->DeleteLocalRef(stop);
                }
            }
            const auto * vocab = llama_model_get_vocab(session->model);
            for (jint index = 0; index < max_tokens; ++index) {
                if (session->cancelled.load(std::memory_order_relaxed) || session->closing.load(std::memory_order_relaxed)) {
                    status = kCancelled;
                    output.clear();
                    break;
                }
                // llama_sampler_sample accepts the token itself; a second accept would advance the grammar twice.
                const llama_token token = llama_sampler_sample(sampler, session->context, -1);
                if (llama_vocab_is_eog(vocab, token)) {
                    status = kSuccess;
                    break;
                }
                char piece[512];
                const int32_t piece_size = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
                if (piece_size < 0 || output.size() + static_cast<size_t>(piece_size) > kMaxTextBytes) {
                    status = kTruncated;
                    output.clear();
                    break;
                }
                output.append(piece, static_cast<size_t>(piece_size));
                bool stop_hit = false;
                for (const auto & stop : stops) {
                    if (!stop.empty() && output.size() >= stop.size() && output.compare(output.size() - stop.size(), stop.size(), stop) == 0) {
                        output.resize(output.size() - stop.size());
                        status = kSuccess;
                        stop_hit = true;
                        break;
                    }
                }
                if (stop_hit) break;
                auto next = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
                if (llama_decode(session->context, next) != 0) {
                    status = session->cancelled.load(std::memory_order_relaxed) ? kCancelled : kRuntimeError;
                    output.clear();
                    break;
                }
                if (index + 1 == max_tokens) {
                    output.clear();
                    status = kTruncated;
                }
            }
        }
    } catch (...) {
        status = session->cancelled.load(std::memory_order_relaxed) ? kCancelled : kRuntimeError;
        output.clear();
    }
    if (sampler != nullptr) llama_sampler_free(sampler);
    llama_memory_clear(llama_get_memory(session->context), true);
    {
        std::lock_guard<std::mutex> guard(session->request_mutex);
        session->active_request.clear();
    }
    return generation_result(env, status, output);
}

extern "C" JNIEXPORT void JNICALL
Java_org_sakshi_processing_llm_engine_NativeLlmBridge_nativeCancel(JNIEnv * env, jclass, jlong handle, jstring request_id) {
    const auto session = find_session(handle);
    if (session == nullptr || request_id == nullptr) return;
    const std::string target = from_java(env, request_id);
    std::lock_guard<std::mutex> guard(session->request_mutex);
    if (target == session->active_request) session->cancelled.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_org_sakshi_processing_llm_engine_NativeLlmBridge_nativeFreeModel(JNIEnv *, jclass, jlong handle) {
    std::shared_ptr<Session> session;
    {
        std::lock_guard<std::mutex> guard(g_sessions_mutex);
        const auto found = g_sessions.find(handle);
        if (found == g_sessions.end()) return;
        session = found->second;
        g_sessions.erase(found);
    }
    session->closing.store(true, std::memory_order_relaxed);
    session->cancelled.store(true, std::memory_order_relaxed);
}
