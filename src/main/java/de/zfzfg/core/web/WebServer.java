package de.zfzfg.core.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Eingebetteter HTTP-Server für das Web-Interface
 * Serves statische Dateien und REST API mit Token-Authentifizierung
 */
public class WebServer {
    
    private final EventPlugin plugin;
    private final WebConfigManager configManager;
    private final WebApiHandler apiHandler;
    private final WebAuthManager authManager;
    private final int port;
    private HttpServer httpServer;
    private final Gson gson;
    private final boolean authEnabled;
    /** Rate-Limit: max. Requests je IP innerhalb eines Zeitfensters. */
    private static final int RATE_LIMIT_MAX_REQUESTS = 100;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;

    /** Zaehlerstand einer IP im laufenden Zeitfenster. */
    private static final class RateWindow {
        long windowStart;
        int count;
    }

    private final Map<String, RateWindow> rateLimitCounters = new ConcurrentHashMap<>();

    /** Optional: Item-Texturen aus dem Server-Resourcepack. */
    private final ResourcePackTextureService textureService;

    public WebServer(EventPlugin plugin, WebConfigManager configManager, WebAuthManager authManager, int port, boolean authEnabled) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.apiHandler = new WebApiHandler(plugin, configManager);
        this.authManager = authManager;
        this.port = port;
        this.authEnabled = authEnabled;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.textureService = new ResourcePackTextureService(plugin);
    }
    
    // Konstruktor für Rückwärtskompatibilität
    public WebServer(EventPlugin plugin, WebConfigManager configManager, int port) {
        this(plugin, configManager, null, port, false);
    }

    /**
     * Startet den HTTP-Server
     */
    public void start() {
        try {
            String bindAddress = configManager.getBindAddress();
            java.net.InetAddress address = bindAddress.isEmpty() ? null : java.net.InetAddress.getByName(bindAddress);
            httpServer = HttpServer.create(new InetSocketAddress(address, port), 0);
            
            // Texturen aus dem Resourcepack im Hintergrund uebernehmen. Laeuft asynchron und
            // haelt den Start nicht auf; schlaegt es fehl, bleiben die mitgelieferten Icons.
            textureService.refreshAsync(
                    configManager.isResourcePackTexturesEnabled(),
                    configManager.getResourcePackMaxSizeMb());

            // Statische Dateien (Login-Seite braucht keine Auth)
            httpServer.createContext("/", new StaticFileHandler(plugin, textureService.getOverrideDirectory()));
            
            // Auth Endpoints (brauchen keine Session)
            httpServer.createContext("/api/auth/login", this::handleLoginRequest);
            httpServer.createContext("/api/auth/logout", this::handleLogoutRequest);
            httpServer.createContext("/api/auth/check", this::handleAuthCheckRequest);
            httpServer.createContext("/api/auth/validate", this::handleAuthCheckRequest); // Alias für Frontend
            
            // Geschützte API Endpoints
            httpServer.createContext("/api/config/get", exchange -> handleProtectedApiRequest(exchange, 
                () -> apiHandler.getConfig()));
            httpServer.createContext("/api/config/save", exchange -> handleProtectedApiPostRequest(exchange, 
                body -> apiHandler.saveConfig(parseJson(body))));
            
            httpServer.createContext("/api/worlds/get", exchange -> handleProtectedApiRequest(exchange, 
                () -> apiHandler.getWorlds()));
            httpServer.createContext("/api/worlds/save", exchange -> handleProtectedApiPostRequest(exchange, 
                body -> apiHandler.saveWorlds(parseJson(body))));
            
            // Multiverse-Weltverwaltung: Liste lesen, Welt erstellen, laden/entladen/loeschen,
            // Job-Status pollen (create/delete laufen zu lange fuer einen HTTP-Request).
            httpServer.createContext("/api/mvworlds/list", exchange -> handleProtectedApiRequest(exchange,
                () -> apiHandler.getMvWorlds()));
            httpServer.createContext("/api/mvworlds/create", exchange -> handleProtectedApiPostRequest(exchange,
                body -> apiHandler.createMvWorld(parseJson(body))));
            httpServer.createContext("/api/mvworlds/action", exchange -> handleProtectedApiPostRequest(exchange,
                body -> apiHandler.mvWorldAction(parseJson(body))));
            httpServer.createContext("/api/mvworlds/job", exchange -> handleProtectedApiQueryRequest(exchange,
                query -> apiHandler.getMvJob(query)));
            httpServer.createContext("/api/mvworlds/backups", exchange -> handleProtectedApiRequest(exchange,
                () -> apiHandler.getMvBackups()));
            httpServer.createContext("/api/mvworlds/backup-action", exchange -> handleProtectedApiPostRequest(exchange,
                body -> apiHandler.mvBackupAction(parseJson(body))));

            // Inventar-Verwaltung: Betriebsart umschalten, Backups einsehen und
            // wiederherstellen, offene Sitzungen des Guard-Journals ueberwachen.
            httpServer.createContext("/api/inventories/status", exchange -> handleProtectedApiRequest(exchange,
                () -> apiHandler.getInventoryStatus()));
            httpServer.createContext("/api/inventories/provider", exchange -> handleProtectedApiPostRequest(exchange,
                body -> apiHandler.setInventoryProvider(parseJson(body))));
            httpServer.createContext("/api/inventories/list", exchange -> handleProtectedApiQueryRequest(exchange,
                query -> apiHandler.listInventories(query)));
            httpServer.createContext("/api/inventories/get", exchange -> handleProtectedApiQueryRequest(exchange,
                query -> apiHandler.getInventory(query)));
            httpServer.createContext("/api/inventories/restore", exchange -> handleProtectedApiPostRequest(exchange,
                body -> apiHandler.restoreInventory(parseJson(body))));
            httpServer.createContext("/api/inventories/delete", exchange -> handleProtectedApiPostRequest(exchange,
                body -> apiHandler.deleteInventory(parseJson(body))));
            httpServer.createContext("/api/inventories/guard", exchange -> handleProtectedApiRequest(exchange,
                () -> apiHandler.getInventoryGuard()));

            httpServer.createContext("/api/equipment/get", exchange -> handleProtectedApiRequest(exchange,
                () -> apiHandler.getEquipment()));
            httpServer.createContext("/api/equipment/save", exchange -> handleProtectedApiPostRequest(exchange, 
                body -> apiHandler.saveEquipment(parseJson(body))));
            
            // Item-Katalog des laufenden Servers: speist Item-Auswahl, Verzauberungslisten
            // und Stapelgroessen im Panel, damit dort nichts angeboten wird, was der Server
            // gar nicht kennt.
            httpServer.createContext("/api/materials", exchange -> handleProtectedApiRequest(exchange,
                () -> apiHandler.getMaterials()));

            // Welche Items eine Textur aus dem Server-Resourcepack haben. Antwortet auch bei
            // abgeschalteter Funktion - dann eben mit einer leeren Liste.
            httpServer.createContext("/api/textures/overrides", exchange -> handleProtectedApiRequest(exchange,
                () -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("materials", new java.util.ArrayList<>(textureService.getAvailableMaterials()));
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("data", data);
                    return response;
                }));

            httpServer.createContext("/api/webconfig/get", exchange -> handleProtectedApiRequest(exchange,
                () -> apiHandler.getWebConfig()));
            httpServer.createContext("/api/webconfig/save", exchange -> handleProtectedApiPostRequest(exchange, 
                body -> apiHandler.saveWebConfig(parseJson(body))));
            
            httpServer.createContext("/api/reload", exchange -> handleProtectedApiPostRequest(exchange, 
                body -> apiHandler.reload()));
            
            httpServer.createContext("/api/status", exchange -> handleProtectedApiRequest(exchange, 
                () -> apiHandler.getStatus()));
            
            // Language API (kein Auth nötig für GET, damit Login-Screen richtige Sprache zeigt)
            httpServer.createContext("/api/language/get", exchange -> {
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    sendCorsHeaders(exchange);
                    try { exchange.sendResponseHeaders(204, -1); } catch (Exception e) {}
                    return;
                }
                sendJsonResponse(exchange, 200, apiHandler.getLanguage());
            });
            httpServer.createContext("/api/language/save", exchange -> handleProtectedApiPostRequest(exchange, 
                body -> apiHandler.saveLanguage(parseJson(body))));
            
            httpServer.setExecutor(null); // Standard-Executor verwenden
            httpServer.start();
            
            String authStatus = authEnabled ? "with authentication" : "without authentication";  // i18n-ignore: HTTP/HTML level; panel formats text from web/lang
            plugin.getLogger().log(Level.INFO, plugin.getConsoleMsg("web-started", "port", String.valueOf(port), "auth", authStatus));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error starting web server", e);  // i18n-ignore: technical web exception log
        }
    }

    /**
     * Stoppt den HTTP-Server
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            plugin.getLogger().log(Level.INFO, plugin.getConsoleMsg("web-stopped"));
        }
    }

    // ============ Auth Endpoints ============
    
    /**
     * Handlet Login-Request (Token -> Session)
     */
    private void handleLoginRequest(HttpExchange exchange) {
        try {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            
            String body = readRequestBody(exchange);
            Map<String, Object> request = parseJson(body);
            String token = (String) request.get("token");
            
            if (token == null || token.isEmpty()) {
                sendJsonResponse(exchange, 400, Map.of("success", false, "error", "Token fehlt"));  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
                return;
            }
            
            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            String sessionId = authManager.validateTokenAndCreateSession(token, clientIp);
            
            if (sessionId == null) {
                sendJsonResponse(exchange, 401, Map.of("success", false, "error", "Invalid or expired token"));  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
                return;
            }
            
            // Session-Cookie setzen
            WebAuthManager.AuthSession session = authManager.validateSession(sessionId, clientIp);
            exchange.getResponseHeaders().add("Set-Cookie", "session=" + sessionId + "; Path=/; HttpOnly; SameSite=Strict");
            
            sendJsonResponse(exchange, 200, Map.of(
                "success", true, 
                "playerName", session.playerName,
                "message", "Logged in successfully"  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
            ));
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Login Error: " + e.getMessage(), e);  // i18n-ignore: technical web exception log
            sendError(exchange, 500, "Internal Server Error");
        }
    }
    
    /**
     * Handlet Logout-Request
     */
    private void handleLogoutRequest(HttpExchange exchange) {
        try {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            String sessionId = getSessionIdFromCookie(exchange);
            if (sessionId != null) {
                authManager.invalidateSession(sessionId);
            }
            
            // Cookie löschen
            exchange.getResponseHeaders().add("Set-Cookie", "session=; Path=/; HttpOnly; Max-Age=0");
            sendJsonResponse(exchange, 200, Map.of("success", true, "message", "Logged out successfully"));  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Logout Error: " + e.getMessage(), e);  // i18n-ignore: technical web exception log
            sendError(exchange, 500, "Internal Server Error");
        }
    }
    
    /**
     * Prüft ob Session gültig ist
     */
    private void handleAuthCheckRequest(HttpExchange exchange) {
        try {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            // Wenn Auth deaktiviert ist, immer OK zurückgeben
            if (!authEnabled) {
                sendJsonResponse(exchange, 200, Map.of(
                    "authenticated", true,
                    "authRequired", false,
                    "playerName", "Admin"
                ));
                return;
            }
            
            String sessionId = getSessionIdFromCookie(exchange);
            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            
            WebAuthManager.AuthSession session = authManager.validateSession(sessionId, clientIp);
            
            if (session != null) {
                sendJsonResponse(exchange, 200, Map.of(
                    "authenticated", true,
                    "authRequired", true,
                    "playerName", session.playerName
                ));
            } else {
                sendJsonResponse(exchange, 200, Map.of(
                    "authenticated", false,
                    "authRequired", true
                ));
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Auth Check Error: " + e.getMessage(), e);  // i18n-ignore: technical web exception log
            sendError(exchange, 500, "Internal Server Error");
        }
    }
    
    // ============ Protected API Handlers ============
    
    /**
     * Handlet GET-Requests für geschützte API (mit Auth-Check)
     */
    private void handleProtectedApiRequest(HttpExchange exchange, ResponseProvider provider) {
        try {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            // Rate limit check
            if (!checkRateLimit(exchange)) return;

            // Auth-Check wenn aktiviert
            if (authEnabled && !isAuthenticated(exchange)) {
                sendJsonResponse(exchange, 401, Map.of("success", false, "error", "Nicht authentifiziert"));  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            
            Object response = provider.get();
            String jsonResponse = gson.toJson(response);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            sendCorsHeaders(exchange);
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "API Error: " + e.getMessage(), e);  // i18n-ignore: technical web exception log
            sendError(exchange, 500, "Internal Server Error");
        }
    }

    /**
     * Wie {@link #handleProtectedApiRequest}, reicht dem Handler aber die Query-Parameter durch.
     *
     * <p>Noetig fuer {@code /api/mvworlds/job?id=...}: der {@code ResponseProvider} der
     * Standard-GET-Variante bekommt den {@link HttpExchange} gar nicht zu sehen und kann die
     * Query daher nicht lesen.</p>
     */
    private void handleProtectedApiQueryRequest(HttpExchange exchange, QueryRequestHandler handler) {
        try {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!checkRateLimit(exchange)) return;

            if (authEnabled && !isAuthenticated(exchange)) {
                sendJsonResponse(exchange, 401, Map.of("success", false, "error", "Nicht authentifiziert"));  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            sendJsonResponse(exchange, 200, handler.handle(parseQuery(exchange.getRequestURI().getRawQuery())));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "API Error: " + e.getMessage(), e);  // i18n-ignore: technical web exception log
            sendError(exchange, 500, "Internal Server Error");
        }
    }

    /**
     * Zerlegt einen Query-String in ein Map. Werte werden URL-dekodiert, Parameter ohne
     * {@code =} landen mit leerem Wert in der Map.
     */
    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return params;

        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            int separator = pair.indexOf('=');
            try {
                if (separator < 0) {
                    params.put(java.net.URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                } else {
                    params.put(
                        java.net.URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
                }
            } catch (IllegalArgumentException ignored) {
                // Kaputtes Prozent-Encoding: Parameter ueberspringen statt den Request zu killen.
            }
        }
        return params;
    }

    /**
     * Handlet POST-Requests für geschützte API (mit Auth-Check)
     */
    private void handleProtectedApiPostRequest(HttpExchange exchange, PostRequestHandler handler) {
        try {
            // Handle CORS preflight
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            // Rate limit check
            if (!checkRateLimit(exchange)) return;

            // Auth-Check wenn aktiviert
            if (authEnabled && !isAuthenticated(exchange)) {
                sendJsonResponse(exchange, 401, Map.of("success", false, "error", "Nicht authentifiziert"));  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            
            String body = readRequestBody(exchange);
            Object response = handler.handle(body);
            String jsonResponse = gson.toJson(response);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            sendCorsHeaders(exchange);
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "API Error: " + e.getMessage(), e);  // i18n-ignore: technical web exception log
            sendError(exchange, 500, "Internal Server Error");
        }
    }

    // ============ Helper Methods ============
    
    /**
     * Prüft ob Request authentifiziert ist
     */
    private boolean isAuthenticated(HttpExchange exchange) {
        if (authManager == null) return true;
        
        String sessionId = getSessionIdFromCookie(exchange);
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        
        return authManager.validateSession(sessionId, clientIp) != null;
    }
    
    /**
     * Extrahiert Session-ID aus Cookie
     */
    private String getSessionIdFromCookie(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        
        for (String cookie : cookieHeader.split(";")) {
            String trimmed = cookie.trim();
            if (trimmed.startsWith("session=")) {
                return trimmed.substring(8);
            }
        }
        return null;
    }
    
    /**
     * Liest Request-Body
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
    
    /**
     * Sendet CORS Headers
     */
    private void sendCorsHeaders(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        // Mirror the request origin for credential support; deny if none provided
        if (origin == null || origin.isEmpty()) {
            origin = "null";
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
        exchange.getResponseHeaders().set("Vary", "Origin");
    }
    
    /**
     * Sendet JSON Response
     */
    private void sendJsonResponse(HttpExchange exchange, int code, Map<String, Object> data) {
        try {
            String jsonResponse = gson.toJson(data);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            sendCorsHeaders(exchange);
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, responseBytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (IOException ignored) {}
    }

    /**
     * Handlet GET-Requests für API (ohne Auth)
     */
    private void handleApiRequest(HttpExchange exchange, ResponseProvider provider) {
        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            
            Object response = provider.get();
            String jsonResponse = gson.toJson(response);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            sendCorsHeaders(exchange);
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "API Error: " + e.getMessage(), e);  // i18n-ignore: technical web exception log
            sendError(exchange, 500, "Internal Server Error");
        }
    }

    /**
     * Handlet POST-Requests für API (ohne Auth)
     */
    private void handleApiPostRequest(HttpExchange exchange, PostRequestHandler handler) {
        try {
            // Handle CORS preflight
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            Object response = handler.handle(sb.toString());
            String jsonResponse = gson.toJson(response);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            sendCorsHeaders(exchange);
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "API Error: " + e.getMessage(), e);  // i18n-ignore: technical web exception log
            sendError(exchange, 500, "Internal Server Error");
        }
    }

    /**
     * Sendet Error-Response
     */
    private void sendError(HttpExchange exchange, int code, String message) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("error", message);
            error.put("code", code);
            String jsonError = gson.toJson(error);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            sendCorsHeaders(exchange);
            byte[] errorBytes = jsonError.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, errorBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorBytes);
            }
        } catch (IOException ignored) {}
    }

    private boolean checkRateLimit(HttpExchange exchange) {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();

        RateWindow window = rateLimitCounters.computeIfAbsent(clientIp, k -> new RateWindow());
        boolean limited;
        synchronized (window) {
            // Abgelaufenes Fenster startet neu. Ohne diesen Reset zaehlte der
            // Wert unbegrenzt weiter und sperrte den Admin nach 100 Requests
            // dauerhaft aus -- bis zum Server-Neustart.
            if (now - window.windowStart >= RATE_LIMIT_WINDOW_MS) {
                window.windowStart = now;
                window.count = 0;
            }
            window.count++;
            limited = window.count > RATE_LIMIT_MAX_REQUESTS;
        }

        if (limited) {
            exchange.getResponseHeaders().set("Retry-After", // i18n-ignore: HTTP-Header-Name, erreicht nie einen Spieler
                    String.valueOf(RATE_LIMIT_WINDOW_MS / 1000L));
            sendJsonResponse(exchange, 429, Map.of("success", false, "error", "Rate limit exceeded"));  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
            return false;
        }

        // Verwaiste Eintraege entfernen, damit die Map nicht unbegrenzt waechst.
        if (rateLimitCounters.size() > 512) {
            rateLimitCounters.values().removeIf(w -> {
                synchronized (w) {
                    return now - w.windowStart >= RATE_LIMIT_WINDOW_MS * 2;
                }
            });
        }

        return true;
    }

    /**
     * Parst JSON zu Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return gson.fromJson(json, Map.class);
    }

    // ============ Functional Interfaces ============

    @FunctionalInterface
    interface ResponseProvider {
        Object get() throws Exception;
    }

    @FunctionalInterface
    interface PostRequestHandler {
        Object handle(String body) throws Exception;
    }

    /** GET-Handler, der die Query-Parameter des Requests braucht. */
    @FunctionalInterface
    interface QueryRequestHandler {
        Map<String, Object> handle(Map<String, String> query) throws Exception;
    }

    // ============ Statischer File Handler ============

    private static class StaticFileHandler implements HttpHandler {
        /**
         * URL-Praefix der Texturen aus dem Server-Resourcepack. Diese liegen als einzige
         * statische Dateien nicht im JAR, sondern im Plugin-Datenordner - sie entstehen ja
         * erst zur Laufzeit aus dem Pack des Servers.
         */
        private static final String OVERRIDE_PREFIX = "/item-assets/override/";

        private final JavaPlugin plugin;
        private final File overrideDir;
        private static final Map<String, String> MIME_TYPES = new HashMap<>();

        /** Unveraenderliche Assets - alles andere (html/js/json) wird bewusst nicht gecacht. */
        private static final Set<String> CACHEABLE_EXTENSIONS = new HashSet<>(Arrays.asList(
                "png", "jpg", "jpeg", "gif", "svg", "ico", "woff", "woff2", "ttf"));

        static {
            MIME_TYPES.put("html", "text/html; charset=UTF-8");
            MIME_TYPES.put("css", "text/css; charset=UTF-8");
            MIME_TYPES.put("js", "application/javascript; charset=UTF-8");
            MIME_TYPES.put("json", "application/json; charset=UTF-8");
            MIME_TYPES.put("png", "image/png");
            MIME_TYPES.put("jpg", "image/jpeg");
            MIME_TYPES.put("jpeg", "image/jpeg");
            MIME_TYPES.put("gif", "image/gif");
            MIME_TYPES.put("svg", "image/svg+xml");
            MIME_TYPES.put("ico", "image/x-icon");
            MIME_TYPES.put("woff", "font/woff");
            MIME_TYPES.put("woff2", "font/woff2");
            MIME_TYPES.put("ttf", "font/ttf");
        }

        StaticFileHandler(JavaPlugin plugin, File overrideDir) {
            this.plugin = plugin;
            this.overrideDir = overrideDir;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // Redirect root zu index.html
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
            }

            // Security: Keine Directory Traversal
            if (path.contains("..") || path.contains("//")) {
                sendError(exchange, 403, "Forbidden");
                return;
            }

            // Resourcepack-Texturen kommen aus dem Datenordner statt aus dem JAR.
            if (path.startsWith(OVERRIDE_PREFIX)) {
                sendOverrideTexture(exchange, path.substring(OVERRIDE_PREFIX.length()));
                return;
            }

            // Lade Datei aus Resources
            String resourcePath = "web" + path;
            InputStream is = plugin.getResource(resourcePath);

            if (is == null) {
                sendError(exchange, 404, "Not Found");
                return;
            }

            try {
                byte[] content = is.readAllBytes();
                
                // Content-Type bestimmen
                String extension = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : "html";
                String contentType = MIME_TYPES.getOrDefault(extension.toLowerCase(), "application/octet-stream");
                
                exchange.getResponseHeaders().set("Content-Type", contentType);
                // Panel-Code und Sprachbundles duerfen nie aus dem Browser-Cache kommen:
                // sonst zeigt das Panel nach einem Plugin-Update alte Texte bzw. rohe i18n-Keys.
                // Nur unveraenderliche Assets (Bilder, Fonts) werden weiterhin lange gecacht.
                if (CACHEABLE_EXTENSIONS.contains(extension.toLowerCase())) {
                    exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
                } else {
                    exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate"); // i18n-ignore: HTTP-Header-Wert, erreicht nie einen Spieler
                }
                exchange.sendResponseHeaders(200, content.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } finally {
                is.close();
            }
        }

        /**
         * Liefert eine Textur aus {@code plugins/&lt;Plugin&gt;/texture-overrides/}.
         *
         * <p>Diese Dateien stammen aus einem heruntergeladenen Resourcepack, liegen also
         * ausserhalb des JARs. Der Dateiname wird deshalb streng geprueft: nur
         * {@code GROSSBUCHSTABEN_UND_ZAHLEN.png}, und der aufgeloeste Pfad muss nachweislich
         * im Override-Ordner liegen. Die {@code ..}-Pruefung des Aufrufers allein wuerde
         * kodierte Varianten und Symlinks nicht abdecken.</p>
         */
        private void sendOverrideTexture(HttpExchange exchange, String fileName) throws IOException {
            if (overrideDir == null || !ResourcePackTextureService.isValidOverrideFileName(fileName)) {
                sendError(exchange, 404, "Not Found");
                return;
            }

            File file = new File(overrideDir, fileName);
            if (!file.isFile()
                    || !file.getCanonicalPath().startsWith(overrideDir.getCanonicalPath() + File.separator)) {
                sendError(exchange, 404, "Not Found");
                return;
            }

            byte[] content = java.nio.file.Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            // Kuerzer als die JAR-Assets: ein Pack-Wechsel soll im Panel zeitnah ankommen.
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=300");  // i18n-ignore: HTTP-Header-Wert
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            String errorHtml = "<!DOCTYPE html><html><head><title>Error " + code + "</title></head>"  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
                + "<body style='font-family:sans-serif;text-align:center;padding-top:50px;'>"  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
                + "<h1 style='color:#f44336;'>Error " + code + "</h1>"  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
                + "<p>" + message + "</p></body></html>";  // i18n-ignore: HTTP-/HTML-Ebene; das Panel formuliert seine Texte selbst aus web/lang
            byte[] bytes = errorHtml.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
