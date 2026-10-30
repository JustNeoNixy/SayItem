#include "ctranslate2/models/whisper.h"
#include <algorithm>
#include <cstring>
#include <exception>
#ifdef _WIN32
#define API extern "C" __declspec(dllexport)
#else
#define API extern "C" __attribute__((visibility("default")))
#endif
using Check = int (*)(void *);
API const char *sayitem_ct2_version() { return "4.8.4"; }
static void error_text(char *out, int cap, const char *message) {
    if (out && cap > 0) { std::strncpy(out, message, cap - 1); out[cap - 1] = 0; }
}
API void * sayitem_ct2_load(const char *path, int threads, char *error, int capacity) {
    try {
        ctranslate2::ReplicaPoolConfig config;
        config.num_threads_per_replica = threads;
        return new ctranslate2::models::Whisper(path, ctranslate2::Device::CPU,
            ctranslate2::ComputeType::INT8, {0}, false, config);
    } catch (const std::exception &e) { error_text(error, capacity, e.what()); return nullptr; }
}
API void sayitem_ct2_free(void *pointer) { delete static_cast<ctranslate2::models::Whisper *>(pointer); }
API int sayitem_ct2_transcribe(void *pointer, float *features, int *prefix, int prefix_count,
        int *tokens, int capacity, float *metrics, Check cancelled, void *data, char *error, int error_capacity) {
    try {
        auto &model = *static_cast<ctranslate2::models::Whisper *>(pointer);
        if (cancelled && cancelled(data)) return -2;
        ctranslate2::StorageView input({1, 80, 3000}, features);
        auto encoded = model.encode(input, true).get();
        if (cancelled && cancelled(data)) return -2;
        std::vector<size_t> prompt(prefix, prefix + prefix_count);
        ctranslate2::models::WhisperOptions options;
        options.beam_size = 5;
        options.max_length = 64;
        options.return_scores = true;
        options.return_no_speech_prob = true;
        auto result = model.generate(encoded, std::vector<std::vector<size_t>>{prompt}, options)[0].get();
        if (cancelled && cancelled(data)) return -2;
        if (result.sequences_ids.empty()) return 0;
        const auto &ids = result.sequences_ids[0];
        if (ids.size() > static_cast<size_t>(capacity)) throw std::runtime_error("Speech result is too long");
        std::copy(ids.begin(), ids.end(), tokens);
        metrics[0] = result.no_speech_prob;
        metrics[1] = result.scores.empty() ? 0 : result.scores[0];
        return static_cast<int>(ids.size());
    } catch (const std::exception &e) { error_text(error, error_capacity, e.what()); return -1; }
}
