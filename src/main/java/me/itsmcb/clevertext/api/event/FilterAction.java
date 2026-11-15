package me.itsmcb.clevertext.api.event;

/**
 * Action taken on a matched segment of text.
 */
public enum FilterAction {
    REPLACE,
    BLOCK,
    ALLOW_HYPERLINK
}