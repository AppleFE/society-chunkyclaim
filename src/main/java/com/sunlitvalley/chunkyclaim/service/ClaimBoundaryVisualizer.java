package com.sunlitvalley.chunkyclaim.service;

import com.sunlitvalley.chunkyclaim.config.ChunkyClaimConfig;
import com.sunlitvalley.chunkyclaim.data.Claim;
import com.sunlitvalley.chunkyclaim.data.ClaimBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClaimBoundaryVisualizer {
    private static final int PARTICLE_SPACING = 6;
    private static final int CREATION_BURST_COUNT = 16;
    private static final Set<UUID> CLAIM_VIEWERS = new HashSet<>();

    private ClaimBoundaryVisualizer() {
    }

    public static ToggleResult toggle(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (CLAIM_VIEWERS.remove(playerId)) {
            return ToggleResult.success(false, "사유지 경계 표시를 끕니다.");
        }

        if (ClaimService.data(player.getServer()).forPlayer(playerId).isEmpty()) {
            return ToggleResult.failure("표시할 소속 사유지가 없습니다.");
        }

        CLAIM_VIEWERS.add(playerId);
        return ToggleResult.success(true, "사유지 경계 표시를 켰습니다.");
    }

    public static void tick(ServerPlayer player) {
        if (holdsTicket(player)) {
            showBoundary(player, ClaimBounds.centeredAt(player.level(), player.blockPosition()), ParticleTypes.END_ROD);
            return;
        }

        if (!CLAIM_VIEWERS.contains(player.getUUID())) {
            return;
        }

        Optional<Claim> claim = ClaimService.data(player.getServer()).forPlayer(player.getUUID());
        if (claim.isEmpty()) {
            CLAIM_VIEWERS.remove(player.getUUID());
            return;
        }
        showBoundary(player, claim.get().bounds(), ParticleTypes.HAPPY_VILLAGER);
    }

    public static void onClaimCreated(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos position = player.blockPosition();
        level.sendParticles(
                player,
                ParticleTypes.HAPPY_VILLAGER,
                true,
                position.getX() + 0.5D,
                position.getY() + 1.0D,
                position.getZ() + 0.5D,
                CREATION_BURST_COUNT,
                0.6D,
                0.8D,
                0.6D,
                0.05D
        );
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.2F);
    }

    public static void clearPlayer(ServerPlayer player) {
        CLAIM_VIEWERS.remove(player.getUUID());
    }

    public static void clear() {
        CLAIM_VIEWERS.clear();
    }

    private static boolean holdsTicket(ServerPlayer player) {
        return ChunkyClaimConfig.isTicket(player.getMainHandItem())
                || ChunkyClaimConfig.isTicket(player.getOffhandItem());
    }

    private static void showBoundary(ServerPlayer player, ClaimBounds bounds, ParticleOptions particle) {
        if (!bounds.dimension().equals(player.level().dimension().location().toString())) {
            return;
        }

        int minX = bounds.minBlockX();
        int maxX = bounds.maxBlockX() + 1;
        int minZ = bounds.minBlockZ();
        int maxZ = bounds.maxBlockZ() + 1;
        double y = player.getY() + 0.5D;

        for (int x = minX; x <= maxX; x += PARTICLE_SPACING) {
            sendParticle(player, particle, x, y, minZ);
            sendParticle(player, particle, x, y, maxZ);
        }
        for (int z = minZ + PARTICLE_SPACING; z < maxZ; z += PARTICLE_SPACING) {
            sendParticle(player, particle, minX, y, z);
            sendParticle(player, particle, maxX, y, z);
        }
    }

    private static void sendParticle(ServerPlayer player, ParticleOptions particle, double x, double y, double z) {
        player.serverLevel().sendParticles(player, particle, true, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    public record ToggleResult(boolean success, boolean enabled, String message) {
        private static ToggleResult success(boolean enabled, String message) {
            return new ToggleResult(true, enabled, message);
        }

        private static ToggleResult failure(String message) {
            return new ToggleResult(false, false, message);
        }
    }
}
