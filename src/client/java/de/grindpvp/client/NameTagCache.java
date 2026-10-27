package de.grindpvp.client;

import com.google.gson.JsonObject;
import de.grindpvp.GrindPvPMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletionException;

public final class NameTagCache {
    private static final ConcurrentMap<String, ProfileStyle> PROFILES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Long> NEXT_ATTEMPT = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Boolean> PENDING = new ConcurrentHashMap<>();

    private NameTagCache() {
    }

    public static Component format(Player player, Component vanillaName) {
        String username = player.getName().getString();
        String key = username.toLowerCase(Locale.ROOT);
        ProfileStyle profile = PROFILES.get(key);
        if (profile == null) {
            requestProfile(key, username);
            return vanillaName;
        }

        MutableComponent formatted = Component.empty();
        formatted.append(Component.literal("[" + profile.overallTier + "] ")
                .withStyle(color(tierColor(profile.overallTier))));
        if (!profile.role.equals("MEMBER") && !profile.role.equals("CUPWINNER")
                && !profile.role.equals("CUPMASTER")) {
            formatted.append(Component.literal("[" + roleLabel(profile.role) + "] ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(roleColor(profile.role))).withBold(true)));
        }

        int nameColor = switch (profile.role) {
            case "MEMBER" -> 0x9E99DA;
            case "CUPWINNER" -> 0xF1C40F;
            case "CUPMASTER" -> 0xAA5959;
            default -> -1;
        };
        MutableComponent name = Component.literal(username);
        if (nameColor >= 0) {
            name.withStyle(color(nameColor));
        } else {
            name.withStyle(vanillaName.getStyle());
        }
        formatted.append(name);

        if (profile.hasMod) {
            formatted.append(Component.literal(" [G]").withStyle(color(0xFF8A24)));
        }
        return formatted;
    }

    private static void requestProfile(String key, String username) {
        long now = System.currentTimeMillis();
        if (NEXT_ATTEMPT.getOrDefault(key, 0L) > now || PENDING.putIfAbsent(key, true) != null) {
            return;
        }
        String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);
        BackendClient.get("/api/players/by-name?name=" + encodedName)
                .whenComplete((json, error) -> {
                    PENDING.remove(key);
                    NEXT_ATTEMPT.put(key, System.currentTimeMillis() + 60_000L);
                    if (error != null) {
                        GrindPvPMod.LOGGER.warn("Could not load GrindPvP name tag for {}", username, unwrap(error));
                        return;
                    }
                    JsonObject profile = json.getAsJsonObject();
                    String tier = profile.has("overallTier")
                            ? profile.get("overallTier").getAsString() : "LT5";
                    String role = profile.has("role")
                            ? profile.get("role").getAsString() : "MEMBER";
                    boolean hasMod = profile.has("hasMod") && profile.get("hasMod").getAsBoolean();
                    PROFILES.put(key, new ProfileStyle(tier, role, hasMod));
                });
    }

    private static int tierColor(String tier) {
        if (tier.endsWith("5")) return 0x92969B;
        if (tier.endsWith("4")) return 0x9CCBDF;
        if (tier.endsWith("3")) return 0xFFB36B;
        if (tier.endsWith("2")) return 0xD5D9DF;
        return 0xFFD24A;
    }

    private static int roleColor(String role) {
        return switch (role) {
            case "HELPER" -> 0x73CF9A;
            case "MOD" -> 0x6AADDA;
            case "ADMIN" -> 0xDBB16D;
            case "OWNER" -> 0xA11111;
            case "VIP" -> 0xFF8A24;
            default -> 0xE5E6E7;
        };
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "HELPER" -> "Helper";
            case "MOD" -> "Mod";
            case "ADMIN" -> "Admin";
            case "OWNER" -> "Owner";
            case "VIP" -> "Vip";
            default -> role;
        };
    }

    private static Style color(int rgb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb));
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
    }

    private record ProfileStyle(String overallTier, String role, boolean hasMod) {
    }
}
