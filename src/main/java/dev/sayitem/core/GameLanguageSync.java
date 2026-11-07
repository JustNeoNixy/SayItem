package dev.sayitem.core;

import java.util.concurrent.CompletableFuture;

/** Serializes resource reloads and keeps the user's original interface language. */
public final class GameLanguageSync {
    public interface Host {
        String current();
        boolean available(String locale);
        String previous();
        void previous(String locale);
        CompletableFuture<Void> change(String locale);
        void execute(Runnable task);
        void busy(boolean loading);
        void failed();
    }
    private final Host host;
    private String desired = "";
    private boolean restoring, loading, busy;
    public GameLanguageSync(Host host) { this.host = host; }
    public boolean busy() { return busy; }
    public static boolean validLocale(String locale) {
        return locale != null && locale.matches("[a-z0-9_-]{2,64}");
    }
    public void follow(String pack, String whisperLanguage) {
        String target = pack.replace('-', '_');
        if (!host.available(target)) target = BuiltInLanguages.LOCALES.getOrDefault(whisperLanguage, "");
        if (!target.isEmpty() && host.available(target)) request(target, false);
    }
    public void restore() { if (!host.previous().isEmpty()) request(host.previous(), true); }
    private void request(String target, boolean restore) { desired = target; restoring = restore; pump(); }
    private void setBusy(boolean value) { if (busy != value) { busy = value; host.busy(value); } }
    private void pump() {
        if (loading) return;
        if (desired.isEmpty()) { setBusy(false); return; }
        if (!host.available(desired)) { desired = ""; host.failed(); setBusy(false); return; }
        if (host.current().equals(desired)) {
            if (restoring) host.previous("");
            desired = ""; setBusy(false); return;
        }
        if (!restoring && host.previous().isEmpty()) host.previous(host.current());
        String target = desired;
        loading = true; setBusy(true);
        CompletableFuture<Void> changed;
        try { changed = host.change(target); }
        catch (RuntimeException error) { changed = CompletableFuture.failedFuture(error); }
        changed.whenComplete((ignored, failure) -> host.execute(() -> {
            loading = false;
            if (failure != null) host.failed();
            if (desired.equals(target)) {
                if (failure == null && restoring) host.previous("");
                desired = "";
            }
            pump();
        }));
    }
}
