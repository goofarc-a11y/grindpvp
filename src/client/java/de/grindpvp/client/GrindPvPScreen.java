package de.grindpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.grindpvp.GrindPvPMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;

public class GrindPvPScreen extends Screen {
    private static final int PANEL_WIDTH = 660;
    private static final int PANEL_HEIGHT = 390;
    private static final int PANEL_COLOR = 0xF0181A1D;
    private static final int SURFACE_COLOR = 0xFF222529;
    private static final int ORANGE = 0xFFFF8A24;
    private static final Kit[] KITS = Kit.values();
    private final Map<String, Float> hoverProgress = new HashMap<>();

    private Tab tab = Tab.KITS;
    private Kit selectedKit = Kit.SWORD;
    private int scrollOffset;
    private String status = "Verbinde mit dem GrindPvP-Backend...";
    private List<JsonObject> leaderboard = List.of();
    private JsonObject profile;
    private EditBox profileName;
    private EditBox adminCode;
    private EditBox targetName;
    private EditBox selectedRole;
    private EditBox reportKit;
    private EditBox winnerName;
    private EditBox winnerUuid;
    private EditBox loserName;
    private EditBox loserUuid;
    private EditBox matchesPlayed;

    protected GrindPvPScreen() {
        super(Component.literal("GrindPvP"));
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        if (tab == Tab.PROFILE) {
            profileName = new EditBox(font, left + 158, height / 2 - 111, 190, 22,
                    Component.literal("Minecraft-Name"));
            profileName.setMaxLength(16);
            addRenderableWidget(profileName);
            profileName.setValue(Minecraft.getInstance().getUser().getName());
        } else if (tab == Tab.ADMIN) {
            int formX = left + 230;
            int top = (height - PANEL_HEIGHT) / 2;
            adminCode = addField(formX, top + 60, 230, "Admin-Code");
            targetName = addField(formX, top + 88, 230, "Spielername");
            selectedRole = addField(formX, top + 116, 230, "Rolle, z. B. HELPER");
            reportKit = addField(formX, top + 188, 230, "Kit, z. B. UHC");
            winnerName = addField(formX, top + 214, 230, "Siegername");
            winnerUuid = addField(formX, top + 240, 230, "Sieger-UUID");
            loserName = addField(formX, top + 266, 230, "Verlierername");
            loserUuid = addField(formX, top + 292, 230, "Verlierer-UUID");
            matchesPlayed = addField(formX, top + 318, 230, "Anzahl Matches");
            matchesPlayed.setValue("1");
        }
    }

    private EditBox addField(int x, int y, int fieldWidth, String hint) {
        EditBox field = new EditBox(font, x, y, fieldWidth, 20, Component.literal(hint));
        field.setMaxLength(64);
        field.setHint(Component.literal(hint));
        addRenderableWidget(field);
        return field;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xB9000000);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_COLOR);
        graphics.fill(left, top, left + 4, top + PANEL_HEIGHT, ORANGE);
        graphics.drawString(font, "GRIND", left + 22, top + 20, 0xFFFFFFFF, true);
        graphics.drawString(font, "PVP", left + 67, top + 20, ORANGE, true);
        graphics.drawString(font, "Competitive profiles", left + 22, top + 39, 0xFF9A9DA1, false);

        drawTab(graphics, left + 20, top + 67, 112, 28, "Kits", Tab.KITS, mouseX, mouseY);
        drawTab(graphics, left + 20, top + 101, 112, 28, "Profile", Tab.PROFILE, mouseX, mouseY);
        drawTab(graphics, left + 20, top + 135, 112, 28, "Admin", Tab.ADMIN, mouseX, mouseY);

        int contentX = left + 152;
        graphics.fill(contentX, top + 20, left + PANEL_WIDTH - 18, top + 21, 0xFF383B3F);
        switch (tab) {
            case KITS -> renderKits(graphics, contentX, top, mouseX, mouseY);
            case PROFILE -> renderProfile(graphics, contentX, top);
            case ADMIN -> renderAdmin(graphics, contentX, top);
        }
        graphics.drawString(font, status, left + 20, top + PANEL_HEIGHT - 23, 0xFFB8BABD, false);
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderKits(GuiGraphics graphics, int contentX, int top, int mouseX, int mouseY) {
        graphics.drawString(font, "LEADERBOARD  /  " + selectedKit.label, contentX, top + 34,
                0xFFFFFFFF, true);
        int listX = contentX;
        int listY = top + 57;
        int listWidth = 153;
        int visibleRows = 8;
        graphics.enableScissor(listX, listY, listX + listWidth, listY + visibleRows * 34);
        for (int i = 0; i < KITS.length; i++) {
            int y = listY + (i - scrollOffset) * 34;
            if (y < listY - 34 || y > listY + visibleRows * 34) continue;
            boolean hovered = mouseX >= listX && mouseX < listX + listWidth
                    && mouseY >= y && mouseY < y + 30;
            int background = selectedKit == KITS[i] ? 0xFF4A3324
                    : hovered ? 0xFF303338 : SURFACE_COLOR;
            float hover = animate("kit-" + KITS[i].name(), hovered ? 1.0F : 0.0F);
            graphics.fill(listX, y, listX + listWidth, y + 30,
                    selectedKit == KITS[i] ? 0xFF4A3324 : blend(SURFACE_COLOR, 0xFF303338, hover));
            if (selectedKit == KITS[i] || hover > 0.01F) {
                graphics.fill(listX, y, listX + 3, y + 30, ORANGE);
            }
            graphics.renderItem(new ItemStack(KITS[i].icon), listX + 9, y + 7);
            graphics.drawString(font, KITS[i].label, listX + 32, y + 11, 0xFFE5E6E7, false);
        }
        graphics.disableScissor();

        int tableX = contentX + listWidth + 18;
        graphics.drawString(font, "#   Spieler                 Tier      W/L",
                tableX, top + 61, 0xFF9A9DA1, false);
        if (leaderboard.isEmpty()) {
            graphics.drawString(font, "Keine Ranglisten-Daten geladen.",
                    tableX, top + 88, 0xFFB8BABD, false);
        } else {
            int rowY = top + 83;
            int count = Math.min(9, leaderboard.size());
            for (int i = 0; i < count; i++) {
                JsonObject entry = leaderboard.get(i);
                boolean ownEntry = entry.has("username")
                        && entry.get("username").getAsString().equalsIgnoreCase(
                        Minecraft.getInstance().getUser().getName());
                graphics.fill(tableX - 5, rowY - 3, leftEdge(tableX, 320), rowY + 17,
                        ownEntry ? 0x664A3324 : 0x00000000);
                int place = value(entry, "place", i + 1);
                Item medal = place == 1 ? Items.GOLD_INGOT : place == 2 ? Items.IRON_INGOT
                        : place == 3 ? Items.COPPER_INGOT : null;
                if (medal == null) {
                    graphics.drawString(font, Integer.toString(place), tableX + 5,
                            rowY, 0xFF9A9DA1, false);
                } else {
                    graphics.renderItem(new ItemStack(medal), tableX + 3, rowY - 5);
                }
                graphics.drawString(font, clip(value(entry, "username", "-"), 12),
                        tableX + 28, rowY, ownEntry ? ORANGE : 0xFFE5E6E7, true);
                graphics.drawString(font, value(entry, "tier", "LT5"),
                        tableX + 143, rowY, tierColor(value(entry, "tier", "LT5")), true);
                graphics.drawString(font, value(entry, "wins", 0) + "/"
                                + value(entry, "losses", 0), tableX + 196, rowY,
                        0xFFB8BABD, false);
                rowY += 24;
            }
        }
        graphics.drawString(font, "Scroll für alle Kits",
                contentX, top + 354, 0xFF777B80, false);
    }

    private void renderProfile(GuiGraphics graphics, int contentX, int top) {
        graphics.drawString(font, "PLAYER PROFILE", contentX, top + 34, 0xFFFFFFFF, true);
        graphics.drawString(font, "Minecraft-Name", contentX, top + 81, 0xFFB8BABD, false);
        drawAction(graphics, contentX + 208, top + 82, "Suchen");
        if (profile == null) {
            graphics.drawString(font, "Profil noch nicht geladen. Nutze den Namen oben und klicke Suchen.",
                    contentX, top + 119, 0xFFB8BABD, false);
            return;
        }
        String username = value(profile, "username", "-");
        String role = value(profile, "role", "MEMBER");
        graphics.drawString(font, username, contentX, top + 119, roleColor(role), true);
        graphics.drawString(font, "Rolle: " + role + "    Overall: "
                        + value(profile, "overallTier", "LT5"),
                contentX, top + 140, 0xFFE5E6E7, false);
        graphics.drawString(font, "Kit                       Tier      Siege   Niederlagen",
                contentX, top + 171, 0xFF9A9DA1, false);
        if (profile.has("kits")) {
            JsonObject kits = profile.getAsJsonObject("kits");
            int y = top + 191;
            for (Kit kit : KITS) {
                if (kit == Kit.OVERALL || !kits.has(kit.name())) continue;
                JsonObject stat = kits.getAsJsonObject(kit.name());
                graphics.drawString(font, kit.label, contentX, y, 0xFFE5E6E7, false);
                graphics.drawString(font, value(stat, "tier", "LT5"),
                        contentX + 142, y, tierColor(value(stat, "tier", "LT5")), true);
                graphics.drawString(font, value(stat, "wins", 0) + "     "
                                + value(stat, "losses", 0),
                        contentX + 200, y, 0xFFB8BABD, false);
                y += 18;
            }
        }
    }

    private void renderAdmin(GuiGraphics graphics, int contentX, int top) {
        graphics.drawString(font, "ADMIN PANEL", contentX, top + 34, ORANGE, true);
        graphics.drawString(font, "Rolle setzen", contentX, top + 78, 0xFFFFFFFF, true);
        graphics.drawString(font, "Rollen: HELPER, MOD, ADMIN, OWNER, VIP, CUPWINNER, CUPMASTER",
                contentX, top + 148, 0xFF9A9DA1, false);
        graphics.drawString(font, "Match-Ergebnis", contentX, top + 174, 0xFFFFFFFF, true);
        drawAction(graphics, leftOf(contentX, PANEL_WIDTH) - 80, top + 116, "Speichern");
        drawAction(graphics, leftOf(contentX, PANEL_WIDTH) - 80, top + 348, "Einreichen");
    }

    private void drawTab(GuiGraphics graphics, int x, int y, int tabWidth, int tabHeight,
                         String label, Tab target, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + tabWidth && mouseY >= y && mouseY < y + tabHeight;
        float hover = animate("tab-" + target.name(), hovered ? 1.0F : 0.0F);
        graphics.fill(x, y, x + tabWidth, y + tabHeight,
                tab == target ? 0xFF4A3324 : blend(SURFACE_COLOR, 0xFF303338, hover));
        if (tab == target || hover > 0.01F) graphics.fill(x, y, x + 3, y + tabHeight, ORANGE);
        graphics.drawString(font, label, x + 14, y + 10, tab == target ? ORANGE : 0xFFE5E6E7, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() != 0) return false;
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        if (mouseX >= left + 20 && mouseX <= left + 132) {
            if (mouseY >= top + 67 && mouseY < top + 95) setTab(Tab.KITS);
            else if (mouseY >= top + 101 && mouseY < top + 129) setTab(Tab.PROFILE);
            else if (mouseY >= top + 135 && mouseY < top + 163) setTab(Tab.ADMIN);
            else return false;
            playClick();
            return true;
        }
        if (tab == Tab.KITS) {
            int listX = left + 152;
            int listY = top + 57;
            if (mouseX >= listX && mouseX < listX + 153) {
                int row = (int) ((mouseY - listY) / 34) + scrollOffset;
                if (row >= 0 && row < KITS.length && mouseY >= listY) {
                    selectedKit = KITS[row];
                    loadLeaderboard();
                    playClick();
                    return true;
                }
            }
        }
        if (tab == Tab.PROFILE && mouseX >= left + 360 && mouseX < left + 428
                && mouseY >= top + 82 && mouseY < top + 104) {
            loadProfile();
            playClick();
            return true;
        }
        if (tab == Tab.ADMIN) {
            int actionX = left + 562;
            if (mouseX >= actionX && mouseX < actionX + 68
                    && mouseY >= top + 116 && mouseY < top + 138) {
                submitRole();
                playClick();
                return true;
            }
            if (mouseX >= actionX && mouseX < actionX + 68
                    && mouseY >= top + 348 && mouseY < top + 370) {
                submitResult();
                playClick();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == Tab.KITS) {
            scrollOffset = Math.max(0, Math.min(KITS.length - 8,
                    scrollOffset + (verticalAmount < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void setTab(Tab selected) {
        tab = selected;
        clearWidgets();
        init();
        if (tab == Tab.KITS) loadLeaderboard();
    }

    private void loadLeaderboard() {
        status = "Lade " + selectedKit.label + " Leaderboard...";
        String kitPath = selectedKit.name();
        BackendClient.get("/api/leaderboards/" + kitPath)
                .whenComplete((json, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null) {
                        status = "Backend-Fehler: " + rootMessage(error);
                        leaderboard = List.of();
                        GrindPvPMod.LOGGER.warn("Could not load GrindPvP leaderboard", error);
                    } else {
                        JsonArray entries = json.getAsJsonArray();
                        List<JsonObject> loaded = new ArrayList<>();
                        entries.forEach(element -> loaded.add(element.getAsJsonObject()));
                        leaderboard = List.copyOf(loaded);
                        status = "Leaderboard aktualisiert";
                    }
                }));
    }

    private void loadProfile() {
        if (profileName == null || profileName.getValue().isBlank()) {
            status = "Bitte einen Minecraft-Namen eingeben.";
            return;
        }
        status = "Lade Profil...";
        String name = java.net.URLEncoder.encode(profileName.getValue(), java.nio.charset.StandardCharsets.UTF_8);
        BackendClient.get("/api/players/by-name?name=" + name)
                .whenComplete((json, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null) {
                        status = "Profil nicht geladen: " + rootMessage(error);
                        profile = null;
                        GrindPvPMod.LOGGER.warn("Could not load GrindPvP profile", error);
                    } else {
                        profile = json.getAsJsonObject();
                        status = "Profil geladen";
                    }
                }));
    }

    private void submitRole() {
        JsonObject body = new JsonObject();
        body.addProperty("username", targetName.getValue().trim());
        body.addProperty("role", selectedRole.getValue().trim().toUpperCase(Locale.ROOT));
        submit("/api/admin/roles", body, "Rolle gespeichert");
    }

    private void submitResult() {
        JsonObject body = new JsonObject();
        body.addProperty("kit", reportKit.getValue().trim().toUpperCase(Locale.ROOT));
        body.addProperty("winnerName", winnerName.getValue().trim());
        body.addProperty("winnerId", winnerUuid.getValue().trim());
        body.addProperty("loserName", loserName.getValue().trim());
        body.addProperty("loserId", loserUuid.getValue().trim());
        try {
            body.addProperty("matchesPlayed", Integer.parseInt(matchesPlayed.getValue().trim()));
        } catch (NumberFormatException exception) {
            status = "Anzahl Matches muss eine Zahl sein.";
            return;
        }
        body.addProperty("report", formatReport(body));
        submit("/api/admin/results", body, "Ergebnis gespeichert");
    }

    private void submit(String path, JsonObject body, String successMessage) {
        if (adminCode == null || adminCode.getValue().isBlank()) {
            status = "Admin-Code fehlt.";
            return;
        }
        status = "Sende Anfrage...";
        BackendClient.post(path, adminCode.getValue(), body)
                .whenComplete((json, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null) {
                        status = "Aktion fehlgeschlagen: " + rootMessage(error);
                        GrindPvPMod.LOGGER.warn("GrindPvP admin request failed", error);
                    } else {
                        status = successMessage;
                        if (path.endsWith("/results")) loadLeaderboard();
                    }
                }));
    }

    private static String formatReport(JsonObject body) {
        return "Kit: " + body.get("kit").getAsString()
                + " | Sieger: " + body.get("winnerName").getAsString()
                + " | Verlierer: " + body.get("loserName").getAsString()
                + " | Matches: " + body.get("matchesPlayed").getAsInt();
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static int leftEdge(int x, int width) {
        return x + width;
    }

    private int leftOf(int contentX, int panelWidth) {
        return contentX + panelWidth - 170;
    }

    private void drawAction(GuiGraphics graphics, int x, int y, String label) {
        graphics.fill(x, y, x + 68, y + 22, 0xFF5A351C);
        graphics.fill(x, y, x + 68, y + 2, ORANGE);
        graphics.drawString(font, label, x + 7, y + 7, 0xFFFFFFFF, true);
    }

    private float animate(String key, float target) {
        float current = hoverProgress.getOrDefault(key, 0.0F);
        float next = current + (target - current) * 0.24F;
        if (Math.abs(next - target) < 0.01F) next = target;
        hoverProgress.put(key, next);
        return next;
    }

    private static int blend(int from, int to, float amount) {
        int red = (int) (((from >> 16) & 0xFF) * (1.0F - amount) + ((to >> 16) & 0xFF) * amount);
        int green = (int) (((from >> 8) & 0xFF) * (1.0F - amount) + ((to >> 8) & 0xFF) * amount);
        int blue = (int) ((from & 0xFF) * (1.0F - amount) + (to & 0xFF) * amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static String clip(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static int value(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static String value(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : fallback;
    }

    private static int tierColor(String tier) {
        if (tier.endsWith("5")) return 0xFF92969B;
        if (tier.endsWith("4")) return 0xFF9CCBDF;
        if (tier.endsWith("3")) return 0xFFFFB36B;
        if (tier.endsWith("2")) return 0xFFD5D9DF;
        return 0xFFFFD24A;
    }

    private static int roleColor(String role) {
        return switch (role) {
            case "HELPER" -> 0xFF73CF9A;
            case "MOD" -> 0xFF6AADDA;
            case "ADMIN" -> 0xFFDBB16D;
            case "OWNER" -> 0xFFA11111;
            case "VIP" -> 0xFFFF8A24;
            case "CUPWINNER" -> 0xFFF1C40F;
            case "CUPMASTER" -> 0xFFAA5959;
            case "MEMBER" -> 0xFF9E99DA;
            default -> 0xFFE5E6E7;
        };
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Tab {
        KITS,
        PROFILE,
        ADMIN
    }

    private enum Kit {
        OVERALL("Overall", Items.NETHER_STAR),
        SWORD("Sword", Items.DIAMOND_SWORD),
        AXE("Axe", Items.DIAMOND_AXE),
        UHC("UHC", Items.GOLDEN_APPLE),
        DIAPOT("Diapot", Items.SPLASH_POTION),
        NETHOP("NethOP", Items.NETHERITE_HELMET),
        SMP("SMP", Items.ENDER_PEARL),
        MACE("Mace", Items.MACE),
        CRYSTAL("Crystal", Items.END_CRYSTAL),
        SPEARMACE("Spearmace", Items.NETHERITE_SPEAR),
        CART("Cart", Items.TNT),
        DIASMP("DiaSMP", Items.DIAMOND_CHESTPLATE),
        MANHUNT("Manhunt", Items.COMPASS),
        OGVANILLA("OGVanilla", Items.ENCHANTED_GOLDEN_APPLE),
        SPEED("Speed", Items.SPLASH_POTION);

        private final String label;
        private final Item icon;

        Kit(String label, Item icon) {
            this.label = label;
            this.icon = icon;
        }
    }
}
