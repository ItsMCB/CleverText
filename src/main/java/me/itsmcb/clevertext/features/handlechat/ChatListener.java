package me.itsmcb.clevertext.features.handlechat;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.itsmcb.clevertext.CleverText;
import me.itsmcb.vexelcore.bukkit.api.utils.ChatUtils;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    public CleverText instance;

    public ChatListener(CleverText instance) {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) { return; }
        // Remove all players from seeing default chat but still let the console see it
        ChatUtils.removePlayerAudience(event.viewers());
        // Send final result with message format manager
        String messageText = ((TextComponent) event.originalMessage()).content();
        instance.getMessageFormatManager().sendAs(event.getPlayer(), messageText);
    }

}
