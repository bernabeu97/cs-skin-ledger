package com.cs.skinledger.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:browsercsrf;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@ActiveProfiles("test")
class BrowserCsrfIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void rawCookieTokenAllowsBrowserMutation() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        URI base = URI.create("http://127.0.0.1:" + port);

        HttpResponse<String> register = client.send(HttpRequest.newBuilder(base.resolve("/api/auth/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"browser_user\",\"password\":\"password123\"}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, register.statusCode());

        HttpResponse<String> csrf = client.send(HttpRequest.newBuilder(base.resolve("/api/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, csrf.statusCode());
        String rawToken = cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst().orElseThrow();

        HttpResponse<String> save = client.send(HttpRequest.newBuilder(base.resolve("/api/settings/csqaq-token"))
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", rawToken)
                        .PUT(HttpRequest.BodyPublishers.ofString("{\"token\":\"COOKIE1234567890\"}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, save.statusCode(), save.body());
    }

    @Test
    void xorResponseTokenAllowsDesktopMutationWhenFormatIsExplicit() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        URI base = URI.create("http://127.0.0.1:" + port);

        HttpResponse<String> register = client.send(HttpRequest.newBuilder(base.resolve("/api/auth/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"desktop_user\",\"password\":\"password123\"}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, register.statusCode());

        HttpResponse<String> csrf = client.send(HttpRequest.newBuilder(base.resolve("/api/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, csrf.statusCode());
        String xorToken = new ObjectMapper().readTree(csrf.body()).path("token").asText();

        HttpResponse<String> save = client.send(HttpRequest.newBuilder(base.resolve("/api/settings/csqaq-token"))
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", xorToken)
                        .header("X-CSRF-TOKEN-FORMAT", "xor")
                        .PUT(HttpRequest.BodyPublishers.ofString("{\"token\":\"DESKTOP1234567890\"}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, save.statusCode(), save.body());
    }
}
