package dev.grindpvp.mixin;

import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TranslatableContents.class)
public interface TranslatableContentsAccessor {
    @Accessor("args")
    Object[] grindpvp$getArgs();

    @Accessor("key")
    String grindpvp$getKey();

    @Accessor("fallback")
    String grindpvp$getFallback();
}
