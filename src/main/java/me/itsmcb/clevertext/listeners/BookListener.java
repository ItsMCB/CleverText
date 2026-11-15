package me.itsmcb.clevertext.listeners;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.api.ProcessedTextResult;
import me.itsmcb.clevertext.api.event.TextSurface;
import me.itsmcb.vexelcore.bukkit.api.framework.TextUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;

public class BookListener implements Listener {

    private final CleverText instance;

    public BookListener(CleverText instance) {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent event) {
        if (!instance.isSurfaceEnabled(TextSurface.BOOK)) {
            return;
        }

        Player player = event.getPlayer();
        BookMeta newMeta = event.getNewBookMeta();
        boolean signing = event.isSigning();
        boolean anyModified = false;
        boolean anyCensored = false;

        if (signing && newMeta.hasTitle()) {
            Component titleComp = newMeta.title();
            String plainTitle = titleComp != null
                    ? PlainTextComponentSerializer.plainText().serialize(titleComp)
                    : newMeta.getTitle();

            if (plainTitle != null && !plainTitle.isBlank()) {
                ProcessedTextResult titleResult = instance.getTextProcessorManager().process(
                        player,
                        plainTitle,
                        TextSurface.BOOK,
                        NamedTextColor.BLACK,
                        false,
                        false,
                        false,
                        "Book Title"
                );

                if (titleResult.isBlocked()) {
                    event.setCancelled(true);
                    TextUtils.send(player, instance.getBlockedSurfaceMessage(),
                            Placeholder.unparsed("surface", "book title"));
                    return;
                }

                if (titleResult.isCensored()) {
                    newMeta.title(Component.text(titleResult.getPlainCensored()));
                    anyModified = true;
                    anyCensored = true;
                }
            }
        }

        int pageCount = newMeta.getPageCount();
        for (int page = 1; page <= pageCount; page++) {
            Component pageComp = newMeta.page(page);
            String plainPage = PlainTextComponentSerializer.plainText().serialize(pageComp);
            if (plainPage.isBlank()) {
                continue;
            }

            ProcessedTextResult pageResult = instance.getTextProcessorManager().process(
                    player,
                    plainPage,
                    TextSurface.BOOK,
                    NamedTextColor.BLACK,
                    signing,
                    false,
                    false,
                    "Page " + page
            );

            if (pageResult.isBlocked()) {
                event.setCancelled(true);
                TextUtils.send(player, instance.getBlockedSurfaceMessage(),
                        Placeholder.unparsed("surface", "book (Page " + page + ")"));
                return;
            }

            if (pageResult.isModified()) {
                if (signing) {
                    newMeta.page(page, pageResult.getRegularComponent());
                } else {
                    newMeta.page(page, Component.text(pageResult.getPlainCensored()));
                }
                anyModified = true;
                if (pageResult.isCensored()) {
                    anyCensored = true;
                }
            }
        }

        if (anyModified) {
            event.setNewBookMeta(newMeta);
        }

        if (anyCensored && instance.isNotifyOnCensor()) {
            TextUtils.send(player, instance.getCensoredSurfaceMessage(),
                    Placeholder.unparsed("surface", "book"));
        }
    }
}