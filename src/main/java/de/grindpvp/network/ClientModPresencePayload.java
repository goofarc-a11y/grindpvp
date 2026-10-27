package de.grindpvp.network;

import de.grindpvp.GrindPvPMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientModPresencePayload() implements CustomPacketPayload {
    public static final Type<ClientModPresencePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GrindPvPMod.MOD_ID, "client_presence"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientModPresencePayload> CODEC =
            StreamCodec.unit(new ClientModPresencePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
