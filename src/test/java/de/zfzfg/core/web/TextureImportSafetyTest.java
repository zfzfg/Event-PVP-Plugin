package de.zfzfg.core.web;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.zip.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TextureImportSafetyTest {
    @TempDir Path directory;
    @Test void oversizedImageRejectedBeforeReplacingPreviousTextures() throws Exception {
        var plugin = mock(JavaPlugin.class); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("texture-test"));
        Path overrides = Files.createDirectory(directory.resolve("texture-overrides"));
        Files.writeString(overrides.resolve("DIAMOND.png"), "previous");
        var png = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_ARGB), "png", png);
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) { zip.putNextEntry(new ZipEntry("assets/minecraft/textures/item/diamond.png")); zip.write(png.toByteArray()); zip.closeEntry(); }
        var http = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/pack.zip", exchange -> { exchange.sendResponseHeaders(200, bytes.size()); try (var out = exchange.getResponseBody()) { out.write(bytes.toByteArray()); } });
        http.start();
        try {
            var service = new ResourcePackTextureService(plugin);
            var method = ResourcePackTextureService.class.getDeclaredMethod("importPack", String.class, String.class, int.class); method.setAccessible(true);
            var error = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(service, "http://127.0.0.1:" + http.getAddress().getPort() + "/pack.zip", null, 10));
            assertInstanceOf(IOException.class, error.getCause());
            assertEquals("previous", Files.readString(overrides.resolve("DIAMOND.png")));
        } finally { http.stop(0); }
    }
}
