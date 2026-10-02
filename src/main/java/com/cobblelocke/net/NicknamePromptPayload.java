package com.cobblelocke.net;

import com.cobblelocke.Cobblelocke;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record NicknamePromptPayload(UUID pokemonId, String species) implements CustomPayload {
    public static final CustomPayload.Id<NicknamePromptPayload> ID =
            new CustomPayload.Id<>(Cobblelocke.id("nickname_prompt"));

    public static final PacketCodec<RegistryByteBuf, NicknamePromptPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, NicknamePromptPayload::pokemonId,
            PacketCodecs.STRING, NicknamePromptPayload::species,
            NicknamePromptPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
