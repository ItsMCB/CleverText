package me.itsmcb.clevertext.commands;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.itsmcb.clevertext.CleverTextContext;
import me.itsmcb.clevertext.api.MessageFormatManager;
import me.itsmcb.clevertext.api.ProcessedTextResult;
import me.itsmcb.clevertext.api.event.FilterCatch;
import me.itsmcb.clevertext.api.event.TextSurface;
import me.itsmcb.vexelcore.bukkit.api.framework.FlagParser;
import me.itsmcb.vexelcore.bukkit.api.framework.TextUtils;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelArg;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelFlag;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelFlags;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelSenderSubCommand;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TestSCmd extends VexelSenderSubCommand<CleverTextContext> {

    private final VexelArg<PlayerSelectorArgumentResolver> PLAYER_ARG;
    private final VexelFlags testFlags;
    private final VexelArg<String> INPUT_ARG;

    public TestSCmd(@NotNull CleverTextContext context) {
        super(context, "test", "Preview how text is formatted and filtered across surfaces", "clevertext.admin");
        this.PLAYER_ARG = VexelArg.builder("player", ArgumentTypes.player(), PlayerSelectorArgumentResolver.class,
                        "Target player whose permissions and placeholders to simulate")
                .build();

        List<String> surfaceChoices = Arrays.stream(TextSurface.values()).map(Enum::name).toList();
        this.testFlags = VexelFlags.builder()
                .flag(VexelFlag.valued("surface", "Surface to simulate (CHAT, COMMAND, SIGN, BOOK, ANVIL, ENTITY_NAME)", surfaceChoices)
                        .alias("s"))
                .build();
        this.INPUT_ARG = testFlags.asArg("input", "[--surface <surface>] <message>");
    }

    @Override
    public @NotNull List<VexelArg<?>> getArguments() {
        return List.of(PLAYER_ARG, INPUT_ARG);
    }

    @Override
    public void execute(@NotNull CommandContext<CommandSourceStack> ctx, @NotNull CommandSender sender) {
        PlayerSelectorArgumentResolver resolver = requireArg(ctx, PLAYER_ARG);
        String rawInput = optionalArg(ctx, INPUT_ARG);

        Player target;
        try {
            List<Player> resolved = resolver.resolve(ctx.getSource());
            if (resolved.isEmpty()) {
                TextUtils.send(sender, "<red>Player not found.</red>");
                return;
            }
            target = resolved.getFirst();
        } catch (Exception e) {
            TextUtils.send(sender, "<red>Invalid player selector.</red>");
            return;
        }

        FlagParser parsed = testFlags.parse(rawInput);
        String message = String.join(" ", parsed.getPositionalArgs()).trim();
        if (message.isEmpty()) {
            TextUtils.send(sender, "<red>Please provide a sample message: <white>/ct test <player> [--surface <surface>] <message></white></red>");
            return;
        }

        TextSurface surface = TextSurface.CHAT;
        if (parsed.hasFlag("surface")) {
            String rawSurface = parsed.getValue("surface").orElse("CHAT").toUpperCase(Locale.ROOT);
            try {
                surface = TextSurface.valueOf(rawSurface);
            } catch (IllegalArgumentException e) {
                TextUtils.send(sender, "<red>Unknown surface '<white><surface></white>'.</red>",
                        Placeholder.unparsed("surface", rawSurface));
                return;
            }
        }

        TextUtils.send(sender, "<dark_gray><st>          </st> <color:#e98df5>CleverText Preview (<player> @ <surface>)</color> <st>          </st></dark_gray>",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("surface", surface.name()));

        if (surface == TextSurface.CHAT) {
            MessageFormatManager.ChatPreview preview = context.getMessageFormatManager().formatChat(target, message, true);
            String catchesSummary = formatCatches(preview.processedResult().getCatches());
            TextUtils.send(sender, " <gray>Format:</gray> <green><fmt></green> <dark_gray>|</dark_gray> <gray>Blocked:</gray> <blocked> <dark_gray>|</dark_gray> <gray>Catches:</gray> <yellow><catches></yellow>",
                    Placeholder.unparsed("fmt", preview.formatId()),
                    Placeholder.parsed("blocked", preview.processedResult().isBlocked() ? "<red>Yes</red>" : "<green>No</green>"),
                    Placeholder.unparsed("catches", catchesSummary));
            TextUtils.send(sender, " <gray>Regular:</gray> <comp>",
                    Placeholder.component("comp", preview.regularComponent()));
            TextUtils.send(sender, " <gray>Staff:</gray> <comp>",
                    Placeholder.component("comp", preview.staffComponent()));
        } else {
            boolean applyLinks = surface == TextSurface.BOOK;
            ProcessedTextResult result = context.getTextProcessorManager().process(
                    target, message, surface, NamedTextColor.WHITE, applyLinks, true, false, null);
            String catchesSummary = formatCatches(result.getCatches());
            TextUtils.send(sender, " <gray>Blocked:</gray> <blocked> <dark_gray>|</dark_gray> <gray>Censored:</gray> <censored> <dark_gray>|</dark_gray> <gray>Catches:</gray> <yellow><catches></yellow>",
                    Placeholder.parsed("blocked", result.isBlocked() ? "<red>Yes</red>" : "<green>No</green>"),
                    Placeholder.parsed("censored", result.isCensored() ? "<yellow>Yes</yellow>" : "<green>No</green>"),
                    Placeholder.unparsed("catches", catchesSummary));
            TextUtils.send(sender, " <gray>Plain Output:</gray> <white><out></white>",
                    Placeholder.unparsed("out", result.getPlainCensored()));
        }
    }

    private static String formatCatches(List<FilterCatch> catches) {
        if (catches.isEmpty()) {
            return "None";
        }
        return catches.stream()
                .map(c -> c.ruleId() + " (" + c.action() + ")")
                .distinct()
                .collect(Collectors.joining(", "));
    }
}