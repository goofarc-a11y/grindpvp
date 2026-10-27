package de.grindpvp.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Component
public class MinecraftProfileResolver {
    private static final String MOJANG_PROFILE_URL =
            "https://api.mojang.com/users/profiles/minecraft/";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private final ObjectMapper objectMapper;

    public MinecraftProfileResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResolvedProfile resolve(String username) {
        String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(MOJANG_PROFILE_URL + encodedName))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 204 || response.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Minecraft account not found: " + username);
            }
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Mojang profile lookup returned HTTP " + response.statusCode() + ".");
            }

            JsonNode profile = objectMapper.readTree(response.body());
            String id = profile.path("id").asText();
            String canonicalName = profile.path("name").asText();
            if (!id.matches("[0-9a-fA-F]{32}") || canonicalName.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Mojang returned an invalid player profile.");
            }
            String uuid = id.replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                    "$1-$2-$3-$4-$5");
            return new ResolvedProfile(UUID.fromString(uuid), canonicalName);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Mojang profile lookup was interrupted.", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not contact Mojang to resolve the Minecraft username.", exception);
        }
    }

    public record ResolvedProfile(UUID id, String username) {
    }
}
