package dev.sayitem.speech;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BundledFilesTest {
    @TempDir Path cache;
    @Test void extractsAndRepairsCachedModelWithoutNetwork() throws Exception {
        Path file = cache.resolve("model.json");
        String expected;
        try (var stream = LocalWhisper.class.getResourceAsStream("/sayitem-speech/model.json")) {
            expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        assertEquals(file, BundledFiles.extract(LocalWhisper.class, "/sayitem-speech/model.json", file, expected));
        Files.writeString(file, "damaged cache");
        BundledFiles.extract(LocalWhisper.class, "/sayitem-speech/model.json", file, expected);
        assertEquals(expected, BundledFiles.sha256(file));
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
