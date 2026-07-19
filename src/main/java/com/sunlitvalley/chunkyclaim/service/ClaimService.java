package com.sunlitvalley.chunkyclaim.service;

import com.sunlitvalley.chunkyclaim.config.ChunkyClaimConfig;
import com.sunlitvalley.chunkyclaim.data.Claim;
import com.sunlitvalley.chunkyclaim.data.ClaimBounds;
import com.sunlitvalley.chunkyclaim.data.ClaimSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClaimService {
    private static final Map<UUID, PendingInvite> INVITES = new HashMap<>();

    private ClaimService() {
    }

    public static ClaimSavedData data(MinecraftServer server) {
        return ClaimSavedData.get(server);
    }

    public static CreateResult createClaim(ServerPlayer player) {
        ClaimSavedData data = data(player.getServer());
        if (data.hasAffiliation(player.getUUID())) {
            return CreateResult.failure("이미 소유하거나 소속된 사유지가 있습니다.");
        }

        ClaimBounds proposed = ClaimBounds.centeredAt(player.level(), player.blockPosition());
        ChunkyClaimConfig.ConfigData config = ChunkyClaimConfig.get();
        double spawnDistance = proposed.distanceToPoint(
                config.spawn.dimension,
                config.spawn.x,
                config.spawn.z
        );
        if (spawnDistance <= config.spawnExclusionBlocks) {
            return CreateResult.failure("스폰 보호 구역으로부터 " + config.spawnExclusionBlocks
                    + "블럭 이내에는 사유지를 만들 수 없습니다.");
        }

        for (Claim existing : data.all()) {
            double distance = proposed.distanceTo(existing.bounds());
            if (distance <= config.nearbyClaimExclusionBlocks) {
                return CreateResult.failure("다른 사유지로부터 " + config.nearbyClaimExclusionBlocks
                        + "블럭 이내에는 사유지를 만들 수 없습니다.");
            }
        }

        Claim claim = new Claim(
                player.getUUID(),
                player.getGameProfile().getName(),
                proposed,
                System.currentTimeMillis()
        );
        if (!data.add(claim)) {
            return CreateResult.failure("사유지를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        clearInvitesFor(player.getUUID());
        return CreateResult.success(claim);
    }

    public static Optional<Claim> claimAt(Level level, BlockPos position) {
        if (level.isClientSide || level.getServer() == null) {
            return Optional.empty();
        }
        return data(level.getServer()).at(level.dimension(), position);
    }

    public static Optional<Claim> ownedClaim(ServerPlayer player) {
        return data(player.getServer()).byOwner(player.getUUID());
    }

    public static boolean ownsPosition(ServerPlayer player, Level level, BlockPos position) {
        return ownedClaim(player)
                .map(claim -> claim.bounds().contains(level.dimension(), position))
                .orElse(false);
    }

    public static boolean isOperator(ServerPlayer player) {
        return player.getServer().getPlayerList().isOp(player.getGameProfile());
    }

    public static boolean mayAccess(ServerPlayer player, Level level, BlockPos position) {
        if (isOperator(player)) {
            return true;
        }
        return claimAt(level, position)
                .map(claim -> claim.canAccess(player.getUUID()))
                .orElse(true);
    }

    public static ActionResult invite(ServerPlayer owner, ServerPlayer target) {
        cleanupExpiredInvites();
        ClaimSavedData data = data(owner.getServer());
        Optional<Claim> owned = data.byOwner(owner.getUUID());
        if (owned.isEmpty()) {
            return ActionResult.failure("본인이 소유한 사유지가 없습니다.");
        }
        if (owner.getUUID().equals(target.getUUID())) {
            return ActionResult.failure("자기 자신은 초대할 수 없습니다.");
        }
        if (data.hasAffiliation(target.getUUID())) {
            return ActionResult.failure("해당 플레이어는 이미 사유지를 소유하거나 소속되어 있습니다.");
        }

        long expiresAt = System.currentTimeMillis()
                + ChunkyClaimConfig.get().inviteExpirationSeconds * 1_000L;
        INVITES.put(target.getUUID(), new PendingInvite(owner.getUUID(), expiresAt));
        target.sendSystemMessage(Component.literal(owner.getGameProfile().getName()
                + "님의 사유지 초대를 받았습니다. /수락 또는 /거절을 입력하세요."));
        return ActionResult.success(target.getGameProfile().getName() + "님을 사유지에 초대했습니다.");
    }

    public static ActionResult accept(ServerPlayer target) {
        cleanupExpiredInvites();
        PendingInvite invite = INVITES.remove(target.getUUID());
        if (invite == null) {
            return ActionResult.failure("수락할 사유지 초대가 없습니다.");
        }

        ClaimSavedData data = data(target.getServer());
        if (data.hasAffiliation(target.getUUID())) {
            return ActionResult.failure("이미 사유지를 소유하거나 소속되어 있어 초대를 수락할 수 없습니다.");
        }
        Optional<Claim> claim = data.byOwner(invite.ownerId());
        if (claim.isEmpty()) {
            return ActionResult.failure("초대한 플레이어의 사유지가 더 이상 존재하지 않습니다.");
        }

        claim.get().addMember(target.getUUID(), target.getGameProfile().getName());
        data.changed();
        ServerPlayer owner = target.getServer().getPlayerList().getPlayer(invite.ownerId());
        if (owner != null) {
            owner.sendSystemMessage(Component.literal(target.getGameProfile().getName()
                    + "님이 사유지 초대를 수락했습니다."));
        }
        return ActionResult.success(claim.get().ownerName() + "님의 사유지에 소속되었습니다.");
    }

    public static ActionResult decline(ServerPlayer target) {
        cleanupExpiredInvites();
        PendingInvite invite = INVITES.remove(target.getUUID());
        if (invite == null) {
            return ActionResult.failure("거절할 사유지 초대가 없습니다.");
        }
        ServerPlayer owner = target.getServer().getPlayerList().getPlayer(invite.ownerId());
        if (owner != null) {
            owner.sendSystemMessage(Component.literal(target.getGameProfile().getName()
                    + "님이 사유지 초대를 거절했습니다."));
        }
        return ActionResult.success("사유지 초대를 거절했습니다.");
    }

    public static ActionResult forceInvite(MinecraftServer server, UUID ownerId, String ownerName,
                                           UUID targetId, String targetName) {
        ClaimSavedData data = data(server);
        Optional<Claim> claim = data.byOwner(ownerId);
        if (claim.isEmpty()) {
            return ActionResult.failure(ownerName + "님이 소유한 사유지가 없습니다.");
        }
        if (ownerId.equals(targetId)) {
            return ActionResult.failure("소유자를 자신의 사유지 구성원으로 추가할 수 없습니다.");
        }
        if (data.hasAffiliation(targetId)) {
            return ActionResult.failure(targetName + "님은 이미 사유지를 소유하거나 소속되어 있습니다.");
        }
        claim.get().addMember(targetId, targetName);
        data.changed();
        INVITES.remove(targetId);
        return ActionResult.success(targetName + "님을 " + claim.get().ownerName()
                + "님의 사유지에 강제로 소속시켰습니다.");
    }

    public static ActionResult kickMember(MinecraftServer server, UUID ownerId, String ownerName,
                                          UUID targetId, String targetName) {
        ClaimSavedData data = data(server);
        Optional<Claim> claim = data.byOwner(ownerId);
        if (claim.isEmpty()) {
            return ActionResult.failure(ownerName + "님이 소유한 사유지가 없습니다.");
        }
        if (!claim.get().removeMember(targetId)) {
            return ActionResult.failure(targetName + "님은 해당 사유지의 구성원이 아닙니다.");
        }
        data.changed();
        INVITES.remove(targetId);
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target != null) {
            target.sendSystemMessage(Component.literal(claim.get().ownerName()
                    + "님의 사유지에서 추방되었습니다."));
        }
        return ActionResult.success(targetName + "님을 " + claim.get().ownerName()
                + "님의 사유지에서 추방했습니다.");
    }

    public static ActionResult demolish(MinecraftServer server, UUID ownerId, String ownerName) {
        Optional<Claim> removed = data(server).removeByOwner(ownerId);
        if (removed.isEmpty()) {
            return ActionResult.failure(ownerName + "님이 소유한 사유지가 없습니다.");
        }
        clearInvitesFor(ownerId);
        removed.get().members().keySet().forEach(INVITES::remove);
        return ActionResult.success(ownerName + "님의 사유지를 철거했습니다.");
    }

    public static ActionResult demolishByStoredOwnerName(MinecraftServer server, String ownerName) {
        ClaimSavedData data = data(server);
        List<Claim> matches = data.byOwnerName(ownerName);
        if (matches.isEmpty()) {
            return ActionResult.failure("저장된 소유자명이 " + ownerName + "인 사유지가 없습니다.");
        }
        if (matches.size() > 1) {
            return ActionResult.failure("저장된 소유자명이 " + ownerName + "인 사유지가 "
                    + matches.size() + "개라 안전하게 철거할 수 없습니다.");
        }

        Claim claim = matches.get(0);
        Optional<Claim> removed = data.removeByOwner(claim.ownerId());
        if (removed.isEmpty()) {
            return ActionResult.failure(ownerName + "님의 사유지를 철거하지 못했습니다.");
        }
        clearInvitesFor(claim.ownerId());
        claim.members().keySet().forEach(INVITES::remove);
        return ActionResult.success(ownerName + "님의 사유지를 저장된 소유자명으로 철거했습니다.");
    }

    public static ActionResult setHome(ServerPlayer owner) {
        ClaimSavedData data = data(owner.getServer());
        Optional<Claim> claim = data.byOwner(owner.getUUID());
        if (claim.isEmpty()) {
            return ActionResult.failure("본인이 소유한 사유지가 없습니다.");
        }
        if (!claim.get().bounds().contains(owner.level().dimension(), owner.blockPosition())) {
            return ActionResult.failure("홈은 본인이 소유한 사유지 안에서만 설정할 수 있습니다.");
        }
        claim.get().setHome(new Claim.HomePosition(
                owner.level().dimension().location().toString(),
                owner.blockPosition(),
                owner.getYRot(),
                owner.getXRot()
        ));
        data.changed();
        return ActionResult.success("현재 위치를 사유지 홈으로 설정했습니다.");
    }

    public static ActionResult teleportHome(ServerPlayer player) {
        Optional<Claim> claim = data(player.getServer()).forPlayer(player.getUUID());
        if (claim.isEmpty()) {
            return ActionResult.failure("소유하거나 소속된 사유지가 없습니다.");
        }
        return teleportToClaim(player, claim.get(), true);
    }

    public static ActionResult teleportToClaim(ServerPlayer player, Claim claim, boolean useHome) {
        String dimension = claim.bounds().dimension();
        BlockPos destination;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        if (useHome && claim.home() != null) {
            dimension = claim.home().dimension();
            destination = claim.home().position();
            yaw = claim.home().yaw();
            pitch = claim.home().pitch();
        } else {
            ServerLevel targetLevel = level(player.getServer(), dimension);
            if (targetLevel == null) {
                return ActionResult.failure("사유지 차원을 찾을 수 없습니다: " + dimension);
            }
            int x = claim.bounds().centerBlockX();
            int z = claim.bounds().centerBlockZ();
            int y = targetLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            destination = new BlockPos(x, y, z);
        }

        ServerLevel targetLevel = level(player.getServer(), dimension);
        if (targetLevel == null) {
            return ActionResult.failure("사유지 차원을 찾을 수 없습니다: " + dimension);
        }
        player.teleportTo(
                targetLevel,
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                yaw,
                pitch
        );
        return ActionResult.success(claim.ownerName() + "님의 사유지로 이동했습니다.");
    }

    public static void refreshPlayerName(ServerPlayer player) {
        ClaimSavedData data = data(player.getServer());
        data.byOwner(player.getUUID()).ifPresent(claim -> {
            if (!claim.ownerName().equals(player.getGameProfile().getName())) {
                claim.updateOwnerName(player.getGameProfile().getName());
                data.changed();
            }
        });
        data.byMember(player.getUUID()).ifPresent(claim -> {
            String saved = claim.members().get(player.getUUID());
            if (!player.getGameProfile().getName().equals(saved)) {
                claim.updateMemberName(player.getUUID(), player.getGameProfile().getName());
                data.changed();
            }
        });
    }

    public static void clearRuntimeState() {
        INVITES.clear();
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        INVITES.values().removeIf(invite -> invite.expiresAtEpochMillis() < now);
    }

    private static void clearInvitesFor(UUID playerId) {
        INVITES.remove(playerId);
        Iterator<Map.Entry<UUID, PendingInvite>> iterator = INVITES.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().ownerId().equals(playerId)) {
                iterator.remove();
            }
        }
    }

    private record PendingInvite(UUID ownerId, long expiresAtEpochMillis) {
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message);
        }
    }

    public record CreateResult(boolean success, Claim claim, String message) {
        public static CreateResult success(Claim claim) {
            return new CreateResult(true, claim, "3×3 청크 사유지를 생성했습니다.");
        }

        public static CreateResult failure(String message) {
            return new CreateResult(false, null, message);
        }
    }
}
