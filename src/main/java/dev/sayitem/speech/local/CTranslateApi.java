package dev.sayitem.speech.local;

import com.sun.jna.*;

public interface CTranslateApi extends Library {
    String sayitem_ct2_version();
    Pointer sayitem_ct2_load(String path, int threads, byte[] error, int capacity);
    void sayitem_ct2_free(Pointer context);
    int sayitem_ct2_transcribe(Pointer context, float[] features, int[] prefix, int prefixCount,
        int[] tokens, int capacity, float[] metrics, Cancelled cancelled, Pointer userData, byte[] error, int errorCapacity);
    interface Cancelled extends Callback { int invoke(Pointer userData); }
}
