package dev.sayitem.core;

import java.util.*;

/** In-memory run state. A reconnect keeps unlocks; restarting the server/world resets them. */
public final class RunState {
    public static final long TIME_LIMIT_MS = 15_000;
    public record Round(String nonce, String item, long deadline) {
        public long remaining(long now) { return Math.max(0, deadline - now); }
        public boolean expired(long now) { return now >= deadline; }
    }
    public static final class PlayerRun {
        public String language = "ja";
        public final Set<String> unlocked = new HashSet<>();
        public final Map<String, Round> rounds = new LinkedHashMap<>();
        public Round collect(String item, long now) {
            if (unlocked.contains(item)) return null;
            return rounds.computeIfAbsent(item, key -> new Round(UUID.randomUUID().toString(), key, now + TIME_LIMIT_MS));
        }
        public Round current() { return rounds.values().stream().findFirst().orElse(null); }
        public void unlock(String item) { unlocked.add(item); rounds.remove(item); }
    }
    private final Map<UUID, PlayerRun> players = new HashMap<>();
    public PlayerRun player(UUID id) { return players.computeIfAbsent(id, key -> new PlayerRun()); }
}
