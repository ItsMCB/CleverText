package me.itsmcb.clevertext.api;

import java.util.ArrayList;

public class TextFragments {

    private String originalText;
    private ArrayList<PatternCatch> catches;

    public TextFragments(String originalText, ArrayList<PatternCatch> catches) {
        this.originalText = originalText;
        this.catches = catches;
    }

    public String getOriginalText() {
        return originalText;
    }

    public ArrayList<PatternCatch> getCatches() {
        return catches;
    }
}
