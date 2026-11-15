package me.itsmcb.clevertext.config;

import libs.dev.dejvokep.boostedyaml.block.implementation.Section;
import me.itsmcb.clevertext.api.event.TextSurface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlFilterConfig {

    public enum Mode {
        ALLOW_ALL,
        WHITELIST_BLOCK,
        WHITELIST_REPLACE,
        DISABLED
    }

    public record WhitelistRule(
            @NotNull String rawRule,
            @NotNull String host,
            boolean allowSubdomains,
            @Nullable String pathPrefix
    ) {
        public boolean matches(@NotNull URI uri) {
            String uriHost = uri.getHost();
            if (uriHost == null || uriHost.isBlank()) {
                return false;
            }
            String normalizedHost = uriHost.toLowerCase(Locale.ROOT);
            if (normalizedHost.endsWith(".")) {
                normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
            }

            boolean hostMatch = normalizedHost.equals(host)
                    || (allowSubdomains && normalizedHost.endsWith("." + host));
            if (!hostMatch) {
                return false;
            }

            if (pathPrefix == null || pathPrefix.isEmpty()) {
                return true;
            }

            String rawPath = uri.getRawPath();
            String normalizedPath = rawPath != null ? rawPath.toLowerCase(Locale.ROOT) : "";
            if (pathPrefix.endsWith("/")) {
                return normalizedPath.startsWith(pathPrefix);
            }
            return normalizedPath.equals(pathPrefix) || normalizedPath.startsWith(pathPrefix + "/");
        }
    }

    public record DetectedUrl(
            int start,
            int end,
            @NotNull String rawText,
            @NotNull String clickableUrl,
            boolean whitelisted
    ) {}

    // Full http(s)://... URLs
    private static final Pattern SCHEME_URL_PATTERN = Pattern.compile("(?i)\\bhttps?://[^\\s<>\"'`]+");

    // Bare IPv4 addresses or domains with common TLDs (avoids version numbers like 1.21.4 or filenames like config.yml)
    private static final Pattern BARE_URL_PATTERN = Pattern.compile(
            "(?i)(?<![\\w@./])(?:"
                    + "(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?(?:/[^\\s<>\"'`]*)?"
                    + "|"
                    + "(?:[a-z0-9-]+\\.)+(?:com|net|org|gg|io|co|me|tv|xyz|dev|app|info|biz|club|online|site|store|pro|wiki|live|stream|link|ly|to|cc|tk|ml|ga|cf|gq|uk|us|ca|de|fr|es|br|au|ru|jp|nl|pl|se|no|eu|edu|gov)(?::\\d{1,5})?(?:/[^\\s<>\"'`]*)?"
                    + ")"
    );

    private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?:[:/].*)?$");

    private final boolean enabled;
    private final @NotNull Mode mode;
    private final @NotNull String replacement;
    private final boolean hyperlinkAllowed;
    private final boolean matchBareDomains;
    private final boolean logging;
    private final boolean fireEvent;
    private final @NotNull Set<TextSurface> surfaces;
    private final @NotNull List<WhitelistRule> whitelist;

    public UrlFilterConfig(
            boolean enabled,
            @NotNull Mode mode,
            @NotNull String replacement,
            boolean hyperlinkAllowed,
            boolean matchBareDomains,
            boolean logging,
            boolean fireEvent,
            @NotNull Set<TextSurface> surfaces,
            @NotNull List<WhitelistRule> whitelist
    ) {
        this.enabled = enabled;
        this.mode = mode;
        this.replacement = replacement.isEmpty() ? "[link removed]" : replacement;
        this.hyperlinkAllowed = hyperlinkAllowed;
        this.matchBareDomains = matchBareDomains;
        this.logging = logging;
        this.fireEvent = fireEvent;
        this.surfaces = surfaces.isEmpty() ? EnumSet.noneOf(TextSurface.class) : EnumSet.copyOf(surfaces);
        this.whitelist = List.copyOf(whitelist);
    }

    public boolean isEnabled() {
        return enabled && mode != Mode.DISABLED;
    }

    public @NotNull Mode getMode() {
        return mode;
    }

    public @NotNull String getReplacement() {
        return replacement;
    }

    public boolean isHyperlinkAllowed() {
        return hyperlinkAllowed;
    }

    public boolean isMatchBareDomains() {
        return matchBareDomains;
    }

    public boolean shouldLogCatches() {
        return logging;
    }

    public boolean shouldFireEvent() {
        return fireEvent;
    }

    public @NotNull Set<TextSurface> getSurfaces() {
        return surfaces;
    }

    public @NotNull List<WhitelistRule> getWhitelist() {
        return whitelist;
    }

    public boolean appliesTo(@NotNull TextSurface surface) {
        return isEnabled() && (surfaces.isEmpty() || surfaces.contains(surface));
    }

    public @NotNull List<DetectedUrl> detectUrls(@NotNull String text) {
        if (!isEnabled() || text.isEmpty()) {
            return List.of();
        }

        List<DetectedUrl> candidates = new ArrayList<>();

        Matcher schemeMatcher = SCHEME_URL_PATTERN.matcher(text);
        while (schemeMatcher.find()) {
            int start = schemeMatcher.start();
            int end = trimTrailingPunctuation(text, start, schemeMatcher.end());
            if (end > start) {
                String raw = text.substring(start, end);
                candidates.add(buildDetectedUrl(start, end, raw, raw));
            }
        }

        if (matchBareDomains) {
            Matcher bareMatcher = BARE_URL_PATTERN.matcher(text);
            while (bareMatcher.find()) {
                int start = bareMatcher.start();
                int end = trimTrailingPunctuation(text, start, bareMatcher.end());
                if (end <= start) {
                    continue;
                }
                boolean overlapsScheme = candidates.stream()
                        .anyMatch(c -> start < c.end() && end > c.start());
                if (overlapsScheme) {
                    continue;
                }
                String raw = text.substring(start, end);
                if (!isValidIpv4IfNumeric(raw)) {
                    continue;
                }
                candidates.add(buildDetectedUrl(start, end, raw, "https://" + raw));
            }
        }

        candidates.sort(Comparator.comparingInt(DetectedUrl::start));
        return candidates;
    }

    private DetectedUrl buildDetectedUrl(int start, int end, String raw, String clickable) {
        boolean whitelisted = false;
        try {
            URI uri = URI.create(clickable);
            whitelisted = whitelist.stream().anyMatch(rule -> rule.matches(uri));
        } catch (IllegalArgumentException ignored) {
            // Malformed URI stays non-whitelisted
        }
        return new DetectedUrl(start, end, raw, clickable, whitelisted);
    }

    private static boolean isValidIpv4IfNumeric(@NotNull String candidate) {
        Matcher ipMatcher = IPV4_PATTERN.matcher(candidate);
        if (!ipMatcher.matches()) {
            return true; // Not numeric IPv4 — domain TLD already verified by regex
        }
        for (int i = 1; i <= 4; i++) {
            try {
                int octet = Integer.parseInt(ipMatcher.group(i));
                if (octet < 0 || octet > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static int trimTrailingPunctuation(@NotNull String text, int start, int end) {
        int currentEnd = end;
        while (currentEnd > start) {
            char last = text.charAt(currentEnd - 1);
            if (last == '.' || last == ',' || last == '!' || last == '?' || last == ';' || last == ':'
                    || last == ']' || last == '}' || last == '"' || last == '\'') {
                currentEnd--;
            } else if (last == ')') {
                String slice = text.substring(start, currentEnd);
                long opens = slice.chars().filter(c -> c == '(').count();
                long closes = slice.chars().filter(c -> c == ')').count();
                if (closes > opens) {
                    currentEnd--;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        return currentEnd;
    }

    public static @NotNull UrlFilterConfig disabled() {
        return new UrlFilterConfig(false, Mode.DISABLED, "[link removed]", false, false, false, false, Set.of(), List.of());
    }

    public static @NotNull UrlFilterConfig fromSection(@Nullable Section section, @Nullable Logger logger) {
        if (section == null) {
            return disabled();
        }

        boolean enabled = section.getBoolean("enabled", true);
        Mode mode = parseMode(section.getString("mode"));
        String replacement = section.getString("replacement", "[link removed]");
        boolean hyperlinkAllowed = section.getBoolean("hyperlink-allowed", true);
        boolean matchBareDomains = section.getBoolean("match-bare-domains", true);
        boolean logging = section.getBoolean("logging", true);
        boolean fireEvent = section.getBoolean("fire-event", true);

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
                        logger.warning("Unknown surface '" + s + "' in url-filter");
                    }
                }
            }
        }

        List<WhitelistRule> rules = new ArrayList<>();
        List<String> rawRules = section.getStringList("whitelist");
        if (rawRules != null) {
            for (String rawRule : rawRules) {
                WhitelistRule parsed = parseRule(rawRule);
                if (parsed != null) {
                    rules.add(parsed);
                }
            }
        }

        return new UrlFilterConfig(enabled, mode, replacement, hyperlinkAllowed, matchBareDomains, logging, fireEvent, surfaces, rules);
    }

    private static @Nullable WhitelistRule parseRule(@Nullable String rawRule) {
        if (rawRule == null || rawRule.isBlank()) {
            return null;
        }
        String normalized = rawRule.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("https://")) {
            normalized = normalized.substring("https://".length());
        } else if (normalized.startsWith("http://")) {
            normalized = normalized.substring("http://".length());
        }

        boolean allowSubdomains = true;
        if (normalized.startsWith("*.")) {
            normalized = normalized.substring(2);
        }

        int slashIdx = normalized.indexOf('/');
        String host = slashIdx >= 0 ? normalized.substring(0, slashIdx) : normalized;
        String pathPrefix = slashIdx >= 0 ? normalized.substring(slashIdx) : null;

        // Strip optional port if someone specifies host:port in whitelist
        int colonIdx = host.indexOf(':');
        if (colonIdx >= 0) {
            host = host.substring(0, colonIdx);
        }
        if (host.isBlank()) {
            return null;
        }
        if (pathPrefix != null && (pathPrefix.isBlank() || pathPrefix.equals("/"))) {
            pathPrefix = null;
        }

        return new WhitelistRule(rawRule.trim(), host, allowSubdomains, pathPrefix);
    }

    private static @NotNull Mode parseMode(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return Mode.WHITELIST_REPLACE;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ALLOW_ALL" -> Mode.ALLOW_ALL;
            case "WHITELIST_BLOCK" -> Mode.WHITELIST_BLOCK;
            case "DISABLED" -> Mode.DISABLED;
            default -> Mode.WHITELIST_REPLACE;
        };
    }
}