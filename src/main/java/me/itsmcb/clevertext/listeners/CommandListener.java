package me.itsmcb.clevertext.listeners;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.api.ProcessedTextResult;
import me.itsmcb.clevertext.api.event.TextSurface;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

public class CommandListener implements Listener {

    private final CleverText instance;

    public CommandListener(CleverText instance) {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!instance.isSurfaceEnabled(TextSurface.COMMAND)) {
            return;
        }

        String raw = event.getMessage();
        if (!raw.startsWith("/") || raw.length() <= 1) {
            return;
        }

        int firstSpace = raw.indexOf(' ');
        if (firstSpace < 0) {
            return;
        }

        String label = raw.substring(1, firstSpace).toLowerCase(Locale.ROOT);
        Integer startArg = instance.getMonitoredCommands().get(label);
        if (startArg == null) {
            return;
        }

        int messageStartIndex = findArgumentStartIndex(raw, firstSpace + 1, startArg);
        if (messageStartIndex < 0 || messageStartIndex >= raw.length()) {
            return;
        }

        String prefix = raw.substring(0, messageStartIndex);
        String body = raw.substring(messageStartIndex);
        if (body.isBlank()) {
            return;
        }

        ProcessedTextResult result = instance.getTextProcessorManager().process(
                event.getPlayer(),
                body,
                TextSurface.COMMAND,
                NamedTextColor.WHITE,
                false,
                false,
                true,
                "/" + label
        );

        if (result.isBlocked()) {
            event.setCancelled(true);
            return;
        }

        if (result.isCensored()) {
            event.setMessage(prefix + result.getPlainCensored());
        }
    }

    /**
     * Finds the character index in {@code raw} where argument {@code targetArgIndex} (0-based after the command label) begins.
     */
    private static int findArgumentStartIndex(String raw, int searchFrom, int targetArgIndex) {
        int len = raw.length();
        int idx = searchFrom;
        int currentArg = 0;

        while (idx < len) {
            while (idx < len && Character.isWhitespace(raw.charAt(idx))) {
                idx++;
            }
            if (idx >= len) {
                return -1;
            }
            if (currentArg == targetArgIndex) {
                return idx;
            }
            while (idx < len && !Character.isWhitespace(raw.charAt(idx))) {
                idx++;
            }
            currentArg++;
        }
        return -1;
    }
}