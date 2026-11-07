package dev.sayitem.core;

import java.util.*;

/** In-memory challenge queues backed by world-owned unlock progress. */
public final class RunState {
    public static final long RECOGNITION_GRACE_MILLIS = 15_000;
    public record Round(String nonce, String item, long durationMillis, long deadline, boolean active) {
        public long remaining(long now) { return active ? Math.max(0, deadline - now) : durationMillis; }
        public boolean expired(long now) { return active && now >= deadline; }
        private Round start(long now) {
            return active ? this : new Round(nonce, item, durationMillis, now + durationMillis, true);
        }
    }
    public static final class PlayerRun {
        public String language = "ja";
        public final Set<String> unlocked;
        private final UUID player;
        private final UnlockProgress progress;
        private Long pausedAt;
        private String randomNonce = "", randomLanguage = "";
        private String recognitionNonce = "";
        private long recognitionUntil;
        private final Map<String, Round> rounds = new LinkedHashMap<>();
        private PlayerRun(UUID player, UnlockProgress progress) {
            this.player = player; this.progress = progress; unlocked = progress.unlocked(player);
        }
        public Round collect(String item, long now) {
            return collect(item, now, TimerLimit.DEFAULT);
        }
        public Round collect(String item, long now, int timerSeconds) {
            long duration = TimerLimit.millis(timerSeconds);
            if (unlocked.contains(item)) return null;
            var existing = rounds.get(item);
            if (existing != null) return existing;
            var round = new Round(UUID.randomUUID().toString(), item, duration, 0, false);
            if (rounds.isEmpty() && pausedAt == null) round = round.start(now);
            rounds.put(item, round);
            return round;
        }
        public Round current() { return rounds.isEmpty() ? null : rounds.values().iterator().next(); }
        public Round current(String nonce) {
            var round = current();
            return round != null && round.nonce().equals(nonce) ? round : null;
        }
        public int pendingCount() { return Math.max(0, rounds.size() - 1); }
        public long remaining(long now) {
            var round = current(); return round == null ? 0 : round.remaining(pausedAt == null ? now : pausedAt);
        }
        public boolean beginRecognition(String nonce, long now) {
            var round = current(nonce);
            if (pausedAt != null || round == null || round.expired(now) || !recognitionNonce.isEmpty()) return false;
            recognitionNonce = nonce; recognitionUntil = now + RECOGNITION_GRACE_MILLIS; pause(now); return true;
        }
        public boolean finishRecognition(String nonce, long now) {
            if (recognitionNonce.isEmpty() || !recognitionNonce.equals(nonce)) return false;
            long resumeAt = Math.min(now, recognitionUntil);
            cancelRecognition(); resume(resumeAt); return true;
        }
        public void cancelRecognition() { recognitionNonce = ""; recognitionUntil = 0; }
        public Round expireRecognition(long now) {
            if (recognitionNonce.isEmpty() || now < recognitionUntil) return null;
            var round = current(recognitionNonce); long resumeAt = recognitionUntil;
            cancelRecognition(); resume(resumeAt); return round;
        }
        public String challengeLanguage(List<String> available, java.util.function.IntUnaryOperator choose) {
            if (!language.equals(BuiltInLanguages.RANDOM)) return language;
            var round = current();
            if (round == null) return "en";
            if (!randomNonce.equals(round.nonce())) {
                randomLanguage = available.isEmpty() ? "en" : available.get(choose.applyAsInt(available.size()));
                randomNonce = round.nonce();
            }
            return randomLanguage;
        }
        public boolean unlockCurrent(String nonce, long now) {
            if (pausedAt != null) return false;
            var round = current(nonce);
            if (round == null || round.expired(now)) return false;
            progress.grant(player, round.item()); rounds.remove(round.item()); startNext(now);
            return true;
        }
        public Round skipUnlocked(long now) {
            var previous = current();
            if (previous != null && unlocked.contains(previous.item())) finishRecognition(previous.nonce(), now);
            rounds.values().removeIf(round -> unlocked.contains(round.item()));
            if (previous != null && unlocked.contains(previous.item())) {
                startNext(now); return previous;
            }
            return null;
        }
        public Round expireCurrent(long now) {
            if (pausedAt != null) return null;
            var round = current();
            if (round == null || !round.expired(now)) return null;
            rounds.remove(round.item()); startNext(now);
            return round;
        }
        private void startNext(long now) {
            var round = current();
            if (round != null && pausedAt == null) rounds.put(round.item(), round.start(now));
        }
        public void pause(long now) { if (pausedAt == null) pausedAt = now; }
        public void resume(long now) {
            if (pausedAt == null || !recognitionNonce.isEmpty()) return;
            var round = current();
            if (round != null && round.active()) rounds.put(round.item(), new Round(round.nonce(), round.item(),
                round.durationMillis(), round.deadline() + Math.max(0, now-pausedAt), true));
            pausedAt = null; startNext(now);
        }
        public void selectLanguage(String selected) {
            if (language.equals(selected)) return;
            language = selected;
            // A result for the previous language cannot answer the new wording. Deadlines stay fixed.
            rounds.replaceAll((item, round) -> new Round(UUID.randomUUID().toString(), item,
                round.durationMillis(), round.deadline(), round.active()));
        }
    }
    private final UnlockProgress progress;
    private final Map<UUID, PlayerRun> players = new HashMap<>();
    public RunState() { this(new UnlockProgress()); }
    public RunState(UnlockProgress progress) { this.progress = Objects.requireNonNull(progress); }
    public PlayerRun player(UUID id) { return players.computeIfAbsent(id, key -> new PlayerRun(key, progress)); }
}
