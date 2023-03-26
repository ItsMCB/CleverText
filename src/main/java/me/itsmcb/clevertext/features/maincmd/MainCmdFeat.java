package me.itsmcb.clevertext.features.maincmd;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.vexelcore.bukkit.BukkitFeature;

public class MainCmdFeat extends BukkitFeature {

    public MainCmdFeat(CleverText instance) {
        super("CleverText", "Main command", null, instance);
        registerCommand(new CleverTextCmd(instance));
    }
}
