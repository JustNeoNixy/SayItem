package dev.sayitem.speech;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.function.Consumer;

/** Optional local speech data downloads; audio is never sent through this client. */
public final class AssetDownloads {
    private AssetDownloads() {}
    public static Path ensure(Path target, URI url, String hash, long bytes, Path supplied,
                              Consumer<String> progress, String label) throws IOException, InterruptedException {
        if (Files.isRegularFile(target) && Files.size(target) == bytes && BundledFiles.sha256(target).equals(hash)) return target;
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), "download-", ".part");
        try {
            progress.accept("Preparing " + label + "…");
            if (supplied != null) Files.copy(supplied, temp, StandardCopyOption.REPLACE_EXISTING);
            else {
                var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build();
                var request = HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(10)).header("User-Agent", "SayItem/0.4").GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (var input = response.body(); var output = Files.newOutputStream(temp)) {
                    if (response.statusCode() != 200) throw new IOException("Download returned HTTP " + response.statusCode());
                    byte[] buffer = new byte[65536]; long total = 0; int last = -1, count;
                    while ((count = input.read(buffer)) >= 0) {
                        total += count;
                        if (total > bytes) throw new IOException("Download exceeded expected size");
                        output.write(buffer, 0, count);
                        int percent = (int) (total * 100 / Math.max(1, bytes));
                        if (percent != last) { progress.accept("Downloading " + label + " · " + percent + "%"); last = percent; }
                    }
                }
            }
            if (Files.size(temp) != bytes || !BundledFiles.sha256(temp).equals(hash)) throw new IOException(label + " checksum mismatch");
            try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            return target;
        } finally { Files.deleteIfExists(temp); }
    }
}
