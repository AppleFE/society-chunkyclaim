package com.sunlitvalley.chunkyclaim.mixin.compat;

import com.sunlitvalley.chunkyclaim.service.ClaimService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "nimble.portable_blueprints.altro.PlacingBlockManager$PlaceBlock", remap = false)
public abstract class PortableBlueprintPlaceMixin {
    @Shadow(remap = false)
    @Final
    private ServerPlayer player;

    @Shadow(remap = false)
    @Final
    public ServerLevel serverLevel;

    @Shadow(remap = false)
    @Final
    public BlockPos blockPos;

    @Inject(method = "placeNow", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void societyChunkyClaim$checkTarget(CallbackInfo callback) {
        if (!ClaimService.ownsPosition(player, serverLevel, blockPos)) {
            callback.cancel();
        }
    }
}
