package me.itsmcb.clevertext.api;

import net.kyori.adventure.text.TextComponent;

public class ProcessedCleverText {

    enum processTarget {
        CHAT,
        SIGN,
        ITEM,
    }

    /*
    Is message blocked?
    Staff version with catch "insight" versus normal player pov "this is blocked btw"
     */

    private boolean messageBlocked;
    private TextComponent processedTextComponent;

    public ProcessedCleverText() {

    }

}
