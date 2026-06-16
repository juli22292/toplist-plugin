package topList;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TutorialSettings {

    private final String saveFolder;
    private final String fancyNamePrefix;
    private final HologramOptions hologramOptions;
    private final Map<String, TutorialTemplate> templates;
    private final Map<String, String> messages;

    private TutorialSettings(
            String saveFolder,
            String fancyNamePrefix,
            HologramOptions hologramOptions,
            Map<String, TutorialTemplate> templates,
            Map<String, String> messages
    ) {
        this.saveFolder = saveFolder;
        this.fancyNamePrefix = fancyNamePrefix;
        this.hologramOptions = hologramOptions;
        this.templates = templates;
        this.messages = messages;
    }

    static TutorialSettings load(JavaPlugin plugin) {
        return load(plugin, "tutorialconfig.yml", "tutorialhologramme", "tutorial_");
    }

    static TutorialSettings load(JavaPlugin plugin, String resourceName, String defaultSaveFolder, String defaultFancyNamePrefix) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
        }

        YamlConfiguration config = new YamlConfiguration();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            config.load(reader);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Could not load " + resourceName + ": " + exception.getMessage());
        }

        boolean fixedRotation = config.getBoolean("hologram.fixed-rotation", true);
        HologramOptions options = new HologramOptions(
                Math.max(1, config.getInt("hologram.visibility-distance", 64)),
                fixedRotation ? Display.Billboard.FIXED : parseEnum(Display.Billboard.class, config.getString("hologram.billboard", "CENTER"), Display.Billboard.CENTER),
                parseEnum(TextDisplay.TextAlignment.class, config.getString("hologram.text-alignment", "CENTER"), TextDisplay.TextAlignment.CENTER),
                config.getBoolean("hologram.text-shadow", true),
                config.getBoolean("hologram.see-through", false),
                config.getBoolean("hologram.double-sided", false)
        );

        return new TutorialSettings(
                config.getString("settings.save-folder", defaultSaveFolder),
                config.getString("settings.fancy-name-prefix", defaultFancyNamePrefix),
                options,
                readTemplates(config),
                readMessages(config)
        );
    }

    String saveFolder() {
        return saveFolder;
    }

    String fancyNamePrefix() {
        return fancyNamePrefix;
    }

    HologramOptions hologramOptions() {
        return hologramOptions;
    }

    Map<String, TutorialTemplate> templates() {
        return templates;
    }

    TutorialTemplate template(String name) {
        if (name == null) {
            return null;
        }
        return templates.get(name.toLowerCase(Locale.ROOT));
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

    private static Map<String, TutorialTemplate> readTemplates(YamlConfiguration config) {
        Map<String, TutorialTemplate> templates = new LinkedHashMap<>();
        if (!config.isConfigurationSection("holograms")) {
            return templates;
        }

        for (String name : config.getConfigurationSection("holograms").getKeys(false)) {
            String path = "holograms." + name;
            if (!HologramStore.isValidName(name)) {
                continue;
            }

            String displayName = config.getString(path + ".display-name", "&a" + name);
            Material icon = Material.matchMaterial(config.getString(path + ".icon", "BOOK"));
            if (icon == null || !icon.isItem()) {
                icon = Material.BOOK;
            }

            List<String> lines = config.getStringList(path + ".lines");
            if (lines.isEmpty()) {
                lines = List.of(displayName);
            }

            TutorialTemplate template = new TutorialTemplate(name, displayName, icon, List.copyOf(lines));
            templates.put(name.toLowerCase(Locale.ROOT), template);
        }
        return templates;
    }

    private static Map<String, String> readMessages(YamlConfiguration config) {
        Map<String, String> values = new HashMap<>();
        if (config.isConfigurationSection("messages")) {
            for (String key : config.getConfigurationSection("messages").getKeys(false)) {
                values.put(key, config.getString("messages." + key, ""));
            }
        }
        return values;
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
}
