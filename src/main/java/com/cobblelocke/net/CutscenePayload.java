package com.cobblelocke.net;

import com.cobblelocke.Cobblelocke;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record CutscenePayload(String clipId) implements CustomPayload {
    public static final CustomPayload.Id<CutscenePayload> ID =
            new CustomPayload.Id<>(Cobblelocke.id("cutscene"));

    public static final PacketCodec<RegistryByteBuf, CutscenePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, CutscenePayload::clipId,
            CutscenePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
