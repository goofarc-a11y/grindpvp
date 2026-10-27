package de.grindpvp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import de.grindpvp.network.ClientModPresencePayload;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrindPvPMod implements ModInitializer {
    public static final String MOD_ID = "grindpvp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    @Override
    public void onInitialize() {
        LOGGER.info("GrindPvP loaded");
        PayloadTypeRegistry.playC2S().register(
                ClientModPresencePayload.TYPE, ClientModPresencePayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            registerPlayer(player, false);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                registerPlayer(handler.getPlayer(), false));
        ServerPlayNetworking.registerGlobalReceiver(ClientModPresencePayload.TYPE, (payload, context) ->
                context.server().execute(() -> registerPlayer(context.player(), true)));
    }

    private static void registerPlayer(ServerPlayer player, boolean hasMod) {
        String serverToken = System.getenv("GRINDPVP_SERVER_TOKEN");
        if (serverToken == null || serverToken.isBlank()) {
            LOGGER.error("Cannot register player {}: GRINDPVP_SERVER_TOKEN is not configured.",
                    player.getName().getString());
            return;
        }

        String baseUrl = System.getProperty("grindpvp.backend-url", "http://localhost:8080")
                .replaceAll("/+$", "");
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            LOGGER.error("Cannot register players: grindpvp.backend-url must start with http:// or https://.");
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("id", player.getUUID().toString());
        payload.addProperty("username", player.getName().getString());
        payload.addProperty("hasMod", hasMod);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/server/players"))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("X-Server-Token", serverToken)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> {
                    if (error != null) {
                        LOGGER.error("Could not register player {} with the GrindPvP backend.",
                                player.getName().getString(), error);
                    } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        LOGGER.error("GrindPvP backend rejected player registration for {} with HTTP {}: {}",
                                player.getName().getString(), response.statusCode(), response.body());
                    } else {
                        LOGGER.info("Registered {} with the GrindPvP backend.",
                                player.getName().getString());
                    }
                });
    }
}
