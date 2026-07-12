package com.sunlitvalley.chunkyclaim.mixin.compat;

import com.sunlitvalley.chunkyclaim.SocietyChunkyClaimMod;
import com.sunlitvalley.chunkyclaim.event.ProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets2.common.events.ServerTickHandler", remap = false)
public abstract class BuildingGadgetsTickMixin {
    @Shadow(remap = false)
    @Final
    public static HashMap<UUID, Object> buildMap;

    @Inject(method = "handleTickEndEvent", at = @At("HEAD"), require = 0, remap = false)
    private static void societyChunkyClaim$filterBuildTargets(TickEvent.ServerTickEvent event,
                                                               CallbackInfo callback) {
        for (Object buildList : List.copyOf(buildMap.values())) {
            try {
                Class<?> type = buildList.getClass();
                UUID playerId = (UUID) publicField(type, "playerUUID").get(buildList);
                Level level = (Level) publicField(type, "level").get(buildList);
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
                if (player == null || level == null) {
                    continue;
                }

                Object targets = publicField(type, "statePosList").get(buildList);
                if (targets instanceof List<?> targetList) {
                    targetList.removeIf(target -> !allowStatePos(player, level, target));
                }

                Object retries = publicField(type, "retryList").get(buildList);
                if (retries instanceof List<?> retryList) {
                    retryList.removeIf(target -> target instanceof BlockPos position
                            && !ProtectionEvents.allowModAction(player, level, position));
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                SocietyChunkyClaimMod.LOGGER.warn(
                        "Unable to inspect a Building Gadgets 2 build queue; denying that queue for safety",
                        exception
                );
                buildMap.values().remove(buildList);
            }
        }
    }

    private static boolean allowStatePos(ServerPlayer player, Level level, Object statePos) {
        try {
            Object value = publicField(statePos.getClass(), "pos").get(statePos);
            return !(value instanceof BlockPos position)
                    || ProtectionEvents.allowModAction(player, level, position);
        } catch (ReflectiveOperationException exception) {
            SocietyChunkyClaimMod.LOGGER.warn("Unable to inspect a Building Gadgets 2 target", exception);
            return false;
        }
    }

    private static Field publicField(Class<?> type, String name) throws NoSuchFieldException {
        return type.getField(name);
    }
}
