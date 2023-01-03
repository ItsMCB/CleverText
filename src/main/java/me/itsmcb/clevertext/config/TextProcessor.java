package me.itsmcb.clevertext.config;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@SerializableAs("TextProcessor")
public class TextProcessor implements ConfigurationSerializable {

    public enum ScanMode {
        REGEX("REGEX"),
        REGEXWITHENTRIES("REGEX-WITH-ENTRIES");

        private String scanMode;

        ScanMode(String scanMode) {
            this.scanMode = scanMode;
        }

        public String get() {
            return scanMode;
        }

        @Override
        public String toString() {
            return String.valueOf(get());
        }
    }

    public enum ModifyMode {
        REPLACE("REPLACE"),
        BLOCKSEND("BLOCK-SEND"),
        MAKEHYPERLINK("MAKE-HYPERLINK");

        private String modifyMode;

        ModifyMode(String scanMode) {
            this.modifyMode = scanMode;
        }

        public String get() {
            return modifyMode;
        }

        @Override
        public String toString() {
            return String.valueOf(get());
        }
    }

    private String id;
    private String scanRegexPattern;
    private ScanMode scanMode;
    private ArrayList<String> entries;
    private ModifyMode modifyMode;
    private String replaceModeReplacement;
    private boolean logCatches;

    public TextProcessor(String id, String scanRegexPattern, ScanMode scanMode, ArrayList<String> entries, ModifyMode modifyMode, String replaceModeReplacement, boolean logCatches) {
        this.id = id;
        this.scanRegexPattern = scanRegexPattern;
        this.scanMode = scanMode;
        this.entries = entries;
        this.modifyMode = modifyMode;
        this.replaceModeReplacement = replaceModeReplacement;
        this.logCatches = logCatches;
    }

    public String getId() {
        return id;
    }

    public String getScanRegexPattern() {
        return scanRegexPattern;
    }

    public ScanMode getScanMode() {
        return scanMode;
    }

    public ArrayList<String> getEntries() {
        return entries;
    }

    public ModifyMode getModifyMode() {
        return modifyMode;
    }

    public String getReplaceModeReplacement() {
        return replaceModeReplacement;
    }

    public boolean shouldLogCatches() {
        return logCatches;
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("scan-regex-pattern", scanRegexPattern);
        map.put("scan-mode", scanMode.get());
        if (scanMode.equals(ScanMode.REGEXWITHENTRIES)) {
            map.put("entries", entries);
        }
        map.put("modify-mode", modifyMode.get());
        if (modifyMode.equals(ModifyMode.REPLACE)) {
            map.put("replacement", replaceModeReplacement);
        }
        Map<String, Object> logging = new HashMap<>();
        logging.put("enabled", logCatches);
        map.put("logging", logging);
        return map;
    }

    public static TextProcessor deserialize(Map<String, Object> map) {
        return new TextProcessor(
                (String) map.get("id"),
                (String) map.get("scan-regex-pattern"),
                getScanModeByName((String) map.get("scan-mode")),
                findEntriesOrNull(map),
                getModifyModeByName((String) map.get("modify-mode")),
                findReplacementOrNull(map),
                shouldLog(map)
        );
    }

    public static ScanMode getScanModeByName(String s) {
        return Stream.of(ScanMode.values()).filter(sm -> sm.get().equals(s)).findFirst().orElse(null);
    }

    public static ModifyMode getModifyModeByName(String s) {
        return Stream.of(ModifyMode.values()).filter(mm -> mm.get().equals(s)).findFirst().orElse(null);
    }

    public static ArrayList<String> findEntriesOrNull(Map<String, Object> map) {
        if (map.containsKey("entries")) {
            return (ArrayList<String>) map.get("entries");
        }
        return null;
    }

    public static String findReplacementOrNull(Map<String, Object> map) {
        if (map.containsKey("replacement")) {
            return (String) map.get("replacement");
        }
        return null;
    }

    public static boolean shouldLog(Map<String, Object> map) {
        if (map.containsKey("logging")) {
            Map<String, Object> map2 = (Map<String, Object>) map.get("logging");
            if (map2.containsKey("enabled")) {
                return (boolean) map2.get("enabled");
            }
        }
        return false;
    }

    public ArrayList<Pattern> getPatterns() {
        ArrayList<Pattern> patterns = new ArrayList<>();
        if (scanMode.equals(ScanMode.REGEXWITHENTRIES)) {
            for (String entry : entries) {
                patterns.add(Pattern.compile(scanRegexPattern.replace("[ENTRY]", entry)));
            }
        } else {
            patterns.add(Pattern.compile(scanRegexPattern));
        }
        return patterns;
    }
}
