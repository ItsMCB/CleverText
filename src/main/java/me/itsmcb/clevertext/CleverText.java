package me.itsmcb.clevertext;

import libs.dev.dejvokep.boostedyaml.spigot.SpigotSerializer;
import me.itsmcb.clevertext.api.MessageFormatManager;
import me.itsmcb.clevertext.api.TextProcessorManager;
import me.itsmcb.clevertext.config.MessageFormat;
import me.itsmcb.clevertext.config.TextProcessor;
import me.itsmcb.clevertext.features.clearchat.ClearChatFeat;
import me.itsmcb.clevertext.features.handlechat.ChatHandlerFeat;
import me.itsmcb.clevertext.features.maincmd.MainCmdFeat;
import me.itsmcb.vexelcore.bukkit.api.managers.BukkitFeatureManager;
import me.itsmcb.vexelcore.bukkit.api.managers.LocalizationManager;
import me.itsmcb.vexelcore.bukkit.api.text.BukkitMsgBuilder;
import me.itsmcb.vexelcore.common.api.config.BoostedConfig;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class CleverText extends JavaPlugin {
    private BukkitFeatureManager bukkitFeatureManager;
    private LocalizationManager localizationManager;
    private BoostedConfig mainConfig;
    private TextProcessorManager textProcessorManager;
    private MessageFormatManager messageFormatManager;

    public TextProcessorManager getTextProcessorManager() {
        return textProcessorManager;
    }

    public MessageFormatManager getMessageFormatManager() {
        return messageFormatManager;
    }

    public LocalizationManager getLocalizationManager() {
        return localizationManager;
    }

    public BoostedConfig getMainConfig() {
        return mainConfig;
    }


    public void resetManagers() {
        this.textProcessorManager = new TextProcessorManager(this);
        List<TextProcessor> textProcessors = (List<TextProcessor>) getConfig().getList("features.text-processors");
        textProcessors.forEach(textProcessor -> textProcessorManager.register(textProcessor));
        this.messageFormatManager = new MessageFormatManager(this);
        List<MessageFormat> messageFormats = (List<MessageFormat>) getConfig().getList("features.message-formats");
        messageFormats.forEach(messageFormat -> messageFormatManager.register(messageFormat));
        bukkitFeatureManager.reload();
    }

    @Override
    public void onEnable() {
        // TODO add support for signs, anvils, and books
        // TODO implement MODE for filtering
        // TODO maybe extend filtering to have replacement component customization options like the chat formats will have

        // Manage config
        mainConfig = new BoostedConfig(getDataFolder(),"config", getResource("config.yml"), new SpigotSerializer());

        // Register custom config serialization
        ConfigurationSerialization.registerClass(TextProcessor.class, "TextProcessor");
        ConfigurationSerialization.registerClass(MessageFormat.class, "MessageFormat");
        ConfigurationSerialization.registerClass(BukkitMsgBuilder.class, "BukkitMsgBuilder");

        // Load configurations and options
        this.localizationManager = new LocalizationManager(this, "en_US");
        localizationManager.register("en_US");

        // Load features
        this.bukkitFeatureManager = new BukkitFeatureManager();
        bukkitFeatureManager.register(new MainCmdFeat(this));
        bukkitFeatureManager.register(new ClearChatFeat(this));
        bukkitFeatureManager.register(new ChatHandlerFeat(this));
        resetManagers();
    }
}
