package topList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class HologramStore {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final JavaPlugin plugin;
    private final Gson gson;
    private final Path folder;

    HologramStore(JavaPlugin plugin, String folderName) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        Path dataFolder = plugin.getDataFolder().toPath().normalize();
        Path configuredFolder = dataFolder.resolve(folderName).normalize();
        if (!configuredFolder.startsWith(dataFolder)) {
            plugin.getLogger().warning("Configured hologram folder points outside the plugin folder. Falling back to 'hologramme'.");
            configuredFolder = dataFolder.resolve("hologramme");
        }
        this.folder = configuredFolder;
    }

    static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    List<StoredHologram> loadAll(String fancyNamePrefix) {
        ensureFolder();
        List<StoredHologram> holograms = new ArrayList<>();

        try (var files = Files.list(folder)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> load(path, fancyNamePrefix, holograms));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not list toplist hologram folder: " + exception.getMessage());
        }

        return holograms;
    }

    void save(StoredHologram hologram) {
        ensureFolder();
        Path file = fileFor(hologram.name());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(hologram, writer);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save toplist hologram '" + hologram.name() + "': " + exception.getMessage());
        }
    }

    boolean delete(StoredHologram hologram) {
        try {
            return Files.deleteIfExists(fileFor(hologram.name()));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not delete toplist hologram '" + hologram.name() + "': " + exception.getMessage());
            return false;
        }
    }

    private void load(Path path, String fancyNamePrefix, List<StoredHologram> target) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StoredHologram hologram = gson.fromJson(reader, StoredHologram.class);
            if (hologram == null || !hologram.hasRequiredData() || !isValidName(hologram.name())) {
                plugin.getLogger().warning("Skipping invalid toplist hologram file: " + path.getFileName());
                return;
            }

            hologram.ensureFancyName(fancyNamePrefix);
            target.add(hologram);
        } catch (IOException | JsonSyntaxException exception) {
            plugin.getLogger().warning("Could not load toplist hologram file '" + path.getFileName() + "': " + exception.getMessage());
        }
    }

    private void ensureFolder() {
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create toplist hologram folder: " + exception.getMessage());
        }
    }

    private Path fileFor(String name) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid hologram name: " + name);
        }
        return folder.resolve(name + ".json").normalize();
    }
}
