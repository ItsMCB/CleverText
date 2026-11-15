package me.itsmcb.clevertext;

import libs.dev.dejvokep.boostedyaml.block.implementation.Section;
import libs.dev.dejvokep.boostedyaml.route.Route;
import libs.dev.dejvokep.boostedyaml.spigot.SpigotSerializer;
import me.itsmcb.clevertext.api.MessageFormatManager;
import me.itsmcb.clevertext.api.TextProcessorManager;
import me.itsmcb.clevertext.api.WebhookService;
import me.itsmcb.clevertext.api.event.TextSurface;
import me.itsmcb.clevertext.commands.ClearChatRootCmd;
import me.itsmcb.clevertext.commands.CleverTextCmd;
import me.itsmcb.clevertext.config.MessageFormat;
import me.itsmcb.clevertext.config.TextProcessor;
import me.itsmcb.clevertext.config.UrlFilterConfig;
import me.itsmcb.clevertext.listeners.AnvilListener;
import me.itsmcb.clevertext.listeners.BookListener;
import me.itsmcb.clevertext.listeners.ChatListener;
import me.itsmcb.clevertext.listeners.CommandListener;
import me.itsmcb.clevertext.listeners.EntityNameListener;
import me.itsmcb.clevertext.listeners.SignListener;
import me.itsmcb.vexelcore.bukkit.api.framework.VexelCommandRegistry;
import me.itsmcb.vexelcore.common.api.config.BoostedConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class CleverText extends JavaPlugin {
    private CleverTextContext context;
    private BoostedConfig mainConfig;
    private WebhookService webhookService;
    private volatile TextProcessorManager textProcessorManager;
    private volatile MessageFormatManager messageFormatManager;

    private volatile boolean handlerEnabled = true;
    private volatile boolean clearChatEnabled = true;
    private volatile boolean debugMode = false;
    private volatile boolean notifyOnCensor = true;

    private volatile boolean chatSurfaceEnabled = true;
    private volatile boolean signSurfaceEnabled = true;
    private volatile boolean bookSurfaceEnabled = true;
    private volatile boolean anvilSurfaceEnabled = true;
    private volatile boolean entityNamingSurfaceEnabled = true;
    private volatile boolean commandSurfaceEnabled = true;
    private volatile Map<String, Integer> monitoredCommands = Collections.emptyMap();

    private volatile String staffPermission = "group.staff";
    private volatile String bypassPermission = "clevertext.bypass";
    private volatile String blockedMessage = "<red>Your message was blocked by the text filter.</red>";
    private volatile String blockedSurfaceMessage = "<red>Your <surface> text was blocked by the text filter.</red>";
    private volatile String censoredSurfaceMessage = "<yellow>Some words in your <surface> were censored by the filter.</yellow>";

    public TextProcessorManager getTextProcessorManager() {
        return textProcessorManager;
    }

    public MessageFormatManager getMessageFormatManager() {
        return messageFormatManager;
    }

    public WebhookService getWebhookService() {
        return webhookService;
    }

    public BoostedConfig getMainConfig() {
        return mainConfig;
    }

    public CleverTextContext getContext() {
        return context;
    }

    public boolean isHandlerEnabled() {
        return handlerEnabled;
    }

    public boolean isClearChatEnabled() {
        return clearChatEnabled;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public boolean isNotifyOnCensor() {
        return notifyOnCensor;
    }

    public boolean isSurfaceEnabled(TextSurface surface) {
        if (!handlerEnabled) {
            return false;
        }
        return switch (surface) {
            case CHAT -> chatSurfaceEnabled;
            case SIGN -> signSurfaceEnabled;
            case BOOK -> bookSurfaceEnabled;
            case ANVIL -> anvilSurfaceEnabled;
            case ENTITY_NAME -> entityNamingSurfaceEnabled;
            case COMMAND -> commandSurfaceEnabled;
            case API -> true;
        };
    }

    public Map<String, Integer> getMonitoredCommands() {
        return monitoredCommands;
    }

    public String getStaffPermission() {
        return staffPermission;
    }

    public String getBypassPermission() {
        return bypassPermission;
    }

    public String getBlockedMessage() {
        return blockedMessage;
    }

    public String getBlockedSurfaceMessage() {
        return blockedSurfaceMessage;
    }

    public String getCensoredSurfaceMessage() {
        return censoredSurfaceMessage;
    }

    public boolean hasStaffPermission(CommandSender sender) {
        if (sender == null) {
            return false;
        }
        return sender.hasPermission("clevertext.staff")
                || (staffPermission != null && !staffPermission.isBlank() && sender.hasPermission(staffPermission));
    }

    public boolean hasBypassPermission(Player player) {
        if (player == null) {
            return false;
        }
        return bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission);
    }

    public void resetManagers() {
        mainConfig.reload();

        var cfg = mainConfig.get();
        this.clearChatEnabled = cfg.getBoolean("features.clearchat.enabled", true);
        this.handlerEnabled = cfg.getBoolean("features.handler.enabled", true);
        this.debugMode = cfg.getBoolean("features.handler.debug", false);
        this.notifyOnCensor = cfg.getBoolean("features.handler.notify-on-censor", true);

        this.chatSurfaceEnabled = cfg.getBoolean("features.handler.surfaces.chat", true);
        this.signSurfaceEnabled = cfg.getBoolean("features.handler.surfaces.signs", true);
        this.bookSurfaceEnabled = cfg.getBoolean("features.handler.surfaces.books", true);
        this.anvilSurfaceEnabled = cfg.getBoolean("features.handler.surfaces.anvils", true);
        this.entityNamingSurfaceEnabled = cfg.getBoolean("features.handler.surfaces.entity-naming", true);
        this.commandSurfaceEnabled = cfg.getBoolean("features.handler.surfaces.commands.enabled", true);

        Map<String, Integer> loadedCommands = new HashMap<>();
        Section cmdMonitored = cfg.getSection("features.handler.surfaces.commands.monitored");
        if (cmdMonitored != null) {
            for (Object keyObj : cmdMonitored.getKeys()) {
                String cmdName = String.valueOf(keyObj).trim().toLowerCase(Locale.ROOT);
                int startArg = Math.max(0, cmdMonitored.getInt(Route.from(keyObj), 0));
                loadedCommands.put(cmdName, startArg);
            }
        }
        this.monitoredCommands = Collections.unmodifiableMap(loadedCommands);

        String configuredStaffPerm = cfg.getString("features.handler.staff-permission");
        this.staffPermission = (configuredStaffPerm != null && !configuredStaffPerm.isBlank()) ? configuredStaffPerm : "group.staff";

        String configuredBypassPerm = cfg.getString("features.handler.bypass-permission");
        this.bypassPermission = (configuredBypassPerm != null && !configuredBypassPerm.isBlank()) ? configuredBypassPerm : "clevertext.bypass";

        String configuredBlockedMsg = cfg.getString("features.handler.blocked-message");
        this.blockedMessage = (configuredBlockedMsg != null && !configuredBlockedMsg.isBlank())
                ? configuredBlockedMsg
                : "<red>Your message was blocked by the text filter.</red>";

        String configuredBlockedSurfaceMsg = cfg.getString("features.handler.blocked-surface-message");
        this.blockedSurfaceMessage = (configuredBlockedSurfaceMsg != null && !configuredBlockedSurfaceMsg.isBlank())
                ? configuredBlockedSurfaceMsg
                : "<red>Your <surface> text was blocked by the text filter.</red>";

        String configuredCensoredSurfaceMsg = cfg.getString("features.handler.censored-surface-message");
        this.censoredSurfaceMessage = (configuredCensoredSurfaceMsg != null && !configuredCensoredSurfaceMsg.isBlank())
                ? configuredCensoredSurfaceMsg
                : "<yellow>Some words in your <surface> were censored by the filter.</yellow>";

        if (webhookService != null) {
            webhookService.reload();
        }

        // Build new TextProcessorManager & URL Filter
        TextProcessorManager newProcessorManager = new TextProcessorManager(this);
        UrlFilterConfig urlFilter = UrlFilterConfig.fromSection(cfg.getSection("features.url-filter"), getLogger());
        newProcessorManager.setUrlFilter(urlFilter);
        getLogger().info("Loaded URL filter (mode: " + urlFilter.getMode()
                + ", hyperlink-allowed: " + urlFilter.isHyperlinkAllowed()
                + ", whitelist rules: " + urlFilter.getWhitelist().size() + ")");

        Section processorsSec = cfg.getSection("features.text-processors");
        if (processorsSec != null) {
            for (String id : processorsSec.getRoutesAsStrings(false)) {
                Section sub = processorsSec.getSection(id);
                if (sub != null) {
                    newProcessorManager.register(TextProcessor.fromSection(id, sub, getLogger()));
                }
            }
        }

        // Build new MessageFormatManager
        MessageFormatManager newFormatManager = new MessageFormatManager(this);
        Section formatsSec = cfg.getSection("features.message-formats");
        if (formatsSec != null) {
            for (String id : formatsSec.getRoutesAsStrings(false)) {
                Section sub = formatsSec.getSection(id);
                if (sub != null) {
                    newFormatManager.register(MessageFormat.fromSection(id, sub));
                }
            }
        }

        // Swap manager references atomically so async chat never observes partially loaded state
        this.textProcessorManager = newProcessorManager;
        this.messageFormatManager = newFormatManager;

        if (context != null) {
            context.overrideService(newProcessorManager);
            context.overrideService(newFormatManager);
        }

        if (Bukkit.getServer() != null) {
            Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        }
    }

    @Override
    public void onEnable() {
        // Manage config
        mainConfig = new BoostedConfig(getDataFolder(), "config", getResource("config.yml"), SpigotSerializer.getInstance());

        // Initialize webhook service & Vexel context
        this.webhookService = new WebhookService(this);
        this.context = new CleverTextContext(this);
        context.registerService(mainConfig);
        context.registerService(webhookService);

        // Load managers & webhook settings
        resetManagers();

        // Register surface listeners
        var pm = getServer().getPluginManager();
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new CommandListener(this), this);
        pm.registerEvents(new SignListener(this), this);
        pm.registerEvents(new BookListener(this), this);
        pm.registerEvents(new AnvilListener(this), this);
        pm.registerEvents(new EntityNameListener(this), this);

        // Register Brigadier commands
        VexelCommandRegistry registry = new VexelCommandRegistry(context);
        registry.register(new CleverTextCmd(context));
        registry.register(new ClearChatRootCmd(context));
    }

    @Override
    public void onDisable() {
        if (webhookService != null) {
            webhookService.shutdown();
        }
    }
}