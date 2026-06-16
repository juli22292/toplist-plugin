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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class CollectibleSignProgressStore {

    private final JavaPlugin plugin;
    private final Gson gson;
    private final Path folder;

    CollectibleSignProgressStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.folder = plugin.getDataFolder().toPath().resolve("ladvancement").resolve("players").normalize();
    }

    Set<String> collected(UUID uuid) {
        ensureFolder();
        Path file = fileFor(uuid);
        if (!Files.isRegularFile(file)) {
            return new LinkedHashSet<>();
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            PlayerSignProgress progress = gson.fromJson(reader, PlayerSignProgress.class);
            if (progress == null || progress.collected == null) {
                return new LinkedHashSet<>();
            }

            Set<String> collected = new LinkedHashSet<>();
            for (String sign : progress.collected) {
                if (sign != null && !sign.isBlank()) {
                    collected.add(sign.toLowerCase(Locale.ROOT));
                }
            }
            return collected;
        } catch (IOException | JsonSyntaxException exception) {
            plugin.getLogger().warning("Could not load LAdvancement progress for " + uuid + ": " + exception.getMessage());
            return new LinkedHashSet<>();
        }
    }

    boolean has(UUID uuid, String signName) {
        return collected(uuid).contains(key(signName));
    }

    boolean add(UUID uuid, String signName) {
        Set<String> collected = collected(uuid);
        boolean added = collected.add(key(signName));
        if (added) {
            save(uuid, collected);
        }
        return added;
    }

    private void save(UUID uuid, Set<String> collected) {
        ensureFolder();
        try (Writer writer = Files.newBufferedWriter(fileFor(uuid), StandardCharsets.UTF_8)) {
            gson.toJson(new PlayerSignProgress(List.copyOf(collected)), writer);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save LAdvancement progress for " + uuid + ": " + exception.getMessage());
        }
    }

    private Path fileFor(UUID uuid) {
        return folder.resolve(uuid + ".json").normalize();
    }

    private void ensureFolder() {
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create LAdvancement player folder: " + exception.getMessage());
        }
    }

    private String key(String signName) {
        return signName.toLowerCase(Locale.ROOT);
    }

    private static final class PlayerSignProgress {

        private List<String> collected;

        private PlayerSignProgress(List<String> collected) {
            this.collected = collected;
        }
    }
}
