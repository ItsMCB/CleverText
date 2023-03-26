package me.itsmcb.clevertext.features.clearchat;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.vexelcore.bukkit.BukkitFeature;

public class ClearChatFeat extends BukkitFeature {

    public ClearChatFeat(CleverText instance) {
        super("ClearChat", "Sends empty messages to getBestFormat rid of viewable in-game client chat history", "features.clearchat", instance);
        registerCommand(new ClearChatCmd(instance));
    }


}
