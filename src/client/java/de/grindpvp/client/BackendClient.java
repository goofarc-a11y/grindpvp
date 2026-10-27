package de.grindpvp.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

final class BackendClient {
    private static final String BASE_URL = normalizeBaseUrl(
            System.getProperty("grindpvp.backend-url", "http://localhost:8080"));
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private BackendClient() {
    }

    static CompletableFuture<JsonElement> get(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(BackendClient::parseResponse);
    }

    static CompletableFuture<JsonElement> post(String path, String adminCode, JsonObject body) {
        return send(path, adminCode, body, "POST");
    }

    private static CompletableFuture<JsonElement> send(
            String path, String adminCode, JsonObject body, String method
    ) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("X-Admin-Code", adminCode)
                .method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(BackendClient::parseResponse);
    }

    private static JsonElement parseResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Backend returned HTTP " + response.statusCode()
                    + (response.body().isBlank() ? "" : ": " + response.body()));
        }
        if (response.body().isBlank()) {
            return new JsonObject();
        }
        return JsonParser.parseString(response.body());
    }

    private static String normalizeBaseUrl(String value) {
        String baseUrl = value.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new IllegalArgumentException("grindpvp.backend-url must start with http:// or https://");
        }
        return baseUrl;
    }
}
