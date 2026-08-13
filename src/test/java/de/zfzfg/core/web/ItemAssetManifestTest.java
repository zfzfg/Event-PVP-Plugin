package de.zfzfg.core.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueft, dass das Icon-Manifest zum Inhalt des Asset-Ordners passt.
 *
 * <p>Beide entstehen aus {@code tools/scale-item-assets.ps1}. Laeuft das Skript nach einem
 * Asset-Update nicht, faellt das sonst erst im Browser auf: das Panel bietet Items an, deren
 * Bild 404 liefert, oder es zeigt Items gar nicht erst an, obwohl das Icon im JAR liegt.
 * Beides sind stille Fehler, die niemand beim Build bemerkt.</p>
 */
class ItemAssetManifestTest {

    private static final Path ASSETS = Paths.get("src", "main", "resources", "web", "item-assets");
    private static final Path MANIFEST = ASSETS.resolve("_index.json");

    /** Ohne erzeugte Assets ueberspringen, damit ein frischer Klon nicht rot wird. */
    static boolean assetsPresent() {
        return Files.isDirectory(ASSETS) && Files.isRegularFile(MANIFEST);
    }

    /**
     * Liest die Namensliste aus dem Manifest.
     *
     * <p>Bewusst mit einem regulaeren Ausdruck statt einer JSON-Bibliothek: die Testabhaengigkeiten
     * sind JUnit und Mockito, und das Manifest hat ein festes, flaches Format.</p>
     */
    private static Set<String> manifestNames() throws IOException {
        String content = new String(Files.readAllBytes(MANIFEST), StandardCharsets.UTF_8);
        int start = content.indexOf("\"items\"");
        assertTrue(start >= 0, "das Manifest muss ein items-Feld haben");

        Set<String> names = new HashSet<>();
        Matcher matcher = Pattern.compile("\"([A-Z0-9_]+)\"").matcher(content.substring(start));
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static Set<String> fileNames() throws IOException {
        try (Stream<Path> files = Files.list(ASSETS)) {
            return files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".png"))
                    .map(name -> name.substring(0, name.length() - 4))
                    .collect(Collectors.toSet());
        }
    }

    @Test
    @EnabledIf("assetsPresent")
    void everyManifestEntryHasAnIconFile() throws IOException {
        Set<String> missing = new HashSet<>(manifestNames());
        missing.removeAll(fileNames());

        assertTrue(missing.isEmpty(),
                "Das Manifest nennt Items ohne Bilddatei - das Panel wuerde sie anbieten und "
                        + "einen 404 laden. Fehlend: " + sample(missing));
    }

    @Test
    @EnabledIf("assetsPresent")
    void everyIconFileIsListedInTheManifest() throws IOException {
        Set<String> unlisted = new HashSet<>(fileNames());
        unlisted.removeAll(manifestNames());

        assertTrue(unlisted.isEmpty(),
                "Es liegen Bilddateien im JAR, die das Manifest nicht kennt - im Notbetrieb "
                        + "ohne /api/materials fehlen sie in der Auswahl. Nicht gelistet: " + sample(unlisted));
    }

    @Test
    @EnabledIf("assetsPresent")
    void manifestRecordsTheIconSize() throws IOException {
        String content = new String(Files.readAllBytes(MANIFEST), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"size\""), "die Kantenlaenge gehoert ins Manifest");
        assertTrue(content.contains("\"count\""), "die Anzahl gehoert ins Manifest");
    }

    @Test
    @EnabledIf("assetsPresent")
    void iconNamesLookLikeMaterialNames() throws IOException {
        // Die Icon-URL wird aus dem Material-Enum gebaut. Ein Dateiname, der davon abweicht,
        // waere ueber /item-assets/<NAME>.png nie erreichbar.
        List<String> odd = fileNames().stream()
                .filter(name -> !name.matches("[A-Z0-9_]+"))
                .sorted()
                .collect(Collectors.toList());

        assertTrue(odd.isEmpty(), "Dateinamen ausserhalb des Material-Schemas: " + odd);
    }

    /** Erste Treffer einer Menge, damit eine Fehlermeldung lesbar bleibt. */
    private static String sample(Set<String> names) {
        List<String> sorted = names.stream().sorted().limit(10).collect(Collectors.toList());
        return names.size() > 10 ? sorted + " (und " + (names.size() - 10) + " weitere)" : sorted.toString();
    }
}
