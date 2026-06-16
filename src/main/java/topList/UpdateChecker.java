package topList;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class UpdateChecker implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private volatile UpdateResult lastResult;
    private volatile boolean checkRunning;

    UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        File file = new File(plugin.getDataFolder(), "updates.yml");
        if (!file.exists()) {
            plugin.saveResource("updates.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    void start() {
        if (!enabled() || !config.getBoolean("check-on-startup", true)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> check(false), 40L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled() || !config.getBoolean("check-on-join", true)) {
            return;
        }

        Player player = event.getPlayer();
        String permission = config.getString("notify-permission", "topliste.admin");
        if (!player.hasPermission(permission) && !player.hasPermission("topliste.*")) {
            return;
        }

        UpdateResult result = lastResult;
        if (result != null && result.updateAvailable()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> notifyPlayer(player, result), 30L);
        }
    }

    private void check(boolean manual) {
        if (checkRunning) {
            return;
        }

        checkRunning = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UpdateResult result = fetchLatest();
            lastResult = result;
            checkRunning = false;

            Bukkit.getScheduler().runTask(plugin, () -> handleResult(result, manual));
        });
    }

    private UpdateResult fetchLatest() {
        String current = plugin.getPluginMeta().getVersion();
        String latestUrl = config.getString("latest-url", "");
        int timeout = Math.max(1000, config.getInt("timeout-millis", 5000));

        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(latestUrl).toURL().openConnection();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestProperty("User-Agent", "TopList-Updater/" + current);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return UpdateResult.failed(current, "HTTP " + status);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String latest = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .filter(line -> !line.startsWith("#"))
                        .findFirst()
                        .orElse("");
                if (latest.isBlank()) {
                    return UpdateResult.failed(current, "latest.txt ist leer");
                }
                return new UpdateResult(current, latest, compareVersions(latest, current) > 0, null);
            }
        } catch (IllegalArgumentException | IOException exception) {
            return UpdateResult.failed(current, exception.getMessage());
        }
    }

    private void handleResult(UpdateResult result, boolean manual) {
        if (!result.success()) {
            if (manual || config.getBoolean("notify-console", true)) {
                plugin.getLogger().warning(configMessage("messages.check-failed", result)
                        .replace("{error}", result.error() == null ? "Unbekannter Fehler" : result.error()));
            }
            return;
        }

        if (!result.updateAvailable()) {
            if (manual || config.getBoolean("log-up-to-date", false)) {
                plugin.getLogger().info(configMessage("messages.up-to-date", result));
            }
            return;
        }

        if (config.getBoolean("notify-console", true)) {
            plugin.getLogger().warning(configMessage("messages.console-update", result));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            String permission = config.getString("notify-permission", "topliste.admin");
            if (player.hasPermission(permission) || player.hasPermission("topliste.*")) {
                notifyPlayer(player, result);
            }
        }
    }

    private void notifyPlayer(Player player, UpdateResult result) {
        player.sendMessage(component(configMessage("messages.player-update", result)));
    }

    private String configMessage(String path, UpdateResult result) {
        String message = config.getString(path, "");
        return message
                .replace("{current}", result.current())
                .replace("{latest}", result.latest())
                .replace("{download}", config.getString("download-url", ""));
    }

    private boolean enabled() {
        return config != null && config.getBoolean("enabled", true);
    }

    private Component component(String text) {
        return LEGACY.deserialize(text == null ? "" : text).decoration(TextDecoration.ITALIC, false);
    }

    private int compareVersions(String left, String right) {
        int[] leftParts = versionParts(left);
        int[] rightParts = versionParts(right);
        int max = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < max; index++) {
            int leftPart = index < leftParts.length ? leftParts[index] : 0;
            int rightPart = index < rightParts.length ? rightParts[index] : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private int[] versionParts(String version) {
        String normalized = version == null ? "" : version.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }

        String[] tokens = normalized.split("[.-]");
        int[] parts = new int[tokens.length];
        for (int index = 0; index < tokens.length; index++) {
            parts[index] = leadingNumber(tokens[index]);
        }
        return parts;
    }

    private int leadingNumber(String token) {
        StringBuilder number = new StringBuilder();
        for (char character : token.toCharArray()) {
            if (!Character.isDigit(character)) {
                break;
            }
            number.append(character);
        }
        if (number.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(number.toString());
    }

    private record UpdateResult(String current, String latest, boolean updateAvailable, String error) {

        static UpdateResult failed(String current, String error) {
            return new UpdateResult(current, "", false, error);
        }

        boolean success() {
            return error == null;
        }
    }
}
