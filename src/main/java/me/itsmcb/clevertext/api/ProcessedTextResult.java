package me.itsmcb.clevertext.api;

import me.itsmcb.clevertext.api.event.FilterCatch;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ProcessedTextResult {

    private final @NotNull String originalText;
    private final @NotNull String plainCensored;
    private final @NotNull TextComponent regularComponent;
    private final @NotNull TextComponent staffComponent;
    private final @NotNull List<FilterCatch> catches;
    private final boolean blocked;
    private final boolean censored;
    private final boolean modified;
    private final boolean eventCancelled;

    public ProcessedTextResult(
            @NotNull String originalText,
            @NotNull String plainCensored,
            @NotNull List<Component> regularComponents,
            @NotNull List<Component> staffComponents,
            @NotNull List<FilterCatch> catches,
            boolean blocked,
            boolean censored,
            boolean modified,
            boolean eventCancelled
    ) {
        this.originalText = originalText;
        this.plainCensored = plainCensored;
        this.regularComponent = Component.text().append(regularComponents).build();
        this.staffComponent = Component.text().append(staffComponents).build();
        this.catches = List.copyOf(catches);
        this.blocked = blocked;
        this.censored = censored;
        this.modified = modified;
        this.eventCancelled = eventCancelled;
    }

    public @NotNull String getOriginalText() {
        return originalText;
    }

    public @NotNull String getPlainCensored() {
        return plainCensored;
    }

    public @NotNull TextComponent getRegularComponent() {
        return regularComponent;
    }

    public @NotNull TextComponent getStaffComponent() {
        return staffComponent;
    }

    public @NotNull List<FilterCatch> getCatches() {
        return catches;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isCensored() {
        return censored;
    }

    public boolean isModified() {
        return modified;
    }

    public boolean isEventCancelled() {
        return eventCancelled;
    }
}