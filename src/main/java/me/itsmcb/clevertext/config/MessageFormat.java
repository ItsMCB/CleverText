package me.itsmcb.clevertext.config;

import me.itsmcb.vexelcore.bukkit.api.text.BukkitMsgBuilder;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@SerializableAs("MessageFormat")
public class MessageFormat implements ConfigurationSerializable {

    private String id;
    private int priority;
    private String permission;
    private NamedTextColor textColor;
    private ArrayList<BukkitMsgBuilder> components;

    public MessageFormat(String id, int priority, String permission, NamedTextColor textColor, ArrayList<BukkitMsgBuilder> components) {
        this.id = id;
        this.priority = priority;
        this.permission = permission;
        this.textColor = textColor;
        this.components = components;
    }

    public String getId() {
        return id;
    }

    public int getPriority() {
        return priority;
    }

    public String getPermission() {
        return permission;
    }

    public NamedTextColor getTextColor() {
        return textColor;
    }

    public ArrayList<BukkitMsgBuilder> getComponents() {
        return components;
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("priority", priority);
        map.put("permission", permission);
        map.put("color", textColor.toString());
        map.put("components", components);
        return map;
    }

    public static MessageFormat deserialize(Map<String, Object> map) {
        return new MessageFormat(
                (String) map.get("id"),
                (int) map.get("priority"),
                (String) map.get("permission"),
                NamedTextColor.NAMES.value((String) map.get("color")),
                (ArrayList<BukkitMsgBuilder>) map.get("components")
        );
    }
}
