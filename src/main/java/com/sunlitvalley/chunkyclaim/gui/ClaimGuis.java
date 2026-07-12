package com.sunlitvalley.chunkyclaim.gui;

import com.sunlitvalley.chunkyclaim.data.Claim;
import com.sunlitvalley.chunkyclaim.data.ClaimBounds;
import com.sunlitvalley.chunkyclaim.service.ClaimService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class ClaimGuis {
    private static final int ADMIN_ROWS = 6;
    private static final int ADMIN_PAGE_SIZE = 45;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private ClaimGuis() {
    }

    public static void openInfo(ServerPlayer viewer, Claim claim) {
        SimpleContainer container = new SimpleContainer(27);
        container.setItem(10, playerHead(
                claim.ownerId(),
                claim.ownerName(),
                Component.literal("사유지 소유자").withStyle(ChatFormatting.GOLD),
                List.of(
                        Component.literal("UUID: " + claim.ownerId()).withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal("생성: " + DATE_FORMAT.format(Instant.ofEpochMilli(claim.createdAtEpochMillis())))
                                .withStyle(ChatFormatting.GRAY)
                )
        ));

        ClaimBounds bounds = claim.bounds();
        container.setItem(12, namedItem(
                Items.FILLED_MAP,
                Component.literal("사유지 범위").withStyle(ChatFormatting.GREEN),
                List.of(
                        Component.literal("차원: " + bounds.dimension()).withStyle(ChatFormatting.GRAY),
                        Component.literal("청크: X " + bounds.minChunkX() + " ~ " + bounds.maxChunkX())
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("청크: Z " + bounds.minChunkZ() + " ~ " + bounds.maxChunkZ())
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("블럭: X " + bounds.minBlockX() + " ~ " + bounds.maxBlockX())
                                .withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal("블럭: Z " + bounds.minBlockZ() + " ~ " + bounds.maxBlockZ())
                                .withStyle(ChatFormatting.DARK_GRAY)
                )
        ));

        List<Component> homeLore = new ArrayList<>();
        if (claim.home() == null) {
            homeLore.add(Component.literal("미설정").withStyle(ChatFormatting.YELLOW));
            homeLore.add(Component.literal("/사유지 홈설정 으로 설정할 수 있습니다.")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            homeLore.add(Component.literal("차원: " + claim.home().dimension()).withStyle(ChatFormatting.GRAY));
            homeLore.add(Component.literal("좌표: " + claim.home().position().toShortString())
                    .withStyle(ChatFormatting.GRAY));
        }
        container.setItem(14, namedItem(
                Items.COMPASS,
                Component.literal("사유지 홈").withStyle(ChatFormatting.AQUA),
                homeLore
        ));

        int slot = 18;
        for (Map.Entry<UUID, String> member : claim.members().entrySet()) {
            if (slot >= 27) {
                break;
            }
            container.setItem(slot++, playerHead(
                    member.getKey(),
                    member.getValue(),
                    Component.literal(member.getValue()).withStyle(ChatFormatting.WHITE),
                    List.of(Component.literal("사유지 구성원").withStyle(ChatFormatting.GREEN))
            ));
        }
        if (claim.members().isEmpty()) {
            container.setItem(22, namedItem(
                    Items.BARRIER,
                    Component.literal("소속 구성원 없음").withStyle(ChatFormatting.GRAY),
                    List.of()
            ));
        }

        viewer.openMenu(new SimpleMenuProvider(
                (id, inventory, player) -> new ReadOnlyChestMenu(
                        MenuType.GENERIC_9x3, id, inventory, container, 3, (ignored, ignoredSlot) -> {
                        }
                ),
                Component.literal(claim.ownerName() + "님의 사유지 정보")
        ));
    }

    public static void openAdminList(ServerPlayer viewer, int requestedPage) {
        List<Claim> claims = ClaimService.data(viewer.getServer()).all().stream()
                .sorted(Comparator.comparing(Claim::ownerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int maxPage = Math.max(0, (claims.size() - 1) / ADMIN_PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        SimpleContainer container = new SimpleContainer(ADMIN_ROWS * 9);
        int start = page * ADMIN_PAGE_SIZE;
        int end = Math.min(start + ADMIN_PAGE_SIZE, claims.size());
        for (int index = start; index < end; index++) {
            Claim claim = claims.get(index);
            container.setItem(index - start, adminClaimHead(claim));
        }

        if (page > 0) {
            container.setItem(45, namedItem(
                    Items.ARROW,
                    Component.literal("이전 페이지").withStyle(ChatFormatting.YELLOW),
                    List.of()
            ));
        }
        container.setItem(49, namedItem(
                Items.PAPER,
                Component.literal("사유지 " + claims.size() + "개").withStyle(ChatFormatting.GOLD),
                List.of(Component.literal((page + 1) + " / " + (maxPage + 1) + " 페이지")
                        .withStyle(ChatFormatting.GRAY))
        ));
        if (page < maxPage) {
            container.setItem(53, namedItem(
                    Items.ARROW,
                    Component.literal("다음 페이지").withStyle(ChatFormatting.YELLOW),
                    List.of()
            ));
        }

        viewer.openMenu(new SimpleMenuProvider(
                (id, inventory, player) -> new ReadOnlyChestMenu(
                        MenuType.GENERIC_9x6,
                        id,
                        inventory,
                        container,
                        ADMIN_ROWS,
                        (clicker, slot) -> {
                            if (!ClaimService.isOperator(clicker)) {
                                clicker.closeContainer();
                                return;
                            }
                            if (slot >= 0 && slot < end - start) {
                                Claim selected = claims.get(start + slot);
                                clicker.closeContainer();
                                send(clicker, ClaimService.teleportToClaim(clicker, selected, true));
                            } else if (slot == 45 && page > 0) {
                                clicker.closeContainer();
                                openAdminList(clicker, page - 1);
                            } else if (slot == 53 && page < maxPage) {
                                clicker.closeContainer();
                                openAdminList(clicker, page + 1);
                            }
                        }
                ),
                Component.literal("사유지 관리 목록")
        ));
    }

    private static ItemStack adminClaimHead(Claim claim) {
        ClaimBounds bounds = claim.bounds();
        return playerHead(
                claim.ownerId(),
                claim.ownerName(),
                Component.literal(claim.ownerName() + "님의 사유지").withStyle(ChatFormatting.GOLD),
                List.of(
                        Component.literal("차원: " + bounds.dimension()).withStyle(ChatFormatting.GRAY),
                        Component.literal("중앙 청크: " + bounds.centerChunkX() + ", " + bounds.centerChunkZ())
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("범위: X " + bounds.minChunkX() + "~" + bounds.maxChunkX()
                                + ", Z " + bounds.minChunkZ() + "~" + bounds.maxChunkZ())
                                .withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal("구성원: " + claim.members().size() + "명")
                                .withStyle(ChatFormatting.AQUA),
                        Component.literal("생성: " + DATE_FORMAT.format(Instant.ofEpochMilli(claim.createdAtEpochMillis())))
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("클릭하여 이동").withStyle(ChatFormatting.GREEN)
                )
        );
    }

    private static ItemStack playerHead(UUID playerId, String playerName, Component name,
                                        List<Component> lore) {
        ItemStack stack = namedItem(Items.PLAYER_HEAD, name, lore);
        CompoundTag owner = new CompoundTag();
        owner.putUUID("Id", playerId);
        owner.putString("Name", playerName);
        stack.getOrCreateTag().put("SkullOwner", owner);
        return stack;
    }

    private static ItemStack namedItem(net.minecraft.world.item.Item item, Component name,
                                       List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(name);
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lines = new ListTag();
        for (Component line : lore) {
            lines.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        display.put("Lore", lines);
        return stack;
    }

    private static void send(ServerPlayer player, ClaimService.ActionResult result) {
        player.sendSystemMessage(Component.literal(result.message()));
    }

    private static final class ReadOnlyChestMenu extends ChestMenu {
        private final int protectedSlots;
        private final BiConsumer<ServerPlayer, Integer> action;

        private ReadOnlyChestMenu(MenuType<?> type, int id, Inventory inventory,
                                  SimpleContainer container, int rows,
                                  BiConsumer<ServerPlayer, Integer> action) {
            super(type, id, inventory, container, rows);
            this.protectedSlots = rows * 9;
            this.action = action;
        }

        @Override
        public void clicked(int slot, int button, ClickType clickType, Player player) {
            if (slot >= 0 && slot < protectedSlots && player instanceof ServerPlayer serverPlayer) {
                action.accept(serverPlayer, slot);
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }
    }
}
