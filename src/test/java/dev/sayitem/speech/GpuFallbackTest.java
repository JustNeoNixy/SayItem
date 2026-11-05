package dev.sayitem.speech;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GpuFallbackTest {
    @TempDir Path cache;
    @Test void unavailableGpuFallsBackToCpuWithoutDownloadingGpuModels() throws Exception {
        String before = System.getProperty("sayitem.gpu.disabled");
        System.setProperty("sayitem.gpu.disabled", "true");
        try (var engine = new LocalWhisper(cache, SpeechProfile.GPU_SMALL)) {
            engine.initialize().get(90, TimeUnit.SECONDS);
            assertTrue(engine.isReady()); assertTrue(engine.status().contains("using CPU"), engine.status());
            assertFalse(Files.exists(cache.resolve("gpu-models")));
        } finally {
            if (before == null) System.clearProperty("sayitem.gpu.disabled"); else System.setProperty("sayitem.gpu.disabled", before);
        }
    }
}
