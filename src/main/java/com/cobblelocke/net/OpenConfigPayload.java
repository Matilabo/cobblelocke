package com.cobblelocke.net;

import com.cobblelocke.Cobblelocke;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record OpenConfigPayload(String configJson, String presetsJson, boolean canEdit,
                                boolean raidDensInstalled) implements CustomPayload {
    public static final CustomPayload.Id<OpenConfigPayload> ID =
            new CustomPayload.Id<>(Cobblelocke.id("open_config"));

    public static final PacketCodec<RegistryByteBuf, OpenConfigPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, OpenConfigPayload::configJson,
            PacketCodecs.STRING, OpenConfigPayload::presetsJson,
            PacketCodecs.BOOL, OpenConfigPayload::canEdit,
            PacketCodecs.BOOL, OpenConfigPayload::raidDensInstalled,
            OpenConfigPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
