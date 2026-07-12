package com.sunlitvalley.chunkyclaim.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimSerializationTest {
    @Test
    void nbtRoundTripPreservesOwnerMembersBoundsAndHome() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Claim original = new Claim(
                owner,
                "Owner",
                new ClaimBounds("minecraft:overworld", 12, -7),
                123_456_789L
        );
        original.addMember(member, "Member");
        original.setHome(new Claim.HomePosition(
                "minecraft:overworld",
                new BlockPos(200, 80, -100),
                90.0F,
                10.0F
        ));

        Claim loaded = Claim.load(original.save());

        assertEquals(owner, loaded.ownerId());
        assertEquals("Owner", loaded.ownerName());
        assertEquals(original.bounds(), loaded.bounds());
        assertEquals(123_456_789L, loaded.createdAtEpochMillis());
        assertEquals("Member", loaded.members().get(member));
        assertNotNull(loaded.home());
        assertEquals(new BlockPos(200, 80, -100), loaded.home().position());
        assertTrue(loaded.canAccess(owner));
        assertTrue(loaded.canAccess(member));
    }
}
