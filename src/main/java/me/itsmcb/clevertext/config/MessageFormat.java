package me.itsmcb.clevertext.config;

import libs.dev.dejvokep.boostedyaml.block.implementation.Section;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MessageFormat {

    public record FormatPart(
            @NotNull String text,
            @Nullable String hover,
            @Nullable ClickEvent.Action clickAction,
            @Nullable String clickValue
    ) {}

    private final @NotNull String id;
    private final int priority;
    private final @Nullable String permission;
    private final @NotNull TextColor messageColor;
    private final @NotNull List<FormatPart> parts;

    public MessageFormat(
            @NotNull String id,
            int priority,
            @Nullable String permission,
            @NotNull TextColor messageColor,
            @NotNull List<FormatPart> parts
    ) {
        this.id = id;
        this.priority = priority;
        this.permission = permission;
        this.messageColor = messageColor;
        this.parts = List.copyOf(parts);
    }

    public @NotNull String getId() {
        return id;
    }

    public int getPriority() {
        return priority;
    }

    public @Nullable String getPermission() {
        return permission;
    }

    public @NotNull TextColor getMessageColor() {
        return messageColor;
    }

    public @NotNull List<FormatPart> getParts() {
        return parts;
    }

    public static @NotNull MessageFormat fromSection(@NotNull String id, @NotNull Section section) {
        int priority = section.getInt("priority", 0);
        String permission = section.getString("permission");
        if (permission != null && (permission.isBlank() || permission.equalsIgnoreCase("null"))) {
            permission = null;
        }

        TextColor messageColor = parseColor(section.getString("message-color"));
        List<FormatPart> parts = new ArrayList<>();

        List<?> rawParts = section.getList("parts");
        if (rawParts != null) {
            for (Object item : rawParts) {
                FormatPart part = parsePart(item);
                if (part != null) {
                    parts.add(part);
                }
            }
        }

        return new MessageFormat(id, priority, permission, messageColor, parts);
    }

    private static @Nullable FormatPart parsePart(@Nullable Object raw) {
        if (raw instanceof String text) {
            return new FormatPart(text, null, null, null);
        }
        if (raw instanceof Section sub) {
            String text = sub.getString("text", "");
            String hover = sub.getString("hover");
            Section clickSec = sub.getSection("click");
            ClickEvent.Action action = clickSec != null ? parseClickAction(clickSec.getString("action")) : null;
            String value = clickSec != null ? clickSec.getString("value") : null;
            return new FormatPart(text, hover, action, value);
        }
        if (raw instanceof Map<?, ?> map) {
            String text = map.get("text") instanceof String s ? s : "";
            String hover = map.get("hover") instanceof String s ? s : null;
            ClickEvent.Action action = null;
            String value = null;
            if (map.get("click") instanceof Map<?, ?> clickMap) {
                action = clickMap.get("action") instanceof String a ? parseClickAction(a) : null;
                value = clickMap.get("value") instanceof String v ? v : null;
            }
            return new FormatPart(text, hover, action, value);
        }
        return null;
    }

    private static @NotNull TextColor parseColor(@Nullable String colorStr) {
        if (colorStr == null || colorStr.isBlank()) {
            return NamedTextColor.WHITE;
        }
        String trimmed = colorStr.trim();
        if (trimmed.startsWith("#")) {
            TextColor hex = TextColor.fromHexString(trimmed);
            if (hex != null) {
                return hex;
            }
        }
        NamedTextColor named = NamedTextColor.NAMES.value(trimmed.toLowerCase(Locale.ROOT));
        return named != null ? named : NamedTextColor.WHITE;
    }

    private static @Nullable ClickEvent.Action parseClickAction(@Nullable String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        return switch (action.trim().toUpperCase(Locale.ROOT)) {
            case "OPEN_URL" -> ClickEvent.Action.OPEN_URL;
            case "OPEN_FILE" -> ClickEvent.Action.OPEN_FILE;
            case "RUN_COMMAND" -> ClickEvent.Action.RUN_COMMAND;
            case "SUGGEST_COMMAND" -> ClickEvent.Action.SUGGEST_COMMAND;
            case "CHANGE_PAGE" -> ClickEvent.Action.CHANGE_PAGE;
            case "COPY_TO_CLIPBOARD" -> ClickEvent.Action.COPY_TO_CLIPBOARD;
            default -> null;
        };
    }
}