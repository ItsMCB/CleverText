package me.itsmcb.clevertext.features.clearchat;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.vexelcore.bukkit.api.text.BukkitMsgBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ClearChatCMD extends Command {

    private CleverText instance;

    public ClearChatCMD(CleverText instance) {
        super("clearchat");
        this.instance = instance;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {



        // TODO check to make sure permission is valid through permission handler/feature handler
        if (!(sender.hasPermission(instance.getConfig().getString("features.clearchat.permission")))) {
            new BukkitMsgBuilder("&cYou don't have permission!").send(sender);
            return true;
        }
        for (int i = 0; i < instance.getConfig().getInt("features.clearchat.empty-message-amount"); i++) {
            new BukkitMsgBuilder("").sendAll();
        }
        new BukkitMsgBuilder("&bChat has been cleared by &3" + sender.getName()).sendAll();
        return true;
    }
}
