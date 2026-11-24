package dev.grindpvp.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.grindpvp.presentation.PlayerPresentationCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
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
        grindpvp$applyNameTag(avatar, state);
    }

    @Inject(
            method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void grindpvp$forceNameTagAtRender(
            AvatarRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState,
            CallbackInfo callback
    ) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var entity = level.getEntity(state.id);
        if (entity instanceof Avatar avatar) {
            grindpvp$applyNameTag(avatar, state);
        }
    }

    private void grindpvp$applyNameTag(Avatar avatar, AvatarRenderState state) {
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
        var role = PlayerPresentationCache.decorateRoleNameTag(uuid, username);
        state.scoreText = role != null ? decorated : null;
        state.nameTag = role != null ? role : decorated != null ? decorated : Component.literal(username);
    }
}
