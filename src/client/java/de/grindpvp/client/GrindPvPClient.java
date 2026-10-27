package de.grindpvp.client;

import de.grindpvp.GrindPvPMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.grindpvp.network.ClientModPresencePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class GrindPvPClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(GrindPvPMod.MOD_ID, "main"));
    private static KeyMapping openMenu;

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(ClientModPresencePayload.TYPE)) {
                ClientPlayNetworking.send(new ClientModPresencePayload());
            }
        });
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.grindpvp.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenu.consumeClick()) {
                if (client.screen == null) {
                    Minecraft.getInstance().setScreen(new GrindPvPScreen());
                }
            }
        });
    }
}
