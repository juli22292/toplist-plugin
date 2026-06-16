package topList;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class TopListManager {

    private final JavaPlugin plugin;
    private final Map<TopListType, HologramService> services;
    private TopListSettings settings;
    private DatabaseSettings databaseSettings;
    private long nextUpdateAtMillis;
    private int updateTaskId = -1;
    private boolean refreshQueueRunning;

    TopListManager(JavaPlugin plugin, Economy economy) {
        this.plugin = plugin;
        this.services = new EnumMap<>(TopListType.class);
        services.put(TopListType.MONEY, new HologramService(plugin, TopListType.MONEY, new MoneyLeaderboardProvider(economy)));
        services.put(TopListType.PLAYTIME, new HologramService(plugin, TopListType.PLAYTIME, new PlaytimeLeaderboardProvider()));
        services.put(TopListType.KILLS, new HologramService(plugin, TopListType.KILLS, databaseBacked(TopListType.KILLS, StatisticLeaderboardProvider.kills())));
        services.put(TopListType.WALKED, new HologramService(plugin, TopListType.WALKED, databaseBacked(TopListType.WALKED, StatisticLeaderboardProvider.walkedBlocks())));
        services.put(TopListType.MINED, new HologramService(plugin, TopListType.MINED, databaseBacked(TopListType.MINED, StatisticLeaderboardProvider.minedBlocks())));
    }

    void load() {
        settings = TopListSettings.from(plugin.getConfig());
        databaseSettings = DatabaseSettings.load(plugin);
        migrateLegacyMoneyFolder();
        for (HologramService service : services.values()) {
            service.load(settings);
        }
        startScheduler();
        startRefreshQueue();
    }

    void reload() {
        stopScheduler();
        refreshQueueRunning = false;
        plugin.reloadConfig();
        settings = TopListSettings.from(plugin.getConfig());
        databaseSettings = DatabaseSettings.load(plugin);
        migrateLegacyMoneyFolder();
        for (HologramService service : services.values()) {
            service.reload(settings);
        }
        startScheduler();
        startRefreshQueue();
    }

    void shutdown() {
        stopScheduler();
        refreshQueueRunning = false;
        for (HologramService service : services.values()) {
            service.shutdown();
        }
    }

    HologramService service(TopListType type) {
        return services.get(type);
    }

    TopListSettings settings() {
        return settings;
    }

    boolean doubleSidedHolograms() {
        return settings.hologramOptions().doubleSided();
    }

    void setDoubleSidedHolograms(boolean enabled) {
        plugin.getConfig().set("hologram.double-sided", enabled);
        plugin.saveConfig();
        reload();
    }

    boolean refreshCreatedToplists() {
        List<HologramService> queue = createdToplistServices();
        if (queue.isEmpty()) {
            return false;
        }

        startRefreshQueue(queue);
        return true;
    }

    private void migrateLegacyMoneyFolder() {
        Path dataFolder = plugin.getDataFolder().toPath();
        Path oldFolder = dataFolder.resolve("holograme");
        Path moneyFolder = dataFolder.resolve(settings.leaderboard(TopListType.MONEY).saveFolder());

        if (!Files.isDirectory(oldFolder) || Files.exists(moneyFolder)) {
            return;
        }

        try {
            Files.move(oldFolder, moneyFolder);
            plugin.getLogger().info("Moved legacy holograme folder to " + moneyFolder.getFileName() + ".");
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not move legacy holograme folder to moneyhologramme: " + exception.getMessage());
        }
    }

    private LeaderboardProvider databaseBacked(TopListType type, LeaderboardProvider fallback) {
        return new DatabaseBackedLeaderboardProvider(plugin, type, fallback, () -> databaseSettings);
    }

    private void startScheduler() {
        stopScheduler();
        nextUpdateAtMillis = System.currentTimeMillis() + updateIntervalMillis();
        updateTimers();
        updateTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
    }

    private void stopScheduler() {
        if (updateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
            updateTaskId = -1;
        }
    }

    private void tick() {
        updateTimers();
        if (!refreshQueueRunning && System.currentTimeMillis() >= nextUpdateAtMillis) {
            startRefreshQueue();
        }
    }

    private void startRefreshQueue() {
        startRefreshQueue(createdToplistServices());
    }

    private void startRefreshQueue(List<HologramService> queue) {
        if (refreshQueueRunning) {
            return;
        }
        if (queue.isEmpty()) {
            nextUpdateAtMillis = System.currentTimeMillis() + updateIntervalMillis();
            updateTimers();
            return;
        }

        refreshQueueRunning = true;
        nextUpdateAtMillis = System.currentTimeMillis();
        updateTimers();
        processRefreshQueue(queue, 0);
    }

    private void processRefreshQueue(List<HologramService> queue, int index) {
        if (!refreshQueueRunning) {
            return;
        }

        if (index >= queue.size()) {
            refreshQueueRunning = false;
            nextUpdateAtMillis = System.currentTimeMillis() + updateIntervalMillis();
            updateTimers();
            return;
        }

        HologramService service = queue.get(index);
        service.refreshEntriesQueued().whenComplete((ignored, exception) -> Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> processRefreshQueue(queue, index + 1),
                1L
        ));
    }

    private void updateTimers() {
        for (HologramService service : services.values()) {
            service.updateTimer(nextUpdateAtMillis);
        }
    }

    private long updateIntervalMillis() {
        long interval = Long.MAX_VALUE;
        for (HologramService service : services.values()) {
            if (!service.hasHolograms()) {
                continue;
            }
            interval = Math.min(interval, service.updateIntervalMillis());
        }
        if (interval == Long.MAX_VALUE) {
            return 60_000L;
        }
        return Math.max(10_000L, interval);
    }

    private List<HologramService> createdToplistServices() {
        List<HologramService> queue = new ArrayList<>();
        for (HologramService service : services.values()) {
            if (service.hasHolograms()) {
                queue.add(service);
            }
        }
        return queue;
    }
}
