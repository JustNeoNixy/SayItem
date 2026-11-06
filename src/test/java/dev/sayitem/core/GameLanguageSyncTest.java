package dev.sayitem.core;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameLanguageSyncTest {
    static final class Host implements GameLanguageSync.Host {
        String current = "en_us", previous = "";
        Set<String> available = Set.of("en_us", "de_de", "it_it", "pt_br", "pt_pt");
        List<String> changes = new ArrayList<>(); List<Boolean> readiness = new ArrayList<>();
        CompletableFuture<Void> pending; int failures;
        public String current() { return current; }
        public boolean available(String code) { return available.contains(code); }
        public String previous() { return previous; }
        public void previous(String code) { previous = code; }
        public CompletableFuture<Void> change(String code) { changes.add(code); current = code; return pending = new CompletableFuture<>(); }
        public void execute(Runnable task) { task.run(); }
        public void busy(boolean value) { readiness.add(value); }
        public void failed() { failures++; }
        void finish() { pending.complete(null); }
    }
    @Test void reloadingPausesReadinessAndRestoresTheOriginalLanguageWhenDisabled() {
        var host = new Host(); var sync = new GameLanguageSync(host);
        sync.follow("it", "it"); assertTrue(sync.busy()); assertEquals("en_us", host.previous);
        sync.follow("it", "it"); assertEquals(1, host.changes.size());
        host.finish(); assertFalse(sync.busy()); assertEquals(List.of(true, false), host.readiness);
        sync.restore(); assertTrue(sync.busy()); host.finish();
        assertEquals("en_us", host.current); assertEquals("", host.previous); assertFalse(sync.busy());
    }
    @Test void rapidChangesAndDisconnectDuringAReloadAreSerialized() {
        var host = new Host(); var sync = new GameLanguageSync(host);
        sync.follow("it", "it"); sync.follow("de", "de"); sync.restore();
        assertEquals(List.of("it_it"), host.changes);
        host.finish(); assertEquals(List.of("it_it", "en_us"), host.changes);
        assertTrue(sync.busy()); assertEquals(List.of(true), host.readiness);
        host.finish(); assertFalse(sync.busy()); assertEquals("", host.previous);
    }
    @Test void restartRecoveryCustomLocalesAndMissingLanguagesAreHandled() {
        var host = new Host(); host.current = "it_it"; host.previous = "en_us";
        var sync = new GameLanguageSync(host); sync.restore(); host.finish();
        assertEquals("en_us", host.current); assertEquals("", host.previous);
        sync.follow("pt-br", "pt"); assertEquals("pt_br", host.current); host.finish();
        sync.follow("zz", "zz"); assertFalse(sync.busy()); assertEquals("pt_br", host.current);
    }
    @Test void failedReloadsReleaseReadinessAndKeepTheOriginalForRecovery() {
        var host = new Host(); var sync = new GameLanguageSync(host);
        sync.follow("it", "it"); host.pending.completeExceptionally(new IllegalStateException("reload failed"));
        assertFalse(sync.busy()); assertEquals(1, host.failures); assertEquals("en_us", host.previous);
        sync.restore(); host.finish(); assertEquals("", host.previous);
    }
}
