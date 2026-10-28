package de.zfzfg.core.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

/** Coalesces immutable snapshots per file; shutdown writes cannot be overtaken by old saves. */
public final class OrderedFileWriter {
    private record Write(String text, Logger logger) {}
    private static final Map<Path, Write> PENDING = new LinkedHashMap<>();
    private static ExecutorService worker;
    private static boolean draining;
    private OrderedFileWriter() {}
    public static synchronized void later(Path path, String text, Logger logger) {
        PENDING.put(path.toAbsolutePath().normalize(), new Write(text, logger));
        if (draining) return;
        if (worker == null || worker.isShutdown()) worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "EventPVP-StatisticsIO"); thread.setDaemon(true); return thread;
        });
        draining = true;
        worker.execute(OrderedFileWriter::drain);
    }
    private static synchronized void drain() {
        for (var entry : PENDING.entrySet()) {
            try { AtomicFiles.write(entry.getKey(), entry.getValue().text()); }
            catch (IOException ex) { entry.getValue().logger().log(java.util.logging.Level.SEVERE, "Statistics write failed: " + entry.getKey(), ex); }
        }
        PENDING.clear(); draining = false;
    }
    public static synchronized void now(Path path, String text) throws IOException {
        path = path.toAbsolutePath().normalize();
        AtomicFiles.write(path, text);
        PENDING.remove(path);
    }
    public static synchronized void flush() { drain(); }
    public static synchronized void shutdown() {
        drain();
        if (worker != null) worker.shutdown();
    }
}
