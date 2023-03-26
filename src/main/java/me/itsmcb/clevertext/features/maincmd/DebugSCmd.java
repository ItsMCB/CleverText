package me.itsmcb.clevertext.features.maincmd;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.vexelcore.bukkit.api.command.CustomCommand;
import me.itsmcb.vexelcore.bukkit.api.text.BukkitMsgBuilder;
import org.bukkit.entity.Player;

public class DebugSCmd extends CustomCommand {

    private CleverText instance;

    public DebugSCmd(CleverText instance) {
        super("debug", "Output plugin debug information", "clevertext.admin");
        this.instance = instance;
    }

    @Override
    public void executeAsPlayer(Player player, String[] args) {
        new BukkitMsgBuilder("&7Text Processors: &a"+instance.getTextProcessorManager().getTextProcessors().size()).send(player);
        new BukkitMsgBuilder("&7Chat Formats: &a"+instance.getMessageFormatManager().getMessageFormats().size()).send(player);
    }
}
