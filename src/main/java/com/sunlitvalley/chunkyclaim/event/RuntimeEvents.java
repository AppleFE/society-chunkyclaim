package com.sunlitvalley.chunkyclaim.event;

import com.sunlitvalley.chunkyclaim.SocietyChunkyClaimMod;
import com.sunlitvalley.chunkyclaim.config.ChunkyClaimConfig;
import com.sunlitvalley.chunkyclaim.service.ClaimService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = SocietyChunkyClaimMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RuntimeEvents {
    private RuntimeEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            ChunkyClaimConfig.initialize(event.getServer());
            SocietyChunkyClaimMod.LOGGER.info("Loaded Society ChunkyClaim config from {}", ChunkyClaimConfig.path());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load Society ChunkyClaim configuration", exception);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClaimService.refreshPlayerName(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ClaimService.clearRuntimeState();
    }
}

