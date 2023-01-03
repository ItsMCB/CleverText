package me.itsmcb.clevertext.api;

import me.itsmcb.clevertext.config.TextProcessor;

public class IdentifiedTextFragment {

    private TextProcessor textProcessor;
    private PatternCatch patternCatch;
    private String newText;

    public IdentifiedTextFragment(TextProcessor textProcessor, PatternCatch patternCatch, String newText) {
        this.textProcessor = textProcessor;
        this.patternCatch = patternCatch;
        this.newText = newText;
    }

    public TextProcessor getTextProcessor() {
        return textProcessor;
    }

    public PatternCatch getPatternCatch() {
        return patternCatch;
    }

    public String getNewText() {
        return newText;
    }
}
