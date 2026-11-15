package me.itsmcb.clevertext.listeners;

import io.papermc.paper.event.player.PlayerNameEntityEvent;
import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.api.ProcessedTextResult;
import me.itsmcb.clevertext.api.event.TextSurface;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class EntityNameListener implements Listener {

    private final CleverText instance;

    public EntityNameListener(CleverText instance) {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerNameEntity(PlayerNameEntityEvent event) {
        if (!instance.isSurfaceEnabled(TextSurface.ENTITY_NAME)) {
            return;
        }

        Component nameComp = event.getName();
        if (nameComp == null) {
            return;
        }

        String plainName = PlainTextComponentSerializer.plainText().serialize(nameComp);
        if (plainName.isBlank()) {
            return;
        }

        ProcessedTextResult result = instance.getTextProcessorManager().process(
                event.getPlayer(),
                plainName,
                TextSurface.ENTITY_NAME,
                NamedTextColor.WHITE,
                false,
                false,
                true,
                event.getEntity().getType().name()
        );

        if (result.isBlocked()) {
            event.setCancelled(true);
            return;
        }

        if (result.isCensored()) {
            event.setName(Component.text(result.getPlainCensored()).style(nameComp.style()));
        }
    }
}