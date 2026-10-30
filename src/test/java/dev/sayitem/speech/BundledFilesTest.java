package dev.sayitem.speech;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BundledFilesTest {
    @TempDir Path cache;
    @Test void extractsAndRepairsCachedModelWithoutNetwork() throws Exception {
        Path file = cache.resolve("models/base.bin");
        assertEquals(file, BundledFiles.extract(LocalWhisper.class, "/sayitem-speech/ggml-base-q5_1.bin", file, LocalWhisper.MODEL_SHA));
        assertEquals(59707625, Files.size(file));
        Files.writeString(file, "damaged cache");
        BundledFiles.extract(LocalWhisper.class, "/sayitem-speech/ggml-base-q5_1.bin", file, LocalWhisper.MODEL_SHA);
        assertEquals(LocalWhisper.MODEL_SHA, BundledFiles.sha256(file));
        try (var files = Files.list(file.getParent())) {
            assertEquals(1, files.count());
        }
    }
    @Test void absentResourcesFailWithoutLeavingPartialFiles() throws Exception {
        Path file = cache.resolve("missing.bin");
        assertThrows(java.io.IOException.class, () -> BundledFiles.extract(LocalWhisper.class, "/not-present", file, null));
        assertFalse(Files.exists(file));
    }
}
