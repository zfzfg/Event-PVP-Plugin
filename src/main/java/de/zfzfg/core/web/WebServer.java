package de.zfzfg.core.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;

/**
 * Eingebetteter HTTP-Server für das Web-Interface
 * Serves statische Dateien und REST API mit Token-Authentifizierung
 */
public class WebServer {
    
    private final JavaPlugin plugin;
    private final WebConfigManager configManager;
    private final WebApiHandler apiHandler;
    private final WebAuthManager authManager;
    private final int port;
    private HttpServer httpServer;
    private final Gson gson;
    private final boolean authEnabled;

    public WebServer(JavaPlugin plugin, WebConfigManager configManager, WebAuthManager authManager, int port, boolean authEnabled) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.apiHandler = new WebApiHandler(plugin, configManager);
        this.authManager = authManager;
        this.port = port;
        this.authEnabled = authEnabled;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    // Konstruktor für Rückwärtskompatibilität
    public WebServer(JavaPlugin plugin, WebConfigManager configManager, int port) {
        this(plugin, configManager, null, port, false);
    }

    /**
     * Startet den HTTP-Server
     */
    public void start() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Statische Dateien (Login-Seite braucht keine Auth)
            httpServer.createContext("/", new StaticFileHandler(plugin));
            
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
            
            httpServer.createContext("/api/equipment/get", exchange -> handleProtectedApiRequest(exchange, 
                () -> apiHandler.getEquipment()));
            httpServer.createContext("/api/equipment/save", exchange -> handleProtectedApiPostRequest(exchange, 
                body -> apiHandler.saveEquipment(parseJson(body))));
            
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
            
            String authStatus = authEnabled ? "mit Authentifizierung" : "ohne Authentifizierung";
            plugin.getLogger().log(Level.INFO, "✓ Web-Server gestartet auf Port " + port + " (" + authStatus + ")");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "✗ Fehler beim Starten des Web-Servers", e);
        }
    }

    /**
     * Stoppt den HTTP-Server
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            plugin.getLogger().log(Level.INFO, "✓ Web-Server gestoppt");
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
                sendJsonResponse(exchange, 400, Map.of("success", false, "error", "Token fehlt"));
                return;
            }
            
            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            String sessionId = authManager.validateTokenAndCreateSession(token, clientIp);
            
            if (sessionId == null) {
                sendJsonResponse(exchange, 401, Map.of("success", false, "error", "Ungültiger oder abgelaufener Token"));
                return;
            }
            
            // Session-Cookie setzen
            WebAuthManager.AuthSession session = authManager.validateSession(sessionId, clientIp);
            exchange.getResponseHeaders().add("Set-Cookie", "session=" + sessionId + "; Path=/; HttpOnly; SameSite=Strict");
            
            sendJsonResponse(exchange, 200, Map.of(
                "success", true, 
                "playerName", session.playerName,
                "message", "Erfolgreich eingeloggt"
            ));
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Login Error: " + e.getMessage(), e);
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
            sendJsonResponse(exchange, 200, Map.of("success", true, "message", "Erfolgreich ausgeloggt"));
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Logout Error: " + e.getMessage(), e);
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
            plugin.getLogger().log(Level.WARNING, "Auth Check Error: " + e.getMessage(), e);
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
            
            // Auth-Check wenn aktiviert
            if (authEnabled && !isAuthenticated(exchange)) {
                sendJsonResponse(exchange, 401, Map.of("success", false, "error", "Nicht authentifiziert"));
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
            plugin.getLogger().log(Level.WARNING, "API Error: " + e.getMessage(), e);
            sendError(exchange, 500, "Internal Server Error");
        }
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
            
            // Auth-Check wenn aktiviert
            if (authEnabled && !isAuthenticated(exchange)) {
                sendJsonResponse(exchange, 401, Map.of("success", false, "error", "Nicht authentifiziert"));
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
            plugin.getLogger().log(Level.WARNING, "API Error: " + e.getMessage(), e);
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
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
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "API Error: " + e.getMessage(), e);
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
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
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
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "API Error: " + e.getMessage(), e);
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
            byte[] errorBytes = jsonError.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, errorBytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorBytes);
            }
        } catch (IOException ignored) {}
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

    // ============ Statischer File Handler ============

    private static class StaticFileHandler implements HttpHandler {
        private final JavaPlugin plugin;
        private static final Map<String, String> MIME_TYPES = new HashMap<>();
        
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

        StaticFileHandler(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // Redirect root zu index.html
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }
            
            // Security: Keine Directory Traversal
            if (path.contains("..") || path.contains("//")) {
                sendError(exchange, 403, "Forbidden");
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
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
                exchange.sendResponseHeaders(200, content.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } finally {
                is.close();
            }
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            String errorHtml = "<!DOCTYPE html><html><head><title>Error " + code + "</title></head>"
                + "<body style='font-family:sans-serif;text-align:center;padding-top:50px;'>"
                + "<h1 style='color:#f44336;'>Error " + code + "</h1>"
                + "<p>" + message + "</p></body></html>";
            byte[] bytes = errorHtml.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
