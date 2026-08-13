package de.zfzfg.core.monitoring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PerformanceMonitor {
    private final Map<String, Long> timings = new ConcurrentHashMap<>();
    private final Logger logger;
    private final long warnThresholdMs;

    public PerformanceMonitor(Logger logger) {
        this(logger, 50L);
    }

    public PerformanceMonitor(Logger logger, long warnThresholdMs) {
        this.logger = logger;
        this.warnThresholdMs = warnThresholdMs;
    }

    public void startTiming(String operation) {
        timings.put(operation, System.nanoTime());
    }

    public void endTiming(String operation) {
        Long start = timings.remove(operation);
        if (start != null) {
            long duration = (System.nanoTime() - start) / 1_000_000; // ms
            if (duration > warnThresholdMs) {
                logger.warning(operation + " took " + duration + "ms");
            }
        }
    }
}