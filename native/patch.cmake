set(WHISPER_CPP "${WHISPER_JNI_SOURCE}/src/main/native/whisper/src/whisper.cpp")
file(READ "${WHISPER_CPP}" WHISPER_TEXT)
if(NOT WHISPER_TEXT MATCHES "sayitem_abort_patch")
    set(OLD_SIGNATURE "                       int   n_threads) {")
    set(NEW_SIGNATURE "                       int   n_threads, ggml_abort_callback abort_callback = nullptr, void * abort_data = nullptr) { // sayitem_abort_patch")
    string(FIND "${WHISPER_TEXT}" "${OLD_SIGNATURE}" MATCH_INDEX)
    if(MATCH_INDEX EQUAL -1)
        message(FATAL_ERROR "Pinned whisper.cpp scheduler signature changed")
    endif()
    string(REPLACE "${OLD_SIGNATURE}" "${NEW_SIGNATURE}" WHISPER_TEXT "${WHISPER_TEXT}")
    string(REPLACE "ggml_backend_cpu_set_n_threads(backend, n_threads);"
        "ggml_backend_cpu_set_n_threads(backend, n_threads);\n            ggml_backend_cpu_set_abort_callback(backend, abort_callback, abort_data);"
        WHISPER_TEXT "${WHISPER_TEXT}")
    string(REPLACE "ggml_graph_compute_helper(sched, gf, n_threads)"
        "ggml_graph_compute_helper(sched, gf, n_threads, abort_callback, abort_callback_data)"
        WHISPER_TEXT "${WHISPER_TEXT}")
    file(WRITE "${WHISPER_CPP}" "${WHISPER_TEXT}")
endif()
