package me.itsmcb.clevertext.api;

import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.api.event.CleverTextCatchEvent;
import me.itsmcb.clevertext.api.event.FilterAction;
import me.itsmcb.clevertext.api.event.FilterCatch;
import me.itsmcb.clevertext.api.event.TextSurface;
import me.itsmcb.clevertext.config.TextProcessor;
import me.itsmcb.clevertext.config.UrlFilterConfig;
import me.itsmcb.vexelcore.bukkit.api.framework.TextUtils;
import me.itsmcb.vexelcore.common.api.text.Icon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextProcessorManager {

    private record MatchCandidate(
            @NotNull String ruleId,
            @NotNull FilterAction action,
            @NotNull String replacement,
            @NotNull String matchedText,
            @Nullable String clickableUrl,
            @NotNull String patternDescription,
            int start,
            int end,
            boolean fireEvent,
            boolean logCatch
    ) {}

    private final CleverText instance;
    private final List<TextProcessor> textProcessors = new ArrayList<>();
    private @NotNull UrlFilterConfig urlFilter = UrlFilterConfig.disabled();

    public TextProcessorManager(@NotNull CleverText instance) {
        this.instance = instance;
    }

    public boolean register(@Nullable TextProcessor textProcessor) {
        if (textProcessor == null) {
            return false;
        }
        instance.getLogger().info("Loaded text processor \"" + textProcessor.getId() + "\" ("
                + (textProcessor.isEnabled() ? textProcessor.getAction() : "DISABLED")
                + ", " + textProcessor.getPatternDescription() + ")");
        return textProcessors.add(textProcessor);
    }

    public void setUrlFilter(@Nullable UrlFilterConfig urlFilter) {
        this.urlFilter = urlFilter != null ? urlFilter : UrlFilterConfig.disabled();
    }

    public @NotNull UrlFilterConfig getUrlFilter() {
        return urlFilter;
    }

    public @NotNull List<TextProcessor> getTextProcessors() {
        return List.copyOf(textProcessors);
    }

    public @Nullable TextProcessor getById(@NotNull String id) {
        return textProcessors.stream()
                .filter(tp -> tp.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public @NotNull ProcessedTextResult processChat(@NotNull Player player, @NotNull String message, @NotNull TextColor baseColor, boolean dryRun) {
        return process(player, message, TextSurface.CHAT, baseColor, true, dryRun, true, null);
    }

    public @NotNull ProcessedTextResult processPlain(@Nullable Player player, @NotNull String text, @NotNull TextSurface surface, boolean dryRun) {
        return process(player, text, surface, NamedTextColor.WHITE, false, dryRun, true, null);
    }

    public @NotNull ProcessedTextResult process(
            @Nullable Player player,
            @NotNull String text,
            @NotNull TextSurface surface,
            @NotNull TextColor baseColor,
            boolean applyHyperlinks,
            boolean dryRun,
            boolean notifyPlayer,
            @Nullable String contextDetail
    ) {
        if (text.isEmpty()) {
            return new ProcessedTextResult("", "", List.of(), List.of(), List.of(), false, false, false, false);
        }

        boolean bypassModeration = player != null && instance.hasBypassPermission(player);
        List<MatchCandidate> candidates = collectCandidates(text, surface, applyHyperlinks, bypassModeration);

        // Prioritize moderation: BLOCK (0) -> REPLACE (1) -> ALLOW_HYPERLINK (2), then longer spans, then earlier start
        List<MatchCandidate> prioritized = candidates.stream()
                .filter(c -> c.end() > c.start())
                .sorted(Comparator
                        .comparingInt((MatchCandidate c) -> actionPriority(c.action()))
                        .thenComparingInt(c -> -(c.end() - c.start()))
                        .thenComparingInt(MatchCandidate::start))
                .toList();

        List<MatchCandidate> selected = new ArrayList<>();
        for (MatchCandidate candidate : prioritized) {
            boolean overlaps = selected.stream()
                    .anyMatch(existing -> candidate.start() < existing.end() && candidate.end() > existing.start());
            if (!overlaps) {
                selected.add(candidate);
            } else if (instance.isDebugMode()) {
                instance.getLogger().info("Skipping overlapping catch \"" + candidate.matchedText()
                        + "\" from rule \"" + candidate.ruleId() + "\" on " + surface);
            }
        }
        selected.sort(Comparator.comparingInt(MatchCandidate::start));

        List<FilterCatch> moderationCatches = new ArrayList<>();
        List<FilterCatch> eventCatches = new ArrayList<>();
        boolean initialBlocked = false;
        boolean anyLoggableRule = false;

        for (MatchCandidate mc : selected) {
            if (mc.action() == FilterAction.ALLOW_HYPERLINK) {
                continue;
            }
            FilterCatch fc = new FilterCatch(mc.ruleId(), mc.action(), mc.matchedText(), mc.start(), mc.end());
            moderationCatches.add(fc);
            if (mc.fireEvent()) {
                eventCatches.add(fc);
            }
            if (mc.logCatch()) {
                anyLoggableRule = true;
            }
            if (mc.action() == FilterAction.BLOCK) {
                initialBlocked = true;
            }
        }

        boolean finalBlocked = initialBlocked;
        boolean eventCancelled = false;

        if (!dryRun && !eventCatches.isEmpty()) {
            CleverTextCatchEvent catchEvent = new CleverTextCatchEvent(
                    player,
                    surface,
                    text,
                    eventCatches,
                    initialBlocked,
                    !Bukkit.isPrimaryThread()
            );
            Bukkit.getPluginManager().callEvent(catchEvent);
            if (catchEvent.isCancelled()) {
                eventCancelled = true;
                finalBlocked = false;
            } else {
                finalBlocked = catchEvent.isBlocked();
            }
        }

        if (eventCancelled) {
            // Exempted by external listener: keep allowed hyperlinks if enabled, omit moderation changes
            return buildExemptedResult(text, baseColor, applyHyperlinks, selected, moderationCatches);
        }

        StringBuilder plainCensored = new StringBuilder();
        List<Component> regularComponents = new ArrayList<>();
        List<Component> staffComponents = new ArrayList<>();
        Set<String> blockingRules = new LinkedHashSet<>();
        boolean censoredAny = false;
        boolean modifiedAny = false;

        int lastIndex = 0;
        for (MatchCandidate mc : selected) {
            if (mc.start() > lastIndex) {
                String plainBefore = text.substring(lastIndex, mc.start());
                plainCensored.append(plainBefore);
                Component c = Component.text(plainBefore, baseColor);
                regularComponents.add(c);
                staffComponents.add(c);
            }

            switch (mc.action()) {
                case REPLACE -> {
                    String censored = computeCensoredString(mc);
                    plainCensored.append(censored);

                    Component staffHover = buildStaffHover(mc, text, surface, contextDetail);
                    Component regularHover = Component.text(Icon.WARNING + " ", NamedTextColor.RED)
                            .append(Component.text("Censored ", NamedTextColor.GRAY))
                            .append(Component.text(Icon.WARNING.toString(), NamedTextColor.RED))
                            .appendNewline()
                            .append(Component.text("Attempts to bypass filter may result in punishment", NamedTextColor.GRAY));

                    staffComponents.add(Component.text(censored, NamedTextColor.RED).hoverEvent(HoverEvent.showText(staffHover)));
                    regularComponents.add(Component.text(censored, NamedTextColor.RED).hoverEvent(HoverEvent.showText(regularHover)));
                    censoredAny = true;
                    modifiedAny = true;
                }
                case BLOCK -> {
                    blockingRules.add(mc.ruleId());
                    if (finalBlocked) {
                        plainCensored.append(mc.matchedText());
                        Component staffHover = buildStaffHover(mc, text, surface, contextDetail);
                        staffComponents.add(Component.text(mc.matchedText(), NamedTextColor.RED).hoverEvent(HoverEvent.showText(staffHover)));
                        regularComponents.add(Component.text(mc.matchedText(), NamedTextColor.RED));
                    } else {
                        // Event listener downgraded BLOCK -> allow censored output instead of leaking raw word
                        String censored = "#".repeat(Math.max(1, mc.end() - mc.start()));
                        plainCensored.append(censored);
                        Component staffHover = buildStaffHover(mc, text, surface, contextDetail);
                        staffComponents.add(Component.text(censored, NamedTextColor.RED).hoverEvent(HoverEvent.showText(staffHover)));
                        regularComponents.add(Component.text(censored, NamedTextColor.RED));
                        censoredAny = true;
                        modifiedAny = true;
                    }
                }
                case ALLOW_HYPERLINK -> {
                    plainCensored.append(mc.matchedText());
                    if (applyHyperlinks && mc.clickableUrl() != null) {
                        Component linkHover = Component.text("Click to open", NamedTextColor.GRAY)
                                .appendNewline()
                                .append(Component.text(mc.clickableUrl(), NamedTextColor.DARK_GRAY));
                        Component link = Component.text(mc.matchedText(), NamedTextColor.GREEN)
                                .hoverEvent(HoverEvent.showText(linkHover))
                                .clickEvent(ClickEvent.openUrl(mc.clickableUrl()));
                        regularComponents.add(link);
                        staffComponents.add(link);
                        modifiedAny = true;
                    } else {
                        Component c = Component.text(mc.matchedText(), baseColor);
                        regularComponents.add(c);
                        staffComponents.add(c);
                    }
                }
            }

            if (!dryRun && mc.action() != FilterAction.ALLOW_HYPERLINK && mc.logCatch()) {
                String actor = player != null ? player.getName() : "Unknown";
                String detailSuffix = (contextDetail != null && !contextDetail.isBlank()) ? " [" + contextDetail + "]" : "";
                instance.getLogger().info("[Filter][" + surface.name() + "]" + detailSuffix + " "
                        + actor + " matched \"" + mc.ruleId() + "\" (" + mc.action() + "): \"" + mc.matchedText() + "\"");
            }

            lastIndex = mc.end();
        }

        if (lastIndex < text.length()) {
            String trailing = text.substring(lastIndex);
            plainCensored.append(trailing);
            Component c = Component.text(trailing, baseColor);
            regularComponents.add(c);
            staffComponents.add(c);
        }

        String censoredResult = plainCensored.toString();
        if (!dryRun) {
            dispatchNotifications(
                    player,
                    text,
                    censoredResult,
                    surface,
                    contextDetail,
                    finalBlocked,
                    censoredAny,
                    notifyPlayer,
                    anyLoggableRule,
                    moderationCatches,
                    blockingRules
            );
        }

        return new ProcessedTextResult(
                text,
                censoredResult,
                regularComponents,
                staffComponents,
                moderationCatches,
                finalBlocked,
                censoredAny,
                modifiedAny,
                false
        );
    }

    private ProcessedTextResult buildExemptedResult(
            String text,
            TextColor baseColor,
            boolean applyHyperlinks,
            List<MatchCandidate> selected,
            List<FilterCatch> moderationCatches
    ) {
        List<Component> components = new ArrayList<>();
        int lastIndex = 0;
        boolean modified = false;

        for (MatchCandidate mc : selected) {
            if (mc.action() != FilterAction.ALLOW_HYPERLINK) {
                continue;
            }
            if (mc.start() > lastIndex) {
                components.add(Component.text(text.substring(lastIndex, mc.start()), baseColor));
            }
            if (applyHyperlinks && mc.clickableUrl() != null) {
                Component linkHover = Component.text("Click to open", NamedTextColor.GRAY)
                        .appendNewline()
                        .append(Component.text(mc.clickableUrl(), NamedTextColor.DARK_GRAY));
                components.add(Component.text(mc.matchedText(), NamedTextColor.GREEN)
                        .hoverEvent(HoverEvent.showText(linkHover))
                        .clickEvent(ClickEvent.openUrl(mc.clickableUrl())));
                modified = true;
            } else {
                components.add(Component.text(mc.matchedText(), baseColor));
            }
            lastIndex = mc.end();
        }
        if (lastIndex < text.length()) {
            components.add(Component.text(text.substring(lastIndex), baseColor));
        }

        return new ProcessedTextResult(text, text, components, components, moderationCatches, false, false, modified, true);
    }

    private List<MatchCandidate> collectCandidates(String text, TextSurface surface, boolean applyHyperlinks, boolean bypassModeration) {
        List<MatchCandidate> candidates = new ArrayList<>();

        if (!bypassModeration) {
            for (TextProcessor tp : textProcessors) {
                if (!tp.isEnabled() || !tp.appliesTo(surface)) {
                    continue;
                }
                Pattern pattern = tp.getCompiledPattern();
                if (pattern == null) {
                    continue;
                }
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    if (matcher.end() > matcher.start()) {
                        candidates.add(new MatchCandidate(
                                tp.getId(),
                                tp.getAction(),
                                tp.getReplacement(),
                                text.substring(matcher.start(), matcher.end()),
                                null,
                                tp.getPatternDescription(),
                                matcher.start(),
                                matcher.end(),
                                tp.shouldFireEvent(),
                                tp.shouldLogCatches()
                        ));
                    }
                }
            }
        }

        if (urlFilter.appliesTo(surface)) {
            for (UrlFilterConfig.DetectedUrl url : urlFilter.detectUrls(text)) {
                if (urlFilter.getMode() == UrlFilterConfig.Mode.ALLOW_ALL) {
                    if (applyHyperlinks && urlFilter.isHyperlinkAllowed()) {
                        candidates.add(new MatchCandidate(
                                "url-filter",
                                FilterAction.ALLOW_HYPERLINK,
                                "",
                                url.rawText(),
                                url.clickableUrl(),
                                "url-allow-all",
                                url.start(),
                                url.end(),
                                false,
                                false
                        ));
                    }
                } else {
                    // WHITELIST_BLOCK or WHITELIST_REPLACE
                    if (url.whitelisted()) {
                        if (applyHyperlinks && urlFilter.isHyperlinkAllowed()) {
                            candidates.add(new MatchCandidate(
                                    "url-whitelist",
                                    FilterAction.ALLOW_HYPERLINK,
                                    "",
                                    url.rawText(),
                                    url.clickableUrl(),
                                    "url-whitelist",
                                    url.start(),
                                    url.end(),
                                    false,
                                    false
                            ));
                        }
                    } else if (!bypassModeration) {
                        FilterAction action = urlFilter.getMode() == UrlFilterConfig.Mode.WHITELIST_BLOCK
                                ? FilterAction.BLOCK
                                : FilterAction.REPLACE;
                        candidates.add(new MatchCandidate(
                                "url-whitelist",
                                action,
                                urlFilter.getReplacement(),
                                url.rawText(),
                                null,
                                "whitelist-policy",
                                url.start(),
                                url.end(),
                                urlFilter.shouldFireEvent(),
                                urlFilter.shouldLogCatches()
                        ));
                    }
                }
            }
        }

        return candidates;
    }

    private void dispatchNotifications(
            @Nullable Player player,
            @NotNull String originalText,
            @NotNull String censoredText,
            @NotNull TextSurface surface,
            @Nullable String contextDetail,
            boolean blocked,
            boolean censored,
            boolean notifyPlayer,
            boolean anyLoggableRule,
            @NotNull List<FilterCatch> moderationCatches,
            @NotNull Set<String> blockingRules
    ) {
        String actor = player != null ? player.getName() : "Unknown";
        String surfaceLabel = surface.displayName();
        String locationLabel = (contextDetail != null && !contextDetail.isBlank())
                ? surface.name() + " (" + contextDetail + ")"
                : surface.name();

        if (blocked) {
            if (notifyPlayer && player != null) {
                if (surface == TextSurface.CHAT) {
                    TextUtils.send(player, instance.getBlockedMessage());
                } else {
                    TextUtils.send(player, instance.getBlockedSurfaceMessage(),
                            Placeholder.unparsed("surface", surfaceLabel));
                }
            }

            String rules = blockingRules.isEmpty()
                    ? moderationCatches.stream().map(FilterCatch::ruleId).distinct().reduce((a, b) -> a + ", " + b).orElse("event-listener")
                    : String.join(", ", blockingRules);

            for (Player onlinePlayer : List.copyOf(Bukkit.getOnlinePlayers())) {
                if (instance.hasStaffPermission(onlinePlayer)) {
                    TextUtils.send(onlinePlayer,
                            "<dark_gray>[<red>Filter</red>]</dark_gray> <yellow><player></yellow> <gray>blocked on</gray> <aqua><surface></aqua> <gray>by</gray> <red><rules></red><gray>:</gray> <white><msg></white>",
                            Placeholder.unparsed("player", actor),
                            Placeholder.unparsed("surface", locationLabel),
                            Placeholder.unparsed("rules", rules),
                            Placeholder.unparsed("msg", originalText));
                }
            }

            instance.getWebhookService().notifyFilterCatch(
                    WebhookService.EVENT_FILTER_BLOCK,
                    player,
                    surface,
                    contextDetail,
                    originalText,
                    censoredText,
                    moderationCatches,
                    anyLoggableRule
            );
            return;
        }

        if (censored) {
            if (notifyPlayer && player != null && surface != TextSurface.CHAT && instance.isNotifyOnCensor()) {
                TextUtils.send(player, instance.getCensoredSurfaceMessage(),
                        Placeholder.unparsed("surface", surfaceLabel));
            }

            // On non-chat surfaces, alert staff directly since those surfaces don't broadcast a staff hover message
            if (surface != TextSurface.CHAT) {
                String rules = moderationCatches.stream()
                        .map(FilterCatch::ruleId)
                        .distinct()
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("filter");
                for (Player onlinePlayer : List.copyOf(Bukkit.getOnlinePlayers())) {
                    if (instance.hasStaffPermission(onlinePlayer)) {
                        TextUtils.send(onlinePlayer,
                                "<dark_gray>[<yellow>Filter</yellow>]</dark_gray> <yellow><player></yellow> <gray>censored on</gray> <aqua><surface></aqua> <gray>by</gray> <yellow><rules></yellow><gray>:</gray> <white><msg></white>",
                                Placeholder.unparsed("player", actor),
                                Placeholder.unparsed("surface", locationLabel),
                                Placeholder.unparsed("rules", rules),
                                Placeholder.unparsed("msg", originalText));
                    }
                }
            }

            instance.getWebhookService().notifyFilterCatch(
                    WebhookService.EVENT_FILTER_REPLACE,
                    player,
                    surface,
                    contextDetail,
                    originalText,
                    censoredText,
                    moderationCatches,
                    anyLoggableRule
            );
        }
    }

    private static int actionPriority(FilterAction action) {
        return switch (action) {
            case BLOCK -> 0;
            case REPLACE -> 1;
            case ALLOW_HYPERLINK -> 2;
        };
    }

    private static String computeCensoredString(MatchCandidate mc) {
        String replacement = mc.replacement();
        if (replacement.length() == 1) {
            return replacement.repeat(Math.max(1, mc.end() - mc.start()));
        }
        return replacement;
    }

    private static Component buildStaffHover(MatchCandidate mc, String originalText, TextSurface surface, @Nullable String contextDetail) {
        return Component.text("==== ", NamedTextColor.GRAY)
                .append(Component.text("Text Filter Catch Info", NamedTextColor.AQUA))
                .append(Component.text(" ====", NamedTextColor.GRAY))
                .appendNewline()
                .append(Component.text("Surface: ", NamedTextColor.DARK_AQUA)).append(Component.text(surface.name() + (contextDetail != null ? " (" + contextDetail + ")" : ""), NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text("Rule: ", NamedTextColor.DARK_AQUA)).append(Component.text(mc.ruleId(), NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text("Action: ", NamedTextColor.DARK_AQUA)).append(Component.text(mc.action().name(), NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text("Finding: ", NamedTextColor.DARK_AQUA)).append(Component.text(mc.matchedText(), NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text("Original: ", NamedTextColor.DARK_AQUA)).append(Component.text(originalText, NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text("Pattern: ", NamedTextColor.DARK_AQUA)).append(Component.text(mc.patternDescription(), NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text("Range: ", NamedTextColor.DARK_AQUA)).append(Component.text(mc.start() + ".." + mc.end(), NamedTextColor.AQUA));
    }
}