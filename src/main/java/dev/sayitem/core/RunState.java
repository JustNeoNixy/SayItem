package dev.sayitem.core;

import java.util.*;

/** In-memory run state. A reconnect keeps unlocks; restarting the server/world resets them. */
public final class RunState {
    public record Round(String nonce, String item, long durationMillis, long deadline, boolean active) {
        public long remaining(long now) { return active ? Math.max(0, deadline - now) : durationMillis; }
        public boolean expired(long now) { return active && now >= deadline; }
        private Round start(long now) {
            return active ? this : new Round(nonce, item, durationMillis, now + durationMillis, true);
        }
    }
    public static final class PlayerRun {
        public String language = "ja";
        public final Set<String> unlocked = new HashSet<>();
        private final Map<String, Round> rounds = new LinkedHashMap<>();
        public Round collect(String item, long now) {
            return collect(item, now, TimerLimit.DEFAULT);
        }
        public Round collect(String item, long now, int timerSeconds) {
            long duration = TimerLimit.millis(timerSeconds);
            if (unlocked.contains(item)) return null;
            var existing = rounds.get(item);
            if (existing != null) return existing;
            var round = new Round(UUID.randomUUID().toString(), item, duration, 0, false);
            if (rounds.isEmpty()) round = round.start(now);
            rounds.put(item, round);
            return round;
        }
        public Round current() { return rounds.isEmpty() ? null : rounds.values().iterator().next(); }
        public Round current(String nonce) {
            var round = current();
            return round != null && round.nonce().equals(nonce) ? round : null;
        }
        public int pendingCount() { return Math.max(0, rounds.size() - 1); }
        public boolean unlockCurrent(String nonce, long now) {
            var round = current(nonce);
            if (round == null || round.expired(now)) return false;
            unlocked.add(round.item()); rounds.remove(round.item()); startNext(now);
            return true;
        }
        public Round expireCurrent(long now) {
            var round = current();
            if (round == null || !round.expired(now)) return null;
            rounds.remove(round.item()); startNext(now);
            return round;
        }
        private void startNext(long now) {
            var round = current();
            if (round != null) rounds.put(round.item(), round.start(now));
        }
        public void selectLanguage(String selected) {
            if (language.equals(selected)) return;
            language = selected;
            // A result for the previous language cannot answer the new wording. Deadlines stay fixed.
            rounds.replaceAll((item, round) -> new Round(UUID.randomUUID().toString(), item,
                round.durationMillis(), round.deadline(), round.active()));
        }
    }
    private final Map<UUID, PlayerRun> players = new HashMap<>();
    public PlayerRun player(UUID id) { return players.computeIfAbsent(id, key -> new PlayerRun()); }
}
