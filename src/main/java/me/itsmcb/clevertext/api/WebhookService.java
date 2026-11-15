package me.itsmcb.clevertext.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import libs.dev.dejvokep.boostedyaml.block.implementation.Section;
import me.itsmcb.clevertext.CleverText;
import me.itsmcb.clevertext.api.event.FilterCatch;
import me.itsmcb.clevertext.api.event.TextSurface;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Asynchronously dispatches sanitized Discord-compatible webhook embeds for filter catches and ClearChat actions.
 */
public class WebhookService {

    public static final String EVENT_FILTER_REPLACE = "FILTER_REPLACE";
    public static final String EVENT_FILTER_BLOCK = "FILTER_BLOCK";
    public static final String EVENT_CLEAR_CHAT = "CLEAR_CHAT";

    private static final int COLOR_BLOCK = 0xED4245;
    private static final int COLOR_REPLACE = 0xFEE75C;
    private static final int COLOR_CLEAR_CHAT = 0x3498DB;

    public record WebhookSettings(
            boolean enabled,
            @NotNull String url,
            @NotNull String username,
            @NotNull String avatarUrl,
            boolean respectRuleLogging,
            @NotNull Set<String> events,
            @NotNull Set<TextSurface> surfaces
    ) {}

    private final CleverText instance;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CleverText-Webhook-Worker");
        thread.setDaemon(true);
        return thread;
    });

    private volatile @NotNull WebhookSettings settings = new WebhookSettings(
            false, "", "CleverText", "", true, Set.of(), Set.of()
    );

    public WebhookService(@NotNull CleverText instance) {
        this.instance = instance;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public @NotNull WebhookSettings getSettings() {
        return settings;
    }

    public boolean isConfiguredAndActive() {
        WebhookSettings current = this.settings;
        return current.enabled() && !current.url().isBlank();
    }

    public void reload() {
        Section section = instance.getMainConfig().get().getSection("webhook");

        boolean enabled = section == null || section.getBoolean("enabled", true);
        String envUrl = System.getenv("CLEVERTEXT_WEBHOOK_URL");
        String configUrl = section != null ? section.getString("url", "") : "";
        String resolvedUrl = (envUrl != null && !envUrl.isBlank())
                ? envUrl.trim()
                : (configUrl != null ? configUrl.trim() : "");

        if (!resolvedUrl.isBlank() && !isValidHttpUrl(resolvedUrl)) {
            instance.getLogger().warning("Invalid webhook URL configured (must start with http:// or https://). Webhook logging disabled.");
            resolvedUrl = "";
        }

        String username = section != null ? section.getString("username", "CleverText") : "CleverText";
        if (username == null || username.isBlank()) {
            username = "CleverText";
        }
        String avatarUrl = section != null ? section.getString("avatar-url", "") : "";
        if (avatarUrl == null) {
            avatarUrl = "";
        }
        boolean respectRuleLogging = section == null || section.getBoolean("respect-rule-logging", true);

        Set<String> events = new HashSet<>();
        if (section != null) {
            List<String> rawEvents = section.getStringList("events");
            if (rawEvents != null) {
                for (String evt : rawEvents) {
                    if (evt != null && !evt.isBlank()) {
                        events.add(evt.trim().toUpperCase(Locale.ROOT));
                    }
                }
            }
        }

        Set<TextSurface> surfaces = EnumSet.noneOf(TextSurface.class);
        if (section != null) {
            List<String> rawSurfaces = section.getStringList("surfaces");
            if (rawSurfaces != null) {
                for (String s : rawSurfaces) {
                    if (s == null || s.isBlank()) {
                        continue;
                    }
                    try {
                        surfaces.add(TextSurface.valueOf(s.trim().toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                        instance.getLogger().warning("Unknown surface '" + s + "' in webhook.surfaces");
                    }
                }
            }
        }

        this.settings = new WebhookSettings(
                enabled,
                resolvedUrl,
                username.trim(),
                avatarUrl.trim(),
                respectRuleLogging,
                Set.copyOf(events),
                surfaces.isEmpty() ? Set.of() : Set.copyOf(surfaces)
        );
    }

    public void notifyFilterCatch(
            @NotNull String eventType,
            @Nullable Player player,
            @NotNull TextSurface surface,
            @Nullable String contextDetail,
            @NotNull String originalText,
            @NotNull String censoredText,
            @NotNull List<FilterCatch> catches,
            boolean anyLoggableRule
    ) {
        WebhookSettings current = this.settings;
        if (!current.enabled() || current.url().isBlank() || !current.events().contains(eventType)) {
            return;
        }
        if (!current.surfaces().isEmpty() && !current.surfaces().contains(surface)) {
            return;
        }
        if (current.respectRuleLogging() && !anyLoggableRule) {
            return;
        }

        // Snapshot Bukkit objects before moving off-thread
        String playerName = player != null ? player.getName() : "Unknown";
        String playerUuid = player != null ? player.getUniqueId().toString() : "N/A";
        boolean blocked = EVENT_FILTER_BLOCK.equals(eventType);

        String ruleNames = catches.stream()
                .map(FilterCatch::ruleId)
                .distinct()
                .collect(Collectors.joining(", "));
        if (ruleNames.isBlank()) {
            ruleNames = "external-listener";
        }

        String matchedSnippets = catches.stream()
                .map(FilterCatch::matchedText)
                .distinct()
                .collect(Collectors.joining(", "));
        if (matchedSnippets.isBlank()) {
            matchedSnippets = "N/A";
        }

        String finalRuleNames = ruleNames;
        String finalMatchedSnippets = matchedSnippets;
        String surfaceLabel = (contextDetail != null && !contextDetail.isBlank())
                ? surface.name() + " (" + contextDetail + ")"
                : surface.name();

        submitPayload(current, () -> {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", truncate((blocked ? "Filter Blocked" : "Filter Censored") + " • " + surfaceLabel, 256));
            embed.addProperty("color", blocked ? COLOR_BLOCK : COLOR_REPLACE);
            embed.addProperty("timestamp", Instant.now().toString());

            JsonArray fields = new JsonArray();
            fields.add(field("Player", playerName + "\n`" + playerUuid + "`", true));
            fields.add(field("Surface", surfaceLabel, true));
            fields.add(field("Rule(s)", finalRuleNames, false));
            fields.add(field("Matched", toCodeBlock(finalMatchedSnippets), false));
            fields.add(field("Original Text", toCodeBlock(originalText), false));
            if (!blocked && !censoredText.equals(originalText)) {
                fields.add(field("Censored Output", toCodeBlock(censoredText), false));
            }
            embed.add("fields", fields);

            JsonObject footer = new JsonObject();
            footer.addProperty("text", "CleverText • " + eventType);
            embed.add("footer", footer);

            return buildWebhookEnvelope(current, embed);
        });
    }

    public void notifyClearChat(@NotNull CommandSender actor, int lines, boolean silent) {
        WebhookSettings current = this.settings;
        if (!current.enabled() || current.url().isBlank() || !current.events().contains(EVENT_CLEAR_CHAT)) {
            return;
        }

        String actorName = actor.getName();
        String actorId = actor instanceof Player p ? p.getUniqueId().toString() : "Console";

        submitPayload(current, () -> {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", "Chat Cleared");
            embed.addProperty("color", COLOR_CLEAR_CHAT);
            embed.addProperty("timestamp", Instant.now().toString());

            JsonArray fields = new JsonArray();
            fields.add(field("Actor", actorName + "\n`" + actorId + "`", true));
            fields.add(field("Lines", String.valueOf(lines), true));
            fields.add(field("Silent", silent ? "Yes" : "No", true));
            embed.add("fields", fields);

            JsonObject footer = new JsonObject();
            footer.addProperty("text", "CleverText • CLEAR_CHAT");
            embed.add("footer", footer);

            return buildWebhookEnvelope(current, embed);
        });
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void submitPayload(@NotNull WebhookSettings current, @NotNull java.util.function.Supplier<String> jsonSupplier) {
        if (executor.isShutdown()) {
            return;
        }
        executor.submit(() -> {
            try {
                String json = jsonSupplier.get();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(current.url()))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "CleverText-Plugin (Minecraft Server)")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 400) {
                    instance.getLogger().warning("Webhook returned HTTP " + response.statusCode());
                }
            } catch (Exception e) {
                instance.getLogger().warning("Failed to send webhook: " + e.getMessage());
            }
        });
    }

    private String buildWebhookEnvelope(@NotNull WebhookSettings current, @NotNull JsonObject embed) {
        JsonArray embeds = new JsonArray();
        embeds.add(embed);

        // Disable all Discord mention parsing so player messages cannot ping @everyone or roles
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());

        JsonObject payload = new JsonObject();
        if (!current.username().isBlank()) {
            payload.addProperty("username", truncate(current.username(), 80));
        }
        if (!current.avatarUrl().isBlank() && isValidHttpUrl(current.avatarUrl())) {
            payload.addProperty("avatar_url", current.avatarUrl());
        }
        payload.add("allowed_mentions", allowedMentions);
        payload.add("embeds", embeds);

        return gson.toJson(payload);
    }

    private static JsonObject field(@NotNull String name, @NotNull String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", truncate(name, 256));
        field.addProperty("value", truncate(value.isBlank() ? "N/A" : value, 1024));
        field.addProperty("inline", inline);
        return field;
    }

    private static String toCodeBlock(@NotNull String input) {
        String safe = input.replace("```", "`\u200B``");
        String body = truncate(safe, 1010);
        return "```\n" + body + "\n```";
    }

    private static String truncate(@NotNull String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 3) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private static boolean isValidHttpUrl(@NotNull String raw) {
        try {
            URI uri = URI.create(raw);
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}