package de.zfzfg.core.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Never truncate the last confirmed copy when a write or replacement fails. */
public final class AtomicFiles {
    private static final java.util.concurrent.atomic.LongAdder writes = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder failures = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.AtomicLong lastNanos = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong maxNanos = new java.util.concurrent.atomic.AtomicLong();
    private AtomicFiles() {}
    public static java.util.Map<String, Object> metrics() {
        return java.util.Map.of("writes", writes.sum(), "failedWrites", failures.sum(),
                "lastWriteMillis", lastNanos.get() / 1_000_000.0, "maxWriteMillis", maxNanos.get() / 1_000_000.0);
    }

    public static void save(YamlConfiguration config, Path target) throws IOException {
        write(target, config.saveToString());
    }

    public static void write(Path target, String contents) throws IOException {
        long started = System.nanoTime();
        try { writeConfirmed(target, contents); writes.increment(); }
        catch (IOException ex) { failures.increment(); throw ex; }
        finally { long elapsed = System.nanoTime() - started; lastNanos.set(elapsed); maxNanos.accumulateAndGet(elapsed, Math::max); }
    }
    private static void writeConfirmed(Path target, String contents) throws IOException {
        target = target.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(contents);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
