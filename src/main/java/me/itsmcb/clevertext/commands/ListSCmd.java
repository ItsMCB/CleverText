package me.itsmcb.clevertext.commands;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.itsmcb.clevertext.CleverTextContext;
import me.itsmcb.clevertext.config.MessageFormat;
import me.itsmcb.clevertext.config.TextProcessor;
import me.itsmcb.clevertext.config.UrlFilterConfig;
import me.itsmcb.vexelcore.bukkit.api.framework.TextUtils;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelArg;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelSenderSubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ListSCmd extends VexelSenderSubCommand<CleverTextContext> {

    public ListSCmd(@NotNull CleverTextContext context) {
        super(context, "list", "List URL policy, text processors, and message formats", "clevertext.admin");
    }

    @Override
    public @NotNull List<VexelArg<?>> getArguments() {
        return Collections.emptyList();
    }

    @Override
    public void execute(@NotNull CommandContext<CommandSourceStack> ctx, @NotNull CommandSender sender) {
        UrlFilterConfig urlFilter = context.getTextProcessorManager().getUrlFilter();
        List<TextProcessor> processors = context.getTextProcessorManager().getTextProcessors();
        List<MessageFormat> formats = context.getMessageFormatManager().getMessageFormats().stream()
                .sorted(Comparator.comparingInt(MessageFormat::getPriority).reversed())
                .toList();

        TextUtils.send(sender, "<dark_gray><st>          </st> <color:#e98df5>URL Filter Policy</color> <st>          </st></dark_gray>");
        String wlPreview = urlFilter.getWhitelist().isEmpty()
                ? "None"
                : urlFilter.getWhitelist().stream().map(UrlFilterConfig.WhitelistRule::rawRule).collect(Collectors.joining(", "));
        TextUtils.send(sender,
                " <gray>Mode:</gray> <aqua><mode></aqua> <dark_gray>|</dark_gray> <gray>Hyperlinks:</gray> <white><links></white> <dark_gray>|</dark_gray> <hover:show_text:'<gray>Whitelisted:</gray><newline><white><wl></white>'><gray>Whitelist:</gray> <green><count> rule(s)</green></hover>",
                Placeholder.unparsed("mode", urlFilter.getMode().name()),
                Placeholder.unparsed("links", String.valueOf(urlFilter.isHyperlinkAllowed())),
                Placeholder.unparsed("wl", wlPreview),
                Placeholder.unparsed("count", String.valueOf(urlFilter.getWhitelist().size())));

        TextUtils.send(sender, "<dark_gray><st>          </st> <color:#e98df5>Text Processors (<count>)</color> <st>          </st></dark_gray>",
                Placeholder.unparsed("count", String.valueOf(processors.size())));

        if (processors.isEmpty()) {
            TextUtils.send(sender, " <gray><italic>No text processors registered.</italic></gray>");
        } else {
            for (TextProcessor tp : processors) {
                String surfaces = tp.getSurfaces().isEmpty()
                        ? "ALL"
                        : tp.getSurfaces().stream().map(Enum::name).collect(Collectors.joining(", "));
                Component idComp = tp.isEnabled()
                        ? Component.text(tp.getId(), NamedTextColor.WHITE)
                        : Component.text(tp.getId(), NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH);

                TextUtils.send(sender,
                        " <dark_gray>•</dark_gray> <hover:show_text:'<gray>Match:</gray> <white><desc></white><newline><gray>Surfaces:</gray> <aqua><surfaces></aqua><newline><gray>Logging:</gray> <white><log></white> | <gray>Event:</gray> <white><evt></white>'><id_comp></hover> <dark_gray>[<yellow><action></yellow>, <gray><desc></gray>]</dark_gray>",
                        Placeholder.component("id_comp", idComp),
                        Placeholder.unparsed("action", tp.getAction().name()),
                        Placeholder.unparsed("desc", tp.getPatternDescription()),
                        Placeholder.unparsed("surfaces", surfaces),
                        Placeholder.unparsed("log", String.valueOf(tp.shouldLogCatches())),
                        Placeholder.unparsed("evt", String.valueOf(tp.shouldFireEvent())));
            }
        }

        TextUtils.send(sender, "<dark_gray><st>          </st> <color:#e98df5>Message Formats (<count>)</color> <st>          </st></dark_gray>",
                Placeholder.unparsed("count", String.valueOf(formats.size())));

        if (formats.isEmpty()) {
            TextUtils.send(sender, " <gray><italic>No message formats registered.</italic></gray>");
        } else {
            for (MessageFormat mf : formats) {
                String perm = mf.getPermission() != null ? mf.getPermission() : "none";
                TextUtils.send(sender,
                        " <dark_gray>•</dark_gray> <hover:show_text:'<gray>Permission:</gray> <aqua><perm></aqua><newline><gray>Message Color:</gray> <white><color></white><newline><gray>Parts:</gray> <white><parts></white>'><green><id></green></hover> <dark_gray>(priority <white><prio></white>, perm: <aqua><perm></aqua>)</dark_gray>",
                        Placeholder.unparsed("id", mf.getId()),
                        Placeholder.unparsed("prio", String.valueOf(mf.getPriority())),
                        Placeholder.unparsed("perm", perm),
                        Placeholder.unparsed("color", mf.getMessageColor().toString()),
                        Placeholder.unparsed("parts", String.valueOf(mf.getParts().size())));
            }
        }
    }
}