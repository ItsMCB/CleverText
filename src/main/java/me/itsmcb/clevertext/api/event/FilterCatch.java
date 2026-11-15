package me.itsmcb.clevertext.api.event;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable details about a single matched rule/URL within a {@link CleverTextCatchEvent}.
 */
public record FilterCatch(
        @NotNull String ruleId,
        @NotNull FilterAction action,
        @NotNull String matchedText,
        int start,
        int end
) {}