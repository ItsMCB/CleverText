package me.itsmcb.clevertext.commands;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.CleverTextContext;
import me.itsmcb.clevertext.api.WebhookService;
import me.itsmcb.clevertext.api.event.TextSurface;
import me.itsmcb.clevertext.config.UrlFilterConfig;
import me.itsmcb.vexelcore.bukkit.api.framework.TextUtils;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelArg;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelSenderSubCommand;
import me.itsmcb.vexelcore.bukkit.api.utils.PluginUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class DebugSCmd extends VexelSenderSubCommand<CleverTextContext> {

    public DebugSCmd(@NotNull CleverTextContext context) {
        super(context, "debug", "Output plugin diagnostics and active configuration summary", "clevertext.admin");
    }

    @Override
    public @NotNull List<VexelArg<?>> getArguments() {
        return Collections.emptyList();
    }

    @Override
    public void execute(@NotNull CommandContext<CommandSourceStack> ctx, @NotNull CommandSender sender) {
        CleverText plugin = context.getCleverText();
        String version = plugin.getPluginMeta().getVersion();
        int processors = context.getTextProcessorManager().getTextProcessors().size();
        int formats = context.getMessageFormatManager().getMessageFormats().size();
        UrlFilterConfig urlFilter = context.getTextProcessorManager().getUrlFilter();
        WebhookService webhook = context.getWebhookService();
        boolean papiLoaded = PluginUtils.pluginIsLoaded("PlaceholderAPI");

        String webhookState = webhook.isConfiguredAndActive()
                ? "<green>Active</green> <dark_gray>(<white>" + webhook.getSettings().events().size() + "</white> event(s))</dark_gray>"
                : (webhook.getSettings().enabled() ? "<yellow>Not Configured</yellow>" : "<red>Disabled</red>");

        TextUtils.send(sender, "<dark_gray><st>          </st> <color:#e98df5>CleverText Diagnostics</color> <st>          </st></dark_gray>");
        TextUtils.send(sender, " <gray>Version:</gray> <white><ver></white>",
                Placeholder.unparsed("ver", version));
        TextUtils.send(sender, " <gray>Handler:</gray> <state> <dark_gray>|</dark_gray> <gray>ClearChat:</gray> <cc> <dark_gray>|</dark_gray> <gray>Debug:</gray> <dbg>",
                Placeholder.parsed("state", plugin.isHandlerEnabled() ? "<green>Enabled</green>" : "<red>Disabled</red>"),
                Placeholder.parsed("cc", plugin.isClearChatEnabled() ? "<green>Enabled</green>" : "<red>Disabled</red>"),
                Placeholder.parsed("dbg", plugin.isDebugMode() ? "<yellow>On</yellow>" : "<gray>Off</gray>"));
        TextUtils.send(sender, " <gray>Surfaces:</gray> <surfaces>",
                Placeholder.parsed("surfaces", formatSurfaceStates(plugin)));
        TextUtils.send(sender, " <gray>Monitored Commands:</gray> <white><cmds></white>",
                Placeholder.unparsed("cmds", String.valueOf(plugin.getMonitoredCommands().size())));
        TextUtils.send(sender, " <gray>URL Filter:</gray> <aqua><mode></aqua> <dark_gray>(<gray>whitelist:</gray> <white><wl></white>, <gray>bare:</gray> <white><bare></white>, <gray>links:</gray> <white><links></white>)</dark_gray>",
                Placeholder.unparsed("mode", urlFilter.getMode().name()),
                Placeholder.unparsed("wl", String.valueOf(urlFilter.getWhitelist().size())),
                Placeholder.unparsed("bare", String.valueOf(urlFilter.isMatchBareDomains())),
                Placeholder.unparsed("links", String.valueOf(urlFilter.isHyperlinkAllowed())));
        TextUtils.send(sender, " <gray>Webhook:</gray> <wh> <dark_gray>|</dark_gray> <gray>PlaceholderAPI:</gray> <papi>",
                Placeholder.parsed("wh", webhookState),
                Placeholder.parsed("papi", papiLoaded ? "<green>Hooked</green>" : "<yellow>Not Installed</yellow>"));
        TextUtils.send(sender, " <gray>Staff Perm:</gray> <aqua><staff></aqua> <dark_gray>|</dark_gray> <gray>Bypass Perm:</gray> <aqua><bypass></aqua>",
                Placeholder.unparsed("staff", plugin.getStaffPermission()),
                Placeholder.unparsed("bypass", plugin.getBypassPermission()));
        TextUtils.send(sender, " <gray>Processors:</gray> <green><procs></green> <dark_gray>|</dark_gray> <gray>Message Formats:</gray> <green><fmts></green>",
                Placeholder.unparsed("procs", String.valueOf(processors)),
                Placeholder.unparsed("fmts", String.valueOf(formats)));
    }

    private static String formatSurfaceStates(CleverText plugin) {
        return badge("Chat", plugin.isSurfaceEnabled(TextSurface.CHAT)) + " "
                + badge("Cmds", plugin.isSurfaceEnabled(TextSurface.COMMAND)) + " "
                + badge("Signs", plugin.isSurfaceEnabled(TextSurface.SIGN)) + " "
                + badge("Books", plugin.isSurfaceEnabled(TextSurface.BOOK)) + " "
                + badge("Anvils", plugin.isSurfaceEnabled(TextSurface.ANVIL)) + " "
                + badge("Tags", plugin.isSurfaceEnabled(TextSurface.ENTITY_NAME));
    }

    private static String badge(String name, boolean enabled) {
        return (enabled ? "<green>" : "<red>") + name + (enabled ? "</green>" : "</red>");
    }
}