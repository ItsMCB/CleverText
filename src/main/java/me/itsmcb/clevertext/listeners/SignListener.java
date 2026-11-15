package me.itsmcb.clevertext.listeners;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.api.ProcessedTextResult;
import me.itsmcb.clevertext.api.event.FilterAction;
import me.itsmcb.clevertext.api.event.FilterCatch;
import me.itsmcb.clevertext.api.event.TextSurface;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

import java.util.List;

public class SignListener implements Listener {

    private final CleverText instance;

    public SignListener(CleverText instance) {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!instance.isSurfaceEnabled(TextSurface.SIGN)) {
            return;
        }

        List<Component> rawLines = event.lines();
        int lineCount = rawLines.size();
        if (lineCount == 0) {
            return;
        }

        String[] plainLines = new String[lineCount];
        int[] lineStarts = new int[lineCount];
        int[] lineEnds = new int[lineCount];
        StringBuilder joined = new StringBuilder();
        boolean hasContent = false;

        for (int i = 0; i < lineCount; i++) {
            plainLines[i] = PlainTextComponentSerializer.plainText().serialize(rawLines.get(i));
            if (!plainLines[i].isBlank()) {
                hasContent = true;
            }
            if (i > 0) {
                joined.append(' ');
            }
            lineStarts[i] = joined.length();
            joined.append(plainLines[i]);
            lineEnds[i] = joined.length();
        }

        if (!hasContent) {
            return;
        }

        String locDetail = event.getBlock().getWorld().getName() + " "
                + event.getBlock().getX() + "," + event.getBlock().getY() + "," + event.getBlock().getZ()
                + " " + event.getSide().name();

        // Process the full joined sign text once so split-line bypasses are caught and one event is fired
        ProcessedTextResult result = instance.getTextProcessorManager().process(
                event.getPlayer(),
                joined.toString(),
                TextSurface.SIGN,
                NamedTextColor.WHITE,
                false,
                false,
                true,
                locDetail
        );

        if (result.isBlocked()) {
            event.setCancelled(true);
            return;
        }

        if (!result.isCensored()) {
            return;
        }

        // Map matched REPLACE spans back onto individual lines while preserving each line's style
        for (int i = 0; i < lineCount; i++) {
            int lStart = lineStarts[i];
            int lEnd = lineEnds[i];
            if (lEnd <= lStart) {
                continue;
            }

            String lineText = plainLines[i];
            StringBuilder rebuilt = new StringBuilder(lineText);
            boolean lineChanged = false;

            // Apply catches touching this line in reverse index order so earlier offsets stay valid
            List<FilterCatch> catches = result.getCatches();
            for (int cIdx = catches.size() - 1; cIdx >= 0; cIdx--) {
                FilterCatch fc = catches.get(cIdx);
                if (fc.action() == FilterAction.ALLOW_HYPERLINK) {
                    continue;
                }
                if (fc.start() < lEnd && fc.end() > lStart) {
                    int localStart = Math.max(0, fc.start() - lStart);
                    int localEnd = Math.min(lineText.length(), fc.end() - lStart);
                    if (localEnd > localStart) {
                        String mask = resolveLineReplacement(fc, localStart, localEnd, fc.start() >= lStart && fc.end() <= lEnd);
                        rebuilt.replace(localStart, localEnd, mask);
                        lineChanged = true;
                    }
                }
            }

            if (lineChanged) {
                event.line(i, Component.text(rebuilt.toString()).style(rawLines.get(i).style()));
            }
        }
    }

    private String resolveLineReplacement(FilterCatch fc, int localStart, int localEnd, boolean singleLineCatch) {
        int spanLen = Math.max(1, localEnd - localStart);
        if (fc.ruleId().equals("url-whitelist")) {
            String urlRep = instance.getTextProcessorManager().getUrlFilter().getReplacement();
            if (singleLineCatch) {
                return urlRep.length() == 1 ? urlRep.repeat(spanLen) : urlRep;
            }
            return (urlRep.length() == 1 ? urlRep : "#").repeat(spanLen);
        }
        var tp = instance.getTextProcessorManager().getById(fc.ruleId());
        if (tp != null) {
            String rep = tp.getReplacement();
            if (rep.length() == 1) {
                return rep.repeat(spanLen);
            }
            return singleLineCatch ? rep : "#".repeat(spanLen);
        }
        return "#".repeat(spanLen);
    }
}