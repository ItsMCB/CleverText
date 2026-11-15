package me.itsmcb.clevertext.commands;

import me.itsmcb.clevertext.CleverTextContext;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelCommandNode;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelRootCommand;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CleverTextCmd extends VexelRootCommand<CleverTextContext> {

    public CleverTextCmd(@NotNull CleverTextContext context) {
        super(context, "clevertext", "Manage CleverText chat formatting and text processing", "clevertext.admin");
    }

    @Override
    public @NotNull List<String> getAliases() {
        return List.of("ct");
    }

    @Override
    public @NotNull List<VexelCommandNode> getChildren() {
        return List.of(
                new ReloadSCmd(context),
                new DebugSCmd(context),
                new ListSCmd(context),
                new TestSCmd(context),
                new ClearChatSCmd(context)
        );
    }
}