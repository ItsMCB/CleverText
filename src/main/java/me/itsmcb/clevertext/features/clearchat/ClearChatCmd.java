package me.itsmcb.clevertext.features.clearchat;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.vexelcore.bukkit.api.command.CustomCommand;
import me.itsmcb.vexelcore.bukkit.api.text.BukkitMsgBuilder;
import org.bukkit.entity.Player;

public class ClearChatCmd extends CustomCommand {

    private CleverText instance;

    public ClearChatCmd(CleverText instance) {
        super("clearchat","Send empty messages to push unwanted messages away",instance.getConfig().getString("features.clearchat.permission"));
        this.instance = instance;
    }

    @Override
    public void executeAsPlayer(Player player, String[] args) {
        for (int i = 0; i < instance.getConfig().getInt("features.clearchat.empty-message-amount"); i++) {
            new BukkitMsgBuilder("").sendAll();
        }
        new BukkitMsgBuilder("&bChat has been cleared by &3" + player.getName()).sendAll();
    }
}
