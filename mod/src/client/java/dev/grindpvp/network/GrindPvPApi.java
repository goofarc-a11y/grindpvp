package dev.grindpvp.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class GrindPvPApi {
    private static final System.Logger LOGGER = System.getLogger(GrindPvPApi.class.getName());
    private static final Gson GSON = new Gson();
    private static final String BASE_URL = System.getProperty(
            "grindpvp.apiUrl",
            "https://grindpvp-backend-1.onrender.com"
    );
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private static final long CACHE_DURATION_NANOS = Duration.ofSeconds(20).toNanos();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    private static final ConcurrentHashMap<String, CachedResponse> GET_CACHE = new ConcurrentHashMap<>();

    private GrindPvPApi() {
    }

    public static void get(String path, Consumer<ApiResult> callback) {
        get(path, null, callback);
    }

    public static void get(String path, String token, Consumer<ApiResult> callback) {
        long now = System.nanoTime();
        GET_CACHE.entrySet().removeIf(entry -> now - entry.getValue().createdAtNanos >= CACHE_DURATION_NANOS);
        String cacheKey = path;
        CachedResponse cached = GET_CACHE.get(cacheKey);
        if (token == null && cached != null) {
            Minecraft.getInstance().execute(() -> callback.accept(cached.result));
            return;
        }
        send("GET", path, null, token, result -> {
            if (token == null && result.success()) {
                if (GET_CACHE.size() >= 128) {
                    GET_CACHE.entrySet().stream()
                            .min(java.util.Comparator.comparingLong(entry -> entry.getValue().createdAtNanos))
                            .ifPresent(entry -> GET_CACHE.remove(entry.getKey(), entry.getValue()));
                }
                GET_CACHE.put(cacheKey, new CachedResponse(System.nanoTime(), result));
            }
            callback.accept(result);
        });
    }

    public static void post(String path, JsonObject body, String token, Consumer<ApiResult> callback) {
        send("POST", path, body, token, result -> {
            if (result.success()) {
                GET_CACHE.clear();
            }
            callback.accept(result);
        });
    }

    public static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void send(
            String method,
            String path,
            JsonObject body,
            String token,
            Consumer<ApiResult> callback
    ) {
        URI uri;
        try {
            uri = URI.create(BASE_URL + path);
            boolean secure = "https".equalsIgnoreCase(uri.getScheme());
            boolean localDevelopment = "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if (!secure && !localDevelopment) {
                throw new IllegalArgumentException("The GrindPvP API URL must use HTTPS.");
            }
        } catch (IllegalArgumentException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Invalid GrindPvP API URL or path.", error);
            Minecraft.getInstance().execute(() -> callback.accept(ApiResult.failure(error.getMessage())));
            return;
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        if (token != null && !token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        } else if ("POST".equals(method)) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.GET();
        }

        CompletableFuture<HttpResponse<String>> response = CLIENT.sendAsync(
                request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        response.whenComplete((httpResponse, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                LOGGER.log(System.Logger.Level.ERROR, "GrindPvP API request failed: " + path, error);
                callback.accept(ApiResult.failure("Backend connection failed: " + error.getMessage()));
                return;
            }
            if (httpResponse.statusCode() == 204) {
                callback.accept(ApiResult.success(new JsonObject()));
                return;
            }
            JsonObject json;
            try {
                json = JsonParser.parseString(httpResponse.body()).getAsJsonObject();
            } catch (RuntimeException parseError) {
                String message = "Backend returned an invalid response (HTTP " + httpResponse.statusCode() + ").";
                LOGGER.log(System.Logger.Level.ERROR, message, parseError);
                callback.accept(ApiResult.failure(message));
                return;
            }
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                JsonElement detail = json.get("detail");
                String message = detail == null || detail.isJsonNull()
                        ? "Backend request failed with HTTP " + httpResponse.statusCode() + "."
                        : detail.isJsonPrimitive() ? detail.getAsString() : detail.toString();
                callback.accept(ApiResult.failure(message));
                return;
            }
            callback.accept(ApiResult.success(json));
        }));
    }

    public record ApiResult(boolean success, JsonObject data, String error) {
        public static ApiResult success(JsonObject data) {
            return new ApiResult(true, data, "");
        }

        public static ApiResult failure(String error) {
            return new ApiResult(false, new JsonObject(), error);
        }
    }

    private record CachedResponse(long createdAtNanos, ApiResult result) {
    }
}
