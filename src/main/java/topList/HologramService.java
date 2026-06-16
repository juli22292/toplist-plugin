package topList;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class HologramService {

    private final JavaPlugin plugin;
    private final TopListType type;
    private final LeaderboardProvider provider;
    private final Map<String, StoredHologram> storedHolograms;
    private final Map<String, List<Hologram>> activeHolograms;

    private TopListSettings settings;
    private LeaderboardSettings leaderboardSettings;
    private HologramStore store;
    private LeaderboardRenderer renderer;
    private List<LeaderboardEntry> cachedEntries = List.of();
    private long nextUpdateAtMillis;
    private boolean refreshInProgress;

    HologramService(JavaPlugin plugin, TopListType type, LeaderboardProvider provider) {
        this.plugin = plugin;
        this.type = type;
        this.provider = provider;
        this.storedHolograms = new LinkedHashMap<>();
        this.activeHolograms = new LinkedHashMap<>();
    }

    void load(TopListSettings settings) {
        this.settings = settings;
        this.leaderboardSettings = settings.leaderboard(type);
        this.store = new HologramStore(plugin, leaderboardSettings.saveFolder());
        this.renderer = new LeaderboardRenderer(
                leaderboardSettings,
                provider,
                new PlaceholderResolver(plugin, leaderboardSettings.placeholderApiEnabled())
        );

        refreshInProgress = false;
        storedHolograms.clear();
        activeHolograms.clear();

        for (StoredHologram hologram : store.loadAll(leaderboardSettings.fancyNamePrefix())) {
            storedHolograms.put(key(hologram.name()), hologram);
            spawn(hologram);
        }
    }

    void reload(TopListSettings settings) {
        despawnAll();
        load(settings);
        updateAllHolograms();
    }

    void shutdown() {
        despawnAll();
    }

    boolean create(String name, Location location) {
        return create(name, location, false);
    }

    boolean create(String name, Location location, boolean useCurrentPitch) {
        if (!HologramStore.isValidName(name) || storedHolograms.containsKey(key(name))) {
            return false;
        }

        StoredHologram stored = StoredHologram.fromLocation(name, leaderboardSettings.fancyNamePrefix() + name, location, useCurrentPitch);
        storedHolograms.put(key(name), stored);
        store.save(stored);
        spawn(stored);
        updateHologram(name);
        return true;
    }

    boolean delete(String name) {
        StoredHologram stored = storedHolograms.remove(key(name));
        if (stored == null) {
            return false;
        }

        removeActive(name);
        store.delete(stored);
        return true;
    }

    boolean refresh(String name) {
        if (!activeHolograms.containsKey(key(name))) {
            return false;
        }

        refreshEntriesQueued();
        updateHologram(name);
        return true;
    }

    void refreshAll() {
        if (!hasHolograms()) {
            return;
        }
        refreshEntriesQueued();
    }

    boolean moveHere(String name, Location location) {
        return moveHere(name, location, true);
    }

    boolean moveHere(String name, Location location, boolean useCurrentPitch) {
        StoredHologram stored = storedHolograms.get(key(name));
        if (stored == null) {
            return false;
        }

        stored.setLocation(location, useCurrentPitch);
        store.save(stored);
        removeActive(name);
        spawn(stored);
        return true;
    }

    List<String> previewLines() {
        return renderer.renderLines(cachedEntries, secondsUntilUpdate());
    }

    Collection<StoredHologram> holograms() {
        return List.copyOf(storedHolograms.values());
    }

    StoredHologram hologram(String name) {
        return storedHolograms.get(key(name));
    }

    LeaderboardSettings leaderboardSettings() {
        return leaderboardSettings;
    }

    boolean isKnown(String name) {
        return storedHolograms.containsKey(key(name));
    }

    TopListType type() {
        return type;
    }

    boolean hasHolograms() {
        return !storedHolograms.isEmpty();
    }

    boolean doubleSidedHolograms() {
        return settings.hologramOptions().doubleSided();
    }

    CompletableFuture<Void> refreshEntriesQueued() {
        if (refreshInProgress) {
            return CompletableFuture.completedFuture(null);
        }

        refreshInProgress = true;
        CompletableFuture<Void> future = new CompletableFuture<>();
        provider.entriesAsync(leaderboardSettings).whenComplete((entries, exception) -> {
            Runnable apply = () -> {
                refreshInProgress = false;
                if (exception != null) {
                    plugin.getLogger().warning("Could not refresh " + type.argument() + " leaderboard: " + exception.getMessage());
                    future.complete(null);
                    return;
                }

                cachedEntries = entries == null ? List.of() : List.copyOf(entries);
                updateAllHolograms();
                future.complete(null);
            };

            if (Bukkit.isPrimaryThread()) {
                apply.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, apply);
            }
        });
        return future;
    }

    void updateTimer(long nextUpdateAtMillis) {
        this.nextUpdateAtMillis = nextUpdateAtMillis;
        if (leaderboardSettings.showUpdateTimer()) {
            updateAllHolograms();
        }
    }

    long updateIntervalMillis() {
        return leaderboardSettings.updateIntervalSeconds() * 1_000L;
    }

    private void spawn(StoredHologram stored) {
        Location location = stored.toLocation();
        if (location == null) {
            plugin.getLogger().warning("Could not spawn " + type.argument() + " toplist hologram '" + stored.name() + "': world '" + stored.world() + "' is not loaded.");
            return;
        }

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        activeHolograms.put(key(stored.name()), FancyTextHolograms.spawn(manager, stored.fancyName(), location, settings.hologramOptions(), renderedLines()));
    }

    private void updateHologram(String name) {
        List<Hologram> holograms = activeHolograms.get(key(name));
        if (holograms != null) {
            FancyTextHolograms.update(holograms, renderedLines());
        }
    }

    private void updateAllHolograms() {
        if (activeHolograms.isEmpty()) {
            return;
        }

        List<String> lines = renderedLines();
        for (List<Hologram> holograms : activeHolograms.values()) {
            FancyTextHolograms.update(holograms, lines);
        }
    }

    private List<String> renderedLines() {
        return renderer.renderLines(cachedEntries, secondsUntilUpdate());
    }

    private void remove(Hologram hologram) {
        try {
            FancyHologramsPlugin.get().getHologramManager().removeHologram(hologram);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not remove " + type.argument() + " toplist hologram '" + hologram.getName() + "': " + exception.getMessage());
        }
    }

    private void removeActive(String name) {
        List<Hologram> active = activeHolograms.remove(key(name));
        if (active != null) {
            for (Hologram hologram : active) {
                remove(hologram);
            }
        }
    }

    private void despawnAll() {
        for (List<Hologram> holograms : activeHolograms.values()) {
            for (Hologram hologram : holograms) {
                remove(hologram);
            }
        }
        activeHolograms.clear();
    }

    private long secondsUntilUpdate() {
        return Math.max(0L, (nextUpdateAtMillis - System.currentTimeMillis() + 999L) / 1_000L);
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
