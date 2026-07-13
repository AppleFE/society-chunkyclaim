package com.sunlitvalley.chunkyclaim.compat;

import com.sunlitvalley.chunkyclaim.data.ClaimBounds;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PortableBlueprintFootprint {
    private static final Pattern POSITION = Pattern.compile("^x=(-?\\d+),y=(-?\\d+),z=(-?\\d+)$");

    private PortableBlueprintFootprint() {
    }

    @SafeVarargs
    public static boolean isInside(ClaimBounds bounds, BlockPos offset,
                                   Map<Integer, Map<String, String>>... blockGroups) {
        if (offset == null) {
            return false;
        }
        for (Map<Integer, Map<String, String>> group : blockGroups) {
            if (group == null) {
                continue;
            }
            for (Map<String, String> layer : group.values()) {
                if (layer == null) {
                    continue;
                }
                for (String encodedPosition : layer.keySet()) {
                    RelativePosition relative = parse(encodedPosition);
                    if (relative == null) {
                        return false;
                    }
                    long targetX = (long) offset.getX() + relative.x();
                    long targetZ = (long) offset.getZ() + relative.z();
                    if (targetX < bounds.minBlockX() || targetX > bounds.maxBlockX()
                            || targetZ < bounds.minBlockZ() || targetZ > bounds.maxBlockZ()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static RelativePosition parse(String encodedPosition) {
        if (encodedPosition == null) {
            return null;
        }
        Matcher matcher = POSITION.matcher(encodedPosition);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new RelativePosition(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(3))
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record RelativePosition(int x, int z) {
    }
}
