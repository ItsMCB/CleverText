package me.itsmcb.clevertext.features.maincmd;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.vexelcore.bukkit.BukkitFeature;

public class MainCMDFeature extends BukkitFeature {

    public MainCMDFeature(CleverText instance) {
        super("CleverText", "Main command", null, instance);
        registerCommand(new CleverTextCMD(instance));
    }
}
