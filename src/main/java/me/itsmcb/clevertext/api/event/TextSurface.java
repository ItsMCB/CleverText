package me.itsmcb.clevertext.api.event;

/**
 * Represents the player-authored text surface processed by CleverText.
 */
public enum TextSurface {
    CHAT,
    COMMAND,
    SIGN,
    BOOK,
    ANVIL,
    ENTITY_NAME,
    API;

    public String displayName() {
        return switch (this) {
            case CHAT -> "chat message";
            case COMMAND -> "command";
            case SIGN -> "sign";
            case BOOK -> "book";
            case ANVIL -> "anvil item name";
            case ENTITY_NAME -> "name tag";
            case API -> "text";
        };
    }
}