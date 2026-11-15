package me.itsmcb.clevertext.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.itsmcb.clevertext.CleverTextContext;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelCommandNode;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelCommandPath;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelRootCommand;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ClearChatRootCmd extends VexelRootCommand<CleverTextContext> {

    private final ClearChatSCmd delegate;

    public ClearChatRootCmd(@NotNull CleverTextContext context) {
        super(context, "clearchat", "Send empty messages to push unwanted messages away", "clevertext.clearchat");
        this.delegate = new ClearChatSCmd(context);
    }

    @Override
    public @NotNull String getPermission() {
        return delegate.getPermission();
    }

    @Override
    public boolean hasPermission(@NotNull CommandSourceStack source) {
        return delegate.hasPermission(source);
    }

    @Override
    public @NotNull List<String> getAliases() {
        return List.of("cc");
    }

    @Override
    public @NotNull List<VexelCommandNode> getChildren() {
        return List.of();
    }

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> buildBrigadier(@NotNull VexelCommandPath parentPath, @NotNull String literalName) {
        return delegate.buildBrigadier(parentPath, literalName);
    }
}