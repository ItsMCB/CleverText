package me.itsmcb.clevertext.commands;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.itsmcb.clevertext.CleverTextContext;
import me.itsmcb.vexelcore.bukkit.api.framework.TextUtils;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelArg;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelSenderSubCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ReloadSCmd extends VexelSenderSubCommand<CleverTextContext> {

    public ReloadSCmd(@NotNull CleverTextContext context) {
        super(context, "reload", "Reload configuration, text processors, URL filter, and message formats", "clevertext.admin");
    }

    @Override
    public @NotNull List<VexelArg<?>> getArguments() {
        return Collections.emptyList();
    }

    @Override
    public void execute(@NotNull CommandContext<CommandSourceStack> ctx, @NotNull CommandSender sender) {
        context.getCleverText().resetManagers();

        int processors = context.getTextProcessorManager().getTextProcessors().size();
        int formats = context.getMessageFormatManager().getMessageFormats().size();
        String urlMode = context.getTextProcessorManager().getUrlFilter().getMode().name();

        TextUtils.send(sender,
                "<green>Reloaded CleverText!</green> <gray>(<white><processors></white> processor(s), <white><formats></white> format(s), URL mode: <aqua><url_mode></aqua>)</gray>",
                Placeholder.unparsed("processors", String.valueOf(processors)),
                Placeholder.unparsed("formats", String.valueOf(formats)),
                Placeholder.unparsed("url_mode", urlMode));
    }
}