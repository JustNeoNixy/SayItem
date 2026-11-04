package dev.sayitem.core;

import java.io.IOException;
import java.nio.file.*;

/** Shared event difficulty. Only the world owner or a server operator can change it. */
public final class ServerRules {
    public int requiredAccuracy = AccuracyRequirement.DEFAULT;
    public static ServerRules load(Path path) throws IOException {
        ServerRules rules = new ServerRules();
        if (Files.exists(path)) try (var reader = Files.newBufferedReader(path)) {
            ServerRules stored = JsonFiles.GSON.fromJson(reader, ServerRules.class);
            if (stored != null) rules = stored;
        }
        rules.requiredAccuracy = AccuracyRequirement.clamp(rules.requiredAccuracy);
        return rules;
    }
    public void save(Path path) throws IOException { JsonFiles.write(path, this); }
}
