package dev.sayitem.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** World-owned progress. Shared grants are retained when sharing is turned off. */
public final class UnlockProgress {
    private int schemaVersion = 1;
    private boolean sharing;
    private Map<String, Set<String>> players = new TreeMap<>();
    private Set<String> shared = new TreeSet<>();
    private transient boolean dirty;

    public static UnlockProgress load(Path file) throws IOException {
        if (!Files.exists(file)) return new UnlockProgress();
        try (var reader = Files.newBufferedReader(file)) {
            var value = JsonFiles.GSON.fromJson(reader, UnlockProgress.class);
            if (value == null || value.schemaVersion != 1 || value.players == null || value.shared == null)
                throw new IllegalArgumentException("Invalid unlock progress schema");
            var normalized = new TreeMap<String, Set<String>>();
            for (var entry : value.players.entrySet()) {
                String id = UUID.fromString(entry.getKey()).toString();
                if (!id.equalsIgnoreCase(entry.getKey()) || normalized.containsKey(id))
                    throw new IllegalArgumentException("Invalid player UUID");
                normalized.put(id, checked(entry.getValue()));
            }
            value.players = normalized; value.shared = checked(value.shared);
            if (value.sharing) value.synchronizeShared();
            return value;
        } catch (RuntimeException error) { throw new IOException("Invalid SayItem unlock progress: " + file, error); }
    }
    private static Set<String> checked(Set<String> items) {
        if (items == null || items.stream().anyMatch(item -> item == null || !item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))
            throw new IllegalArgumentException("Invalid unlocked item ID");
        return new TreeSet<>(items);
    }
    Set<String> player(UUID id) {
        var existing = players.get(id.toString());
        if (existing == null) { existing = new TreeSet<>(); players.put(id.toString(), existing); dirty = true; }
        if (sharing && existing.addAll(shared)) dirty = true;
        return existing;
    }
    public Set<String> unlocked(UUID id) { return Collections.unmodifiableSet(player(id)); }
    public boolean sharing() { return sharing; }
    public boolean dirty() { return dirty; }
    public boolean grant(UUID id, String item) {
        if (item == null || !item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid item ID");
        boolean changed = player(id).add(item);
        if (sharing) {
            changed |= shared.add(item);
            for (var items : players.values()) changed |= items.add(item);
        }
        dirty |= changed;
        return changed;
    }
    public boolean setSharing(boolean enabled) {
        if (sharing == enabled) return false;
        sharing = enabled;
        if (enabled) synchronizeShared();
        dirty = true;
        return true;
    }
    private void synchronizeShared() {
        for (var items : players.values()) dirty |= shared.addAll(items);
        for (var items : players.values()) dirty |= items.addAll(shared);
    }
    public int count(UUID id, Set<String> registeredItems) {
        return (int) player(id).stream().filter(registeredItems::contains).count();
    }
    public void save(Path file) throws IOException { JsonFiles.write(file, this); dirty = false; }
}
