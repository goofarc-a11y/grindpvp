package dev.grindpvp.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftFpsMixin {
    @Inject(method = "getFps", at = @At("RETURN"), cancellable = true)
    private void grindpvp$boostDisplayedFps(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(Math.round(callback.getReturnValue() * 1.5f));
    }
}
