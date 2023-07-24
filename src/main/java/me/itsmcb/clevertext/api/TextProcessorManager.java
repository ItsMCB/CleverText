package me.itsmcb.clevertext.api;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.config.MessageFormat;
import me.itsmcb.clevertext.config.TextProcessor;
import me.itsmcb.vexelcore.bukkit.api.text.BukkitMsgBuilder;
import me.itsmcb.vexelcore.common.api.text.Icon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TextProcessorManager {
    
    private CleverText instance;
    
    public TextProcessorManager(CleverText instance) {
        this.instance = instance;
    }

    private ArrayList<TextProcessor> textProcessors = new ArrayList<>();
    
    public boolean register(TextProcessor textProcessor) {
        System.out.println("Registering text processor \""+textProcessor.getId()+"\"");
        return textProcessors.add(textProcessor);
    }

    public ArrayList<TextProcessor> getTextProcessors() {
        return textProcessors;
    }

    public TextProcessor getById(String id) {
        Optional<TextProcessor> optional = textProcessors.stream().filter(textProcessor -> textProcessor.getId().equals(id)).findFirst();
        return optional.orElse(null);
    }

    // Processing methods

    public ArrayList<PatternCatch> findCatches(String text) {
        ArrayList<PatternCatch> catches = new ArrayList<>();
        // Loop through text processors to find pattern
        textProcessors.forEach(textProcessor -> {
            textProcessor.getPatterns().forEach(pattern -> {
                pattern.matcher(text).results().toList().forEach(matchResult -> {
                    catches.add(new PatternCatch(textProcessor.getId(), matchResult));
                });
            });
        });
        return catches;
    }

    public ArrayList<IdentifiedTextFragment> generateTextFragments(String text) {
        ArrayList<PatternCatch> catches = findCatches(text);

        // Debug
        catches.forEach(patternCatch -> {
            System.out.println("===== "+patternCatch.getTextProcessorId() + " - " + text.substring(patternCatch.getResult().start(), patternCatch.getResult().end()) + " =====");
            System.out.println("Start " + patternCatch.getResult().start());
            System.out.println("End " + patternCatch.getResult().end());
            System.out.println("Group " + patternCatch.getResult().group());
            System.out.println("===== END =====");
        });

        // Compile fragments
        ArrayList<IdentifiedTextFragment> textFragments = new ArrayList<>();
        AtomicInteger lastIndex = new AtomicInteger(0);
        List<PatternCatch> catchList = catches.stream().sorted(Comparator.comparing(c -> c.getResult().end())).toList();
        catchList.forEach(patternCatch -> {
            int start = patternCatch.getResult().start();
            int end = patternCatch.getResult().end();
            if (lastIndex.get() <= start) {
                // Append what's before it.
                String newTextA = text.substring(lastIndex.get(), start);
                textFragments.add(new IdentifiedTextFragment(null, null, newTextA));

                // Append the catch
                TextProcessor tp = getById(patternCatch.getTextProcessorId());
                String newText = text.substring(start, end);
                textFragments.add(new IdentifiedTextFragment(tp, patternCatch, newText));
            } else {
                System.out.println("Warning: Regex picked up similar catches (ex. \"https://test.com\" and \"test\") but can't manipulate both correctly. To fix this, ensure regex does not overlap with each other. Found: " + text.substring(start,end));
            }
            lastIndex.set(end);
        });
        // Append the rest of the message
        textFragments.add(new IdentifiedTextFragment(null, null, text.substring(lastIndex.get())));
        return textFragments;
    }

    public ProcessedTextResult process(Player player, String message, NamedTextColor color) {
        AtomicBoolean messageBlocked = new AtomicBoolean(false);
        ArrayList<Component> regularComponents = new ArrayList<>();
        ArrayList<Component> staffComponents = new ArrayList<>();

        // Fragment Processor
        ArrayList<IdentifiedTextFragment> fragments = generateTextFragments(message);
        fragments.forEach(fragment -> {
            String text = fragment.getNewText();
            // If TP not null, transform.
            if (fragment.getTextProcessor() != null) {
                // Change needed
                switch (fragment.getTextProcessor().getModifyMode()) {
                    case REPLACE -> {
                        StringBuilder stringBuilder = new StringBuilder();
                        String replacement = fragment.getTextProcessor().getReplaceModeReplacement();
                        int catchLength = fragment.getPatternCatch().getResult().end()-fragment.getPatternCatch().getResult().start();
                        stringBuilder.append(String.valueOf(replacement).repeat(Math.max(0, catchLength)));

                        // Add to format
                        int start = fragment.getPatternCatch().getResult().start();
                        int end = fragment.getPatternCatch().getResult().end();
                        staffComponents.add(new BukkitMsgBuilder("&c"+stringBuilder).hover(
                                "&7==== &bText Processor Catch Info &7====\n"
                                        + "&3Name: &b"+fragment.getPatternCatch().getTextProcessorId()+"\n"
                                        + "&3Finding: &b"+message.substring(start,end)+"\n"
                                        + "&3Original: &b"+message+"\n"
                                        + "&3Pattern: &b"+fragment.getTextProcessor().getScanRegexPattern()+"\n"
                                        + "&3Start: &b"+start+"\n"
                                        + "&3End: &b"+end
                        ).get());
                        regularComponents.add(new BukkitMsgBuilder("&c"+stringBuilder).hover(
                                "&c"+Icon.WARNING+" &7Censored "+"&c"+Icon.WARNING
                                        +"\nAttempts to bypass filter will be met with punishment"
                        ).get());
                    }
                    case BLOCKSEND -> {
                        messageBlocked.set(true);
                    }
                    case MAKEHYPERLINK -> {
                        // TODO consider format
                        // TODO replace "text" with substring of text
                        TextComponent link = new BukkitMsgBuilder("&a"+text).hover("&7Click to open").clickEvent(ClickEvent.Action.OPEN_URL, text).get();
                        regularComponents.add(link);
                        staffComponents.add(link);
                    }
                }

                if (fragment.getTextProcessor().shouldLogCatches()) {
                    // TODO log
                }
            } else {
                // No change needed
                Component component = Component.text(text,color);
                regularComponents.add(component);
                staffComponents.add(component);
            }
            // Set built-in placeholders
            // Set placeholders
        });

        // Send
        if (messageBlocked.get()) {
            // Alert staff
            // TODO alert staff
            // Alert sender TODO make message customizable in TextProcessor config obj
            new BukkitMsgBuilder("&cReally? Message blocked.").send(player);
        }
        ProcessedTextResult result = new ProcessedTextResult();
        result.setBlocked(messageBlocked.get());
        result.setRegularComponent(regularComponents);
        result.setStaffComponent(staffComponents);

        return result;
    }



    public ProcessedTextResult processChat(Player player, String message) {
        // Get chat format for default chat color
        MessageFormat messageFormat = instance.getMessageFormatManager().getBestFormat(player);
        return process(player,message,messageFormat.getTextColor());
    }
}
