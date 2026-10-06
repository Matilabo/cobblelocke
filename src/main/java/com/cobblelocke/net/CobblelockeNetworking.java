package com.cobblelocke.net;

import net.minecraft.util.Formatting;
import com.cobblelocke.util.Lang;
import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.config.ConfigFiles;
import com.cobblelocke.data.CobblelockeState;
import com.cobblelocke.eventlock.EventLockService;
import com.cobblelocke.nuzlocke.NicknameService;
import com.cobblelocke.random.GlobalPools;
import com.cobblelocke.random.ShinyRate;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class CobblelockeNetworking {
    private CobblelockeNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenConfigPayload.ID, OpenConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CutscenePayload.ID, CutscenePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NicknamePromptPayload.ID, NicknamePromptPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveConfigPayload.ID, SaveConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetNicknamePayload.ID, SetNicknamePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CutsceneCoveredPayload.ID, CutsceneCoveredPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ExportPresetPayload.ID, ExportPresetPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SaveConfigPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> handleSave(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SetNicknamePayload.ID,
                (payload, context) -> context.server().execute(
                        () -> NicknameService.handleSubmit(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(CutsceneCoveredPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> EventLockService.onScreenCovered(context.player(), payload.clipId())));
        ServerPlayNetworking.registerGlobalReceiver(ExportPresetPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> handleExport(context.player(), payload)));
    }

    private static void handleExport(ServerPlayerEntity player, ExportPresetPayload payload) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return;
        }
        if (!state.canEditConfig(player)) {
            player.sendMessage(Lang.tr("export.denied", "§cOnly the host or an operator can export a preset."));
            return;
        }
        CobblelockeConfig exported = CobblelockeConfig.fromJson(payload.configJson());
        player.sendMessage(exportMessage(payload.name(), ConfigFiles.exportPreset(payload.name(), exported)));
    }

    public static Text exportMessage(String name, ConfigFiles.ExportResult result) {
        Text shown = Lang.hl(name, Formatting.WHITE);
        return switch (result) {
            case ADDED -> Lang.tr("export.added", "§aSaved these settings as the preset %s§a. §7It is in "
                    + "presets.json5 and in the preset switcher.", shown);
            case REPLACED -> Lang.tr("export.replaced", "§aReplaced the preset %s§a with these settings. §7It is "
                    + "in presets.json5 and in the preset switcher.", shown);
            case FAILED -> Lang.tr("export.failed", "§cCould not write the preset. Check the log and "
                    + "config/cobblelocke/presets.json5.");
        };
    }

    public static void sendOpenConfig(ServerPlayerEntity player) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return;
        }
        if (!ServerPlayNetworking.canSend(player, OpenConfigPayload.ID)) {
            player.sendMessage(Lang.tr("config.no_client", "§cCobblelocke is not installed on your client, so "
                    + "the config screen cannot open. Use /cobblelocke set, or edit config/cobblelocke/config.json5."));
            return;
        }
        ServerPlayNetworking.send(player, new OpenConfigPayload(
                state.getConfig().toJson(),
                ConfigFiles.loadPresets().toString(),
                state.canEditConfig(player),
                FabricLoader.getInstance().isModLoaded("cobblemonraiddens")));
    }

    public static boolean sendCutscene(ServerPlayerEntity player, String clipId) {
        if (!ServerPlayNetworking.canSend(player, CutscenePayload.ID)) {
            return false;
        }
        ServerPlayNetworking.send(player, new CutscenePayload(clipId));
        return true;
    }

    private static void handleSave(ServerPlayerEntity player, SaveConfigPayload payload) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return;
        }

        if (!state.canEditConfig(player)) {
            player.sendMessage(Lang.tr("config.denied", "§cOnly the host or an operator can change Cobblelocke "
                    + "settings."));
            return;
        }

        CobblelockeConfig incoming = CobblelockeConfig.fromJson(payload.configJson());
        incoming.configured = true;
        incoming.runActive = payload.startRun() || state.getConfig().runActive;

        state.saveConfig(incoming, payload.startRun());
        CobblelockeConfig saved = state.getConfig();

        ConfigFiles.writeConfig(saved, false);
        ShinyRate.apply(saved);

        if (payload.startRun()) {
            startFreshRun(player, state, saved);
        } else {
            player.sendMessage(Lang.tr("config.saved", "§aCobblelocke settings saved. §7Nothing was reset or "
                    + "re-randomized."));
        }
        Cobblelocke.LOGGER.info("{} saved the Cobblelocke config (run active: {})",
                player.getName().getString(), saved.runActive);
    }

    public static void rerollGlobalPools(CobblelockeConfig config) {
        GlobalPools pools = GlobalPools.get();
        if (pools != null) {
            pools.reset();
            if (config.anyGlobalPool()) {
                pools.prefillAll(config);
            }
        }
    }

    public static void startFreshRun(ServerPlayerEntity player, CobblelockeState state,
                                     CobblelockeConfig saved) {
        state.resetRun();
        EventLockService.reset();
        rerollGlobalPools(saved);
        if (player != null) {
            player.sendMessage(Lang.tr("config.new_run", "§d★ A new Cobblelocke run has begun. §7Catches, "
                    + "deaths and event progress were cleared, and everything was randomized again."));
        }
        Cobblelocke.LOGGER.info("A new Cobblelocke run was started");
    }
}
