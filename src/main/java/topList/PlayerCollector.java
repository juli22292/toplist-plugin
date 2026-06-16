package topList;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PlayerCollector {

    private PlayerCollector() {
    }

    static List<OfflinePlayer> players(boolean includeOfflinePlayers) {
        Map<UUID, OfflinePlayer> players = new LinkedHashMap<>();
        if (includeOfflinePlayers) {
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                players.put(player.getUniqueId(), player);
            }
        }

        Bukkit.getOnlinePlayers().forEach(player -> players.put(player.getUniqueId(), player));
        return new ArrayList<>(players.values());
    }

    static String displayName(OfflinePlayer player, String fallback) {
        String name = player.getName();
        if (name == null || name.isBlank()) {
            return fallback.replace("{uuid}", player.getUniqueId().toString());
        }
        return name;
    }
}
