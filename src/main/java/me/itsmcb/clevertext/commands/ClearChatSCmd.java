package me.itsmcb.clevertext.commands;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.itsmcb.clevertext.CleverTextContext;
import me.itsmcb.vexelcore.bukkit.api.framework.FlagParser;
import me.itsmcb.vexelcore.bukkit.api.framework.TextUtils;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelArg;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelFlag;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelFlags;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelSenderSubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class ClearChatSCmd extends VexelSenderSubCommand<CleverTextContext> {

    private static final int MAX_CLEAR_LINES = 500;

    private final VexelFlags clearFlags;
    private final VexelArg<String> OPTIONS_ARG;

    public ClearChatSCmd(@NotNull CleverTextContext context) {
        super(context, "clearchat", "Send empty messages to push unwanted messages away", "clevertext.clearchat");
        this.clearFlags = VexelFlags.builder()
                .flag(VexelFlag.valued("lines", "Number of empty lines to send (1-" + MAX_CLEAR_LINES + ")", List.of("50", "100", "150", "300"))
                        .alias("l"))
                .flag(VexelFlag.booleanFlag("silent", "Clear chat without broadcasting an announcement")
                        .alias("s"))
                .build();
        this.OPTIONS_ARG = clearFlags.asArg("options", "Optional: [lines] or --lines <amount> --silent");
    }

    @Override
    public @NotNull String getPermission() {
        String configured = context.getMainConfig().get().getString("features.clearchat.permission");
        return (configured != null && !configured.isBlank()) ? configured : "clevertext.clearchat";
    }

    @Override
    public boolean hasPermission(@NotNull CommandSourceStack source) {
        String perm = getPermission();
        return perm.isBlank()
                || source.getSender().hasPermission(perm)
                || source.getSender().hasPermission("clevertext.admin");
    }

    @Override
    protected boolean checkRequirements(@NotNull CommandSourceStack source) {
        return context.getCleverText().isClearChatEnabled() && hasPermission(source);
    }

    @Override
    public @NotNull List<VexelArg<?>> getArguments() {
        return List.of(OPTIONS_ARG);
    }

    @Override
    public void execute(@NotNull CommandContext<CommandSourceStack> ctx, @NotNull CommandSender sender) {
        if (!context.getCleverText().isClearChatEnabled()) {
            TextUtils.send(sender, "<red>The ClearChat feature is currently disabled in config.yml.</red>");
            return;
        }

        String rawOptions = optionalArg(ctx, OPTIONS_ARG);
        FlagParser flags = clearFlags.parse(rawOptions);
        boolean silent = flags.hasFlag("silent");

        int defaultLines = context.getMainConfig().get().getInt("features.clearchat.empty-message-amount");
        if (defaultLines <= 0) {
            defaultLines = 150;
        }

        int lines = defaultLines;
        Optional<String> linesFlag = flags.getValue("lines");
        String rawLines = linesFlag.orElse(!flags.getPositionalArgs().isEmpty() ? flags.getPositionalArgs().getFirst() : null);

        if (rawLines != null) {
            try {
                lines = Integer.parseInt(rawLines);
                if (lines < 1 || lines > MAX_CLEAR_LINES) {
                    TextUtils.send(sender, "<red>Lines must be between <white>1</white> and <white><max></white>.</red>",
                            Placeholder.unparsed("max", String.valueOf(MAX_CLEAR_LINES)));
                    return;
                }
            } catch (NumberFormatException e) {
                TextUtils.send(sender, "<red>Invalid line count '<white><input></white>'. Expected a number.</red>",
                        Placeholder.unparsed("input", rawLines));
                return;
            }
        } else {
            lines = Math.clamp(lines, 1, MAX_CLEAR_LINES);
        }

        for (Player onlinePlayer : List.copyOf(Bukkit.getOnlinePlayers())) {
            for (int i = 0; i < lines; i++) {
                onlinePlayer.sendMessage(Component.empty());
            }
        }

        if (silent) {
            TextUtils.send(sender, "<gray>Silently cleared <white><lines></white> line(s) of chat.</gray>",
                    Placeholder.unparsed("lines", String.valueOf(lines)));
        } else {
            String announcementFmt = context.getMainConfig().get().getString("features.clearchat.announcement");
            if (announcementFmt == null || announcementFmt.isBlank()) {
                announcementFmt = "<aqua>Chat has been cleared by <dark_aqua><actor></dark_aqua></aqua>";
            }
            Component announcement = TextUtils.mm(announcementFmt, Placeholder.unparsed("actor", sender.getName()));
            for (Player onlinePlayer : List.copyOf(Bukkit.getOnlinePlayers())) {
                onlinePlayer.sendMessage(announcement);
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(announcement);
            }
        }

        context.getWebhookService().notifyClearChat(sender, lines, silent);
    }
}