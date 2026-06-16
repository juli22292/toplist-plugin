package topList;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
import java.util.ArrayList;
import java.util.List;
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
        if (!enabled()) {
            return;
        }

        long intervalTicks = Math.max(60L, config.getLong("check-interval-seconds", 7200L)) * 20L;
        long firstDelay = config.getBoolean("check-on-startup", true) ? 40L : intervalTicks;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> check(false), firstDelay, intervalTicks);
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

        if (config.getBoolean("notify-console", true)) {
            Bukkit.getConsoleSender().sendMessage(component(config.getString("messages.checking", "Suche nach TopList Updates...")));
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
                UpdateInfo updateInfo = parseUpdateInfo(reader.lines().toList());
                if (updateInfo.latest().isBlank()) {
                    return UpdateResult.failed(current, "latest.txt ist leer");
                }
                return new UpdateResult(current, updateInfo.latest(), compareVersions(updateInfo.latest(), current) > 0, null, updateInfo.sources());
            }
        } catch (IllegalArgumentException | IOException exception) {
            return UpdateResult.failed(current, exception.getMessage());
        }
    }

    private void handleResult(UpdateResult result, boolean manual) {
        if (!result.success()) {
            if (config.getBoolean("notify-console", true)) {
                Bukkit.getConsoleSender().sendMessage(component(config.getString(
                        "messages.connection-failed",
                        "Es sieht so aus als ob du eine Instabile Internetverbindung hast..."
                )));
            }
            return;
        }

        if (!result.updateAvailable()) {
            if (config.getBoolean("notify-console", true)) {
                Bukkit.getConsoleSender().sendMessage(component(config.getString(
                        "messages.no-update",
                        "Keine Neuere Version gefunden, du bist auf der aktuellsten!"
                )));
            }
            return;
        }

        if (config.getBoolean("notify-console", true)) {
            notifyConsole(result);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            String permission = config.getString("notify-permission", "topliste.admin");
            if (player.hasPermission(permission) || player.hasPermission("topliste.*")) {
                notifyPlayer(player, result);
            }
        }
    }

    private void notifyPlayer(Player player, UpdateResult result) {
        for (String line : config.getStringList("messages.update-available")) {
            player.sendMessage(renderLine(line, result, true));
        }
    }

    private void notifyConsole(UpdateResult result) {
        for (String line : config.getStringList("messages.update-available")) {
            Bukkit.getConsoleSender().sendMessage(renderLine(line, result, false));
        }
    }

    private Component renderLine(String line, UpdateResult result, boolean clickable) {
        String message = placeholders(line, result);
        Component output = Component.empty();
        int cursor = 0;
        int placeholderIndex;
        while ((placeholderIndex = message.indexOf("{downloads}", cursor)) >= 0) {
            output = output.append(component(message.substring(cursor, placeholderIndex)));
            output = output.append(downloads(result, clickable));
            cursor = placeholderIndex + "{downloads}".length();
        }
        output = output.append(component(message.substring(cursor)));
        return output.decoration(TextDecoration.ITALIC, false);
    }

    private String placeholders(String message, UpdateResult result) {
        return message
                .replace("{current}", result.current())
                .replace("{latest}", result.latest())
                .replace("{version}", result.current())
                .replace("{neue_version}", result.latest())
                .replace("{neueste_version}", result.latest())
                .replace("{prefix}", config.getString("prefix", ""));
    }

    private Component downloads(UpdateResult result, boolean clickable) {
        List<DownloadSource> sources = result.sources();
        if (sources.isEmpty()) {
            String fallback = config.getString("download-url", "");
            return component("&9" + (fallback.isBlank() ? "GitHub" : fallback));
        }

        Component output = Component.empty();
        for (int index = 0; index < sources.size(); index++) {
            DownloadSource source = sources.get(index);
            Component name = component("&9" + source.name());
            if (clickable && !source.url().isBlank()) {
                name = name.clickEvent(ClickEvent.openUrl(source.url()));
            }
            output = output.append(name);
            if (index + 1 < sources.size()) {
                output = output.append(component("&7, "));
            }
        }
        return output.decoration(TextDecoration.ITALIC, false);
    }

    private UpdateInfo parseUpdateInfo(List<String> lines) {
        String latest = "";
        List<DownloadSource> sources = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int separator = line.indexOf('=');
            if (separator > 0) {
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                if (isVersionKey(key)) {
                    latest = value;
                } else {
                    sources.add(new DownloadSource(key, value));
                }
                continue;
            }

            if (latest.isBlank()) {
                latest = line;
            }
        }

        return new UpdateInfo(latest, List.copyOf(sources));
    }

    private boolean isVersionKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.equals("version")
                || normalized.equals("latest")
                || normalized.equals("latest-version")
                || normalized.equals("neueste-version");
    }

    private boolean enabled() {
        return config != null && config.getBoolean("enabled", true);
    }

    private Component component(String text) {
        String value = text == null ? "" : text;
        boolean strikeout = value.contains("<strikeout>");
        value = value.replace("<strikeout>", "").replace("</strikeout>", "");
        Component component = LEGACY.deserialize(value).decoration(TextDecoration.ITALIC, false);
        if (strikeout) {
            component = component.decoration(TextDecoration.STRIKETHROUGH, true);
        }
        return component;
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

    private record UpdateInfo(String latest, List<DownloadSource> sources) {
    }

    private record DownloadSource(String name, String url) {
    }

    private record UpdateResult(String current, String latest, boolean updateAvailable, String error, List<DownloadSource> sources) {

        static UpdateResult failed(String current, String error) {
            return new UpdateResult(current, "", false, error, List.of());
        }

        boolean success() {
            return error == null;
        }
    }
}
