package me.itsmcb.clevertext.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.api.event.TextSurface;
import me.itsmcb.vexelcore.bukkit.api.utils.ChatUtils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final CleverText instance;

    public ChatListener(CleverText instance) {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!instance.isSurfaceEnabled(TextSurface.CHAT)) {
            return;
        }

        // Remove all players from seeing default chat while letting console see it
        ChatUtils.removePlayerAudience(event.viewers());

        // Process and send formatted chat output
        String messageText = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        instance.getMessageFormatManager().sendAs(event.getPlayer(), messageText);
    }
}