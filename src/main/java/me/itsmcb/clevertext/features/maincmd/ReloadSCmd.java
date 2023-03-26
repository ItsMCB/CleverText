package me.itsmcb.clevertext.features.maincmd;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.vexelcore.bukkit.api.command.CustomCommand;
import me.itsmcb.vexelcore.bukkit.api.text.BukkitMsgBuilder;
import org.bukkit.entity.Player;

public class ReloadSCmd extends CustomCommand {

    private CleverText instance;

    public ReloadSCmd(CleverText instance) {
        super("reload", "Reload configuration options", "drusk.admin");
        this.instance = instance;
    }

    @Override
    public void executeAsPlayer(Player player, String[] args) {
        instance.getLocalizationManager().reload();
        instance.getMainConfig().reload();
        instance.resetManagers();
        new BukkitMsgBuilder("&aReloaded").hover("&7Configuration file and features have been reloaded").send(player);
    }
}
