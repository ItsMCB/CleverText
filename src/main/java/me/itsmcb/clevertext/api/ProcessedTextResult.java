package me.itsmcb.clevertext.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayList;

public class ProcessedTextResult {

    private boolean blocked;
    private TextComponent regularComponent;
    private TextComponent staffComponent;

    public ProcessedTextResult() {
    }

    public ProcessedTextResult setBlocked(boolean blocked) {
        this.blocked = blocked;
        return this;
    }

    public ProcessedTextResult setRegularComponent(ArrayList<Component> components) {
        this.regularComponent = Component.text().append(components).build();
        return this;
    }

    public ProcessedTextResult setStaffComponent(ArrayList<Component> components) {
        this.staffComponent = Component.text().append(components).build();
        return this;
    }

    public TextComponent getRegularComponent() {
        return regularComponent;
    }

    public TextComponent getStaffComponent() {
        return staffComponent;
    }

    public boolean isBlocked() {
        return blocked;
    }
}
