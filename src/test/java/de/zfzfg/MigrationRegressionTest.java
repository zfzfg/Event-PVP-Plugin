package de.zfzfg;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MigrationRegressionTest {

    @Test
    void keinBungeeCordChatImQuelltext() throws Exception {
        List<Path> treffer = Files.walk(Path.of("src/main/java"))
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> {
                    try { return Files.readString(p).contains("net.md_5"); }
                    catch (IOException e) { return false; }
                }).toList();
        assertTrue(treffer.isEmpty(), "BungeeCord-Chat wieder eingefuehrt in: " + treffer);
    }

    @Test
    void keinSpigotAufruf() throws Exception {
        List<Path> treffer = Files.walk(Path.of("src/main/java"))
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> {
                    try { return Files.readString(p).contains("spigot().sendMessage"); }
                    catch (IOException e) { return false; }
                }).toList();
        assertTrue(treffer.isEmpty(), "spigot().sendMessage wieder eingefuehrt in: " + treffer);
    }

    @Test
    void pluginYmlApiVersion() throws Exception {
        String content = Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertTrue(content.contains("26.2"), "plugin.yml zielt nicht auf 26.2");
    }

    @Test
    void pomOhneBungeeCord() throws Exception {
        String content = Files.readString(Path.of("pom.xml"));
        assertFalse(content.contains("bungeecord-chat"), "pom.xml enthaelt noch bungeecord-chat");
    }

    @Test
    void pomMitPurpurApi() throws Exception {
        String content = Files.readString(Path.of("pom.xml"));
        assertTrue(content.contains("purpur-api"), "pom.xml enthaelt nicht purpur-api");
    }
}
