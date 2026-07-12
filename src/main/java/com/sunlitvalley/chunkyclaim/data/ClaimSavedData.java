package com.sunlitvalley.chunkyclaim.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ClaimSavedData extends SavedData {
    private static final String DATA_NAME = "society_chunkyclaim_claims";
    private static final int DATA_VERSION = 1;

    private final List<Claim> claims = new ArrayList<>();

    public static ClaimSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ClaimSavedData::load,
                ClaimSavedData::new,
                DATA_NAME
        );
    }

    public static ClaimSavedData load(CompoundTag root) {
        ClaimSavedData data = new ClaimSavedData();
        ListTag tags = root.getList("Claims", Tag.TAG_COMPOUND);
        for (int index = 0; index < tags.size(); index++) {
            CompoundTag claimTag = tags.getCompound(index);
            if (claimTag.hasUUID("Owner")) {
                data.claims.add(Claim.load(claimTag));
            }
        }
        return data;
    }

    public Collection<Claim> all() {
        return List.copyOf(claims);
    }

    public Optional<Claim> byOwner(UUID ownerId) {
        return claims.stream().filter(claim -> claim.ownerId().equals(ownerId)).findFirst();
    }

    public Optional<Claim> byMember(UUID memberId) {
        return claims.stream().filter(claim -> claim.isMember(memberId)).findFirst();
    }

    public Optional<Claim> forPlayer(UUID playerId) {
        return claims.stream().filter(claim -> claim.canAccess(playerId)).findFirst();
    }

    public Optional<Claim> at(ResourceKey<Level> dimension, BlockPos position) {
        return claims.stream()
                .filter(claim -> claim.bounds().contains(dimension, position))
                .findFirst();
    }

    public boolean hasAffiliation(UUID playerId) {
        return forPlayer(playerId).isPresent();
    }

    public boolean add(Claim claim) {
        if (hasAffiliation(claim.ownerId())) {
            return false;
        }
        claims.add(claim);
        setDirty();
        return true;
    }

    public Optional<Claim> removeByOwner(UUID ownerId) {
        Optional<Claim> claim = byOwner(ownerId);
        claim.ifPresent(value -> {
            claims.remove(value);
            setDirty();
        });
        return claim;
    }

    public void changed() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        root.putInt("DataVersion", DATA_VERSION);
        ListTag tags = new ListTag();
        claims.stream()
                .sorted(Comparator.comparing(Claim::ownerId))
                .map(Claim::save)
                .forEach(tags::add);
        root.put("Claims", tags);
        return root;
    }
}
