package dev.grindpvp.mixin;

import dev.grindpvp.presentation.PlayerPresentationCache;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("HEAD"), cancellable = true)
    private void grindpvp$decorateTabName(PlayerInfo playerInfo, CallbackInfoReturnable<Component> callback) {
        Component decorated = PlayerPresentationCache.decorateTabName(
                playerInfo.getProfile().id().toString(),
                playerInfo.getProfile().name()
        );
        if (decorated != null) {
            callback.setReturnValue(decorated);
        }
    }
}
