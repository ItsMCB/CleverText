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
        ProcessedTextResult result = instance.getTextProcessorManager().processChat(player,messageText);
        MessageFormat messageFormat = getBestFormat(player);
        TextComponent.Builder format = Component.text();

        messageFormat.getComponents().forEach(component -> {
            BukkitMsgBuilder formattedMessage = new BukkitMsgBuilder("");
            if (component.getMessageText() != null) {
                formattedMessage.text(PlaceholderAPI.setPlaceholders(player, processInternalPlaceholders(component.getMessageText(), player)));
            }
            if (component.getHoverText() != null) {
                formattedMessage.hover(PlaceholderAPI.setPlaceholders(player, processInternalPlaceholders(component.getHoverText(), player)));
            }
            if ((component.getClickEventAction() != null) && (component.getClickEventValue() != null)) {
                formattedMessage.clickEvent(component.getClickEventAction(), PlaceholderAPI.setPlaceholders(player, processInternalPlaceholders(component.getClickEventValue(), player)));
            }
            format.append(formattedMessage.build().get());
        });
        Bukkit.getOnlinePlayers().forEach(onlinePlayer -> {
            TextComponent regular = format.build().append(result.getRegularComponent());
            TextComponent staff = format.build().append(result.getStaffComponent());
            // Don't hard code perm
            if (onlinePlayer.hasPermission("group.staff")) {
                onlinePlayer.sendMessage(staff);
            } else {
                if (result.isBlocked()) {
                    return;
                }
                onlinePlayer.sendMessage(regular);
            }
        });
    }

    public String processInternalPlaceholders(String input, Player player) {
       return input.replaceAll("\\[playerName]",player.getName());
    }

}
