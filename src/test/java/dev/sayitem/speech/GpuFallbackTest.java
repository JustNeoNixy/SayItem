package dev.sayitem.speech;

import dev.speechlib.*;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GpuFallbackTest {
    @TempDir Path cache;
    @Test void unavailableGpuFallsBackToCpuWithoutDownloadingGpuModels() throws Exception {
        String before = System.getProperty("speechlib.gpu.disabled");
        System.setProperty("speechlib.gpu.disabled", "true");
        try (var engine = SpeechLib.builder().cacheDirectory(cache).profile(SpeechProfile.GPU_SMALL).build()) {
            engine.initialize().get(90, TimeUnit.SECONDS);
            assertTrue(engine.isReady()); assertTrue(engine.status().contains("using CPU"), engine.status());
            assertFalse(Files.exists(cache.resolve("gpu-models")));
        } finally {
            if (before == null) System.clearProperty("speechlib.gpu.disabled"); else System.setProperty("speechlib.gpu.disabled", before);
        }
    }
}
