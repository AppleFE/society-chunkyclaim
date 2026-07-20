package com.sunlitvalley.chunkyclaim.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChunkyClaimCommandsTest {
    @Test
    void registersClaimBoundaryToggle() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        ChunkyClaimCommands.register(dispatcher);

        CommandNode<CommandSourceStack> claimCommand = dispatcher.getRoot().getChild("사유지");
        assertNotNull(claimCommand);
        CommandNode<CommandSourceStack> boundaryToggle = claimCommand.getChild("구역보기");
        assertNotNull(boundaryToggle);
        assertNotNull(boundaryToggle.getCommand());
    }
}
