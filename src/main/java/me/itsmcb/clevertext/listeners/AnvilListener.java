package me.itsmcb.clevertext.listeners;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.api.ProcessedTextResult;
import me.itsmcb.clevertext.api.event.TextSurface;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

public class AnvilListener implements Listener {

    private final CleverText instance;

    public AnvilListener(CleverText instance) {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        if (!instance.isSurfaceEnabled(TextSurface.ANVIL)) {
            return;
        }

        AnvilView view = event.getView();
        String renameText = view.getRenameText();
        if (renameText == null || renameText.isBlank()) {
            return;
        }

        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }

        Player player = view.getPlayer() instanceof Player p ? p : null;

        // Dry-run during typing so every keystroke updates the preview silently without spamming events or chat
        ProcessedTextResult processed = instance.getTextProcessorManager()
                .processPlain(player, renameText, TextSurface.ANVIL, true);

        if (processed.isBlocked()) {
            event.setResult(null);
            return;
        }

        if (processed.isCensored()) {
            ItemStack updated = result.clone();
            ItemMeta meta = updated.getItemMeta();
            if (meta != null) {
                Component existing = meta.displayName();
                Style style = existing != null ? existing.style() : Style.empty();
                meta.displayName(Component.text(processed.getPlainCensored()).style(style));
                updated.setItemMeta(meta);
                event.setResult(updated);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAnvilResultClick(InventoryClickEvent event) {
        if (!instance.isSurfaceEnabled(TextSurface.ANVIL)) {
            return;
        }
        if (!(event.getView() instanceof AnvilView anvilView)) {
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack inputItem = anvilView.getTopInventory().getItem(0);
        if (inputItem == null || inputItem.getType().isAir()) {
            return;
        }

        String renameText = anvilView.getRenameText();
        if (renameText == null || renameText.isBlank()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        boolean resultCleared = clicked == null || clicked.getType().isAir();

        // First check if there are any catches at all before firing live event
        ProcessedTextResult dryCheck = instance.getTextProcessorManager()
                .processPlain(player, renameText, TextSurface.ANVIL, true);
        if (dryCheck.getCatches().isEmpty()) {
            return;
        }

        // Fire CleverTextCatchEvent, log, and notify once when the player actually attempts to confirm/take the item
        ProcessedTextResult live = instance.getTextProcessorManager()
                .processPlain(player, renameText, TextSurface.ANVIL, false);

        if (live.isBlocked()) {
            event.setCancelled(true);
            if (!resultCleared) {
                anvilView.setItem(2, null);
            }
            return;
        }

        // If an external listener cancelled CleverTextCatchEvent (exempted), restore the original rename text
        if (live.isEventCancelled() && !resultCleared) {
            ItemStack restored = clicked.clone();
            ItemMeta meta = restored.getItemMeta();
            if (meta != null) {
                Component existing = meta.displayName();
                Style style = existing != null ? existing.style() : Style.empty();
                meta.displayName(Component.text(renameText).style(style));
                restored.setItemMeta(meta);
                event.setCurrentItem(restored);
            }
        }
    }
}