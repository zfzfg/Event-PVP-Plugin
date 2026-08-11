package de.zfzfg.core.web;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Uebernimmt die Item-Texturen des Server-Resourcepacks ins Web-Panel.
 *
 * <p>Zeigt der Server ein Resourcepack an, sehen die Spieler dessen Schwerter und Ruestungen -
 * das Panel zeigte bisher trotzdem die Vanilla-Icons. Wer ein Set zusammenstellt, arbeitet
 * dann mit anderen Bildern als seine Spieler. Dieser Dienst schliesst die Luecke.</p>
 *
 * <h2>Bewusst enger Zuschnitt</h2>
 * <p>Uebernommen werden ausschliesslich direkte Ersetzungen unter
 * {@code assets/minecraft/textures/item/&lt;name&gt;.png}, deren Dateiname sich eins zu eins auf
 * ein {@link Material} abbilden laesst. Damit greifen Packs fuer Schwerter, Ruestung,
 * Werkzeuge und Nahrung - also der ueberwiegende Teil dessen, was ein Equipment-Set enthaelt.</p>
 *
 * <p><strong>Nicht</strong> ausgewertet werden CustomModelData-Ersetzungen aus
 * {@code assets/minecraft/models/item/*.json} (Praedikat-Aufloesung, mehrlagige Modelle,
 * Animations-{@code .mcmeta}) und die getragenen Ruestungstexturen unter
 * {@code textures/models/armor/} - letztere sind nicht das Inventar-Icon. Diese Faelle sicher
 * zu unterstuetzen waere ein eigener Modell-Interpreter; die Funktion soll stabil bleiben und
 * lieber weniger koennen.</p>
 *
 * <h2>Ablauf</h2>
 * <ol>
 *   <li>{@code server.properties} lesen; ohne {@code resource-pack} passiert nichts.</li>
 *   <li>Ist der SHA-1 unveraendert, bleibt der vorhandene Bestand stehen (kein Download).</li>
 *   <li>Sonst asynchron laden (Groessenlimit, Zeitlimit), entpacken, auf 64 px skalieren
 *       und nach {@code plugins/&lt;Plugin&gt;/texture-overrides/&lt;MATERIAL&gt;.png} schreiben.</li>
 * </ol>
 *
 * <p>Jeder Fehler fuehrt zu einer Logzeile und dem Rueckfall auf die mitgelieferten Icons -
 * der Serverstart wird nie blockiert und nie abgebrochen.</p>
 */
public final class ResourcePackTextureService {

    /** Ordner im Plugin-Datenverzeichnis, in dem die uebernommenen Texturen liegen. */
    private static final String OVERRIDE_DIR = "texture-overrides";

    /** Kantenlaenge der erzeugten Icons - identisch zu den mitgelieferten Assets. */
    private static final int ICON_SIZE = 64;

    /** Praefix der Zip-Eintraege, die uebernommen werden. */
    private static final String ITEM_TEXTURE_PREFIX = "assets/minecraft/textures/item/";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    /**
     * Zulaessige Dateinamen im Override-Ordner.
     *
     * <p>Die Dateien entstehen aus {@code Material.name()} und bestehen deshalb nur aus
     * Grossbuchstaben, Ziffern und Unterstrichen. Der Web-Handler prueft eingehende Namen
     * gegen genau dieses Muster, statt sich auf eine {@code ..}-Suche zu verlassen: die
     * Positivliste laesst kodierte Varianten, Backslashes und Unterverzeichnisse von
     * vornherein nicht durch.</p>
     */
    private static final java.util.regex.Pattern OVERRIDE_FILE_NAME =
            java.util.regex.Pattern.compile("[A-Z0-9_]{1,64}\\.png");

    /**
     * Prueft einen aus dem Web angefragten Dateinamen.
     *
     * <p>Sicherheitsrelevant: die Dateien liegen im Plugin-Datenordner, also ausserhalb des
     * JARs. Ein durchgelassener Pfadanteil wuerde beliebige Dateien des Servers lesbar
     * machen.</p>
     */
    public static boolean isValidOverrideFileName(String fileName) {
        return fileName != null && OVERRIDE_FILE_NAME.matcher(fileName).matches();
    }

    private final JavaPlugin plugin;
    private final File overrideDir;

    /** Materialnamen mit uebernommener Textur. Wird nur im Ganzen ersetzt, nie veraendert. */
    private volatile Set<String> available = Collections.emptySet();

    public ResourcePackTextureService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.overrideDir = new File(plugin.getDataFolder(), OVERRIDE_DIR);
    }

    /** Materialnamen, fuer die eine Resourcepack-Textur vorliegt. */
    public Set<String> getAvailableMaterials() {
        return available;
    }

    public File getOverrideDirectory() {
        return overrideDir;
    }

    /**
     * Startet die Uebernahme, falls eingeschaltet und ein Pack gesetzt ist.
     *
     * <p>Kehrt sofort zurueck; Download und Entpacken laufen auf einem Nebenthread.</p>
     */
    public void refreshAsync(boolean enabled, int maxSizeMb) {
        // Bereits vorhandene Texturen sofort bekannt machen, damit das Panel sie auch dann
        // nutzt, wenn der Pack-Server gerade nicht erreichbar ist.
        available = scanExisting();

        if (!enabled) {
            return;
        }

        String packUrl = readServerProperty("resource-pack");
        if (packUrl == null || packUrl.trim().isEmpty()) {
            plugin.getLogger().info("[Textures] No resource-pack configured in server.properties, using bundled icons");  // i18n-ignore: technical texture log
            return;
        }
        String packSha1 = readServerProperty("resource-pack-sha1");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                importPack(packUrl.trim(), packSha1, Math.max(1, maxSizeMb));
            } catch (Throwable t) {
                // Absichtlich Throwable: eine kaputte Zip-Datei darf den Server nicht stoeren.
                plugin.getLogger().log(Level.WARNING, "[Textures] Resource pack import failed, using bundled icons", t);  // i18n-ignore: technical texture log
            }
        });
    }

    // ============ Import ============

    private void importPack(String url, String sha1, int maxSizeMb) throws IOException {
        File stampFile = new File(overrideDir, ".pack-sha1");
        String stamp = sha1 == null || sha1.trim().isEmpty() ? url : sha1.trim();

        if (overrideDir.isDirectory() && stampFile.isFile()) {
            String previous = new String(Files.readAllBytes(stampFile.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (stamp.equals(previous)) {
                plugin.getLogger().info("[Textures] Resource pack unchanged, keeping " + available.size() + " cached textures");  // i18n-ignore: technical texture log
                return;
            }
        }

        File archive = File.createTempFile("respack", ".zip");
        try {
            long maxBytes = (long) maxSizeMb * 1024L * 1024L;
            download(url, archive, maxBytes);
            Set<String> imported = extract(archive);

            available = Collections.unmodifiableSet(imported);
            Files.write(stampFile.toPath(), stamp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            plugin.getLogger().info("[Textures] Imported " + imported.size() + " item textures from the server resource pack");  // i18n-ignore: technical texture log
        } finally {
            // Nur ein Zwischenspeicher - beim Loeschen darf nichts schiefgehen.
            if (!archive.delete()) {
                archive.deleteOnExit();
            }
        }
    }

    private void download(String url, File target, long maxBytes) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "EventPVP-Plugin");  // i18n-ignore: HTTP header value
        try {
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IOException("Resource pack URL returned HTTP " + code);
            }

            // Die angekuendigte Laenge zuerst pruefen, damit ein zu grosses Pack gar nicht
            // erst geladen wird; die Zaehlung unten faengt fehlende/falsche Angaben ab.
            long announced = connection.getContentLengthLong();
            if (announced > maxBytes) {
                throw new IOException("Resource pack is larger than the configured limit ("
                        + announced + " > " + maxBytes + " bytes)");
            }

            long written = 0;
            byte[] buffer = new byte[8192];
            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(target.toPath())) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    written += read;
                    if (written > maxBytes) {
                        throw new IOException("Resource pack exceeds the configured size limit of " + maxBytes + " bytes");
                    }
                    out.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Entpackt die Item-Texturen und schreibt sie skaliert in den Override-Ordner.
     *
     * @return Materialnamen, fuer die eine Textur geschrieben wurde
     */
    private Set<String> extract(File archive) throws IOException {
        if (!overrideDir.isDirectory() && !overrideDir.mkdirs()) {
            throw new IOException("Cannot create texture override directory: " + overrideDir);
        }
        // Bestand aus einem frueheren Pack entfernen, sonst bleiben Texturen stehen, die
        // das neue Pack gar nicht mehr ersetzt.
        clearOverrideDirectory();

        Path base = overrideDir.toPath().toRealPath();
        Set<String> imported = new HashSet<>();

        try (ZipFile zip = new ZipFile(archive)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (!name.startsWith(ITEM_TEXTURE_PREFIX) || !name.endsWith(".png")) {
                    continue;
                }
                // Nur die oberste Ebene: Unterordner enthalten Animationsphasen und Varianten,
                // die keinem einzelnen Material entsprechen.
                String fileName = name.substring(ITEM_TEXTURE_PREFIX.length());
                if (fileName.contains("/")) {
                    continue;
                }

                String materialName = fileName.substring(0, fileName.length() - 4).toUpperCase(Locale.ROOT);
                Material material = Material.matchMaterial(materialName);
                if (material == null || material.isLegacy()) {
                    // Texturname ohne passendes Material (z.B. "bow_pulling_0") - ueberspringen.
                    continue;
                }

                Path target = base.resolve(material.name() + ".png").normalize();
                // Zip-Slip-Schutz: der Zielpfad muss im Override-Ordner bleiben. Der Dateiname
                // stammt zwar aus dem Material-Enum und ist damit harmlos, die Pruefung bleibt
                // trotzdem stehen, damit eine spaetere Aenderung sie nicht versehentlich verliert.
                if (!target.startsWith(base)) {
                    continue;
                }

                try (InputStream in = zip.getInputStream(entry)) {
                    if (writeScaled(in, target)) {
                        imported.add(material.name());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[Textures] Skipped unreadable texture " + name + ": " + e.getMessage());  // i18n-ignore: technical texture log
                }
            }
        }
        return imported;
    }

    /**
     * Skaliert eine Textur auf {@link #ICON_SIZE} und schreibt sie als PNG.
     *
     * @return {@code false}, wenn das Bild nicht lesbar war
     */
    private boolean writeScaled(InputStream in, Path target) throws IOException {
        BufferedImage source = ImageIO.read(in);
        if (source == null) {
            return false;
        }
        BufferedImage scaled = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            // Pixelart: hart skalieren. Bilineares Glaetten macht 16-px-Vorlagen matschig.
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.drawImage(source, 0, 0, ICON_SIZE, ICON_SIZE, null);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(scaled, "png", target.toFile());
        return true;
    }

    private void clearOverrideDirectory() {
        File[] files = overrideDir.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.delete()) {
                plugin.getLogger().warning("[Textures] Could not delete stale texture " + file.getName());  // i18n-ignore: technical texture log
            }
        }
    }

    /** Liest den vorhandenen Bestand, ohne etwas herunterzuladen. */
    private Set<String> scanExisting() {
        File[] files = overrideDir.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null) {
            return Collections.emptySet();
        }
        Set<String> found = new HashSet<>();
        for (File file : files) {
            found.add(file.getName().substring(0, file.getName().length() - 4));
        }
        return Collections.unmodifiableSet(found);
    }

    /**
     * Liest einen Wert aus {@code server.properties}.
     *
     * <p>Die Datei liegt im Arbeitsverzeichnis des Servers, also zwei Ebenen ueber dem
     * Plugin-Datenordner ({@code plugins/<Plugin>/}).</p>
     */
    private String readServerProperty(String key) {
        File properties = new File(plugin.getDataFolder().getParentFile().getParentFile(), "server.properties");
        if (!properties.isFile()) {
            return null;
        }
        Properties loaded = new Properties();
        try (InputStream in = new FileInputStream(properties)) {
            loaded.load(in);
        } catch (IOException e) {
            plugin.getLogger().warning("[Textures] Cannot read server.properties: " + e.getMessage());  // i18n-ignore: technical texture log
            return null;
        }
        return loaded.getProperty(key);
    }
}
