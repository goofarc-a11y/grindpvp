package dev.grindpvp.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.grindpvp.network.GrindPvPApi;
import dev.grindpvp.presentation.PlayerPresentationCache;
import dev.grindpvp.settings.GrindPvPSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class GrindPvPScreen extends Screen {
    private static final System.Logger LOGGER = System.getLogger(GrindPvPScreen.class.getName());
    private static final int PANEL_COLOR = 0xF01B1D21;
    private static final int SIDEBAR_COLOR = 0xFF202328;
    private static final int ROW_COLOR = 0xFF282C31;
    private static final int ACCENT_COLOR = 0xFFFF7A24;
    private static final Identifier HARDCORE_HEART_SPRITE = Identifier.fromNamespaceAndPath(
            "minecraft", "hud/heart/hardcore_full"
    );
    private static final String[] KIT_IDS = {
            "overall", "sword", "axe", "uhc", "diapot", "nethop", "smp", "mace",
            "crystal", "speed", "diasmp", "ogvanilla", "cart", "spearmace", "manhunt"
    };
    private static final String[] KIT_LABELS = {
            "Overall", "Sword", "Axe", "UHC", "DiaPot", "NethOP", "SMP", "Mace",
            "Crystal", "Speed", "DiaSMP", "OGVanilla", "Cart", "Spearmace", "Manhunt"
    };
    private static final Item[] KIT_ICONS = {
            Items.NETHER_STAR, Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.RED_DYE,
            Items.SPLASH_POTION, Items.NETHERITE_HELMET, Items.ENDER_PEARL, Items.MACE,
            Items.END_CRYSTAL, Items.SPLASH_POTION, Items.DIAMOND_CHESTPLATE,
            Items.GOLDEN_APPLE, Items.TNT_MINECART, Items.NETHERITE_SPEAR, Items.COMPASS
    };
    private static final Item[] PODIUM_ICONS = {Items.GOLD_INGOT, Items.IRON_INGOT, Items.COPPER_INGOT};
    private static final ItemStack HEALING_SPLASH_POTION = splashPotion(Potions.HEALING);
    private static final ItemStack SWIFTNESS_SPLASH_POTION = splashPotion(Potions.SWIFTNESS);

    private final GrindPvPSettings settings = GrindPvPSettings.get();
    private final Map<String, Float> hoverProgress = new HashMap<>();
    private final Set<String> hoveredElements = new HashSet<>();
    private final Set<String> hoveredElementsLastFrame = new HashSet<>();
    private final Set<SoundButton> soundButtons = new HashSet<>();
    private Page page = Page.LEADERBOARD;
    private String selectedKit = "overall";
    private String selectedPlayerUuid;
    private String adminToken;
    private JsonObject response = new JsonObject();
    private JsonArray searchResults = new JsonArray();
    private String statusMessage = "Connecting to GrindPvP...";
    private boolean loading;
    private boolean closing;
    private boolean profileSoundPending;
    private int listScroll;
    private int apiPage = 1;
    private int totalItems;
    private int renderMouseX;
    private int renderMouseY;
    private int renderAlpha = 255;
    private final long openedAtNanos = System.nanoTime();
    private long closeStartedAt;
    private boolean widgetHovered;
    private boolean initialRefreshStarted;
    private long pageTransitionStartedAt;
    private float pageTransitionDirection = 1.0f;
    private long navigationTransitionStartedAt;
    private int navigationIndicatorFromX;
    private int navigationIndicatorFromWidth;
    private int kitIndicatorFromY;
    private long kitTransitionStartedAt;
    private float hoverAnimationStep = 0.24f;
    private String profileSkinUuid;
    private PlayerSkin profileSkin;
    private boolean profileSkinLoading;
    private boolean profileSkinFailed;
    private EditBox searchInput;
    private EditBox adminPasswordInput;
    private EditBox playerNameInput;
    private EditBox roleInput;
    private EditBox titleInput;
    private EditBox tierInput;
    private EditBox winnerInput;
    private EditBox loserInput;

    public GrindPvPScreen() {
        super(Component.translatable("screen.grindpvp.title"));
        pageTransitionStartedAt = System.nanoTime();
        playInterfaceSound(SoundEvents.UI_TOAST_IN, 0.2f);
    }

    @Override
    protected void init() {
        int contentLeft = contentLeft();
        int contentWidth = contentWidth();
        soundButtons.clear();
        widgetHovered = false;
        addProfileSkinPreview(contentLeft);

        if (page == Page.PROFILES && selectedPlayerUuid == null) {
            int actionWidth = Math.min(64, Math.max(40, (contentWidth - 24) / 3));
            int pageButtonWidth = Math.min(80, Math.max(0, (contentWidth - 24) / 2));
            int paginationReserve = pageButtonWidth * 2 + 18;
            int searchWidth = Math.max(0, contentWidth - actionWidth * 2 - 24 - paginationReserve);
            int searchX = contentLeft + 8;
            int searchButtonX = searchX + searchWidth + 6;
            int refreshX = searchButtonX + actionWidth + 4;
            searchInput = new EditBox(this.font, searchX, 77, searchWidth, 20,
                    Component.translatable("screen.grindpvp.search"));
            searchInput.setMaxLength(16);
            searchInput.setHint(Component.literal("Minecraft username"));
            searchInput.setCentered(true);
            searchInput.setTextColor(0xFFE6E8EB);
            this.addRenderableWidget(searchInput);
            addButton(Component.literal("Search"), button -> {
                        apiPage = 1;
                        searchPlayers();
                    }, searchButtonX, 77, actionWidth, 20);
            this.addRenderableWidget(soundButton(Component.literal("Refresh"), button -> refreshData(),
                    refreshX, 77, actionWidth, 20));
        } else if (page == Page.ADMIN && adminToken == null) {
            adminPasswordInput = new EditBox(this.font, contentLeft + 8, 145, Math.max(0, contentWidth - 16), 20,
                    Component.literal("Admin password"));
            adminPasswordInput.setMaxLength(256);
            adminPasswordInput.setHint(Component.literal("Password"));
            adminPasswordInput.setCentered(true);
            adminPasswordInput.setTextColor(0xFFE6E8EB);
            adminPasswordInput.addFormatter((value, cursor) ->
                    FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY));
            this.addRenderableWidget(adminPasswordInput);
            addButton(Component.literal("Sign in"), button -> adminLogin(), contentLeft + 8, 177,
                    Math.min(100, Math.max(0, contentWidth - 16)), 20);
        } else if (page == Page.ADMIN) {
            addAdminInputs(contentLeft, contentWidth);
        } else if (page == Page.SETTINGS) {
            int settingColumnWidth = Math.max(1, Math.min(280, (contentWidth - 24) / 2));
            int nametagColumnLeft = contentLeft + 16 + settingColumnWidth;
            addButton(Component.literal(animationSettingLabel()), button -> {
                        settings.animationsEnabled = !settings.animationsEnabled;
                        saveSettings("Animation setting");
                        button.setMessage(Component.literal(animationSettingLabel()));
                    }, contentLeft + 8, 116, settingColumnWidth, 20);
            addButton(Component.literal(soundSettingLabel()), button -> {
                        settings.soundsEnabled = !settings.soundsEnabled;
                        saveSettings("Sound setting");
                        button.setMessage(Component.literal(soundSettingLabel()));
                    }, contentLeft + 8, 144, settingColumnWidth, 20);
            addButton(Component.literal(hoverSettingLabel()), button -> {
                        settings.hoverSoundsEnabled = !settings.hoverSoundsEnabled;
                        saveSettings("Hover sound setting");
                        button.setMessage(Component.literal(hoverSettingLabel()));
                    }, contentLeft + 8, 172, settingColumnWidth, 20);
            addButton(Component.literal("GUI volume: " + settings.guiSoundVolume + "%"), button -> {
                        settings.guiSoundVolume = (settings.guiSoundVolume + 10) % 110;
                        saveSettings("GUI volume");
                        button.setMessage(Component.literal("GUI volume: " + settings.guiSoundVolume + "%"));
                    }, contentLeft + 8, 200, settingColumnWidth, 20);
            addButton(Component.literal("Click volume: " + settings.clickSoundVolume + "%"), button -> {
                        settings.clickSoundVolume = (settings.clickSoundVolume + 10) % 110;
                        saveSettings("Click volume");
                        button.setMessage(Component.literal("Click volume: " + settings.clickSoundVolume + "%"));
                    }, contentLeft + 8, 228, settingColumnWidth, 20);
            addButton(Component.literal(nametagPingSettingLabel()), button -> {
                        settings.nametagPingEnabled = !settings.nametagPingEnabled;
                        saveSettings("Nametag ping setting");
                        button.setMessage(Component.literal(nametagPingSettingLabel()));
                    }, nametagColumnLeft, 116, settingColumnWidth, 20);
            addButton(Component.literal(nametagTierSettingLabel()), button -> {
                        settings.nametagTierEnabled = !settings.nametagTierEnabled;
                        saveSettings("Nametag tier setting");
                        button.setMessage(Component.literal(nametagTierSettingLabel()));
                    }, nametagColumnLeft, 144, settingColumnWidth, 20);
            addButton(Component.literal(nametagRankingSettingLabel()), button -> {
                        settings.nametagRankingEnabled = !settings.nametagRankingEnabled;
                        saveSettings("Nametag ranking setting");
                        button.setMessage(Component.literal(nametagRankingSettingLabel()));
                    }, nametagColumnLeft, 172, settingColumnWidth, 20);
            addButton(Component.literal(nametagRoleSettingLabel()), button -> {
                        settings.nametagRoleEnabled = !settings.nametagRoleEnabled;
                        saveSettings("Nametag role setting");
                        button.setMessage(Component.literal(nametagRoleSettingLabel()));
                    }, nametagColumnLeft, 200, settingColumnWidth, 20);
            addRenderableWidget(new SettingSlider(contentLeft + 8, 270,
                    Math.max(1, Math.min(280, contentWidth - 16)), 1, 255,
                    () -> settings.guiOpacity, value -> {
                        settings.guiOpacity = value;
                        saveSettings("GUI opacity");
                    }));
            addRenderableWidget(new SettingSlider(contentLeft + 8, 320,
                    Math.max(1, Math.min(280, contentWidth - 16)), 0, 20,
                    () -> settings.guiCornerRadius, value -> {
                        settings.guiCornerRadius = value;
                        saveSettings("GUI corner radius");
                    }));
        }

        if (page == Page.LEADERBOARD || page == Page.PROFILES && selectedPlayerUuid == null) {
            int pageButtonWidth = Math.min(80, Math.max(0, (contentWidth - 24) / 2));
            int rightEdge = this.width - 24;
            if (page == Page.LEADERBOARD) {
                rightEdge -= Math.min(82, Math.max(0, this.width - 24)) + 6;
            }
            int pageButtonX = rightEdge - 8 - pageButtonWidth * 2 - 6;
            addButton(Component.literal("Previous"), button -> {
                        apiPage = Math.max(1, apiPage - 1);
                        listScroll = 0;
                        refreshData();
                    }, pageButtonX, 77, pageButtonWidth, 20);
            addButton(Component.literal("Next"), button -> {
                        if (apiPage * 25 < totalItems) {
                            apiPage++;
                            listScroll = 0;
                            refreshData();
                        }
                    }, pageButtonX + pageButtonWidth + 6, 77, pageButtonWidth, 20);
        }

        if (page != Page.PROFILES || selectedPlayerUuid != null) {
            int refreshWidth = Math.min(82, Math.max(0, this.width - 24));
            this.addRenderableWidget(soundButton(Component.literal("Refresh"), button -> refreshData(),
                    contentLeft + contentWidth - 6 - refreshWidth, 77, refreshWidth, 20));
        }
        if (!initialRefreshStarted) {
            initialRefreshStarted = true;
            refreshData();
        }
    }

    private void addAdminInputs(int contentLeft, int contentWidth) {
        int fieldWidth = Math.max(0, contentWidth - 24);
        playerNameInput = addField(contentLeft, 112, fieldWidth, "Minecraft name");
        roleInput = addField(contentLeft, 140, fieldWidth, "Role (VIP, Owner, Admin, Mod, Helper, Developer, Banned)");
        titleInput = addField(contentLeft, 168, fieldWidth, "Title (Member, Cup Winner, Cup Master)");
        tierInput = addField(contentLeft, 196, fieldWidth, "Tier for selected kit");
        winnerInput = addField(contentLeft, 224, fieldWidth, "Winner Minecraft name");
        loserInput = addField(contentLeft, 252, fieldWidth, "Loser Minecraft name");
    }

    private EditBox addField(int left, int top, int width, String hint) {
        EditBox field = new EditBox(this.font, left + 8, top, width, 22, Component.literal(hint));
        field.setMaxLength(100);
        field.setHint(Component.literal(hint));
        field.setCentered(true);
        field.setTextColor(0xFFE6E8EB);
        this.addRenderableWidget(field);
        return field;
    }

    @Override
    public void tick() {
        if (closing && System.nanoTime() - closeStartedAt >= 160_000_000L) {
            closing = false;
            super.onClose();
        }
    }

    @Override
    public void onClose() {
        if (closing) {
            return;
        }
        playInterfaceSound(SoundEvents.UI_TOAST_OUT, 0.2f);
        if (settings.animationsEnabled) {
            closing = true;
            closeStartedAt = System.nanoTime();
        } else {
            super.onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        hoverAnimationStep = Math.max(0.04f, Math.min(0.32f, delta * 14.0f));
        long elapsed = System.nanoTime() - closeStartedAt;
        int transition = settings.animationsEnabled
                ? (closing
                    ? (int) Math.max(0, 255 - elapsed * 255 / 160_000_000L)
                    : (int) Math.min(255, (System.nanoTime() - openedAtNanos) * 255 / 160_000_000L))
                : 255;
        renderAlpha = transition;

        graphics.pose().pushMatrix();
        graphics.pose().translate(0, slideOffset());
        int adjustedMouseY = mouseY - Math.round(slideOffset());
        int adjustedMouseX = mouseX;
        float contentOffsetX = contentTransitionOffsetX();
        int contentAlpha = contentTransitionAlpha(transition);
        renderMouseX = adjustedMouseX - Math.round(contentOffsetX);
        renderMouseY = adjustedMouseY;
        hoveredElements.clear();
        int left = 16;
        int right = this.width - 16;
        int top = 14;
        int bottom = this.height - 18;
        fillRounded(graphics, left, top, right, bottom, withBackgroundOpacity(PANEL_COLOR, transition));
        fillRoundedTopStrip(graphics, left, top, right, top + 3,
                Math.min(settings.guiCornerRadius, 20), withAlpha(ACCENT_COLOR, transition));
        int panelCornerInset = cornerInset(Math.min(settings.guiCornerRadius, 12), 1);
        graphics.fill(left + panelCornerInset, 45, right - panelCornerInset, 46,
                withAlpha(0xFF343A41, transition));
        renderNavigation(graphics, adjustedMouseX, adjustedMouseY, transition);
        if (page == Page.LEADERBOARD || page == Page.MY_PROFILE || page == Page.PROFILES || page == Page.ADMIN) {
            renderKitSidebar(graphics, mouseX, adjustedMouseY, transition);
        }

        int contentLeft = contentLeft();
        int contentWidth = contentWidth();
        int contentTop = 68;
        int contentBottom = this.height - 29;
        fillRounded(graphics, contentLeft, contentTop, contentLeft + contentWidth, contentBottom,
                withBackgroundOpacity(0xC0181A1E, transition));
        int contentCornerInset = cornerInset(Math.min(settings.guiCornerRadius, 12), 1);
        graphics.fill(contentLeft + contentCornerInset, contentTop,
                contentLeft + contentWidth - contentCornerInset, contentTop + 1,
                withAlpha(0xFF343A41, transition));
        graphics.pose().pushMatrix();
        graphics.pose().translate(contentOffsetX, 0);
        drawFittedString(graphics, pageTitle(), contentLeft + 8, 54,
                Math.max(0, contentWidth - 16), withAlpha(0xFFFFFFFF, contentAlpha));
        if (page == Page.PROFILES && selectedPlayerUuid != null) {
            int searchBreadcrumbX = contentLeft + 8 + this.font.width(pageTitle()) + 12;
            drawFittedString(graphics, "< Search", searchBreadcrumbX, 54,
                    Math.max(0, contentLeft + contentWidth - 8 - searchBreadcrumbX),
                    withAlpha(0xFFFFB27A, contentAlpha));
        }
        if (!statusMessage.isBlank()) {
            drawFittedString(graphics, statusMessage, contentLeft + 8, 101,
                    Math.max(0, contentWidth - 16), withAlpha(0xFFFFB27A, contentAlpha));
        }
        if (page == Page.ADMIN && adminToken != null) {
            renderAdminActions(graphics, contentLeft, contentAlpha);
        } else if (page == Page.ADMIN) {
            drawCenteredFittedString(graphics, "Enter the admin password configured in Render.",
                    contentLeft + contentWidth / 2, 119, Math.max(0, contentWidth - 16),
                    withAlpha(0xFFB7BDC5, contentAlpha));
        } else if (page == Page.PROFILES && selectedPlayerUuid == null && !searchResults.isEmpty()) {
            renderSearchResults(graphics, contentLeft, contentWidth, contentAlpha);
        } else if (page == Page.SETTINGS) {
            drawFittedString(graphics, "Saved locally in config/grindpvp.json", contentLeft + 8, 88,
                    Math.max(0, contentWidth - 16), withAlpha(0xFFB7BDC5, contentAlpha));
            drawFittedString(graphics, "GUI opacity: " + settings.guiOpacity + " / 255",
                    contentLeft + 8, 258, Math.max(0, contentWidth - 16),
                    withAlpha(0xFFB7BDC5, contentAlpha));
            drawFittedString(graphics, "GUI corner radius: " + settings.guiCornerRadius + " / 20",
                    contentLeft + 8, 308, Math.max(0, contentWidth - 16),
                    withAlpha(0xFFB7BDC5, contentAlpha));
        } else {
            renderData(graphics, contentLeft, contentWidth, contentAlpha);
        }

        super.render(graphics, adjustedMouseX - Math.round(contentOffsetX), adjustedMouseY, delta);
        graphics.pose().popMatrix();
        graphics.pose().popMatrix();
        renderCursorGlow(graphics, mouseX, mouseY, 255);
        hoveredElementsLastFrame.clear();
        hoveredElementsLastFrame.addAll(hoveredElements);
    }

    private void renderNavigation(GuiGraphics graphics, int mouseX, int mouseY, int alpha) {
        String[] labels = {"Leaderboard", "My Profile", "Profiles"};
        Page[] pages = {Page.LEADERBOARD, Page.MY_PROFILE, Page.PROFILES};
        for (int i = 0; i < labels.length; i++) {
            int[] bounds = navigationTabBounds(i);
            int x = bounds[0];
            int width = bounds[1];
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= 23 && mouseY <= 42;
            int color = hoverColor("nav:" + labels[i], hovered, 0xFF343A41, 0xFF535A62);
            if (page != pages[i]) {
                fillRounded(graphics, x, 23, x + width, 42, withAlpha(color, alpha));
            }
        }
        int adminLeft = adminButtonLeft();
        int adminRight = adminLeft + 76;
        boolean adminHovered = mouseX >= adminLeft && mouseX <= adminRight && mouseY >= 23 && mouseY <= 42;
        int adminColor = hoverColor("nav:Admin", adminHovered, 0xFF343A41, 0xFF535A62);
        if (page != Page.ADMIN) {
            fillRounded(graphics, adminLeft, 23, adminRight, 42, withAlpha(adminColor, alpha));
        }
        int settingsLeft = settingsButtonLeft();
        boolean settingsHovered = mouseX >= settingsLeft && mouseX <= settingsLeft + 76
                && mouseY >= 23 && mouseY <= 42;
        int settingsColor = hoverColor("nav:Settings", settingsHovered, 0xFF343A41, 0xFF535A62);
        if (page != Page.SETTINGS) {
            fillRounded(graphics, settingsLeft, 23, settingsLeft + 76, 42, withAlpha(settingsColor, alpha));
        }

        int[] indicator = navigationIndicatorBounds();
        fillRounded(graphics, indicator[0], 23, indicator[0] + indicator[1], 42,
                withAlpha(0xFF503321, alpha));
        for (int i = 0; i < labels.length; i++) {
            int[] bounds = navigationTabBounds(i);
            drawCenteredFittedString(graphics, labels[i], bounds[0] + bounds[1] / 2, 29,
                    Math.max(0, bounds[1] - 8),
                    withAlpha(page == pages[i] ? 0xFFFFFFFF : 0xFFD4D7DB, alpha));
        }
        drawCenteredFittedString(graphics, Component.literal("GrindPvP").withStyle(ChatFormatting.BOLD),
                this.width / 2, 29, 160, withAlpha(ACCENT_COLOR, alpha));
        drawCenteredFittedString(graphics, "Settings", settingsLeft + 38, 29, 68,
                withAlpha(page == Page.SETTINGS ? 0xFFFFFFFF : 0xFFD4D7DB, alpha));
        drawCenteredFittedString(graphics, "Admin", (adminLeft + adminRight) / 2, 29,
                Math.max(0, adminRight - adminLeft - 8),
                withAlpha(page == Page.ADMIN ? 0xFFFFFFFF : 0xFFD4D7DB, alpha));
    }

    private void renderKitSidebar(GuiGraphics graphics, int mouseX, int mouseY, int alpha) {
        int left = 25;
        int top = 68;
        int bottom = this.height - 29;
        fillRounded(graphics, left, top, 150, bottom, withBackgroundOpacity(SIDEBAR_COLOR, alpha));
        drawFittedString(graphics, "RANKED KITS", left + 9, top + 4, 116, withAlpha(0xFF9EA4AC, alpha));
        if (top + 44 + this.font.lineHeight <= bottom) {
            drawFittedString(graphics, "MainKits", left + 9, top + 44, 116, withAlpha(ACCENT_COLOR, alpha));
        }
        int subHeadingY = top + 54 + 8 + 7 * 22 + 20 + 8;
        if (subHeadingY + this.font.lineHeight <= bottom) {
            drawFittedString(graphics, "SubKits", left + 9, subHeadingY, 116, withAlpha(ACCENT_COLOR, alpha));
        }
        if (page != Page.SETTINGS) {
            int[] kitIndicator = kitIndicatorBounds();
            fillRounded(graphics, left + 4, kitIndicator[0], 145, kitIndicator[0] + 20,
                    withAlpha(0xFF423126, alpha));
        }

        for (int index = 0; index < KIT_IDS.length; index++) {
            int y = kitRowY(index, top);
            if (y + 20 > bottom) {
                continue;
            }
            boolean hovered = mouseX >= left + 4 && mouseX <= 145 && mouseY >= y && mouseY < y + 20;
            boolean selected = KIT_IDS[index].equals(selectedKit);
            int color = hoverColor("kit:" + KIT_IDS[index], hovered, SIDEBAR_COLOR, 0xFF30353B);
            if (!selected && (hovered || hoverProgress.getOrDefault("kit:" + KIT_IDS[index], 0.0f) > 0)) {
                fillRounded(graphics, left + 4, y, 145, y + 20,
                        withBackgroundOpacity(color, alpha));
            }
            renderKitIcon(graphics, KIT_IDS[index], left + 8, y + 2);
            drawFittedString(graphics, KIT_LABELS[index], left + 29, y + 6, 108,
                    withAlpha(selected ? 0xFFFFFFFF : 0xFFC9CDD2, alpha));
        }
    }

    private void renderData(GuiGraphics graphics, int left, int width, int alpha) {
        if (loading) {
            drawCenteredFittedString(graphics, "Loading GrindPvP data...", left + width / 2, 150,
                    Math.max(0, width - 16),
                    withAlpha(0xFFFFFFFF, alpha));
            return;
        }
        if (page == Page.LEADERBOARD || page == Page.PROFILES && selectedPlayerUuid == null) {
            drawFittedString(graphics, "Page " + apiPage + "  ·  " + totalItems + " players",
                    left + 8, this.height - 48, Math.max(0, width - 16), withAlpha(0xFF939AA3, alpha));
        }
        if (!response.has("items") && !response.has("kits")) {
            drawCenteredFittedString(graphics, "Select a page or refresh to load backend data.",
                    left + width / 2, 150, Math.max(0, width - 16), withAlpha(0xFFB7BDC5, alpha));
            return;
        }
        if (page == Page.MY_PROFILE || selectedPlayerUuid != null && page == Page.PROFILES) {
            renderProfile(graphics, left, width, alpha);
        } else {
            renderLeaderboard(graphics, left, width, alpha);
        }
    }

    private void renderLeaderboard(GuiGraphics graphics, int left, int width, int alpha) {
        JsonArray rows = response.has("items") && response.get("items").isJsonArray()
                ? response.getAsJsonArray("items")
                : new JsonArray();
        if (rows.isEmpty()) {
            drawCenteredFittedString(graphics, "No verified players are ranked here yet.", left + width / 2,
                    151, Math.max(0, width - 16), withAlpha(0xFFB7BDC5, alpha));
            return;
        }
        int top = 128;
        graphics.drawString(this.font, "#", left + 8, top, withAlpha(0xFF939AA3, alpha));
        int right = left + Math.max(0, width - 8);
        int pointsX = Math.max(left + 42, right - 46);
        int tierX = Math.max(left + 42, pointsX - 60);
        int playerX = left + 42;
        int playerWidth = Math.max(0, tierX - playerX - 8);
        int tierWidth = Math.max(0, pointsX - tierX - 8);
        int pointsWidth = Math.max(0, right - pointsX);
        drawFittedString(graphics, "PLAYER", playerX, top, playerWidth, withAlpha(0xFF939AA3, alpha));
        drawFittedString(graphics, "TIER", tierX, top, tierWidth, withAlpha(0xFF939AA3, alpha));
        drawFittedString(graphics, "POINTS", pointsX, top, pointsWidth, withAlpha(0xFF939AA3, alpha));
        int currentY = top + 19;
        int availableRows = Math.max(0, (this.height - currentY - 38) / 23);
        listScroll = Math.min(listScroll, Math.max(0, rows.size() - availableRows));
        for (int index = listScroll; index < Math.min(rows.size(), listScroll + availableRows); index++) {
            JsonObject row = rows.get(index).getAsJsonObject();
            int y = currentY + (index - listScroll) * 23;
            String uuid = stringValue(row, "uuid");
            boolean own = Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.getUUID().toString().equalsIgnoreCase(uuid);
            boolean hovered = mouseOverRow(left + 4, y - 3, left + width - 4, y + 17);
            int rowColor = hoverColor("leaderboard:" + index, hovered,
                    own ? 0xFF493322 : ROW_COLOR, 0xFF343A41);
            fillRounded(graphics, left + 4, y - 3, left + width - 4, y + 17,
                    withBackgroundOpacity(rowColor, alpha));
            int rank = intValue(row, "rank");
            if (rank >= 1 && rank <= PODIUM_ICONS.length) {
                graphics.renderItem(PODIUM_ICONS[rank - 1].getDefaultInstance(), left + 8, y - 1);
            } else {
                drawFittedString(graphics, "#" + rank, left + 8, y + 2, 28,
                        withAlpha(own ? ACCENT_COLOR : 0xFFB7BDC5, alpha));
            }
            String role = stringValue(row, "role_name");
            String name = role.isBlank() ? stringValue(row, "username") : "[" + role.toUpperCase() + "] "
                    + stringValue(row, "username");
            drawFittedString(graphics, name, playerX, y + 2, playerWidth,
                    withAlpha(own ? ACCENT_COLOR : titleColor(stringValue(row, "title_name")), alpha));
            drawFittedString(graphics, stringValue(row, "tier"), tierX, y + 2, tierWidth,
                    withAlpha(tierColor(stringValue(row, "tier")), alpha));
            String points = row.has("points") ? stringValue(row, "points") : stringValue(row, "average_points");
            drawFittedString(graphics, points, pointsX, y + 2, pointsWidth,
                    withAlpha(0xFFD4D7DB, alpha));
        }
        if (rows.size() > availableRows) {
            drawFittedString(graphics, "Scroll to browse the leaderboard", left + 8, 113,
                    Math.max(0, width - 16), withAlpha(0xFF939AA3, alpha));
        }
    }

    private void renderProfile(GuiGraphics graphics, int left, int width, int alpha) {
        String username = stringValue(response, "username");
        int cardX = left + 6;
        int cardY = 116;
        int cardRight = left + width - 6;
        int cardBottom = 207;
        int portraitSize = 68;
        int portraitX = cardX + 12;
        int portraitY = cardY + 9;
        fillRounded(graphics, cardX, cardY, cardRight, cardBottom,
                withBackgroundOpacity(0xFF24282D, alpha));
        int cardInset = cornerInset(Math.min(settings.guiCornerRadius, 12), 1);
        graphics.fill(cardX + cardInset, cardY, cardRight - cardInset, cardY + 1,
                withAlpha(0xFF3B4148, alpha));
        graphics.fill(portraitX - 2, portraitY - 2, portraitX + portraitSize + 2,
                portraitY + portraitSize + 2, withAlpha(0xFF434A52, alpha));
        renderProfileSkin(graphics, portraitX, portraitY, portraitSize, alpha);

        int infoX = portraitX + portraitSize + 18;
        int infoWidth = Math.max(0, cardRight - infoX - 12);
        drawFittedString(graphics, username, infoX, cardY + 13, infoWidth,
                withAlpha(titleColor(stringValue(response, "title_name")), alpha));
        String overallTier = stringValue(response, "overall_tier");
        fillRounded(graphics, infoX, cardY + 35, infoX + Math.min(infoWidth, 74), cardY + 55,
                withBackgroundOpacity(0xFF343A41, alpha));
        drawCenteredFittedString(graphics, overallTier, infoX + Math.min(infoWidth, 74) / 2,
                cardY + 41, Math.max(0, Math.min(infoWidth, 74) - 12),
                withAlpha(tierColor(overallTier), alpha));
        drawFittedString(graphics, "Verified  ·  " + stringValue(response, "verified"),
                infoX + 88, cardY + 40, Math.max(0, infoWidth - 90),
                withAlpha(0xFFB7BDC5, alpha));
        drawFittedString(graphics, "Role: " + stringValue(response, "role_name")
                        + "   ·   Title: " + stringValue(response, "title_name"),
                infoX, cardY + 64, infoWidth, withAlpha(0xFFB7BDC5, alpha));
        drawFittedString(graphics, "Average points: " + stringValue(response, "overall_average_points"),
                infoX, cardY + 78, infoWidth, withAlpha(0xFF939AA3, alpha));
        JsonArray kits = response.has("kits") && response.get("kits").isJsonArray()
                ? response.getAsJsonArray("kits")
                : new JsonArray();
        int kitsTop = 222;
        int availableRows = Math.max(0, (this.height - kitsTop - 37) / 22);
        listScroll = Math.min(listScroll, Math.max(0, kits.size() - availableRows));
        for (int index = listScroll; index < Math.min(kits.size(), listScroll + availableRows); index++) {
            JsonObject kit = kits.get(index).getAsJsonObject();
            int rowY = kitsTop + (index - listScroll) * 22;
            fillRounded(graphics, left + 4, rowY - 2, left + width - 4, rowY + 18,
                    withBackgroundOpacity(ROW_COLOR, alpha));
            renderKitIcon(graphics, stringValue(kit, "kit"), left + 8, rowY - 1);
            int rowRight = left + Math.max(0, width - 8);
            int statsX = left + Math.max(29, width * 62 / 100);
            int tierX = left + Math.max(29, width * 40 / 100);
            int winrateWidth = Math.min(32, Math.max(0, width * 18 / 100));
            int winrateX = rowRight - winrateWidth;
            int kitTextWidth = Math.max(0, tierX - left - 34);
            int tierWidth = Math.max(0, statsX - tierX - 8);
            int statsWidth = Math.max(0, winrateX - statsX - 8);
            drawFittedString(graphics, stringValue(kit, "kit").toUpperCase(), left + 29, rowY + 3,
                    kitTextWidth,
                    withAlpha(0xFFD4D7DB, alpha));
            drawFittedString(graphics, stringValue(kit, "tier"), tierX, rowY + 3, tierWidth,
                    withAlpha(tierColor(stringValue(kit, "tier")), alpha));
            drawFittedString(graphics, stringValue(kit, "wins") + "W / " + stringValue(kit, "losses") + "L",
                    statsX, rowY + 3, statsWidth, withAlpha(0xFFB7BDC5, alpha));
            drawFittedString(graphics, stringValue(kit, "winrate") + "%", winrateX, rowY + 3, winrateWidth,
                    withAlpha(0xFFB7BDC5, alpha));
        }
    }

    private void renderProfileSkin(GuiGraphics graphics, int x, int y, int size, int alpha) {
        if (profileSkinLoading || profileSkin == null && !profileSkinFailed) {
            drawCenteredFittedString(graphics, "Loading skin", x + size / 2, y + size + 5, size + 24,
                    withAlpha(0xFFB7BDC5, alpha));
        } else if (profileSkinFailed) {
            drawCenteredFittedString(graphics, "Default model", x + size / 2, y + size + 5, size + 24,
                    withAlpha(0xFF939AA3, alpha));
        }
    }

    private void addProfileSkinPreview(int contentLeft) {
        UUID profileUuid = currentProfileUuid();
        if (profileUuid == null) {
            return;
        }

        int portraitSize = 68;
        PlayerSkinWidget preview = new PlayerSkinWidget(
                portraitSize,
                portraitSize,
                Minecraft.getInstance().getEntityModels(),
                () -> profileSkin != null && profileUuid.toString().equalsIgnoreCase(profileSkinUuid)
                        ? profileSkin
                        : DefaultPlayerSkin.get(profileUuid)
        );
        preview.setPosition(contentLeft + 18, 125);
        this.addRenderableWidget(preview);
    }

    private UUID currentProfileUuid() {
        String uuidValue = page == Page.MY_PROFILE
                ? Minecraft.getInstance().player == null
                        ? null
                        : Minecraft.getInstance().player.getUUID().toString()
                : page == Page.PROFILES ? selectedPlayerUuid : null;
        if (uuidValue == null) {
            return null;
        }
        try {
            return UUID.fromString(uuidValue);
        } catch (IllegalArgumentException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not create profile preview for an invalid UUID.", error);
            return null;
        }
    }

    private void renderSearchResults(GuiGraphics graphics, int left, int width, int alpha) {
        int top = 128;
        int availableRows = Math.max(0, (this.height - top - 40) / 24);
        listScroll = Math.min(listScroll, Math.max(0, searchResults.size() - availableRows));
        for (int index = listScroll; index < Math.min(searchResults.size(), listScroll + availableRows); index++) {
            JsonObject player = searchResults.get(index).getAsJsonObject();
            int y = top + (index - listScroll) * 24;
            boolean hovered = mouseOverRow(left + 4, y - 3, left + width - 4, y + 17);
            fillRounded(graphics, left + 4, y - 3, left + width - 4, y + 17,
                    withBackgroundOpacity(hoverColor("search:" + index, hovered, ROW_COLOR, 0xFF343A41), alpha));
            int openX = left + Math.max(0, width - 40);
            int tierX = Math.max(left + 10, openX - 70);
            drawFittedString(graphics, stringValue(player, "username"), left + 10, y + 2,
                    Math.max(0, tierX - left - 18), withAlpha(0xFFFFFFFF, alpha));
            drawFittedString(graphics, stringValue(player, "overall_tier"), tierX, y + 2,
                    Math.max(0, openX - tierX - 8), withAlpha(ACCENT_COLOR, alpha));
            drawFittedString(graphics, "OPEN", openX, y + 2,
                    Math.max(0, left + width - 8 - openX), withAlpha(0xFFB7BDC5, alpha));
        }
    }

    private void renderAdminActions(GuiGraphics graphics, int left, int alpha) {
        drawFittedString(graphics, "Signed in  ·  audit entries: "
                        + (response.has("total") ? stringValue(response, "total") : "—"),
                left + 8, 77, Math.max(0, contentWidth() - 150), withAlpha(0xFFB7BDC5, alpha));
        drawCenteredFittedString(graphics, "Sign out", this.width - 74, 55, 84,
                withAlpha(0xFFFFB27A, alpha));
        int y = 292;
        int buttonWidth = adminActionButtonWidth();
        String[] labels = {
                "Register / update player", "Verify name", "Unverify name", "Set role", "Remove role",
                "Set title", "Remove title", "Set tier for " + selectedKit, "Clear tier",
                "Submit match for " + selectedKit
        };
        for (int i = 0; i < labels.length; i++) {
            int row = i / 2;
            int column = i % 2;
            int x = left + 8 + column * (buttonWidth + 8);
            int buttonY = y + row * 25;
            boolean hovered = mouseOverRow(x, buttonY, x + buttonWidth, buttonY + 20);
            fillRounded(graphics, x, buttonY, x + buttonWidth, buttonY + 20,
                    withBackgroundOpacity(hoverColor("admin:" + i, hovered, 0xFF343A41, 0xFF535A62), alpha));
            drawCenteredFittedString(graphics, labels[i], x + buttonWidth / 2, buttonY + 6,
                    Math.max(0, buttonWidth - 8),
                    withAlpha(0xFFE6E8EB, alpha));
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double contentMouseX = event.x() - contentTransitionOffsetX();
        double widgetMouseX = contentMouseX;
        double mouseY = event.y() - slideOffset();
        var adjustedEvent = new net.minecraft.client.input.MouseButtonEvent(
                widgetMouseX, mouseY, event.buttonInfo()
        );
        if (super.mouseClicked(adjustedEvent, doubleClick)) {
            return true;
        }
        double mouseX = event.x();
        int button = event.button();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (mouseY >= 23 && mouseY <= 42) {
            for (int index = 0; index < 3; index++) {
                int[] bounds = navigationTabBounds(index);
                if (mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[1]) {
                    playClickSound();
                    changePage(new Page[]{Page.LEADERBOARD, Page.MY_PROFILE, Page.PROFILES}[index]);
                    return true;
                }
            }
            if (mouseX >= settingsButtonLeft() && mouseX <= settingsButtonLeft() + 76) {
                playClickSound();
                changePage(Page.SETTINGS);
                return true;
            }
            if (mouseX >= adminButtonLeft() && mouseX <= adminButtonLeft() + 76) {
                playClickSound();
                changePage(Page.ADMIN);
                return true;
            }
        }
        if (page == Page.LEADERBOARD || page == Page.MY_PROFILE || page == Page.PROFILES || page == Page.ADMIN) {
            if (mouseX >= 25 && mouseX <= 150 && mouseY >= 73 && mouseY < this.height - 29) {
                int index = kitIndexAt(mouseY);
                if (index >= 0) {
                    playClickSound();
                    if (!KIT_IDS[index].equals(selectedKit)) {
                        beginKitTransition(index);
                    }
                    selectedKit = KIT_IDS[index];
                    apiPage = 1;
                    listScroll = 0;
                    if (page == Page.LEADERBOARD) {
                        refreshData();
                    }
                    return true;
                }
            }
        }
        if (page == Page.LEADERBOARD && contentMouseX >= contentLeft()
                && mouseY >= 144 && response.has("items")) {
            int index = listScroll + (int) ((mouseY - 144) / 23);
            JsonArray rows = response.getAsJsonArray("items");
            if (index >= 0 && index < rows.size()
                    && mouseY < 164 + (index - listScroll) * 23) {
                playClickSound();
                selectedPlayerUuid = stringValue(rows.get(index).getAsJsonObject(), "uuid");
                loadProfile(selectedPlayerUuid);
                return true;
            }
        }
        if (page == Page.PROFILES && selectedPlayerUuid == null
                && mouseY >= 125 && response.has("items")) {
            int index = listScroll + (int) ((mouseY - 125) / 24);
            if (index >= 0 && index < searchResults.size()
                    && mouseY < 145 + (index - listScroll) * 24) {
                playClickSound();
                selectedPlayerUuid = stringValue(searchResults.get(index).getAsJsonObject(), "uuid");
                loadProfile(selectedPlayerUuid);
                return true;
            }
        }
        if (page == Page.ADMIN && adminToken != null && mouseY >= 292 && mouseY < 412) {
            int row = (int) ((mouseY - 292) / 25);
            int rowY = 292 + row * 25;
            int buttonWidth = adminActionButtonWidth();
            int secondX = contentLeft() + 16 + buttonWidth;
            int column = mouseX >= contentLeft() + 8 && mouseX < contentLeft() + 8 + buttonWidth ? 0
                    : mouseX >= secondX && mouseX < secondX + buttonWidth ? 1 : -1;
            if (row < 5 && mouseY < rowY + 20 && column >= 0) {
                playClickSound();
                performAdminAction(row * 2 + column);
                return true;
            }
        }
        if (page == Page.ADMIN && adminToken != null
                && mouseX >= this.width - 120 && mouseX <= this.width - 28
                && mouseY >= 46 && mouseY <= 65) {
            playClickSound();
            adminLogout();
            return true;
        }
        if (page == Page.PROFILES && selectedPlayerUuid != null) {
            int breadcrumbX = contentLeft() + 8 + this.font.width(pageTitle()) + 12;
            int breadcrumbWidth = this.font.width("< Search");
            if (contentMouseX < breadcrumbX || contentMouseX > breadcrumbX + breadcrumbWidth
                    || mouseY < 48 || mouseY > 66) {
                return false;
            }
            playClickSound();
            String previousSearch = searchInput == null ? "" : searchInput.getValue();
            selectedPlayerUuid = null;
            response = new JsonObject();
            loading = false;
            statusMessage = "";
            profileSoundPending = false;
            resetProfileSkin();
            listScroll = 0;
            this.clearWidgets();
            this.init();
            if (searchInput != null) {
                searchInput.setValue(previousSearch);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        listScroll = Math.max(0, listScroll + (verticalAmount < 0 ? 1 : -1));
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        double adjustedX = mouseX - contentTransitionOffsetX();
        super.mouseMoved(adjustedX, mouseY - slideOffset());
        double adjustedY = mouseY - slideOffset();
        boolean hovered = soundButtons.stream().anyMatch(button -> button.isMouseOver(adjustedX, adjustedY));
        if (hovered && !widgetHovered && settings.hoverSoundsEnabled) {
            playInterfaceSound(SoundEvents.UI_BUTTON_CLICK, 0.12f);
        }
        widgetHovered = hovered;
    }

    private void changePage(Page next) {
        if (page == next) {
            profileSoundPending = next == Page.MY_PROFILE;
            refreshData();
            return;
        }
        beginPageTransition(next);
        playInterfaceSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.12f);
        page = next;
        profileSoundPending = next == Page.MY_PROFILE;
        apiPage = 1;
        listScroll = 0;
        totalItems = 0;
        response = new JsonObject();
        searchResults = new JsonArray();
        selectedPlayerUuid = null;
        resetProfileSkin();
        statusMessage = "";
        this.clearWidgets();
        this.init();
        refreshData();
    }

    private void refreshData() {
        if (page == Page.LEADERBOARD) {
            String path = ("overall".equals(selectedKit)
                    ? "/leaderboard/overall"
                    : "/leaderboard/" + selectedKit) + "?page=" + apiPage + "&page_size=25";
            requestData(path, null);
        } else if (page == Page.MY_PROFILE) {
            if (Minecraft.getInstance().player == null) {
                statusMessage = "Join a world or server to load your Minecraft profile.";
                return;
            }
            requestData("/profiles/" + Minecraft.getInstance().player.getUUID(), adminToken);
        } else if (page == Page.PROFILES && selectedPlayerUuid == null
                && searchInput != null && !searchInput.getValue().isBlank()) {
            searchPlayers();
        } else if (page == Page.ADMIN && adminToken != null) {
            requestData("/admin/logs?page=1&page_size=20", adminToken);
        }
    }

    private void requestData(String path, String token) {
        loading = true;
        statusMessage = "Loading...";
        GrindPvPApi.get(path, token, result -> {
            if (path.startsWith("/profiles/") && !isCurrentProfileRequest(path)) {
                return;
            }
            loading = false;
            if (result.success()) {
                if (path.startsWith("/profiles/")) {
                    String requestedUuid = path.substring("/profiles/".length());
                    String responseUuid = stringValue(result.data(), "uuid");
                    if (!requestedUuid.equalsIgnoreCase(responseUuid)) {
                        profileSoundPending = false;
                        resetProfileSkin();
                        profileSkinFailed = true;
                        response = new JsonObject();
                        statusMessage = "Profile response did not match the requested player.";
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Rejected profile response with a mismatched UUID.");
                        return;
                    }
                }
                response = result.data();
                totalItems = intValue(response, "total");
                statusMessage = "";
                if (path.startsWith("/profiles/") && profileSoundPending
                        && (page == Page.MY_PROFILE || page == Page.PROFILES && selectedPlayerUuid != null)) {
                    playInterfaceSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.45f);
                    profileSoundPending = false;
                }
                if (path.startsWith("/profiles/")) {
                    loadProfileSkin(stringValue(response, "uuid"), stringValue(response, "username"));
                }
            } else {
                if (path.startsWith("/profiles/")) {
                    profileSoundPending = false;
                    profileSkinLoading = false;
                    if (profileSkin == null) {
                        profileSkinFailed = true;
                    }
                }
                statusMessage = result.error();
            }
        });
    }

    private void loadProfile(String uuid) {
        beginPageTransition(Page.PROFILES);
        playInterfaceSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.12f);
        page = Page.PROFILES;
        selectedPlayerUuid = uuid;
        resetProfileSkin();
        profileSoundPending = true;
        this.clearWidgets();
        this.init();
        requestData("/profiles/" + uuid, adminToken);
    }

    private boolean isCurrentProfileRequest(String path) {
        String requestedUuid = path.substring("/profiles/".length());
        if (page == Page.MY_PROFILE && Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.getUUID().toString().equalsIgnoreCase(requestedUuid);
        }
        return page == Page.PROFILES && selectedPlayerUuid != null
                && selectedPlayerUuid.equalsIgnoreCase(requestedUuid);
    }

    private void resetProfileSkin() {
        profileSkinUuid = null;
        profileSkin = null;
        profileSkinLoading = false;
        profileSkinFailed = false;
    }

    private void loadProfileSkin(String uuidValue, String username) {
        if (uuidValue.isBlank() || username.isBlank() || uuidValue.equalsIgnoreCase(profileSkinUuid)) {
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidValue);
        } catch (IllegalArgumentException error) {
            profileSkinUuid = uuidValue;
            profileSkinLoading = false;
            profileSkinFailed = true;
            LOGGER.log(System.Logger.Level.WARNING, "Profile returned an invalid UUID for skin lookup.", error);
            return;
        }

        profileSkinUuid = uuid.toString();
        profileSkin = null;
        profileSkinLoading = true;
        profileSkinFailed = false;
        Minecraft minecraft = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> minecraft.services().profileResolver().fetchById(uuid))
                .thenCompose(profile -> profile
                        .map(minecraft.getSkinManager()::get)
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())))
                .whenComplete((skin, error) -> minecraft.execute(() -> {
                    if (!uuid.toString().equalsIgnoreCase(profileSkinUuid)) {
                        return;
                    }
                    profileSkinLoading = false;
                    if (error != null) {
                        profileSkinFailed = true;
                        LOGGER.log(System.Logger.Level.WARNING, "Could not load Minecraft skin for " + username, error);
                        return;
                    }
                    Optional<PlayerSkin> loadedSkin = skin == null ? Optional.empty() : skin;
                    if (loadedSkin.isPresent()) {
                        profileSkin = loadedSkin.get();
                    } else {
                        profileSkinFailed = true;
                        LOGGER.log(System.Logger.Level.INFO, "Minecraft profile has no downloadable skin: " + username);
                    }
                }));
    }

    private void searchPlayers() {
        if (searchInput == null || searchInput.getValue().isBlank()) {
            statusMessage = "Enter a Minecraft username to search.";
            return;
        }
        selectedPlayerUuid = null;
        listScroll = 0;
        loading = true;
        statusMessage = "Searching...";
        String query = GrindPvPApi.encodeQuery(searchInput.getValue().trim());
        GrindPvPApi.get("/players?search=" + query + "&page=" + apiPage + "&page_size=25", result -> {
            loading = false;
            if (result.success()) {
                response = result.data();
                searchResults = result.data().has("items") ? result.data().getAsJsonArray("items") : new JsonArray();
                totalItems = intValue(response, "total");
                statusMessage = searchResults.isEmpty() ? "No verified players found." : "Select a result to open the profile.";
            } else {
                statusMessage = result.error();
            }
        });
    }

    private void adminLogin() {
        if (adminPasswordInput == null || adminPasswordInput.getValue().isBlank()) {
            statusMessage = "Enter the admin password configured in Render.";
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("password", adminPasswordInput.getValue());
        loading = true;
        statusMessage = "Signing in...";
        GrindPvPApi.post("/admin/login", payload, null, result -> {
            loading = false;
            if (!result.success()) {
                statusMessage = result.error();
                return;
            }
            String issuedToken = stringValue(result.data(), "access_token");
            if (issuedToken.isBlank()) {
                statusMessage = "Backend login response did not include an admin session.";
                return;
            }
            adminToken = issuedToken;
            adminPasswordInput.setValue("");
            adminPasswordInput = null;
            statusMessage = "Admin session active in memory until logout or restart.";
            this.clearWidgets();
            this.init();
            refreshData();
        });
    }

    private void adminLogout() {
        GrindPvPApi.post("/admin/logout", new JsonObject(), adminToken, result -> {
            if (!result.success()) {
                statusMessage = result.error();
                return;
            }
            adminToken = null;
            response = new JsonObject();
            statusMessage = "Admin session ended.";
            this.clearWidgets();
            this.init();
        });
    }

    private void performAdminAction(int action) {
        if (action == 9) {
            submitAdminMatch();
            return;
        }
        if (action == 0) {
            JsonObject payload = new JsonObject();
            payload.addProperty("username", fieldValue(playerNameInput));
            GrindPvPApi.post("/admin/players", payload, adminToken, this::showAdminResult);
        } else if (action == 1 || action == 2) {
            JsonObject payload = new JsonObject();
            payload.addProperty("username", fieldValue(playerNameInput));
            payload.addProperty("verified", action == 1);
            GrindPvPApi.post("/admin/verify", payload, adminToken, result -> {
                showAdminResult(result);
                if (action == 1 && result.success()) {
                    playInterfaceSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.35f);
                }
            });
        } else if (action == 3 || action == 4) {
            JsonObject payload = new JsonObject();
            payload.addProperty("username", fieldValue(playerNameInput));
            if (action == 3) {
                payload.addProperty("role", fieldValue(roleInput));
            } else {
                payload.add("role", null);
            }
            GrindPvPApi.post("/admin/roles", payload, adminToken, this::showAdminResult);
        } else if (action == 5 || action == 6) {
            JsonObject payload = new JsonObject();
            payload.addProperty("username", fieldValue(playerNameInput));
            if (action == 5) {
                payload.addProperty("title", fieldValue(titleInput));
            } else {
                payload.add("title", null);
            }
            GrindPvPApi.post("/admin/titles", payload, adminToken, this::showAdminResult);
        } else {
            JsonObject payload = new JsonObject();
            payload.addProperty("username", fieldValue(playerNameInput));
            payload.addProperty("kit", selectedKit);
            if (action == 7) {
                payload.addProperty("tier", fieldValue(tierInput));
            } else {
                payload.add("tier", null);
            }
            GrindPvPApi.post("/admin/tiers", payload, adminToken, this::showAdminResult);
        }
    }

    private void submitAdminMatch() {
        if ("overall".equals(selectedKit)) {
            statusMessage = "Choose a ranked kit before submitting a result.";
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("idempotency_key", "admin:" + UUID.randomUUID());
        payload.addProperty("kit", selectedKit);
        payload.addProperty("winner_username", fieldValue(winnerInput));
        payload.addProperty("loser_username", fieldValue(loserInput));
        GrindPvPApi.post("/admin/matches", payload, adminToken, this::showAdminResult);
    }

    private void showAdminResult(GrindPvPApi.ApiResult result) {
        if (result.success()) {
            response = result.data();
            statusMessage = "Admin action completed.";
            PlayerPresentationCache.refresh();
        } else {
            statusMessage = result.error();
        }
    }

    private void saveSettings(String name) {
        statusMessage = settings.save() ? name + " saved locally." : name + " could not be saved.";
    }

    private String animationSettingLabel() {
        return "GUI animations: " + onOff(settings.animationsEnabled);
    }

    private String soundSettingLabel() {
        return "GUI sounds: " + onOff(settings.soundsEnabled);
    }

    private String hoverSettingLabel() {
        return "Hover sounds: " + onOff(settings.hoverSoundsEnabled);
    }

    private String nametagPingSettingLabel() {
        return "Nametag ping: " + onOff(settings.nametagPingEnabled);
    }

    private String nametagTierSettingLabel() {
        return "Nametag tier: " + onOff(settings.nametagTierEnabled);
    }

    private String nametagRankingSettingLabel() {
        return "Nametag ranking: " + onOff(settings.nametagRankingEnabled);
    }

    private String nametagRoleSettingLabel() {
        return "Nametag role: " + onOff(settings.nametagRoleEnabled);
    }

    private String pageTitle() {
        return switch (page) {
            case LEADERBOARD -> "Leaderboard  /  " + displayKit();
            case MY_PROFILE -> "My Profile";
            case PROFILES -> selectedPlayerUuid == null ? "Find a Player" : "Player Profile";
            case SETTINGS -> "Settings";
            case ADMIN -> "Admin Panel";
        };
    }

    private String displayKit() {
        for (int i = 0; i < KIT_IDS.length; i++) {
            if (KIT_IDS[i].equals(selectedKit)) {
                return KIT_LABELS[i];
            }
        }
        return selectedKit;
    }

    private int contentLeft() {
        return page == Page.SETTINGS ? 35 : 160;
    }

    private int contentWidth() {
        return Math.max(0, this.width - contentLeft() - 24);
    }

    private int[] navigationTabBounds(int index) {
        int[] baseWidths = {74, 80, 74};
        int available = Math.max(0, this.width - 296 - 40);
        float scale = Math.min(1.0f, available / 228.0f);
        int x = 28;
        int width = 0;
        for (int current = 0; current <= index; current++) {
            width = Math.round(baseWidths[current] * scale);
            if (current < index) {
                x += width + 6;
            }
        }
        return new int[]{x, width};
    }

    private int[] navigationBounds(Page targetPage) {
        return switch (targetPage) {
            case LEADERBOARD -> navigationTabBounds(0);
            case MY_PROFILE -> navigationTabBounds(1);
            case PROFILES -> navigationTabBounds(2);
            case ADMIN -> new int[]{adminButtonLeft(), 76};
            case SETTINGS -> new int[]{settingsButtonLeft(), 76};
        };
    }

    private int adminButtonLeft() {
        return settingsButtonLeft() - 6 - 76;
    }

    private int settingsButtonLeft() {
        return this.width - 16 - 6 - 76;
    }

    private int[] navigationIndicatorBounds() {
        int[] target = navigationBounds(page);
        if (!settings.animationsEnabled || navigationTransitionStartedAt == 0) {
            return target;
        }
        float progress = Math.min(1.0f,
                (System.nanoTime() - navigationTransitionStartedAt) / 190_000_000.0f);
        float eased = progress * progress * (3.0f - 2.0f * progress);
        return new int[]{
                Math.round(navigationIndicatorFromX
                        + (target[0] - navigationIndicatorFromX) * eased),
                Math.round(navigationIndicatorFromWidth
                        + (target[1] - navigationIndicatorFromWidth) * eased)
        };
    }

    private int[] kitIndicatorBounds() {
        int targetY = kitRowY(kitIndex(selectedKit), 68);
        if (!settings.animationsEnabled || kitTransitionStartedAt == 0) {
            return new int[]{targetY, 20};
        }
        float progress = Math.min(1.0f,
                (System.nanoTime() - kitTransitionStartedAt) / 190_000_000.0f);
        progress = progress * progress * (3.0f - 2.0f * progress);
        return new int[]{
                Math.round(kitIndicatorFromY + (targetY - kitIndicatorFromY) * progress),
                20
        };
    }

    private int kitIndex(String kit) {
        for (int index = 0; index < KIT_IDS.length; index++) {
            if (KIT_IDS[index].equals(kit)) {
                return index;
            }
        }
        return 0;
    }

    private int adminActionButtonWidth() {
        return Math.max(0, Math.min(150, (contentWidth() - 24) / 2));
    }

    private void addButton(Component message, Button.OnPress onPress, int x, int y, int width, int height) {
        this.addRenderableWidget(soundButton(message, onPress, x, y, width, height));
    }

    private SoundButton soundButton(Component message, Button.OnPress onPress, int x, int y, int width, int height) {
        SoundButton button = new SoundButton(x, y, width, height, message, onPress);
        soundButtons.add(button);
        return button;
    }

    private void playClickSound() {
        if (settings.soundsEnabled && settings.clickSoundVolume > 0) {
            playInterfaceSound(SoundEvents.UI_BUTTON_CLICK,
                    0.35f * settings.clickSoundVolume / 100.0f);
        }
    }

    private void playInterfaceSound(SoundEvent sound, float volume) {
        if (!settings.soundsEnabled || settings.guiSoundVolume <= 0) {
            return;
        }
        float adjustedVolume = Math.min(0.4f, volume * settings.guiSoundVolume / 100.0f);
        if (adjustedVolume > 0) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, adjustedVolume));
        }
    }

    private void playInterfaceSound(Holder<SoundEvent> sound, float volume) {
        if (!settings.soundsEnabled || settings.guiSoundVolume <= 0) {
            return;
        }
        float adjustedVolume = Math.min(0.4f, volume * settings.guiSoundVolume / 100.0f);
        if (adjustedVolume > 0) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, adjustedVolume));
        }
    }

    private float slideOffset() {
        if (!settings.animationsEnabled) {
            return 0.0f;
        }
        long elapsed = closing ? System.nanoTime() - closeStartedAt : System.nanoTime() - openedAtNanos;
        int alpha = closing
                ? (int) Math.max(0, 255 - elapsed * 255 / 160_000_000L)
                : (int) Math.min(255, elapsed * 255 / 160_000_000L);
        return (255 - alpha) * 8.0f / 255.0f;
    }

    private void beginPageTransition(Page nextPage) {
        int[] indicator = navigationIndicatorBounds();
        navigationIndicatorFromX = indicator[0];
        navigationIndicatorFromWidth = indicator[1];
        beginContentTransition(nextPage.ordinal() >= page.ordinal() ? 1.0f : -1.0f);
        navigationTransitionStartedAt = pageTransitionStartedAt;
    }

    private void beginKitTransition(int nextKitIndex) {
        int[] indicator = kitIndicatorBounds();
        kitIndicatorFromY = indicator[0];
        pageTransitionDirection = nextKitIndex >= kitIndex(selectedKit) ? 1.0f : -1.0f;
        kitTransitionStartedAt = System.nanoTime();
        pageTransitionStartedAt = kitTransitionStartedAt;
    }

    private void beginContentTransition(float direction) {
        pageTransitionDirection = direction;
        pageTransitionStartedAt = System.nanoTime();
    }

    private float contentTransitionProgress() {
        if (!settings.animationsEnabled || pageTransitionStartedAt == 0) {
            return 1.0f;
        }
        float progress = Math.min(1.0f, (System.nanoTime() - pageTransitionStartedAt) / 190_000_000.0f);
        return progress * progress * (3.0f - 2.0f * progress);
    }

    private float contentTransitionOffsetX() {
        return settings.animationsEnabled && pageTransitionStartedAt > 0
                ? (1.0f - contentTransitionProgress()) * 12.0f * pageTransitionDirection
                : 0.0f;
    }

    private int contentTransitionAlpha(int baseAlpha) {
        return Math.round(baseAlpha * contentTransitionProgress());
    }

    private int hoverColor(String key, boolean hovered, int baseColor, int hoverColor) {
        float current = hoverProgress.getOrDefault(key, 0.0f);
        float next = Math.max(0.0f, Math.min(1.0f, current
                + (hovered ? hoverAnimationStep : -hoverAnimationStep)));
        hoverProgress.put(key, next);
        if (hovered) {
            hoveredElements.add(key);
            if (!hoveredElementsLastFrame.contains(key) && settings.hoverSoundsEnabled) {
                playInterfaceSound(SoundEvents.UI_BUTTON_CLICK, 0.12f);
            }
        }
        return blendColor(baseColor, hoverColor, next);
    }

    private boolean mouseOverRow(int left, int top, int right, int bottom) {
        return renderMouseX >= left && renderMouseX <= right
                && renderMouseY >= top && renderMouseY <= bottom;
    }

    private static int blendColor(int from, int to, float amount) {
        int red = Math.round(((from >>> 16) & 0xFF) * (1.0f - amount) + ((to >>> 16) & 0xFF) * amount);
        int green = Math.round(((from >>> 8) & 0xFF) * (1.0f - amount) + ((to >>> 8) & 0xFF) * amount);
        int blue = Math.round((from & 0xFF) * (1.0f - amount) + (to & 0xFF) * amount);
        return (from & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static ItemStack splashPotion(Holder<Potion> potion) {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return stack;
    }

    private static int kitRowY(int index, int top) {
        if (index == 0) {
            return top + 16;
        }
        if (index <= 8) {
            return top + 62 + (index - 1) * 22;
        }
        return top + 54 + 8 * 22 + 20 + 11 + (index - 9) * 22;
    }

    private int kitIndexAt(double mouseY) {
        for (int index = 0; index < KIT_IDS.length; index++) {
            int y = kitRowY(index, 68);
            if (mouseY >= y && mouseY < y + 20) {
                return index;
            }
        }
        return -1;
    }

    private static void renderKitIcon(GuiGraphics graphics, String kit, int x, int y) {
        if ("uhc".equals(kit)) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HARDCORE_HEART_SPRITE, x, y, 16, 16);
        } else if ("diapot".equals(kit)) {
            graphics.renderItem(HEALING_SPLASH_POTION, x, y);
        } else if ("speed".equals(kit)) {
            graphics.renderItem(SWIFTNESS_SPLASH_POTION, x, y);
        } else {
            for (int index = 0; index < KIT_IDS.length; index++) {
                if (KIT_IDS[index].equals(kit)) {
                    graphics.renderItem(KIT_ICONS[index].getDefaultInstance(), x, y);
                    return;
                }
            }
            graphics.renderItem(Items.NETHER_STAR.getDefaultInstance(), x, y);
        }
    }

    private static int intValue(JsonObject object, String key) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : 0;
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Unexpected API value for field " + key, error);
            return 0;
        }
    }

    private static int tierColor(String tier) {
        return switch (tier) {
            case "HT1" -> 0xFFFFD700;
            case "LT1" -> 0xFFE5C100;
            case "HT2" -> 0xFFC0C0C0;
            case "LT2" -> 0xFFAEB6BF;
            case "HT3" -> 0xFFCD7F32;
            case "LT3" -> 0xFFB87333;
            case "HT4" -> 0xFFB3E5FC;
            case "LT4" -> 0xFF81D4FA;
            case "HT5" -> 0xFF800000;
            case "LT5" -> 0xFF5D001E;
            default -> 0xFF808080;
        };
    }

    private static int titleColor(String title) {
        return switch (title) {
            case "Member" -> 0xFF9E99DA;
            case "Cup Winner" -> 0xFFF1C40F;
            case "Cup Master" -> 0xFFAA5959;
            default -> 0xFFFFFFFF;
        };
    }

    private static String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private static String fieldValue(EditBox field) {
        return field == null ? "" : field.getValue().trim();
    }

    private static String stringValue(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Unexpected API value for field " + key, error);
            return "";
        }
    }

    private void drawFittedString(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color) {
        graphics.drawString(this.font, fitText(value, maxWidth), x, y, color);
    }

    private void drawFittedString(
            GuiGraphics graphics,
            Component value,
            int x,
            int y,
            int maxWidth,
            int color
    ) {
        if (this.font.width(value) <= maxWidth) {
            graphics.drawString(this.font, value, x, y, color);
        } else {
            drawFittedString(graphics, value.getString(), x, y, maxWidth, color);
        }
    }

    private void drawCenteredFittedString(
            GuiGraphics graphics,
            String value,
            int centerX,
            int y,
            int maxWidth,
            int color
    ) {
        graphics.drawCenteredString(this.font, fitText(value, maxWidth), centerX, y, color);
    }

    private void drawCenteredFittedString(
            GuiGraphics graphics,
            Component value,
            int centerX,
            int y,
            int maxWidth,
            int color
    ) {
        if (this.font.width(value) <= maxWidth) {
            graphics.drawCenteredString(this.font, value, centerX, y, color);
        } else {
            drawCenteredFittedString(graphics, value.getString(), centerX, y, maxWidth, color);
        }
    }

    private String fitText(String value, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (this.font.width(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int textWidth = Math.max(0, maxWidth - this.font.width(ellipsis));
        return this.font.plainSubstrByWidth(value, textWidth) + ellipsis;
    }

    private int withAlpha(int color, int alpha) {
        int originalAlpha = color >>> 24;
        int combinedAlpha = originalAlpha * Math.max(0, Math.min(255, alpha)) / 255;
        return (color & 0x00FFFFFF) | (combinedAlpha << 24);
    }

    private int withBackgroundOpacity(int color, int alpha) {
        return withAlpha(color, Math.max(0, Math.min(255, alpha))
                * Math.max(1, Math.min(255, settings.guiOpacity)) / 255);
    }

    private void fillRounded(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        int radius = Math.min(settings.guiCornerRadius, Math.min((right - left) / 2, (bottom - top) / 2));
        if (radius <= 0) {
            graphics.fill(left, top, right, bottom, color);
            return;
        }
        for (int y = 0; y < radius; y++) {
            int inset = cornerInset(radius, y);
            graphics.fill(left + inset, top + y, right - inset, top + y + 1, color);
            graphics.fill(left + inset, bottom - y - 1, right - inset, bottom - y, color);
        }
        graphics.fill(left, top + radius, right, bottom - radius, color);
    }

    private static void fillRoundedTopStrip(
            GuiGraphics graphics, int left, int top, int right, int bottom, int radius, int color
    ) {
        int height = bottom - top;
        for (int y = 0; y < height; y++) {
            int inset = cornerInset(radius, y);
            graphics.fill(left + inset, top + y, right - inset, top + y + 1, color);
        }
    }

    private static int cornerInset(int radius, int y) {
        if (radius <= 0) {
            return 0;
        }
        double distanceFromCenter = radius - y - 0.5;
        return (int) Math.ceil(radius - Math.sqrt(Math.max(0.0,
                radius * radius - distanceFromCenter * distanceFromCenter)));
    }

    private void renderCursorGlow(GuiGraphics graphics, int mouseX, int mouseY, int alpha) {
        int cursorX = mouseX;
        int cursorY = mouseY;
        int[] radii = {18, 14, 10, 7, 4};
        int[] colors = {0x0AFF6A12, 0x12FF6A12, 0x20FF7A24, 0x42FF8B3D, 0x80FFB35A};
        for (int index = 0; index < radii.length; index++) {
            fillCircle(graphics, cursorX, cursorY, radii[index], withAlpha(colors[index], alpha));
        }
    }

    private static void fillCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int halfWidth = (int) Math.sqrt(radius * radius - y * y);
            graphics.fill(centerX - halfWidth, centerY + y, centerX + halfWidth + 1, centerY + y + 1, color);
        }
    }

    private final class SettingSlider extends AbstractSliderButton {
        private final int minimum;
        private final int maximum;
        private final IntConsumer setter;
        private int lastApplied;

        private SettingSlider(
                int x,
                int y,
                int width,
                int minimum,
                int maximum,
                IntSupplier getter,
                IntConsumer setter
        ) {
            super(x, y, width, 20, Component.empty(),
                    (getter.getAsInt() - minimum) / (double) (maximum - minimum));
            this.minimum = minimum;
            this.maximum = maximum;
            this.setter = setter;
            this.lastApplied = getter.getAsInt();
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int current = Math.max(minimum, Math.min(maximum, (int) Math.round(
                    minimum + value * (maximum - minimum))));
            setMessage(Component.literal(current + " / " + maximum));
        }

        @Override
        protected void applyValue() {
            int current = Math.max(minimum, Math.min(maximum, (int) Math.round(
                    minimum + value * (maximum - minimum))));
            if (current != lastApplied) {
                lastApplied = current;
                setter.accept(current);
            }
            updateMessage();
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int trackLeft = getX() + 6;
            int trackRight = getX() + this.width - 6;
            int trackY = getY() + this.height / 2;
            int thumbX = trackLeft + Math.round((trackRight - trackLeft) * (float) value);
            int trackColor = this.active ? 0xFF25292E : 0xFF1C1F23;
            fillRounded(graphics, trackLeft, trackY - 2, trackRight, trackY + 2,
                    withAlpha(trackColor, renderAlpha));
            fillRounded(graphics, trackLeft, trackY - 2, Math.max(trackLeft, thumbX), trackY + 2,
                    withAlpha(ACCENT_COLOR, renderAlpha));
            if (isHoveredOrFocused()) {
                fillCircle(graphics, thumbX, trackY, 9, withAlpha(0x24FF7A24, renderAlpha));
            }
            fillCircle(graphics, thumbX, trackY, 5,
                    withAlpha(this.active ? 0xFFFF9A55 : 0xFF777D84, renderAlpha));
            fillCircle(graphics, thumbX, trackY, 2, withAlpha(0xFFFFFFFF, renderAlpha));
            drawCenteredFittedString(graphics, getMessage(), getX() + this.width / 2,
                    getY() + this.height - 2, Math.max(0, this.width - 8),
                    withAlpha(0xFFE6E8EB, renderAlpha));
        }
    }

    private final class SoundButton extends Button {
        private float hoverAmount;
        private long pressedAtNanos;

        private SoundButton(int x, int y, int width, int height, Component message, Button.OnPress onPress) {
            super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            hoverAmount = Math.max(0.0f, Math.min(1.0f,
                    hoverAmount + (isHoveredOrFocused() ? 1.0f : -1.0f) * Math.max(0.04f, delta * 10.0f)));
            float scale = 1.0f + hoverAmount * 0.012f;
            long pressedElapsed = System.nanoTime() - pressedAtNanos;
            if (pressedAtNanos > 0 && pressedElapsed < 100_000_000L) {
                scale -= 0.025f * (1.0f - pressedElapsed / 100_000_000.0f);
            }
            graphics.pose().pushMatrix();
            graphics.pose().translate(getX() + this.width / 2.0f, getY() + this.height / 2.0f);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-getX() - this.width / 2.0f, -getY() - this.height / 2.0f);
            int baseColor = this.active ? 0xFF343A41 : 0xFF25292E;
            int hoverColor = this.active ? 0xFF535A62 : 0xFF383D43;
            fillRounded(graphics, getX(), getY(), getX() + this.width, getY() + this.height,
                    withAlpha(blendColor(baseColor, hoverColor, hoverAmount),
                            contentTransitionAlpha(renderAlpha)));
            drawCenteredFittedString(graphics, getMessage(), getX() + this.width / 2,
                    getY() + (this.height - 8) / 2, Math.max(0, this.width - 8),
                    withAlpha(this.active ? 0xFFFFFFFF : 0xFFA0A0A0,
                            contentTransitionAlpha(renderAlpha)));
            graphics.pose().popMatrix();
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            pressedAtNanos = System.nanoTime();
            super.onClick(event, doubleClick);
        }

        @Override
        public void playDownSound(SoundManager manager) {
            if (settings.soundsEnabled && settings.clickSoundVolume > 0) {
                manager.play(SimpleSoundInstance.forUI(
                        SoundEvents.UI_BUTTON_CLICK, 0.35f * settings.clickSoundVolume / 100.0f
                ));
            }
        }
    }

    private enum Page {
        LEADERBOARD,
        MY_PROFILE,
        PROFILES,
        SETTINGS,
        ADMIN
    }
}
