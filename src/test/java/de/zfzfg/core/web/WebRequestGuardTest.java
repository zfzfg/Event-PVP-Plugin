package de.zfzfg.core.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebRequestGuardTest {

    private static final String PUBLIC_URL = "https://panel.example.org";

    @Test
    @DisplayName("Requests ohne Origin (Navigation, curl) sind erlaubt")
    void noOriginAllowed() {
        assertThat(WebRequestGuard.isOriginAllowed(null, "localhost:8085", PUBLIC_URL)).isTrue();
        assertThat(WebRequestGuard.isOriginAllowed("", "localhost:8085", PUBLIC_URL)).isTrue();
    }

    @Test
    @DisplayName("Same-Origin (Origin passt zum Host-Header) ist erlaubt")
    void sameOriginAllowed() {
        assertThat(WebRequestGuard.isOriginAllowed("http://localhost:8085", "localhost:8085", PUBLIC_URL)).isTrue();
        assertThat(WebRequestGuard.isOriginAllowed("http://MyServer.de:8085", "myserver.de:8085", PUBLIC_URL)).isTrue();
        assertThat(WebRequestGuard.isOriginAllowed("http://example.org", "example.org", PUBLIC_URL)).isTrue();
    }

    @Test
    @DisplayName("Anderer Port auf demselben Host (z. B. Dynmap) wird abgewiesen")
    void sameHostOtherPortRejected() {
        assertThat(WebRequestGuard.isOriginAllowed("http://localhost:8123", "localhost:8085", PUBLIC_URL)).isFalse();
    }

    @Test
    @DisplayName("Fremde und 'null'-Origins werden abgewiesen")
    void foreignOriginRejected() {
        assertThat(WebRequestGuard.isOriginAllowed("http://evil.example", "localhost:8085", PUBLIC_URL)).isFalse();
        assertThat(WebRequestGuard.isOriginAllowed("null", "localhost:8085", PUBLIC_URL)).isFalse();
    }

    @Test
    @DisplayName("public-url ist erlaubt, auch wenn der Proxy den Host-Header umschreibt")
    void publicUrlAllowed() {
        assertThat(WebRequestGuard.isOriginAllowed("https://panel.example.org", "127.0.0.1:8085", PUBLIC_URL)).isTrue();
        assertThat(WebRequestGuard.isOriginAllowed("https://panel.example.org:443", "127.0.0.1:8085", PUBLIC_URL)).isTrue();
        assertThat(WebRequestGuard.isOriginAllowed("http://panel.example.org", "127.0.0.1:8085", PUBLIC_URL)).isFalse();
    }

    @Test
    @DisplayName("Body bis zum Limit wird gelesen, Zeilenumbrueche bleiben erhalten")
    void bodyWithinLimit() throws Exception {
        String body = "{\"a\":\n\"b\"}";
        String read = WebRequestGuard.readBodyLimited(
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), 64);
        assertThat(read).isEqualTo(body);
    }

    @Test
    @DisplayName("Body ueber dem Limit wirft PayloadTooLargeException")
    void bodyOverLimit() {
        byte[] data = new byte[65];
        assertThatThrownBy(() -> WebRequestGuard.readBodyLimited(new ByteArrayInputStream(data), 64))
                .isInstanceOf(WebRequestGuard.PayloadTooLargeException.class);
    }

    @Test
    @DisplayName("RateLimiter sperrt nach maxRequests und gibt nach Ablauf des Fensters frei")
    void rateLimiter() {
        WebRequestGuard.RateLimiter limiter = new WebRequestGuard.RateLimiter(10, 60_000L);
        long now = 1_000_000L;
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4", now)).isTrue();
        }
        assertThat(limiter.tryAcquire("1.2.3.4", now)).isFalse();
        assertThat(limiter.tryAcquire("5.6.7.8", now)).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4", now + 60_000L)).isTrue();
        assertThat(limiter.windowSeconds()).isEqualTo(60L);
    }

    @Test
    @DisplayName("allowed-ips: leer = alle, sonst nur gelistete IPs")
    void ipAllowlist() {
        assertThat(WebRequestGuard.isIpAllowed("8.8.8.8", java.util.List.of())).isTrue();
        assertThat(WebRequestGuard.isIpAllowed("127.0.0.1", java.util.List.of(" 127.0.0.1 ", "10.0.0.5"))).isTrue();
        assertThat(WebRequestGuard.isIpAllowed("8.8.8.8", java.util.List.of("127.0.0.1"))).isFalse();
        assertThat(WebRequestGuard.isIpAllowed(null, java.util.List.of("127.0.0.1"))).isFalse();
    }
}
