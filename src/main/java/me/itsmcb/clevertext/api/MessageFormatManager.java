package me.itsmcb.clevertext.api;

import me.clip.placeholderapi.PlaceholderAPI;
import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.config.MessageFormat;
import me.itsmcb.vexelcore.bukkit.api.framework.TextUtils;
import me.itsmcb.vexelcore.bukkit.api.utils.PluginUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageFormatManager {

    private static final MessageFormat FALLBACK_FORMAT = new MessageFormat(
            "fallback",
            0,
            null,
            NamedTextColor.WHITE,
            List.of(new MessageFormat.FormatPart("&e[playerName] &8&lᐅ ", null, null, null))
    );

    private static final Pattern LEGACY_HEX_AMP = Pattern.compile("(?i)[&§]#([0-9a-f]{6})");
    private static final Pattern LEGACY_BUNGEE_HEX = Pattern.compile("(?i)[&§]x(?:[&§]([0-9a-f])){6}");
    private static final Pattern LEGACY_CODE = Pattern.compile("(?i)[&§]([0-9a-fk-or])");

    private final CleverText instance;
    private final List<MessageFormat> messageFormats = new ArrayList<>();

    public MessageFormatManager(@NotNull CleverText instance) {
        this.instance = instance;
    }

    public @NotNull List<MessageFormat> getMessageFormats() {
        return List.copyOf(messageFormats);
    }

    public boolean register(@Nullable MessageFormat messageFormat) {
        if (messageFormat == null) {
            return false;
        }
        instance.getLogger().info("Loaded message format \"" + messageFormat.getId()
                + "\" (priority " + messageFormat.getPriority() + ", "
                + messageFormat.getParts().size() + " part(s))");
        return messageFormats.add(messageFormat);
    }

    public @NotNull MessageFormat getBestFormat(@NotNull Player player) {
        return messageFormats.stream()
                .sorted(Comparator.comparingInt(MessageFormat::getPriority).reversed())
                .filter(format -> format.getPermission() == null || player.hasPermission(format.getPermission()))
                .findFirst()
                .orElseGet(() -> messageFormats.stream()
                        .filter(format -> format.getPermission() == null)
                        .findFirst()
                        .orElse(FALLBACK_FORMAT));
    }

    public record ChatPreview(
            @NotNull String formatId,
            @NotNull Component regularComponent,
            @NotNull Component staffComponent,
            @NotNull ProcessedTextResult processedResult
    ) {}

    public @NotNull ChatPreview formatChat(@NotNull Player player, @NotNull String messageText, boolean dryRun) {
        MessageFormat messageFormat = getBestFormat(player);
        ProcessedTextResult result = instance.getTextProcessorManager()
                .processChat(player, messageText, messageFormat.getMessageColor(), dryRun);

        TextComponent.Builder prefixBuilder = Component.text();
        for (MessageFormat.FormatPart part : messageFormat.getParts()) {
            Component comp = renderRichText(applyPlaceholders(player, part.text()));

            if (part.hover() != null && !part.hover().isEmpty()) {
                Component hoverComp = renderRichText(applyPlaceholders(player, part.hover()));
                comp = comp.hoverEvent(HoverEvent.showText(hoverComp));
            }
            if (part.clickAction() != null && part.clickValue() != null && !part.clickValue().isEmpty()) {
                ClickEvent clickEvent = buildClickEvent(part.clickAction(), applyPlaceholders(player, part.clickValue()));
                if (clickEvent != null) {
                    comp = comp.clickEvent(clickEvent);
                }
            }
            prefixBuilder.append(comp);
        }

        Component prefix = prefixBuilder.build();
        Component regular = prefix.append(result.getRegularComponent());
        Component staff = prefix.append(result.getStaffComponent());

        return new ChatPreview(messageFormat.getId(), regular, staff, result);
    }

    public void sendAs(@NotNull Player player, @NotNull String messageText) {
        ChatPreview preview = formatChat(player, messageText, false);
        if (preview.processedResult().isBlocked()) {
            return;
        }

        for (Player onlinePlayer : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (instance.hasStaffPermission(onlinePlayer)) {
                onlinePlayer.sendMessage(preview.staffComponent());
            } else {
                onlinePlayer.sendMessage(preview.regularComponent());
            }
        }
    }

    private String applyPlaceholders(@NotNull Player player, @NotNull String input) {
        String resolved = processInternalPlaceholders(input, player);
        if (PluginUtils.pluginIsLoaded("PlaceholderAPI")) {
            resolved = PlaceholderAPI.setPlaceholders(player, resolved);
        }
        return resolved;
    }

    public @NotNull String processInternalPlaceholders(@NotNull String input, @NotNull Player player) {
        return input.replace("[playerName]", player.getName());
    }

    private static @NotNull Component renderRichText(@NotNull String input) {
        return TextUtils.mm(legacyToMiniMessage(input));
    }

    /**
     * Translates legacy & / § color and formatting codes into MiniMessage tags so both
     * legacy codes (including PAPI placeholders) and MiniMessage tags work seamlessly together.
     */
    private static @NotNull String legacyToMiniMessage(@NotNull String input) {
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) {
            return input;
        }

        // Convert Spigot &x&R&R&G&G&B&B hex codes
        Matcher bungeeMatcher = LEGACY_BUNGEE_HEX.matcher(input);
        StringBuilder bungeeOut = new StringBuilder();
        while (bungeeMatcher.find()) {
            String hex = bungeeMatcher.group(0).replaceAll("[&§xX]", "");
            bungeeMatcher.appendReplacement(bungeeOut, Matcher.quoteReplacement("<reset><#" + hex + ">"));
        }
        bungeeMatcher.appendTail(bungeeOut);

        // Convert &#RRGGBB hex codes
        Matcher hexMatcher = LEGACY_HEX_AMP.matcher(bungeeOut.toString());
        StringBuilder hexOut = new StringBuilder();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(hexOut, Matcher.quoteReplacement("<reset><#" + hexMatcher.group(1) + ">"));
        }
        hexMatcher.appendTail(hexOut);

        // Convert standard &0-9, &a-f, &k-o, &r codes
        Matcher codeMatcher = LEGACY_CODE.matcher(hexOut.toString());
        StringBuilder out = new StringBuilder();
        while (codeMatcher.find()) {
            char code = Character.toLowerCase(codeMatcher.group(1).charAt(0));
            String tag = switch (code) {
                case '0' -> "<reset><black>";
                case '1' -> "<reset><dark_blue>";
                case '2' -> "<reset><dark_green>";
                case '3' -> "<reset><dark_aqua>";
                case '4' -> "<reset><dark_red>";
                case '5' -> "<reset><dark_purple>";
                case '6' -> "<reset><gold>";
                case '7' -> "<reset><gray>";
                case '8' -> "<reset><dark_gray>";
                case '9' -> "<reset><blue>";
                case 'a' -> "<reset><green>";
                case 'b' -> "<reset><aqua>";
                case 'c' -> "<reset><red>";
                case 'd' -> "<reset><light_purple>";
                case 'e' -> "<reset><yellow>";
                case 'f' -> "<reset><white>";
                case 'k' -> "<obf>";
                case 'l' -> "<b>";
                case 'm' -> "<st>";
                case 'n' -> "<u>";
                case 'o' -> "<i>";
                case 'r' -> "<reset>";
                default -> "";
            };
            codeMatcher.appendReplacement(out, Matcher.quoteReplacement(tag));
        }
        codeMatcher.appendTail(out);
        return out.toString();
    }

    private static @Nullable ClickEvent buildClickEvent(@NotNull ClickEvent.Action action, @NotNull String value) {
        if (Objects.equals(action, ClickEvent.Action.SUGGEST_COMMAND)) {
            return ClickEvent.suggestCommand(value);
        }
        if (Objects.equals(action, ClickEvent.Action.RUN_COMMAND)) {
            return ClickEvent.runCommand(value);
        }
        if (Objects.equals(action, ClickEvent.Action.OPEN_URL)) {
            return ClickEvent.openUrl(value);
        }
        if (Objects.equals(action, ClickEvent.Action.COPY_TO_CLIPBOARD)) {
            return ClickEvent.copyToClipboard(value);
        }
        if (Objects.equals(action, ClickEvent.Action.OPEN_FILE)) {
            return ClickEvent.openFile(value);
        }
        if (Objects.equals(action, ClickEvent.Action.CHANGE_PAGE)) {
            try {
                return ClickEvent.changePage(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}