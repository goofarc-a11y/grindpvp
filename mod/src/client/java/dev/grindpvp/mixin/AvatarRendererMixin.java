package dev.grindpvp.mixin;

import dev.grindpvp.presentation.PlayerPresentationCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    private void grindpvp$decorateNameTag(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo callback) {
        String uuid = avatar.getUUID().toString();
        String username = avatar.getName().getString();
        int latency = -1;
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            PlayerInfo playerInfo = connection.getPlayerInfo(avatar.getUUID());
            if (playerInfo != null) {
                latency = playerInfo.getLatency();
            }
        }
        var decorated = PlayerPresentationCache.decorateNameTag(uuid, username, latency);
        state.nameTag = decorated != null ? decorated : Component.literal(username);
    }
}
