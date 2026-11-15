package me.itsmcb.clevertext.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fired whenever CleverText catches moderation-relevant text or disallowed URLs on a monitored surface.
 *
 * <p>External plugins can inspect {@link #getCatches()}, change whether the submission is blocked via
 * {@link #setBlocked(boolean)}, or cancel the event via {@link #setCancelled(boolean)} to exempt the input.</p>
 */
public class CleverTextCatchEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final @Nullable Player player;
    private final @NotNull TextSurface surface;
    private final @NotNull String originalText;
    private final @NotNull List<FilterCatch> catches;
    private boolean blocked;
    private boolean cancelled;

    public CleverTextCatchEvent(
            @Nullable Player player,
            @NotNull TextSurface surface,
            @NotNull String originalText,
            @NotNull List<FilterCatch> catches,
            boolean blocked,
            boolean isAsync
    ) {
        super(isAsync);
        this.player = player;
        this.surface = surface;
        this.originalText = originalText;
        this.catches = List.copyOf(catches);
        this.blocked = blocked;
    }

    public @Nullable Player getPlayer() {
        return player;
    }

    public @NotNull TextSurface getSurface() {
        return surface;
    }

    public @NotNull String getOriginalText() {
        return originalText;
    }

    public @NotNull List<FilterCatch> getCatches() {
        return catches;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}