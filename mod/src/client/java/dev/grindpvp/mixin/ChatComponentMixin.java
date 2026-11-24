package dev.grindpvp.mixin;

import dev.grindpvp.presentation.PlayerPresentationCache;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component grindpvp$decorateChatSender(Component message) {
        if (!(message.getContents() instanceof TranslatableContents contents)) {
            return message;
        }
        TranslatableContentsAccessor accessor = (TranslatableContentsAccessor) (Object) contents;
        Object[] originalArgs = accessor.grindpvp$getArgs();
        if (originalArgs.length == 0 || !(originalArgs[0] instanceof Component sender)) {
            return message;
        }
        Component decoratedSender = PlayerPresentationCache.decorateChatName(sender);
        if (decoratedSender == null) {
            return message;
        }
        Object[] args = originalArgs.clone();
        args[0] = decoratedSender;
        MutableComponent decorated = Component.translatableWithFallback(
                accessor.grindpvp$getKey(),
                accessor.grindpvp$getFallback(),
                args
        ).withStyle(message.getStyle());
        message.getSiblings().forEach(decorated::append);
        return decorated;
    }
}
