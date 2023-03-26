package me.itsmcb.clevertext.api;

import me.clip.placeholderapi.PlaceholderAPI;
import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.config.MessageFormat;
import me.itsmcb.vexelcore.bukkit.api.text.BukkitMsgBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;

public class MessageFormatManager {

    private CleverText instance;

    public MessageFormatManager(CleverText instance) {
        this.instance = instance;
    }

    private ArrayList<MessageFormat> messageFormats = new ArrayList<>();

    public ArrayList<MessageFormat> getMessageFormats() {
        return messageFormats;
    }

    public boolean register(@NotNull MessageFormat messageFormat) {
        System.out.println("Registering message format \""+messageFormat.getId()+"\" with " + messageFormat.getComponents().size() + " components.");
        return messageFormats.add(messageFormat);
    }

    public MessageFormat getBestFormat(@NotNull Player player) {
        AtomicReference<MessageFormat> bestFormat = new AtomicReference<>();
        // Figure out the right chat format based on permission and priority level
        messageFormats.stream().sorted(Comparator.comparing(MessageFormat::getPriority)).forEach(messageFormat -> {
            if (messageFormat.getPermission() != null) {
                if (player.hasPermission(messageFormat.getPermission())) {
                    bestFormat.set(messageFormat);
                }
            } else {
                bestFormat.set(messageFormat);
            }
        });
        // Todo fallback to default if needed
        return bestFormat.get();
    }

    public void sendAs(Player player, String messageText) {
        TextComponent tcnormal = instance.getTextProcessorManager().process(player, messageText, false);
        //TextComponent tcstaff = instance.getTextProcessorManager().process(player, messageText, true);
        MessageFormat messageFormat = getBestFormat(player);

        TextComponent.Builder builder = Component.text();

        messageFormat.getComponents().forEach(component -> {
            BukkitMsgBuilder bukkitMsgBuilder = new BukkitMsgBuilder("");
            if (component.getMessageText() != null) {
                bukkitMsgBuilder.text(PlaceholderAPI.setPlaceholders(player, processInternalPlaceholders(component.getMessageText(), player)));
            }
            if (component.getHoverText() != null) {
                bukkitMsgBuilder.hover(PlaceholderAPI.setPlaceholders(player, processInternalPlaceholders(component.getHoverText(), player)));
            }
            if ((component.getClickEventAction() != null) && (component.getClickEventValue() != null)) {
                bukkitMsgBuilder.clickEvent(component.getClickEventAction(), PlaceholderAPI.setPlaceholders(player, processInternalPlaceholders(component.getClickEventValue(), player)));
            }
            builder.append(bukkitMsgBuilder.build().get());
        });
        Bukkit.getOnlinePlayers().forEach(onlinePlayer -> {
            // Don't hard code
            if (onlinePlayer.hasPermission("group.owner")) {
                // TODO fix w/ staff version
                onlinePlayer.sendMessage(builder.build().append(tcnormal));
            } else {
                onlinePlayer.sendMessage(builder.build().append(tcnormal));
            }
        });
    }

    public String processInternalPlaceholders(String input, Player player) {
       return input.replaceAll("\\[playerName]",player.getName());
    }

}
