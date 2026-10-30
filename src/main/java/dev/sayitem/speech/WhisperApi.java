package dev.sayitem.speech;

import com.sun.jna.*;

/** Pinned whisper.cpp 1.7.1 C ABI. Direct binding exposes its real computation-abort callback. */
public interface WhisperApi extends Library {
    Pointer whisper_context_default_params_by_ref();
    Pointer whisper_init_from_file_with_params(String file, ContextParams params);
    void whisper_free_context_params(Pointer params);
    Pointer whisper_full_default_params_by_ref(int strategy);
    void whisper_free_params(Pointer params);
    int whisper_full(Pointer context, FullParams params, float[] samples, int count);
    int whisper_full_n_segments(Pointer context);
    String whisper_full_get_segment_text(Pointer context, int index);
    int whisper_is_multilingual(Pointer context);
    int whisper_lang_id(String language);
    void whisper_free(Pointer context);
    void whisper_log_set(LogCallback logger, Pointer userData);
    long sayitem_full_params_size();
    long sayitem_context_params_size();
    interface AbortCallback extends Callback { byte invoke(Pointer userData); }
    interface LogCallback extends Callback { void invoke(int level, Pointer text, Pointer userData); }

    @Structure.FieldOrder({"count", "heads"})
    class Heads extends Structure { public long count; public Pointer heads; }

    @Structure.FieldOrder({"useGpu", "flashAttention", "gpuDevice", "dtwTimestamps", "dtwPreset", "dtwTop", "dtwHeads", "dtwMemory"})
    class ContextParams extends Structure implements Structure.ByValue {
        public byte useGpu, flashAttention;
        public int gpuDevice;
        public byte dtwTimestamps;
        public int dtwPreset, dtwTop;
        public Heads dtwHeads = new Heads();
        public long dtwMemory;
        public ContextParams(Pointer pointer) { super(pointer); read(); }
    }

    @Structure.FieldOrder({"strategy", "threads", "maxTextContext", "offsetMs", "durationMs",
        "translate", "noContext", "noTimestamps", "singleSegment", "printSpecial", "printProgress", "printRealtime", "printTimestamps",
        "tokenTimestamps", "timestampThreshold", "timestampSumThreshold", "maxLength", "splitOnWord", "maxTokens", "debug", "audioContext", "diarize",
        "suppressRegex", "initialPrompt", "promptTokens", "promptCount", "language", "detectLanguage", "suppressBlank", "suppressNonSpeech",
        "temperature", "maxInitialTimestamp", "lengthPenalty", "temperatureIncrement", "entropyThreshold", "logprobThreshold", "noSpeechThreshold",
        "greedyBest", "beamSize", "beamPatience", "newSegmentCallback", "newSegmentData", "progressCallback", "progressData",
        "encoderCallback", "encoderData", "abortCallback", "abortData", "logitsCallback", "logitsData", "grammarRules", "grammarCount", "grammarStart", "grammarPenalty"})
    class FullParams extends Structure implements Structure.ByValue {
        public int strategy, threads, maxTextContext, offsetMs, durationMs;
        public byte translate, noContext, noTimestamps, singleSegment, printSpecial, printProgress, printRealtime, printTimestamps;
        public byte tokenTimestamps;
        public float timestampThreshold, timestampSumThreshold;
        public int maxLength;
        public byte splitOnWord;
        public int maxTokens;
        public byte debug;
        public int audioContext;
        public byte diarize;
        public Pointer suppressRegex, initialPrompt, promptTokens;
        public int promptCount;
        public String language;
        public byte detectLanguage, suppressBlank, suppressNonSpeech;
        public float temperature, maxInitialTimestamp, lengthPenalty, temperatureIncrement, entropyThreshold, logprobThreshold, noSpeechThreshold;
        public int greedyBest, beamSize;
        public float beamPatience;
        public Pointer newSegmentCallback, newSegmentData, progressCallback, progressData, encoderCallback, encoderData;
        public AbortCallback abortCallback;
        public Pointer abortData, logitsCallback, logitsData, grammarRules;
        public long grammarCount, grammarStart;
        public float grammarPenalty;
        public FullParams(Pointer pointer) { super(pointer); read(); }
    }
}
