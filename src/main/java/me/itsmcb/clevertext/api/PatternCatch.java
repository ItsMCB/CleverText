package me.itsmcb.clevertext.api;

import java.util.regex.MatchResult;

public class PatternCatch {

    private String textProcessorId;

    private MatchResult result;

    public PatternCatch(String textProcessorId, MatchResult result) {
        this.textProcessorId = textProcessorId;
        this.result = result;
    }

    public String getTextProcessorId() {
        return textProcessorId;
    }

    public MatchResult getResult() {
        return result;
    }
}
