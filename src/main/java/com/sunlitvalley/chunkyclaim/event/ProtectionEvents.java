package com.sunlitvalley.chunkyclaim.event;

import com.sunlitvalley.chunkyclaim.SocietyChunkyClaimMod;
import com.sunlitvalley.chunkyclaim.config.ChunkyClaimConfig;
import com.sunlitvalley.chunkyclaim.data.Claim;
import com.sunlitvalley.chunkyclaim.service.ClaimService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = SocietyChunkyClaimMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProtectionEvents {
    private static final Map<UUID, Long> LAST_DENIAL_MESSAGE = new HashMap<>();
    private static final Component DENIAL_MESSAGE = Component.literal("이 사유지에서는 상호작용할 수 없습니다.");

    private ProtectionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos clicked = event.getPos();
        BlockPos adjacent = event.getFace() == null ? clicked : clicked.relative(event.getFace());
        if (!ClaimService.mayAccess(player, event.getLevel(), clicked)
                || !ClaimService.mayAccess(player, event.getLevel(), adjacent)) {
            deny(player);
            event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !ClaimService.mayAccess(player, event.getLevel(), event.getPos())) {
            deny(player);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        if (event.getPlayer() instanceof ServerPlayer player) {
            if (!ClaimService.mayAccess(player, level, event.getPos())) {
                deny(player);
                event.setCanceled(true);
            }
        } else if (ClaimService.claimAt(level, event.getPos()).isPresent()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        ServerPlayer player = event.getEntity() instanceof ServerPlayer value ? value : null;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace) {
            for (BlockSnapshot snapshot : multiPlace.getReplacedBlockSnapshots()) {
                if (!mayModify(player, level, snapshot.getPos())) {
                    denyIfPlayer(player);
                    event.setCanceled(true);
                    return;
                }
            }
        } else if (!mayModify(player, level, event.getPos())) {
            denyIfPlayer(player);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToolModify(BlockEvent.BlockToolModificationEvent event) {
        if (event.isSimulated() || !(event.getLevel() instanceof Level level)) {
            return;
        }
        ServerPlayer player = event.getPlayer() instanceof ServerPlayer value ? value : null;
        if (!mayModify(player, level, event.getPos())) {
            denyIfPlayer(player);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBonemeal(BonemealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !ClaimService.mayAccess(player, event.getLevel(), event.getPos())) {
            deny(player);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFillBucket(FillBucketEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos target = hitPosition(event.getTarget());
        if (target != null && !ClaimService.mayAccess(player, event.getLevel(), target)) {
            deny(player);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        protectEntityInteraction(event, event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        protectEntityInteraction(event, event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !ClaimService.mayAccess(player, event.getTarget().level(), event.getTarget().blockPosition())) {
            deny(player);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        ServerPlayer attacker = responsiblePlayer(event.getSource().getEntity());
        if (attacker != null && !ClaimService.mayAccess(
                attacker,
                event.getEntity().level(),
                event.getEntity().blockPosition()
        )) {
            deny(attacker);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMount(EntityMountEvent event) {
        if (!event.isMounting() || !(event.getEntityMounting() instanceof ServerPlayer player)) {
            return;
        }
        Entity mount = event.getEntityBeingMounted();
        if (!ClaimService.mayAccess(player, event.getLevel(), mount.blockPosition())) {
            deny(player);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !ClaimService.mayAccess(player, event.getItem().level(), event.getItem().blockPosition())) {
            deny(player);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        ServerPlayer owner = responsiblePlayer(event.getProjectile().getOwner());
        if (owner == null) {
            return;
        }
        BlockPos target = hitPosition(event.getRayTraceResult());
        if (target != null && !ClaimService.mayAccess(owner, event.getProjectile().level(), target)) {
            deny(owner);
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
            event.getProjectile().discard();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        ServerPlayer player = responsiblePlayer(event.getEntity());
        Optional<Claim> claim = ClaimService.claimAt(level, event.getPos());
        if (claim.isPresent() && (player == null || !claim.get().canAccess(player.getUUID()))) {
            denyIfPlayer(player);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (ClaimService.claimAt(entity.level(), entity.blockPosition()).isPresent()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (ClaimService.claimAt(event.getEntity().level(), event.getPos()).isPresent()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        Optional<UUID> sourceOwner = ownerAt(level, event.getLiquidPos());
        Optional<UUID> targetOwner = ownerAt(level, event.getPos());
        if (!sourceOwner.equals(targetOwner)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (!resolver.resolve()) {
            return;
        }

        Optional<UUID> pistonOwner = ownerAt(level, event.getPos());
        for (BlockPos source : resolver.getToPush()) {
            Optional<UUID> sourceOwner = ownerAt(level, source);
            Optional<UUID> destinationOwner = ownerAt(level, source.relative(resolver.getPushDirection()));
            if (!sourceOwner.equals(destinationOwner) || !pistonOwner.equals(sourceOwner)) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos destroyed : resolver.getToDestroy()) {
            if (!pistonOwner.equals(ownerAt(level, destroyed))) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer responsible = explosionPlayer(event.getExplosion());
        event.getAffectedBlocks().removeIf(position -> !mayModify(responsible, level, position));
        event.getAffectedEntities().removeIf(entity -> !mayModify(
                responsible,
                level,
                entity.blockPosition()
        ));
    }

    private static void protectEntityInteraction(PlayerInteractEvent event, Entity target) {
        if (event.getEntity() instanceof ServerPlayer player
                && !ClaimService.mayAccess(player, target.level(), target.blockPosition())) {
            deny(player);
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    private static boolean mayModify(ServerPlayer player, Level level, BlockPos position) {
        Optional<Claim> claim = ClaimService.claimAt(level, position);
        if (claim.isEmpty()) {
            return true;
        }
        return player != null && (ClaimService.isOperator(player) || claim.get().canAccess(player.getUUID()));
    }

    public static boolean allowModAction(ServerPlayer player, Level level, BlockPos position) {
        boolean allowed = mayModify(player, level, position);
        if (!allowed) {
            deny(player);
        }
        return allowed;
    }

    private static Optional<UUID> ownerAt(Level level, BlockPos position) {
        return ClaimService.claimAt(level, position).map(Claim::ownerId);
    }

    private static ServerPlayer responsiblePlayer(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    private static ServerPlayer explosionPlayer(Explosion explosion) {
        ServerPlayer player = responsiblePlayer(explosion.getExploder());
        if (player != null) {
            return player;
        }
        return responsiblePlayer(explosion.getDirectSourceEntity());
    }

    private static BlockPos hitPosition(HitResult hit) {
        if (hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        if (hit instanceof EntityHitResult entityHit) {
            return entityHit.getEntity().blockPosition();
        }
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            return BlockPos.containing(hit.getLocation());
        }
        return null;
    }

    private static void denyIfPlayer(ServerPlayer player) {
        if (player != null) {
            deny(player);
        }
    }

    private static void deny(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long last = LAST_DENIAL_MESSAGE.getOrDefault(player.getUUID(), 0L);
        if (now - last >= ChunkyClaimConfig.get().protectionMessageCooldownMillis) {
            player.displayClientMessage(DENIAL_MESSAGE, true);
            LAST_DENIAL_MESSAGE.put(player.getUUID(), now);
        }
    }
}
