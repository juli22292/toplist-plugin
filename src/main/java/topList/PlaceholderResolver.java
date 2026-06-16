package topList;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class PlaceholderResolver {

    private final JavaPlugin plugin;
    private final boolean enabled;
    private Method setPlaceholders;

    PlaceholderResolver(JavaPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled && plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        loadMethod();
    }

    String setPlaceholders(OfflinePlayer player, String text) {
        if (!enabled || setPlaceholders == null || text == null || text.isEmpty()) {
            return text;
        }

        try {
            return (String) setPlaceholders.invoke(null, player, text);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().warning("PlaceholderAPI placeholder parsing failed: " + exception.getMessage());
            return text;
        }
    }

    private void loadMethod() {
        if (!enabled) {
            return;
        }

        try {
            Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholders = placeholderApi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            plugin.getLogger().warning("PlaceholderAPI was detected, but its setPlaceholders API could not be loaded.");
        }
    }
}
