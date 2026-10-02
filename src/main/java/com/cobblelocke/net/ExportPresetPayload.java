package com.cobblelocke.net;

import com.cobblelocke.Cobblelocke;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ExportPresetPayload(String name, String configJson) implements CustomPayload {
    public static final CustomPayload.Id<ExportPresetPayload> ID =
            new CustomPayload.Id<>(Cobblelocke.id("export_preset"));

    public static final PacketCodec<RegistryByteBuf, ExportPresetPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ExportPresetPayload::name,
            PacketCodecs.STRING, ExportPresetPayload::configJson,
            ExportPresetPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
