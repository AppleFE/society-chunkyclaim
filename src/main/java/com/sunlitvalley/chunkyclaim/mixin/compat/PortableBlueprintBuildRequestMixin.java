package com.sunlitvalley.chunkyclaim.mixin.compat;

import com.sunlitvalley.chunkyclaim.compat.PortableBlueprintProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.List;
import java.util.Map;

@Pseudo
@Mixin(targets = "nimble.portable_blueprints.network.CtoS_CreateStructureByBlueprint", remap = false)
public abstract class PortableBlueprintBuildRequestMixin {
    @Shadow(remap = false)
    @Final
    private ItemStack blueprint_item;

    @Inject(method = "lambda$handle$2", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void societyChunkyClaim$checkUseLocation(NetworkEvent.Context context, CallbackInfo callback) {
        ServerPlayer player = context.getSender();
        if (player == null || blueprint_item.isEmpty()) {
            callback.cancel();
            return;
        }

        BlockPos origin = parseOrigin(blueprint_item.getTag());
        if (origin == null || !PortableBlueprintProtection.allowUse(player, player.serverLevel(), origin)) {
            callback.cancel();
        }
    }

    @Inject(method = "lambda$handle$1", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void societyChunkyClaim$checkWholeStructure(
            File blueprintFile,
            double rotation,
            short autofillMode,
            ServerLevel level,
            boolean mirrorX,
            boolean mirrorY,
            boolean mirrorZ,
            BlockPos origin,
            CompoundTag blueprintTag,
            ServerPlayer player,
            List<String> blocksDataToRemove,
            List<String> nonSolidBlocksDataToRemove,
            boolean skipObstructionBlock,
            boolean buildAnyway,
            Map<?, ?> availableItems,
            List<BlockPos> inventoryPositions,
            CallbackInfo callback
    ) {
        if (!PortableBlueprintProtection.allowBlueprintFile(
                player,
                level,
                blueprintFile,
                rotation,
                autofillMode,
                mirrorX,
                mirrorY,
                mirrorZ,
                origin
        )) {
            callback.cancel();
        }
    }

    private static BlockPos parseOrigin(CompoundTag tag) {
        if (tag == null || !tag.contains("originPos")) {
            return null;
        }
        String encoded = tag.getString("originPos");
        String normalized = encoded.replace("=", "")
                .replace("x", "")
                .replace("y", "")
                .replace("z", "");
        String[] parts = normalized.split(",", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
