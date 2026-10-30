package dev.sayitem.speech;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** Extracts bundled data atomically. Never downloads anything at runtime. */
public final class BundledFiles {
    private BundledFiles() {}
    public static Path extract(Class<?> owner, String resource, Path destination, String expectedSha) throws IOException {
        if (Files.isRegularFile(destination) && (expectedSha == null || sha256(destination).equals(expectedSha))) return destination;
        Files.createDirectories(destination.getParent());
        Path temp = Files.createTempFile(destination.getParent(), "sayitem-", ".part");
        try {
            try (InputStream input = owner.getResourceAsStream(resource)) {
                if (input == null) throw new IOException("Missing bundled speech resource: " + resource);
                Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (expectedSha != null && !sha256(temp).equals(expectedSha)) throw new IOException("Bundled speech model is damaged. Reinstall SayItem.");
            try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING); }
            return destination;
        } finally { Files.deleteIfExists(temp); }
    }
    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
