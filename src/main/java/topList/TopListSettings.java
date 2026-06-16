package topList;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TopListSettings {

    private final HologramOptions hologramOptions;
    private final Map<TopListType, LeaderboardSettings> leaderboards;
    private final Map<String, String> messages;

    private TopListSettings(
            HologramOptions hologramOptions,
            Map<TopListType, LeaderboardSettings> leaderboards,
            Map<String, String> messages
    ) {
        this.hologramOptions = hologramOptions;
        this.leaderboards = leaderboards;
        this.messages = messages;
    }

    static TopListSettings from(FileConfiguration config) {
        boolean fixedRotation = config.getBoolean("hologram.fixed-rotation", true);
        HologramOptions hologramOptions = new HologramOptions(
                Math.max(1, config.getInt("hologram.visibility-distance", 64)),
                fixedRotation ? Display.Billboard.FIXED : parseEnum(Display.Billboard.class, config.getString("hologram.billboard", "CENTER"), Display.Billboard.CENTER),
                parseEnum(TextDisplay.TextAlignment.class, config.getString("hologram.text-alignment", "CENTER"), TextDisplay.TextAlignment.CENTER),
                config.getBoolean("hologram.text-shadow", true),
                config.getBoolean("hologram.see-through", false),
                config.getBoolean("hologram.double-sided", false)
        );

        Map<TopListType, LeaderboardSettings> leaderboards = new EnumMap<>(TopListType.class);
        for (TopListType type : TopListType.values()) {
            leaderboards.put(type, readLeaderboard(config, type));
        }

        return new TopListSettings(hologramOptions, leaderboards, readMessages(config));
    }

    private static LeaderboardSettings readLeaderboard(FileConfiguration config, TopListType type) {
        String path = type.configPath();
        String decimalPattern = config.getString(path + ".balance-decimal-format", "#,##0.00");
        DecimalFormat balanceFormat = new DecimalFormat(decimalPattern, DecimalFormatSymbols.getInstance(Locale.US));

        return new LeaderboardSettings(
                type,
                config.getString(path + ".save-folder", type.defaultFolder()),
                config.getString(path + ".fancy-name-prefix", type.defaultFancyNamePrefix()),
                Math.max(10, config.getInt(path + ".update-interval-seconds", 60)),
                config.getBoolean(path + ".show-update-timer", true),
                Math.max(1, config.getInt(path + ".max-entries", 20)),
                config.getBoolean(path + ".include-offline-players", config.getBoolean("settings.include-offline-players", true)),
                config.getBoolean(path + ".require-economy-account", true),
                config.getBoolean(path + ".hide-zero-values", true),
                config.getBoolean(path + ".show-empty-ranks", false),
                config.getString(path + ".player-name-fallback", "Unbekannt"),
                balanceFormat,
                config.getBoolean(path + ".use-vault-format", false),
                config.getString(path + ".value-format", "{days}d {hours}h {minutes}m"),
                config.getBoolean(path + ".placeholderapi.enabled", true),
                config.getString(path + ".placeholderapi.value-placeholder", ""),
                config.getBoolean(path + ".placeholderapi.parse-entry-line", true),
                copyOrDefault(config.getStringList(path + ".lines"), List.of("{entries}")),
                config.getString(path + ".entry-line", "&a#{platz} &e{spieler} &fhat &a{wert}&8."),
                config.getString(path + ".empty-line", "&8#{platz} &7-"),
                config.getString(path + ".no-data-line", "&cKeine Statistiken für Spieler gefunden."),
                config.getString(path + ".timer-line", "&7Aktualisierung in &e{time}")
        );
    }

    private static Map<String, String> readMessages(FileConfiguration config) {
        Map<String, String> values = new HashMap<>();
        if (config.isConfigurationSection("messages")) {
            for (String key : config.getConfigurationSection("messages").getKeys(false)) {
                values.put(key, config.getString("messages." + key, ""));
            }
        }
        return values;
    }

    private static List<String> copyOrDefault(List<String> values, List<String> fallback) {
        if (values == null || values.isEmpty()) {
            return List.copyOf(fallback);
        }
        return List.copyOf(values);
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, T fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    HologramOptions hologramOptions() {
        return hologramOptions;
    }

    LeaderboardSettings leaderboard(TopListType type) {
        return leaderboards.get(type);
    }

    String message(String key) {
        return message(key, Map.of());
    }

    String message(String key, Map<String, String> replacements) {
        String prefix = messages.getOrDefault("prefix", "");
        String message = messages.getOrDefault(key, key).replace("{prefix}", prefix);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }
}
