package com.sunlitvalley.chunkyclaim.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sunlitvalley.chunkyclaim.SocietyChunkyClaimMod;
import com.sunlitvalley.chunkyclaim.data.Claim;
import com.sunlitvalley.chunkyclaim.service.ClaimService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

public final class PortableBlueprintProtection {
    private static final String BLUEPRINT_DATA_CLASS =
            "nimble.portable_blueprints.altro.Blueprint$BlueprintDatas";
    private static final Gson GSON = new GsonBuilder().create();

    private PortableBlueprintProtection() {
    }

    public static boolean allowUse(ServerPlayer player, ServerLevel level, BlockPos origin) {
        Optional<Claim> ownedClaim = ClaimService.ownedClaim(player);
        if (ownedClaim.isEmpty()
                || !ownedClaim.get().bounds().contains(level.dimension(), player.blockPosition())
                || !ownedClaim.get().bounds().contains(level.dimension(), origin)) {
            deny(player, "청사진은 본인이 소유한 사유지 안에서만 사용할 수 있습니다.");
            return false;
        }
        return true;
    }

    public static boolean allowFootprint(ServerPlayer player, ServerLevel level, BlockPos offset,
                                         Map<Integer, Map<String, String>> solidBlocks,
                                         Map<Integer, Map<String, String>> nonSolidBlocks) {
        Optional<Claim> ownedClaim = ClaimService.ownedClaim(player);
        if (ownedClaim.isEmpty()
                || !ownedClaim.get().bounds().dimension().equals(level.dimension().location().toString())) {
            deny(player, "청사진은 본인이 소유한 사유지 안에서만 사용할 수 있습니다.");
            return false;
        }
        if (!PortableBlueprintFootprint.isInside(
                ownedClaim.get().bounds(),
                offset,
                solidBlocks,
                nonSolidBlocks
        )) {
            deny(player, "청사진 건축물은 사유지 경계를 1블럭도 벗어날 수 없습니다.");
            return false;
        }
        return true;
    }

    public static boolean allowBlueprintFile(ServerPlayer player, ServerLevel level,
                                             File blueprintFile, double rotation, short autofillMode,
                                             boolean mirrorX, boolean mirrorY, boolean mirrorZ,
                                             BlockPos origin) {
        if (blueprintFile == null || origin == null) {
            return denyUnreadable(player, blueprintFile, null);
        }
        try {
            Class<?> dataClass = Class.forName(BLUEPRINT_DATA_CLASS);
            Object blueprintData;
            try (FileReader reader = new FileReader(blueprintFile)) {
                blueprintData = GSON.fromJson(reader, dataClass);
            }
            if (blueprintData == null) {
                return denyUnreadable(player, blueprintFile, null);
            }
            if (rotation != 0.0D) {
                blueprintData = dataClass
                        .getMethod("rotate360", double.class, short.class, ServerLevel.class)
                        .invoke(blueprintData, rotation, autofillMode, level);
            }
            if (mirrorX || mirrorY || mirrorZ) {
                blueprintData = dataClass
                        .getMethod("Mirror", boolean.class, boolean.class, boolean.class)
                        .invoke(blueprintData, mirrorX, mirrorY, mirrorZ);
            }

            BlockPos savedOffset = (BlockPos) field(dataClass, "offset").get(blueprintData);
            BlockPos finalOffset = (savedOffset == null ? BlockPos.ZERO : savedOffset).offset(origin);
            return allowFootprint(
                    player,
                    level,
                    finalOffset,
                    blockMap(field(dataClass, "BlocksData").get(blueprintData)),
                    blockMap(field(dataClass, "NotSolidBlocksData").get(blueprintData))
            );
        } catch (IOException | ReflectiveOperationException | RuntimeException exception) {
            return denyUnreadable(player, blueprintFile, exception);
        }
    }

    private static Field field(Class<?> dataClass, String name) throws NoSuchFieldException {
        return dataClass.getField(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Map<String, String>> blockMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw new ClassCastException("Blueprint block data is not a map");
        }
        return (Map<Integer, Map<String, String>>) value;
    }

    private static boolean denyUnreadable(ServerPlayer player, File blueprintFile, Exception exception) {
        String fileName = blueprintFile == null ? "<null>" : blueprintFile.getName();
        if (exception == null) {
            SocietyChunkyClaimMod.LOGGER.error("Portable Blueprint data was empty: {}", fileName);
        } else {
            SocietyChunkyClaimMod.LOGGER.error("Failed to validate Portable Blueprint file: {}", fileName, exception);
        }
        deny(player, "청사진의 설치 범위를 확인할 수 없어 사용이 취소되었습니다.");
        return false;
    }

    private static void deny(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
