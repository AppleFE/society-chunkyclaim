package com.sunlitvalley.chunkyclaim.event;

import com.sunlitvalley.chunkyclaim.SocietyChunkyClaimMod;
import com.sunlitvalley.chunkyclaim.config.ChunkyClaimConfig;
import com.sunlitvalley.chunkyclaim.service.ClaimService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SocietyChunkyClaimMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TicketEvents {
    private TicketEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        handle(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        handle(event);
    }

    private static void handle(PlayerInteractEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = player.getItemInHand(event.getHand());
        if (!ChunkyClaimConfig.isTicket(held)) {
            return;
        }

        ClaimService.CreateResult result = ClaimService.createClaim(player);
        if (result.success()) {
            held.shrink(1);
            player.sendSystemMessage(Component.literal(result.message()));
        } else {
            player.sendSystemMessage(Component.literal(result.message()));
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
