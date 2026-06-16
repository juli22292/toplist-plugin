package topList;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TutorialHologramService {

    private final JavaPlugin plugin;
    private final String resourceName;
    private final String defaultSaveFolder;
    private final String defaultFancyNamePrefix;
    private final String logName;
    private final Map<String, StoredHologram> storedHolograms;
    private final Map<String, List<Hologram>> activeHolograms;

    private TutorialSettings settings;
    private HologramStore store;

    TutorialHologramService(JavaPlugin plugin) {
        this(plugin, "tutorialconfig.yml", "tutorialhologramme", "tutorial_", "tutorial");
    }

    TutorialHologramService(JavaPlugin plugin, String resourceName, String defaultSaveFolder, String defaultFancyNamePrefix, String logName) {
        this.plugin = plugin;
        this.resourceName = resourceName;
        this.defaultSaveFolder = defaultSaveFolder;
        this.defaultFancyNamePrefix = defaultFancyNamePrefix;
        this.logName = logName;
        this.storedHolograms = new LinkedHashMap<>();
        this.activeHolograms = new LinkedHashMap<>();
    }

    void load() {
        settings = TutorialSettings.load(plugin, resourceName, defaultSaveFolder, defaultFancyNamePrefix);
        store = new HologramStore(plugin, settings.saveFolder());
        storedHolograms.clear();
        activeHolograms.clear();

        for (StoredHologram hologram : store.loadAll(settings.fancyNamePrefix())) {
            storedHolograms.put(key(hologram.name()), hologram);
            spawnStored(hologram);
        }
    }

    void reload() {
        despawnAll();
        load();
    }

    void shutdown() {
        despawnAll();
    }

    TutorialActionResult create(String templateName, Location location) {
        return create(templateName, nextInstanceName(templateName), location);
    }

    TutorialActionResult create(String templateName, String instanceName, Location location) {
        return create(templateName, instanceName, location, false);
    }

    TutorialActionResult create(String templateName, String instanceName, Location location, boolean useCurrentPitch) {
        if (!HologramStore.isValidName(templateName) || !HologramStore.isValidName(instanceName)) {
            return TutorialActionResult.INVALID_NAME;
        }
        if (settings.template(templateName) == null) {
            return TutorialActionResult.TEMPLATE_NOT_FOUND;
        }
        if (isKnown(instanceName)) {
            return TutorialActionResult.ALREADY_EXISTS;
        }

        StoredHologram stored = StoredHologram.fromLocation(instanceName, settings.fancyNamePrefix() + instanceName, templateName, location, useCurrentPitch);
        storedHolograms.put(key(instanceName), stored);
        store.save(stored);
        spawnStored(stored);
        return TutorialActionResult.SUCCESS;
    }

    TutorialActionResult spawn(String name, Location fallbackLocation) {
        if (!HologramStore.isValidName(name)) {
            return TutorialActionResult.INVALID_NAME;
        }
        StoredHologram stored = storedHolograms.get(key(name));
        if (stored != null) {
            if (settings.template(stored.templateName()) == null) {
                return TutorialActionResult.TEMPLATE_NOT_FOUND;
            }
            removeActive(name);
            spawnStored(stored);
            return TutorialActionResult.SUCCESS;
        }

        if (settings.template(name) == null) {
            return TutorialActionResult.TEMPLATE_NOT_FOUND;
        }
        if (fallbackLocation == null) {
            return TutorialActionResult.NOT_FOUND;
        }
        return create(name, fallbackLocation);
    }

    TutorialActionResult spawn(String templateName, String instanceName, Location fallbackLocation) {
        return spawn(templateName, instanceName, fallbackLocation, false);
    }

    TutorialActionResult spawn(String templateName, String instanceName, Location fallbackLocation, boolean useCurrentPitch) {
        if (fallbackLocation == null) {
            return TutorialActionResult.NOT_FOUND;
        }
        StoredHologram stored = storedHolograms.get(key(instanceName));
        if (stored != null) {
            removeActive(instanceName);
            spawnStored(stored);
            return TutorialActionResult.SUCCESS;
        }
        return create(templateName, instanceName, fallbackLocation, useCurrentPitch);
    }

    TutorialActionResult reload(String name) {
        settings = TutorialSettings.load(plugin, resourceName, defaultSaveFolder, defaultFancyNamePrefix);
        if (!HologramStore.isValidName(name)) {
            return TutorialActionResult.INVALID_NAME;
        }

        StoredHologram stored = storedHolograms.get(key(name));
        if (stored != null) {
            if (settings.template(stored.templateName()) == null) {
                return TutorialActionResult.TEMPLATE_NOT_FOUND;
            }
            removeActive(stored.name());
            spawnStored(stored);
            return TutorialActionResult.SUCCESS;
        }

        if (settings.template(name) == null) {
            return TutorialActionResult.TEMPLATE_NOT_FOUND;
        }

        for (StoredHologram hologram : hologramsForTemplate(name)) {
            removeActive(hologram.name());
            spawnStored(hologram);
        }
        return TutorialActionResult.SUCCESS;
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
        spawnStored(stored);
        return true;
    }

    Collection<StoredHologram> holograms() {
        return List.copyOf(storedHolograms.values());
    }

    StoredHologram hologram(String name) {
        return storedHolograms.get(key(name));
    }

    boolean isKnown(String name) {
        return storedHolograms.containsKey(key(name));
    }

    Collection<TutorialTemplate> templates() {
        return List.copyOf(settings.templates().values());
    }

    TutorialTemplate template(String name) {
        return settings.template(name);
    }

    List<String> previewLines(String name) {
        StoredHologram stored = storedHolograms.get(key(name));
        TutorialTemplate template = settings.template(stored == null ? name : stored.templateName());
        if (template == null) {
            return List.of();
        }
        return template.lines();
    }

    List<StoredHologram> hologramsForTemplate(String templateName) {
        String key = key(templateName);
        return storedHolograms.values().stream()
                .filter(hologram -> key(hologram.templateName()).equals(key))
                .toList();
    }

    int countForTemplate(String templateName) {
        return hologramsForTemplate(templateName).size();
    }

    String nextInstanceName(String templateName) {
        String prefix = templateName.toLowerCase(Locale.ROOT) + "_";
        int index = Math.max(1, countForTemplate(templateName) + 1);
        while (isKnown(prefix + index)) {
            index++;
        }
        return prefix + index;
    }

    TutorialSettings settings() {
        return settings;
    }

    boolean doubleSidedHolograms() {
        return settings.hologramOptions().doubleSided();
    }

    boolean setDoubleSidedHolograms(boolean enabled) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
        }

        YamlConfiguration config = new YamlConfiguration();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            config.load(reader);
            config.set("hologram.double-sided", enabled);
            Files.writeString(file.toPath(), config.saveToString(), StandardCharsets.UTF_8);
            reload();
            return true;
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Could not save " + resourceName + ": " + exception.getMessage());
            return false;
        }
    }

    private void spawnStored(StoredHologram stored) {
        Location location = stored.toLocation();
        if (location == null) {
            plugin.getLogger().warning("Could not spawn " + logName + " hologram '" + stored.name() + "': world '" + stored.world() + "' is not loaded.");
            return;
        }

        TutorialTemplate template = settings.template(stored.templateName());
        if (template == null) {
            plugin.getLogger().warning("Could not spawn " + logName + " hologram '" + stored.name() + "': missing entry '" + stored.templateName() + "' in " + resourceName + ".");
            return;
        }

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        activeHolograms.put(key(stored.name()), FancyTextHolograms.spawn(manager, stored.fancyName(), location, settings.hologramOptions(), template.lines()));
    }

    private void removeActive(String name) {
        List<Hologram> active = activeHolograms.remove(key(name));
        if (active != null) {
            for (Hologram hologram : active) {
                remove(hologram);
            }
        }
    }

    private void remove(Hologram hologram) {
        try {
            FancyHologramsPlugin.get().getHologramManager().removeHologram(hologram);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not remove " + logName + " hologram '" + hologram.getName() + "': " + exception.getMessage());
        }
    }

    private void despawnAll() {
        for (List<Hologram> holograms : new ArrayList<>(activeHolograms.values())) {
            for (Hologram hologram : holograms) {
                remove(hologram);
            }
        }
        activeHolograms.clear();
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
