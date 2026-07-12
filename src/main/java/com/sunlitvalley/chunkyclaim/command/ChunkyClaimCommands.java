package com.sunlitvalley.chunkyclaim.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.sunlitvalley.chunkyclaim.SocietyChunkyClaimMod;
import com.sunlitvalley.chunkyclaim.config.ChunkyClaimConfig;
import com.sunlitvalley.chunkyclaim.data.Claim;
import com.sunlitvalley.chunkyclaim.data.ClaimSavedData;
import com.sunlitvalley.chunkyclaim.gui.ClaimGuis;
import com.sunlitvalley.chunkyclaim.service.ClaimService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = SocietyChunkyClaimMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChunkyClaimCommands {
    private static final int ADMIN_PERMISSION_LEVEL = 2;
    private static final String PLAYER = "닉네임";
    private static final String OWNER = "소유자";
    private static final String MEMBER = "구성원";
    private static final SimpleCommandExceptionType ONE_PROFILE = new SimpleCommandExceptionType(
            Component.literal("플레이어 한 명만 지정해야 합니다.")
    );

    private ChunkyClaimCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("청크권")
                .requires(ChunkyClaimCommands::isAdministrator)
                .then(Commands.literal("설정").executes(ChunkyClaimCommands::setTicket))
                .then(Commands.literal("발급")
                        .executes(ChunkyClaimCommands::giveTicketToSelf)
                        .then(Commands.argument(PLAYER, EntityArgument.player())
                                .executes(ChunkyClaimCommands::giveTicket)))
                .then(Commands.literal("리로드").executes(ChunkyClaimCommands::reloadConfig))
        );

        dispatcher.register(Commands.literal("사유지관리")
                .requires(ChunkyClaimCommands::isAdministrator)
                .then(Commands.literal("목록").executes(ChunkyClaimCommands::openAdminList))
                .then(Commands.literal("철거")
                        .then(Commands.argument(PLAYER, GameProfileArgument.gameProfile())
                                .executes(ChunkyClaimCommands::demolish)))
                .then(Commands.literal("강제초대")
                        .then(Commands.argument(OWNER, GameProfileArgument.gameProfile())
                                .then(Commands.argument(MEMBER, GameProfileArgument.gameProfile())
                                        .executes(ChunkyClaimCommands::forceInvite))))
                .then(Commands.literal("강제추방")
                        .then(Commands.argument(OWNER, GameProfileArgument.gameProfile())
                                .then(Commands.argument(MEMBER, GameProfileArgument.gameProfile())
                                        .executes(ChunkyClaimCommands::forceKick))))
        );

        dispatcher.register(Commands.literal("사유지")
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("정보").executes(ChunkyClaimCommands::openInfo))
                .then(Commands.literal("초대")
                        .then(Commands.argument(PLAYER, EntityArgument.player())
                                .executes(ChunkyClaimCommands::invite)))
                .then(Commands.literal("추방")
                        .then(Commands.argument(PLAYER, GameProfileArgument.gameProfile())
                                .executes(ChunkyClaimCommands::kick)))
                .then(Commands.literal("홈설정").executes(ChunkyClaimCommands::setHome))
                .then(Commands.literal("홈").executes(ChunkyClaimCommands::home))
        );

        dispatcher.register(Commands.literal("수락").executes(ChunkyClaimCommands::accept));
        dispatcher.register(Commands.literal("거절").executes(ChunkyClaimCommands::decline));
    }

    private static int setTicket(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            held = player.getOffhandItem();
        }
        if (held.isEmpty()) {
            return fail(context.getSource(), "주 손 또는 보조 손에 청크권으로 설정할 아이템을 들어야 합니다.");
        }
        try {
            ChunkyClaimConfig.setTicketItem(held);
            return success(context.getSource(), "손에 든 아이템을 청크권으로 설정했습니다.", true);
        } catch (IOException exception) {
            SocietyChunkyClaimMod.LOGGER.error("Failed to save claim ticket configuration", exception);
            return fail(context.getSource(), "설정 파일 저장에 실패했습니다: " + exception.getMessage());
        }
    }

    private static int giveTicketToSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return giveTicket(context.getSource(), context.getSource().getPlayerOrException());
    }

    private static int giveTicket(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return giveTicket(context.getSource(), EntityArgument.getPlayer(context, PLAYER));
    }

    private static int giveTicket(CommandSourceStack source, ServerPlayer target) {
        ItemStack ticket;
        try {
            ticket = ChunkyClaimConfig.ticketItem();
        } catch (IllegalStateException exception) {
            return fail(source, "설정된 청크권 정보가 올바르지 않습니다.");
        }
        if (ticket.isEmpty()) {
            return fail(source, "먼저 /청크권 설정 명령어로 청크권 아이템을 설정해야 합니다.");
        }
        if (!target.addItem(ticket.copy())) {
            target.drop(ticket.copy(), false);
        }
        return success(source, target.getGameProfile().getName() + "님에게 청크권을 발급했습니다.", true);
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        try {
            ChunkyClaimConfig.reload();
            return success(context.getSource(), "설정 파일을 다시 불러왔습니다: "
                    + ChunkyClaimConfig.path(), true);
        } catch (IOException exception) {
            SocietyChunkyClaimMod.LOGGER.error("Failed to reload claim configuration", exception);
            return fail(context.getSource(), "설정 리로드에 실패했습니다: " + exception.getMessage());
        }
    }

    private static int openAdminList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ClaimGuis.openAdminList(player, 0);
        return 1;
    }

    private static int demolish(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GameProfile owner = oneProfile(context, PLAYER);
        return send(context.getSource(), ClaimService.demolish(
                context.getSource().getServer(), owner.getId(), owner.getName()
        ), true);
    }

    private static int forceInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GameProfile owner = oneProfile(context, OWNER);
        GameProfile member = oneProfile(context, MEMBER);
        return send(context.getSource(), ClaimService.forceInvite(
                context.getSource().getServer(),
                owner.getId(), owner.getName(),
                member.getId(), member.getName()
        ), true);
    }

    private static int forceKick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GameProfile owner = oneProfile(context, OWNER);
        GameProfile member = oneProfile(context, MEMBER);
        return send(context.getSource(), ClaimService.kickMember(
                context.getSource().getServer(),
                owner.getId(), owner.getName(),
                member.getId(), member.getName()
        ), true);
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[사유지 명령어]").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("/사유지 정보 - 소유하거나 소속된 사유지 정보"), false);
        source.sendSuccess(() -> Component.literal("/사유지 초대 <닉네임> - 내 사유지에 플레이어 초대"), false);
        source.sendSuccess(() -> Component.literal("/수락, /거절 - 받은 사유지 초대 응답"), false);
        source.sendSuccess(() -> Component.literal("/사유지 추방 <닉네임> - 내 사유지 구성원 추방"), false);
        source.sendSuccess(() -> Component.literal("/사유지 홈설정 - 현재 위치를 홈으로 설정"), false);
        source.sendSuccess(() -> Component.literal("/사유지 홈 - 사유지 홈으로 이동"), false);
        return 1;
    }

    private static int openInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<Claim> claim = ClaimService.data(player.getServer()).forPlayer(player.getUUID());
        if (claim.isEmpty()) {
            return showHelp(context.getSource());
        }
        ClaimGuis.openInfo(player, claim.get());
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer owner = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, PLAYER);
        if (ClaimService.data(owner.getServer()).forPlayer(owner.getUUID()).isEmpty()) {
            return showHelp(context.getSource());
        }
        return send(context.getSource(), ClaimService.invite(owner, target), false);
    }

    private static int accept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return send(context.getSource(), ClaimService.accept(context.getSource().getPlayerOrException()), false);
    }

    private static int decline(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return send(context.getSource(), ClaimService.decline(context.getSource().getPlayerOrException()), false);
    }

    private static int kick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer owner = context.getSource().getPlayerOrException();
        ClaimSavedData data = ClaimService.data(owner.getServer());
        if (data.forPlayer(owner.getUUID()).isEmpty()) {
            return showHelp(context.getSource());
        }
        GameProfile target = oneProfile(context, PLAYER);
        return send(context.getSource(), ClaimService.kickMember(
                owner.getServer(),
                owner.getUUID(), owner.getGameProfile().getName(),
                target.getId(), target.getName()
        ), false);
    }

    private static int setHome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer owner = context.getSource().getPlayerOrException();
        if (ClaimService.data(owner.getServer()).forPlayer(owner.getUUID()).isEmpty()) {
            return showHelp(context.getSource());
        }
        return send(context.getSource(), ClaimService.setHome(owner), false);
    }

    private static int home(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (ClaimService.data(player.getServer()).forPlayer(player.getUUID()).isEmpty()) {
            return showHelp(context.getSource());
        }
        return send(context.getSource(), ClaimService.teleportHome(player), false);
    }

    private static GameProfile oneProfile(CommandContext<CommandSourceStack> context, String argument)
            throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, argument);
        if (profiles.size() != 1) {
            throw ONE_PROFILE.create();
        }
        return profiles.iterator().next();
    }

    private static int send(CommandSourceStack source, ClaimService.ActionResult result, boolean broadcast) {
        if (!result.success()) {
            return fail(source, result.message());
        }
        return success(source, result.message(), broadcast);
    }

    private static int success(CommandSourceStack source, String message, boolean broadcast) {
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), broadcast);
        return 1;
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message).withStyle(ChatFormatting.RED));
        return 0;
    }

    private static boolean isAdministrator(CommandSourceStack source) {
        return source.hasPermission(ADMIN_PERMISSION_LEVEL);
    }
}
