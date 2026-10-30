#include <cstdio>
#include <cstddef>
#include <initializer_list>
#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

int main(int argc, char **argv) {
    if (argc != 2) return 2;
#ifdef _WIN32
    auto library = LoadLibraryA(argv[1]);
    auto symbol = [library](const char *name) { return reinterpret_cast<void *>(GetProcAddress(library, name)); };
#else
    auto library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    auto symbol = [library](const char *name) { return dlsym(library, name); };
#endif
    if (!library) { std::fprintf(stderr, "Cannot load speech library\n"); return 1; }
    for (const char *name : {"whisper_context_default_params_by_ref", "whisper_free_context_params",
            "whisper_init_from_file_with_params", "whisper_full_default_params_by_ref", "whisper_free_params",
            "whisper_full", "whisper_full_n_segments", "whisper_full_get_segment_text", "whisper_lang_id",
            "whisper_is_multilingual", "whisper_free", "whisper_log_set",
            "sayitem_full_params_size", "sayitem_context_params_size"}) {
        if (!symbol(name)) { std::fprintf(stderr, "Missing export: %s\n", name); return 1; }
    }
    using Size = std::size_t (*)();
    if (reinterpret_cast<Size>(symbol("sayitem_full_params_size"))() != 264 ||
            reinterpret_cast<Size>(symbol("sayitem_context_params_size"))() != 48) {
        std::fprintf(stderr, "Unexpected speech ABI size\n"); return 1;
    }
    std::puts("Native speech exports and ABI verified");
}
