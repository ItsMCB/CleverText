package me.itsmcb.clevertext.features.maincmd;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.vexelcore.bukkit.api.text.BukkitMsgBuilder;
import me.itsmcb.vexelcore.common.api.command.CMDHelper;
import net.kyori.adventure.title.Title;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class CleverTextCMD extends Command {

    private CleverText instance;

    public CleverTextCMD(CleverText instance) {
        super("clevertext");
        this.instance = instance;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (instance.getPermissionManager().lacks(sender, "admin")) {
            new BukkitMsgBuilder(instance.getLocalizationManager().get("error-permission")).send(sender);
            return true;
        }
        CMDHelper cmdHelper = new CMDHelper(args);
        if (cmdHelper.isCalling("reload")) {
            instance.getLocalizationManager().reload();
            instance.getMainConfig().reload();
            instance.resetManagers();
            new BukkitMsgBuilder("&aReloaded").hover("&7Configuration file and features have been reloaded").send(sender);
        }
        if (cmdHelper.isCalling("clearchat")) {
            instance.getServer().dispatchCommand(sender, "clearchat");
            Title title = Title.title(new BukkitMsgBuilder("&a&l✔").get(),new BukkitMsgBuilder("&aChat cleared").get());
            sender.showTitle(title);
        }
        if (cmdHelper.isCalling("debug")) {
            new BukkitMsgBuilder("&7Text Processors: &a"+instance.getTextProcessorManager().amountRegistered()).send(sender);
            new BukkitMsgBuilder("&7Chat Formats: &a"+instance.getMessageFormatManager().amountRegistered()).send(sender);
        }
        return false;
    }
}
