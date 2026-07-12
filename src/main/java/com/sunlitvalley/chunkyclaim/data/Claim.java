package com.sunlitvalley.chunkyclaim.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class Claim {
    private final UUID ownerId;
    private String ownerName;
    private final ClaimBounds bounds;
    private final long createdAtEpochMillis;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private HomePosition home;

    public Claim(UUID ownerId, String ownerName, ClaimBounds bounds, long createdAtEpochMillis) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public void updateOwnerName(String name) {
        ownerName = Objects.requireNonNull(name, "name");
    }

    public ClaimBounds bounds() {
        return bounds;
    }

    public long createdAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public Map<UUID, String> members() {
        return Collections.unmodifiableMap(members);
    }

    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public boolean canAccess(UUID playerId) {
        return ownerId.equals(playerId) || isMember(playerId);
    }

    public boolean addMember(UUID playerId, String playerName) {
        if (ownerId.equals(playerId)) {
            return false;
        }
        String previous = members.put(playerId, playerName);
        return !Objects.equals(previous, playerName);
    }

    public boolean removeMember(UUID playerId) {
        return members.remove(playerId) != null;
    }

    public void updateMemberName(UUID playerId, String name) {
        if (members.containsKey(playerId)) {
            members.put(playerId, name);
        }
    }

    public HomePosition home() {
        return home;
    }

    public void setHome(HomePosition home) {
        this.home = home;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", ownerId);
        tag.putString("OwnerName", ownerName);
        tag.putString("Dimension", bounds.dimension());
        tag.putInt("CenterChunkX", bounds.centerChunkX());
        tag.putInt("CenterChunkZ", bounds.centerChunkZ());
        tag.putLong("CreatedAt", createdAtEpochMillis);

        ListTag memberTags = new ListTag();
        members.forEach((memberId, memberName) -> {
            CompoundTag member = new CompoundTag();
            member.putUUID("Player", memberId);
            member.putString("Name", memberName);
            memberTags.add(member);
        });
        tag.put("Members", memberTags);
        if (home != null) {
            tag.put("Home", home.save());
        }
        return tag;
    }

    public static Claim load(CompoundTag tag) {
        Claim claim = new Claim(
                tag.getUUID("Owner"),
                tag.getString("OwnerName"),
                new ClaimBounds(
                        tag.getString("Dimension"),
                        tag.getInt("CenterChunkX"),
                        tag.getInt("CenterChunkZ")
                ),
                tag.getLong("CreatedAt")
        );
        ListTag members = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int index = 0; index < members.size(); index++) {
            CompoundTag member = members.getCompound(index);
            if (member.hasUUID("Player")) {
                claim.members.put(member.getUUID("Player"), member.getString("Name"));
            }
        }
        if (tag.contains("Home", Tag.TAG_COMPOUND)) {
            claim.home = HomePosition.load(tag.getCompound("Home"));
        }
        return claim;
    }

    public record HomePosition(String dimension, BlockPos position, float yaw, float pitch) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", dimension);
            tag.putInt("X", position.getX());
            tag.putInt("Y", position.getY());
            tag.putInt("Z", position.getZ());
            tag.putFloat("Yaw", yaw);
            tag.putFloat("Pitch", pitch);
            return tag;
        }

        public static HomePosition load(CompoundTag tag) {
            return new HomePosition(
                    tag.getString("Dimension"),
                    new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")),
                    tag.getFloat("Yaw"),
                    tag.getFloat("Pitch")
            );
        }
    }
}

