#include "whisper.h"
#include <cstddef>
#ifdef _WIN32
#define SAYITEM_EXPORT __declspec(dllexport)
#else
#define SAYITEM_EXPORT __attribute__((visibility("default")))
#endif
extern "C" SAYITEM_EXPORT std::size_t sayitem_full_params_size() { return sizeof(whisper_full_params); }
extern "C" SAYITEM_EXPORT std::size_t sayitem_context_params_size() { return sizeof(whisper_context_params); }
