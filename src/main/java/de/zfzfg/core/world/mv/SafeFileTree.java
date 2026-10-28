package de.zfzfg.core.world.mv;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** Reject links/reparse points before removing any file; never walk a linked directory. */
final class SafeFileTree {
    private SafeFileTree() {}
    static void validate(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Path absolute = root.toAbsolutePath().normalize();
        Files.walkFileTree(absolute, new SimpleFileVisitor<>() {
            private void check(Path p, BasicFileAttributes attrs) throws IOException {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("File traversal interrupted");
                if (attrs.isSymbolicLink() || attrs.isOther() || !p.toRealPath().equals(p.toAbsolutePath().normalize()))
                    throw new IOException("Unsafe linked path: " + p);
            }
            public FileVisitResult preVisitDirectory(Path p, BasicFileAttributes a) throws IOException { check(p,a); return FileVisitResult.CONTINUE; }
            public FileVisitResult visitFile(Path p, BasicFileAttributes a) throws IOException { check(p,a); return FileVisitResult.CONTINUE; }
        });
    }
    static void delete(Path root) throws IOException {
        validate(root);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            public FileVisitResult visitFile(Path p, BasicFileAttributes a) throws IOException {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Deletion interrupted");
                if (a.isSymbolicLink() || a.isOther()) throw new IOException("Linked path changed during deletion");
                Files.delete(p); return FileVisitResult.CONTINUE;
            }
            public FileVisitResult postVisitDirectory(Path p, IOException error) throws IOException {
                if (error != null) throw error;
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Deletion interrupted");
                Files.delete(p); return FileVisitResult.CONTINUE;
            }
        });
    }
}
