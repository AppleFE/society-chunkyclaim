package com.sunlitvalley.chunkyclaim.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimBoundsTest {
    @Test
    void overlappingClaimsHaveZeroDistance() {
        ClaimBounds first = new ClaimBounds("minecraft:overworld", 0, 0);
        ClaimBounds second = new ClaimBounds("minecraft:overworld", 1, 1);

        assertEquals(0.0D, first.distanceTo(second));
    }

    @Test
    void distanceUsesNearestClaimEdges() {
        ClaimBounds first = new ClaimBounds("minecraft:overworld", 0, 0);
        ClaimBounds immediatelyAdjacent = new ClaimBounds("minecraft:overworld", 3, 0);
        ClaimBounds separated = new ClaimBounds("minecraft:overworld", 4, 0);

        assertEquals(1.0D, first.distanceTo(immediatelyAdjacent));
        assertEquals(17.0D, first.distanceTo(separated));
    }

    @Test
    void dimensionsDoNotConflict() {
        ClaimBounds overworld = new ClaimBounds("minecraft:overworld", 0, 0);
        ClaimBounds nether = new ClaimBounds("minecraft:the_nether", 0, 0);

        assertTrue(Double.isInfinite(overworld.distanceTo(nether)));
        assertTrue(Double.isInfinite(overworld.distanceToPoint("minecraft:the_nether", 0, 0)));
    }

    @Test
    void pointDistanceUsesClaimRectangle() {
        ClaimBounds bounds = new ClaimBounds("minecraft:overworld", 0, 0);

        assertEquals(0.0D, bounds.distanceToPoint("minecraft:overworld", 0, 0));
        assertEquals(200.0D, bounds.distanceToPoint(
                "minecraft:overworld",
                bounds.maxBlockX() + 200,
                0
        ));
    }
}
