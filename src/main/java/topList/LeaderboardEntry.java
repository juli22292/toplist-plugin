package topList;

import org.bukkit.OfflinePlayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

record LeaderboardEntry(UUID uuid, OfflinePlayer offlinePlayer, String playerName, double value) {

    LeaderboardEntry(OfflinePlayer player, String playerName, double value) {
        this(player.getUniqueId(), player, playerName, value);
    }

    LeaderboardEntry(UUID uuid, String playerName, double value) {
        this(uuid, null, playerName, value);
    }

    static LeaderboardEntry databaseEntry(String uuidText, String playerName, double value) {
        UUID uuid = parseUuid(uuidText);
        if (uuid == null) {
            uuid = UUID.nameUUIDFromBytes(("TopList:" + playerName).getBytes(StandardCharsets.UTF_8));
        }
        return new LeaderboardEntry(uuid, playerName, value);
    }

    OfflinePlayer player() {
        if (offlinePlayer != null) {
            return offlinePlayer;
        }
        return org.bukkit.Bukkit.getOfflinePlayer(uuid);
    }

    private static UUID parseUuid(String uuidText) {
        if (uuidText == null || uuidText.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(uuidText);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
