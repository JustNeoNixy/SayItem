package dev.sayitem.speech.local;

import com.sun.jna.*;
import java.nio.file.Path;
import java.util.*;

interface VulkanApi extends Library {
    interface Cancelled extends Callback { int invoke(Pointer data); }
    String sayitem_vk_version();
    String sayitem_vk_device();
    Pointer sayitem_vk_load(String model, int gpu, int threads, byte[] error, int capacity);
    void sayitem_vk_free(Pointer context);
    int sayitem_vk_transcribe(Pointer context, float[] samples, int length, String language, int beam,
        byte[] text, int capacity, Cancelled cancelled, Pointer data, byte[] error, int errorCapacity);
    List<NativeLibrary> LIBRARIES = new ArrayList<>();
    static VulkanApi load(Path root) {
        synchronized (LIBRARIES) {
        for (String file : List.of("sayitem-ggml-base.dll", "sayitem-ggml-cpu.dll", "sayitem-ggml-vulkan.dll",
                "sayitem-ggml.dll", "sayitem-whisper.dll")) {
            LIBRARIES.add(NativeLibrary.getInstance(root.resolve(file).toString()));
        }
        return Native.load(root.resolve("sayitem-vulkan.dll").toString(), VulkanApi.class,
            Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
        }
    }
}
