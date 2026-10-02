package com.cobblelocke.net;

import com.cobblelocke.Cobblelocke;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record SaveConfigPayload(String configJson, boolean startRun) implements CustomPayload {
    public static final CustomPayload.Id<SaveConfigPayload> ID =
            new CustomPayload.Id<>(Cobblelocke.id("save_config"));

    public static final PacketCodec<RegistryByteBuf, SaveConfigPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, SaveConfigPayload::configJson,
            PacketCodecs.BOOL, SaveConfigPayload::startRun,
            SaveConfigPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
