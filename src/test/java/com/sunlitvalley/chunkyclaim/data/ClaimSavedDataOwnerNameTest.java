package com.sunlitvalley.chunkyclaim.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimSavedDataOwnerNameTest {
    @Test
    void findsClaimByStoredOwnerNameDespiteDifferentUuidAndCase() {
        ClaimSavedData data = new ClaimSavedData();
        UUID offlineOwnerId = UUID.randomUUID();
        data.add(new Claim(
                offlineOwnerId,
                "OfflinePlayer",
                new ClaimBounds("minecraft:overworld", 4, -2),
                1L
        ));

        List<Claim> matches = data.byOwnerName("offlineplayer");

        assertEquals(1, matches.size());
        assertEquals(offlineOwnerId, matches.get(0).ownerId());
        assertTrue(data.byOwner(UUID.randomUUID()).isEmpty());
    }
}
