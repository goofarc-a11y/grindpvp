package de.grindpvp.client.mixin;

import de.grindpvp.client.NameTagCache;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class PlayerNameTagMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void grindpvp$decoratePlayerName(CallbackInfoReturnable<Component> callback) {
        if ((Object) this instanceof Player player) {
            callback.setReturnValue(NameTagCache.format(player, callback.getReturnValue()));
        }
    }
}
