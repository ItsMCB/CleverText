package me.itsmcb.clevertext.features.maincmd;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.features.clearchat.ClearChatCmd;
import me.itsmcb.vexelcore.bukkit.api.command.CustomCommand;
import me.itsmcb.vexelcore.bukkit.api.text.BukkitMsgBuilder;
import org.bukkit.entity.Player;

public class CleverTextCmd extends CustomCommand {

    private CleverText instance;

    public CleverTextCmd(CleverText instance) {
        super("clevertext","Manage CleverText","clevertext.admin");
        this.instance = instance;
        registerSubCommand(new ReloadSCmd(instance));
        registerSubCommand(new DebugSCmd(instance));
        registerSubCommand(new ClearChatCmd(instance));
    }

    @Override
    public void executeAsPlayer(Player player, String[] args) {
        new BukkitMsgBuilder("&7Running CleverText version ${version}").send(player);
    }
}
