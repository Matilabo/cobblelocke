package com.cobblelocke.net;

import com.cobblelocke.Cobblelocke;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record SetNicknamePayload(UUID pokemonId, String name) implements CustomPayload {
    public static final CustomPayload.Id<SetNicknamePayload> ID =
            new CustomPayload.Id<>(Cobblelocke.id("set_nickname"));

    public static final PacketCodec<RegistryByteBuf, SetNicknamePayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, SetNicknamePayload::pokemonId,
            PacketCodecs.STRING, SetNicknamePayload::name,
            SetNicknamePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
