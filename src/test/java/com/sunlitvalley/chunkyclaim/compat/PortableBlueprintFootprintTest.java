package com.sunlitvalley.chunkyclaim.compat;

import com.sunlitvalley.chunkyclaim.data.ClaimBounds;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableBlueprintFootprintTest {
    private static final ClaimBounds BOUNDS = new ClaimBounds("minecraft:overworld", 0, 0);

    @Test
    void acceptsEveryBlockThroughTheExactClaimEdge() {
        Map<Integer, Map<String, String>> blocks = Map.of(
                0, Map.of(
                        "x=0,y=0,z=0", "stone",
                        "x=47,y=10,z=47", "stone"
                )
        );

        assertTrue(PortableBlueprintFootprint.isInside(
                BOUNDS,
                new BlockPos(BOUNDS.minBlockX(), 64, BOUNDS.minBlockZ()),
                blocks
        ));
    }

    @Test
    void rejectsAOneBlockOverflow() {
        Map<Integer, Map<String, String>> blocks = Map.of(
                0, Map.of("x=48,y=0,z=0", "stone")
        );

        assertFalse(PortableBlueprintFootprint.isInside(
                BOUNDS,
                new BlockPos(BOUNDS.minBlockX(), 64, BOUNDS.minBlockZ()),
                blocks
        ));
    }

    @Test
    void includesNonSolidBlocksInTheFootprint() {
        Map<Integer, Map<String, String>> solidBlocks = Map.of(
                0, Map.of("x=0,y=0,z=0", "stone")
        );
        Map<Integer, Map<String, String>> nonSolidBlocks = Map.of(
                0, Map.of("x=-1,y=0,z=0", "torch")
        );

        assertFalse(PortableBlueprintFootprint.isInside(
                BOUNDS,
                new BlockPos(BOUNDS.minBlockX(), 64, BOUNDS.minBlockZ()),
                solidBlocks,
                nonSolidBlocks
        ));
    }

    @Test
    void malformedCoordinatesFailClosed() {
        Map<Integer, Map<String, String>> blocks = Map.of(
                0, Map.of("not-a-position", "stone")
        );

        assertFalse(PortableBlueprintFootprint.isInside(BOUNDS, BlockPos.ZERO, blocks));
    }
}
