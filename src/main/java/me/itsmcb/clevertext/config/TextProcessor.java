package me.itsmcb.clevertext.config;

import libs.dev.dejvokep.boostedyaml.block.implementation.Section;
import me.itsmcb.clevertext.api.event.FilterAction;
import me.itsmcb.clevertext.api.event.TextSurface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class TextProcessor {

    public enum MatchType {
        WORD_CONTAINS,
        WORD_WITH_ENDINGS,
        WORD_EXACT,
        PHRASE,
        REGEX
    }

    private final @NotNull String id;
    private final boolean enabled;
    private final @NotNull MatchType matchType;
    private final @NotNull FilterAction action;
    private final @NotNull String replacement;
    private final boolean logCatches;
    private final boolean fireEvent;
    private final @NotNull Set<TextSurface> surfaces;
    private final @Nullable String rawPattern;
    private final @NotNull List<String> entries;
    private final @Nullable Pattern compiledPattern;

    public TextProcessor(
            @NotNull String id,
            boolean enabled,
            @NotNull MatchType matchType,
            @NotNull FilterAction action,
            @Nullable String replacement,
            boolean logCatches,
            boolean fireEvent,
            @NotNull Set<TextSurface> surfaces,
            @Nullable String rawPattern,
            @NotNull List<String> entries,
            @Nullable Logger logger
    ) {
        this.id = id;
        this.enabled = enabled;
        this.matchType = matchType;
        this.action = action == FilterAction.BLOCK ? FilterAction.BLOCK : FilterAction.REPLACE;
        this.replacement = (replacement != null && !replacement.isEmpty()) ? replacement : "#";
        this.logCatches = logCatches;
        this.fireEvent = fireEvent;
        this.surfaces = surfaces.isEmpty() ? EnumSet.noneOf(TextSurface.class) : EnumSet.copyOf(surfaces);
        this.rawPattern = rawPattern;
        this.entries = List.copyOf(entries);
        this.compiledPattern = enabled ? compilePattern(logger) : null;
    }

    public @NotNull String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public @NotNull MatchType getMatchType() {
        return matchType;
    }

    public @NotNull FilterAction getAction() {
        return action;
    }

    public @NotNull String getReplacement() {
        return replacement;
    }

    public boolean shouldLogCatches() {
        return logCatches;
    }

    public boolean shouldFireEvent() {
        return fireEvent;
    }

    public @NotNull Set<TextSurface> getSurfaces() {
        return surfaces;
    }

    public boolean appliesTo(@NotNull TextSurface surface) {
        return surfaces.isEmpty() || surfaces.contains(surface);
    }

    public @Nullable String getRawPattern() {
        return rawPattern;
    }

    public @NotNull List<String> getEntries() {
        return entries;
    }

    public @Nullable Pattern getCompiledPattern() {
        return compiledPattern;
    }

    public @NotNull String getPatternDescription() {
        if (matchType == MatchType.REGEX) {
            return rawPattern != null ? rawPattern : "";
        }
        return matchType.name() + " (" + entries.size() + " entries)";
    }

    private @Nullable Pattern compilePattern(@Nullable Logger logger) {
        String regex;
        if (matchType == MatchType.REGEX) {
            if (rawPattern == null || rawPattern.isBlank()) {
                return null;
            }
            regex = rawPattern;
        } else {
            if (entries.isEmpty()) {
                return null;
            }
            // Quote literal words/phrases, deduplicate, and sort longest-first
            String alternation = entries.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .map(Pattern::quote)
                    .collect(Collectors.joining("|"));
            if (alternation.isEmpty()) {
                return null;
            }
            regex = switch (matchType) {
                case WORD_CONTAINS -> "(?i)\\b\\w*(?:" + alternation + ")\\w*\\b";
                case WORD_WITH_ENDINGS -> "(?i)\\b(?:" + alternation + ")(?:ing|ed|er|est|able|ies|ty|es|s|y)?\\b";
                case WORD_EXACT -> "(?i)\\b(?:" + alternation + ")\\b";
                case PHRASE -> "(?i)(?<!\\w)(?:" + alternation + ")(?!\\w)";
                case REGEX -> rawPattern;
            };
        }

        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            if (logger != null) {
                logger.warning("Invalid regex in TextProcessor '" + id + "': " + e.getMessage());
            }
            return null;
        }
    }

    public static @NotNull TextProcessor fromSection(@NotNull String id, @NotNull Section section, @Nullable Logger logger) {
        boolean enabled = section.getBoolean("enabled", true);
        MatchType matchType = parseMatchType(section.getString("match-type"));
        FilterAction action = parseAction(section.getString("action"));
        String replacement = section.getString("replacement", "#");
        boolean logging = section.getBoolean("logging", true);
        boolean fireEvent = section.getBoolean("fire-event", true);
        String pattern = section.getString("pattern");
        List<String> entries = section.getStringList("entries");

        Set<TextSurface> surfaces = EnumSet.noneOf(TextSurface.class);
        List<String> rawSurfaces = section.getStringList("surfaces");
        if (rawSurfaces != null) {
            for (String s : rawSurfaces) {
                if (s == null || s.isBlank()) {
                    continue;
                }
                try {
                    surfaces.add(TextSurface.valueOf(s.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    if (logger != null) {
                        logger.warning("Unknown surface '" + s + "' in TextProcessor '" + id + "'");
                    }
                }
            }
        }

        return new TextProcessor(
                id,
                enabled,
                matchType,
                action,
                replacement,
                logging,
                fireEvent,
                surfaces,
                pattern,
                entries != null ? entries : List.of(),
                logger
        );
    }

    private static @NotNull MatchType parseMatchType(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return MatchType.WORD_CONTAINS;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "WORD_WITH_ENDINGS" -> MatchType.WORD_WITH_ENDINGS;
            case "WORD_EXACT" -> MatchType.WORD_EXACT;
            case "PHRASE" -> MatchType.PHRASE;
            case "REGEX" -> MatchType.REGEX;
            default -> MatchType.WORD_CONTAINS;
        };
    }

    private static @NotNull FilterAction parseAction(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return FilterAction.REPLACE;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "BLOCK", "BLOCK-SEND" -> FilterAction.BLOCK;
            default -> FilterAction.REPLACE;
        };
    }
}