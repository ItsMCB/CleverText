package me.itsmcb.clevertext.features.handlechat;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.vexelcore.bukkit.BukkitFeature;

public class ChatHandlerFeature extends BukkitFeature {


    public ChatHandlerFeature(CleverText instance) {
        super("Chat Handler", "Handles chat for features like filtering", "features.handler", instance);
        registerListener(new ChatListener(instance));
    }
}
