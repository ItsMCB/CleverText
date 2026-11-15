package me.itsmcb.clevertext;

import me.itsmcb.clevertext.api.MessageFormatManager;
import me.itsmcb.clevertext.api.TextProcessorManager;
import me.itsmcb.clevertext.api.WebhookService;
import me.itsmcb.vexelcore.bukkit.api.framework.AbstractVexelContext;
import me.itsmcb.vexelcore.common.api.config.BoostedConfig;
import org.jetbrains.annotations.NotNull;

public class CleverTextContext extends AbstractVexelContext {

    private final CleverText cleverText;

    public CleverTextContext(@NotNull CleverText plugin) {
        super(plugin);
        this.cleverText = plugin;
    }

    public @NotNull CleverText getCleverText() {
        return cleverText;
    }

    public @NotNull BoostedConfig getMainConfig() {
        return cleverText.getMainConfig();
    }

    public @NotNull TextProcessorManager getTextProcessorManager() {
        return cleverText.getTextProcessorManager();
    }

    public @NotNull MessageFormatManager getMessageFormatManager() {
        return cleverText.getMessageFormatManager();
    }

    public @NotNull WebhookService getWebhookService() {
        return cleverText.getWebhookService();
    }
}