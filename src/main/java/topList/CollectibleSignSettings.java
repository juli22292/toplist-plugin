package topList;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
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

final class CollectibleSignSettings {

    private final String guiTitle;
    private final String itemName;
    private final List<String> itemLore;
    private final boolean rewardEnabled;
    private final String rewardAmount;
    private final String rewardCommand;
    private final Map<String, CollectibleSignTemplate> signs;
    private final Map<String, String> messages;

    private CollectibleSignSettings(
            String guiTitle,
            String itemName,
            List<String> itemLore,
            boolean rewardEnabled,
            String rewardAmount,
            String rewardCommand,
            Map<String, CollectibleSignTemplate> signs,
            Map<String, String> messages
    ) {
        this.guiTitle = guiTitle;
        this.itemName = itemName;
        this.itemLore = itemLore;
        this.rewardEnabled = rewardEnabled;
        this.rewardAmount = rewardAmount;
        this.rewardCommand = rewardCommand;
        this.signs = signs;
        this.messages = messages;
    }

    static CollectibleSignSettings load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "signconfig.yml");
        if (!file.exists()) {
            plugin.saveResource("signconfig.yml", false);
        }

        YamlConfiguration config = new YamlConfiguration();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            config.load(reader);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Could not load signconfig.yml: " + exception.getMessage());
        }

        return new CollectibleSignSettings(
                config.getString("gui.title", "&8LAdvancement Schilder"),
                config.getString("item.name", "&6Sammel-Schild: &e{name}"),
                copyOrDefault(config.getStringList("item.lore"), List.of(
                        "&7Platziere dieses Schild,",
                        "&7damit Spieler es sammeln können.",
                        "",
                        "&8Vorlage: &f{id}"
                )),
                config.getBoolean("reward.enabled", false),
                config.getString("reward.amount", "100"),
                config.getString("reward.command", "economy give {spieler} {amount}"),
                readSigns(config, config.getString("reward.amount", "100")),
                readMessages(config)
        );
    }

    String guiTitle() {
        return guiTitle;
    }

    String itemName() {
        return itemName;
    }

    List<String> itemLore() {
        return itemLore;
    }

    boolean rewardEnabled() {
        return rewardEnabled;
    }

    String rewardAmount() {
        return rewardAmount;
    }

    String rewardCommand() {
        return rewardCommand;
    }

    Map<String, CollectibleSignTemplate> signs() {
        return signs;
    }

    CollectibleSignTemplate sign(String name) {
        if (name == null) {
            return null;
        }
        return signs.get(name.toLowerCase(Locale.ROOT));
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

    private static Map<String, CollectibleSignTemplate> readSigns(YamlConfiguration config, String defaultRewardAmount) {
        Map<String, CollectibleSignTemplate> signs = new LinkedHashMap<>();
        if (!config.isConfigurationSection("signs")) {
            return signs;
        }

        for (String name : config.getConfigurationSection("signs").getKeys(false)) {
            if (!HologramStore.isValidName(name)) {
                continue;
            }

            String path = "signs." + name;
            Material material = Material.matchMaterial(config.getString(path + ".material", "OAK_SIGN"));
            if (material == null || !material.isItem() || !material.name().endsWith("SIGN")) {
                material = Material.OAK_SIGN;
            }

            String displayName = config.getString(path + ".display-name", "&a" + name);
            List<String> lines = copyOrDefault(config.getStringList(path + ".lines"), List.of(displayName));
            List<String> guiLore = copyOrDefault(config.getStringList(path + ".gui-lore"), List.of("&7Ein sammelbares Schild."));
            String rewardAmount = config.getString(path + ".reward-amount", config.getString(path + ".amount", defaultRewardAmount));

            signs.put(name.toLowerCase(Locale.ROOT), new CollectibleSignTemplate(
                    name,
                    displayName,
                    material,
                    List.copyOf(lines),
                    List.copyOf(guiLore),
                    rewardAmount
            ));
        }
        return signs;
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

    private static List<String> copyOrDefault(List<String> values, List<String> fallback) {
        if (values == null || values.isEmpty()) {
            return List.copyOf(fallback);
        }
        return List.copyOf(values);
    }
}
