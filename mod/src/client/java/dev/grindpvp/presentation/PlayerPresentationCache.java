package dev.grindpvp.presentation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.grindpvp.network.GrindPvPApi;
import dev.grindpvp.settings.GrindPvPSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerPresentationCache {
    private static final System.Logger LOGGER = System.getLogger(PlayerPresentationCache.class.getName());
    private static final ConcurrentHashMap<String, PlayerPresentation> PRESENTATIONS = new ConcurrentHashMap<>();
    private static final AtomicBoolean REFRESHING = new AtomicBoolean();

    private PlayerPresentationCache() {
    }

    public static void refresh() {
        if (REFRESHING.compareAndSet(false, true)) {
            fetchPage(1, new HashMap<>());
        }
    }

    public static PlayerPresentation find(String uuid, String username) {
        PlayerPresentation presentation = uuid == null ? null : PRESENTATIONS.get(uuid.toLowerCase(Locale.ROOT));
        return presentation != null || username == null
                ? presentation
                : PRESENTATIONS.get(username.toLowerCase(Locale.ROOT));
    }

    public static Component decorateNameTag(String uuid, String username, int latency) {
        PlayerPresentation presentation = find(uuid, username);
        return presentation == null ? null : presentation.nameTag(latency);
    }

    public static Component decorateRoleNameTag(String uuid, String username, Component existingScore) {
        PlayerPresentation presentation = find(uuid, username);
        return presentation == null ? null : presentation.roleNameTag(existingScore);
    }

    public static Component decorateTabName(String uuid, String username) {
        PlayerPresentation presentation = find(uuid, username);
        return presentation == null ? null : presentation.tabName();
    }

    public static Component decorateChatName(Component sender) {
        PlayerPresentation presentation = find(null, sender.getString());
        return presentation == null ? null : presentation.chatName(sender);
    }

    private static void fetchPage(int page, Map<String, PlayerPresentation> accumulated) {
        GrindPvPApi.get("/players/presentation?page=" + page + "&page_size=5000", result -> {
            if (!result.success()) {
                REFRESHING.set(false);
                LOGGER.log(System.Logger.Level.WARNING, "Could not refresh GrindPvP name-tag data: " + result.error());
                return;
            }
            JsonArray items = result.data().has("items") && result.data().get("items").isJsonArray()
                    ? result.data().getAsJsonArray("items")
                    : new JsonArray();
            for (JsonElement element : items) {
                if (!element.isJsonObject()) {
                    continue;
                }
                PlayerPresentation presentation = PlayerPresentation.from(element.getAsJsonObject());
                if (presentation == null) {
                    continue;
                }
                accumulated.put(presentation.uuid().toLowerCase(Locale.ROOT), presentation);
                accumulated.put(presentation.username().toLowerCase(Locale.ROOT), presentation);
            }
            if (items.size() == 5000) {
                fetchPage(page + 1, accumulated);
            } else {
                PRESENTATIONS.clear();
                PRESENTATIONS.putAll(accumulated);
                REFRESHING.set(false);
            }
        });
    }

    private static TextColor color(String value, int fallback) {
        try {
            String hex = value.startsWith("#") ? value.substring(1) : value;
            return TextColor.fromRgb(Integer.parseInt(hex, 16));
        } catch (NumberFormatException exception) {
            return TextColor.fromRgb(fallback);
        }
    }

    private record PlayerPresentation(
            String uuid,
            String username,
            String roleName,
            String roleColor,
            String titleName,
            String titleColor,
            String overallTier,
            String overallTierColor,
            int overallPosition
    ) {
        private static PlayerPresentation from(JsonObject json) {
            String uuid = stringValue(json, "uuid");
            String username = stringValue(json, "username");
            if (uuid.isBlank() || username.isBlank()) {
                return null;
            }
            return new PlayerPresentation(
                    uuid,
                    username,
                    stringValue(json, "role_name"),
                    stringValue(json, "role_color"),
                    stringValue(json, "title_name"),
                    stringValue(json, "title_color"),
                    stringValue(json, "overall_tier"),
                    stringValue(json, "overall_tier_color"),
                    json.has("overall_position") ? json.get("overall_position").getAsInt() : 0
            );
        }

        private Component nameTag(int latency) {
            MutableComponent result = Component.empty();
            GrindPvPSettings settings = GrindPvPSettings.get();
            if (settings.nametagRoleEnabled && !roleName.isBlank()) {
                result.append(Component.literal(roleName)
                        .withStyle(Style.EMPTY.withColor(color(roleColor, 0xFFFFFF)).withBold(true)));
                result.append(Component.literal(" "));
            }
            if (settings.nametagRankingEnabled && overallPosition > 0) {
                result.append(Component.literal("[" + overallPosition + "]")
                        .withStyle(Style.EMPTY.withColor(positionColor(overallPosition))));
                result.append(Component.literal(" "));
            }
            if (settings.nametagTierEnabled) {
                result.append(Component.literal("[" + overallTier + "]")
                        .withStyle(Style.EMPTY.withColor(color(overallTierColor, 0x808080))));
                result.append(Component.literal(" "));
            }
            result.append(Component.literal(username)
                    .withStyle(Style.EMPTY.withColor(color(titleColor, 0xFFFFFF))));
            if (settings.nametagPingEnabled && latency >= 0) {
                result.append(Component.literal(" "));
                result.append(Component.literal(latency + "ms")
                        .withStyle(Style.EMPTY.withColor(pingColor(latency))));
            }
            return result;
        }

        private static int positionColor(int position) {
            return switch (position) {
                case 1 -> 0xFFD700;
                case 2 -> 0xC0C0C0;
                case 3 -> 0xCD7F32;
                default -> 0x808080;
            };
        }

        private static int pingColor(int latency) {
            if (latency <= 30) {
                return 0x7CFC00;
            }
            if (latency <= 50) {
                return 0x90EE90;
            }
            if (latency <= 90) {
                return 0xFFFF00;
            }
            if (latency <= 120) {
                return 0xFF8C00;
            }
            return 0xFF0000;
        }

        private Component roleNameTag(Component existingScore) {
            if (roleName.isBlank()) {
                return existingScore;
            }
            MutableComponent result = Component.literal(roleName)
                    .withStyle(Style.EMPTY.withColor(color(roleColor, 0xFFFFFF)).withBold(true));
            if (existingScore != null) {
                result.append(Component.literal(" "));
                result.append(existingScore);
            }
            return result;
        }

        private Component tabName() {
            MutableComponent result = Component.empty();
            if (!roleName.isBlank()) {
                result.append(Component.literal(roleName)
                        .withStyle(Style.EMPTY.withColor(color(roleColor, 0xFFFFFF))));
                result.append(Component.literal(" "));
            }
            result.append(Component.literal("[" + overallTier + "]")
                    .withStyle(Style.EMPTY.withColor(color(overallTierColor, 0x808080))));
            result.append(Component.literal(" "));
            result.append(Component.literal(username)
                    .withStyle(Style.EMPTY.withColor(color(titleColor, 0xFFFFFF))));
            return result;
        }

        private Component chatName(Component sender) {
            MutableComponent result = Component.empty();
            if (!roleName.isBlank()) {
                result.append(Component.literal(roleName)
                        .withStyle(Style.EMPTY.withColor(color(roleColor, 0xFFFFFF))));
                result.append(Component.literal(" "));
            }
            result.append(Component.literal("[" + overallTier + "]")
                    .withStyle(Style.EMPTY.withColor(color(overallTierColor, 0x808080))));
            result.append(Component.literal(" "));
            result.append(sender.copy().withStyle(style -> style.withColor(color(titleColor, 0xFFFFFF))));
            return result;
        }
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }
}
