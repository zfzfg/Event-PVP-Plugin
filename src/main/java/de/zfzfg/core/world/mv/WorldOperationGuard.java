package de.zfzfg.core.world.mv;

import java.nio.file.Path;
import java.util.*;

/** Reserves canonical paths, including ancestor/descendant conflicts. */
final class WorldOperationGuard {
    private final Map<String, Path> reservations = new HashMap<>();
    synchronized boolean acquire(String owner, Path path) {
        path = path.toAbsolutePath().normalize();
        for (var entry : reservations.entrySet()) {
            if (!entry.getKey().equals(owner) && (path.startsWith(entry.getValue()) || entry.getValue().startsWith(path))) return false;
        }
        reservations.put(owner, path);
        return true;
    }
    synchronized void release(String owner) { reservations.remove(owner); }
}
