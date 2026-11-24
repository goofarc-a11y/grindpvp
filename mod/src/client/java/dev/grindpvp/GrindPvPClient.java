package dev.grindpvp;

import com.mojang.blaze3d.platform.InputConstants;
import dev.grindpvp.gui.GrindPvPScreen;
import dev.grindpvp.presentation.PlayerPresentationCache;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class GrindPvPClient implements ClientModInitializer {
    private static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("grindpvp", "main"));
    private static final KeyMapping OPEN_MENU_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.grindpvp.open_menu",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_O,
                    KEY_CATEGORY
            )
    );

    @Override
    public void onInitializeClient() {
        PlayerPresentationCache.refresh();
        int[] ticksUntilRefresh = {1200};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (--ticksUntilRefresh[0] <= 0) {
                PlayerPresentationCache.refresh();
                ticksUntilRefresh[0] = 1200;
            }
            while (OPEN_MENU_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new GrindPvPScreen());
            }
        });
    }
}
