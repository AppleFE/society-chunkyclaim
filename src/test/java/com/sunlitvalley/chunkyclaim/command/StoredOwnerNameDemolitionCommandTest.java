package com.sunlitvalley.chunkyclaim.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class StoredOwnerNameDemolitionCommandTest {
    @Test
    void registersStoredOwnerNameDemolitionCommand() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        ChunkyClaimCommands.register(dispatcher);

        CommandNode<CommandSourceStack> administration = dispatcher.getRoot().getChild("사유지관리");
        assertNotNull(administration);
        CommandNode<CommandSourceStack> demolition = administration.getChild("이름철거");
        assertNotNull(demolition);
        assertNotNull(demolition.getChild("저장된소유자명"));
    }
}
