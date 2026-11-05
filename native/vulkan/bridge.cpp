#include "whisper.h"
#include "ggml-backend.h"
#include <cstring>
#include <exception>
#include <stdexcept>
#include <string>
#ifdef _WIN32
#define API extern "C" __declspec(dllexport)
#else
#define API extern "C" __attribute__((visibility("default")))
#endif
using Check = int (*)(void *);
struct Session { whisper_context *context; int threads; };
struct Cancellation { Check check; void *data; };
static void error_text(char *out, int cap, const char *message) {
    if (out && cap > 0) { std::strncpy(out, message, cap-1); out[cap-1] = 0; }
}
static bool cancelled(void *data) {
    auto &value = *static_cast<Cancellation *>(data);
    return value.check && value.check(value.data) != 0;
}
API const char *sayitem_vk_version() { return "whisper.cpp-v1.9.5-d1be6fde"; }
API const char *sayitem_vk_device() {
    try {
    ggml_backend_load_all();
    for (size_t i = 0; i < ggml_backend_dev_count(); ++i) {
        auto device = ggml_backend_dev_get(i);
        if (ggml_backend_dev_type(device) == GGML_BACKEND_DEVICE_TYPE_GPU
            && std::strcmp(ggml_backend_reg_name(ggml_backend_dev_backend_reg(device)), "Vulkan") == 0)
            return ggml_backend_dev_description(device);
    }
    return "";
    } catch (const std::exception &) { return ""; }
}
API void *sayitem_vk_load(const char *path, int gpu, int threads, char *error, int capacity) {
    try {
        if (gpu && !*sayitem_vk_device()) throw std::runtime_error("No compatible Vulkan GPU found");
        auto parameters = whisper_context_default_params();
        parameters.use_gpu = gpu != 0;
        parameters.flash_attn = false;
        auto context = whisper_init_from_file_with_params(path, parameters);
        if (!context) throw std::runtime_error("Whisper model could not be loaded");
        return new Session{context, threads};
    } catch (const std::exception &e) { error_text(error, capacity, e.what()); return nullptr; }
}
API void sayitem_vk_free(void *pointer) {
    auto session = static_cast<Session *>(pointer);
    if (session) { whisper_free(session->context); delete session; }
}
API int sayitem_vk_transcribe(void *pointer, float *audio, int samples, const char *language, int beam,
        char *text, int capacity, Check check, void *data, char *error, int error_capacity) {
    try {
        if (samples < 3200 || samples > 96000) throw std::runtime_error("Record 0.2 to 6 seconds of speech");
        Cancellation abort{check, data};
        if (cancelled(&abort)) return -2;
        auto session = static_cast<Session *>(pointer);
        auto parameters = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
        parameters.n_threads = session->threads;
        parameters.language = language;
        parameters.translate = false;
        parameters.no_context = true;
        parameters.no_timestamps = true;
        parameters.single_segment = true;
        parameters.print_realtime = false;
        parameters.print_progress = false;
        parameters.print_timestamps = false;
        parameters.print_special = false;
        parameters.max_tokens = 64;
        parameters.beam_search.beam_size = beam;
        parameters.temperature = 0;
        parameters.temperature_inc = 0;
        parameters.abort_callback = cancelled;
        parameters.abort_callback_user_data = &abort;
        int result = whisper_full(session->context, parameters, audio, samples);
        if (cancelled(&abort)) return -2;
        if (result != 0) throw std::runtime_error("Whisper inference failed");
        std::string answer;
        for (int segment = 0; segment < whisper_full_n_segments(session->context); ++segment)
            answer += whisper_full_get_segment_text(session->context, segment);
        if (answer.size() >= static_cast<size_t>(capacity)) throw std::runtime_error("Speech result is too long");
        std::memcpy(text, answer.c_str(), answer.size()+1);
        return static_cast<int>(answer.size());
    } catch (const std::exception &e) { error_text(error, error_capacity, e.what()); return -1; }
}
