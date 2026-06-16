package topList;

import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class PlaytimeLeaderboardProvider implements LeaderboardProvider {

    private final Statistic playtimeStatistic;

    PlaytimeLeaderboardProvider() {
        this.playtimeStatistic = findPlaytimeStatistic();
    }

    @Override
    public List<LeaderboardEntry> entries(LeaderboardSettings settings) {
        return PlayerCollector.players(settings.includeOfflinePlayers()).stream()
                .map(player -> new LeaderboardEntry(player, PlayerCollector.displayName(player, settings.playerNameFallback()), playtimeSeconds(player)))
                .filter(entry -> !settings.hideZeroValues() || entry.value() > 0.0D)
                .sorted(Comparator.comparingDouble(LeaderboardEntry::value).reversed())
                .limit(settings.maxEntries())
                .toList();
    }

    @Override
    public String formatValue(double value, LeaderboardSettings settings) {
        long totalSeconds = Math.max(0L, (long) value);
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;

        return settings.playtimeValueFormat()
                .replace("{days}", String.valueOf(days))
                .replace("{hours}", String.valueOf(hours))
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{seconds}", String.valueOf(seconds))
                .replace("{total_seconds}", String.valueOf(totalSeconds))
                .replace("{total_minutes}", String.valueOf(totalSeconds / 60L))
                .replace("{total_hours}", String.valueOf(totalSeconds / 3_600L));
    }

    @Override
    public void addPlaceholders(Map<String, String> placeholders, LeaderboardEntry entry, String formattedValue, LeaderboardSettings settings) {
        long totalSeconds = Math.max(0L, (long) entry.value());
        placeholders.put("playtime", formattedValue);
        placeholders.put("spielzeit", formattedValue);
        placeholders.put("zeit", formattedValue);
        placeholders.put("dauer", formattedValue);
        placeholders.put("wert", formattedValue);
        placeholders.put("playtime_seconds", String.valueOf(totalSeconds));
        placeholders.put("playtime_minutes", String.valueOf(totalSeconds / 60L));
        placeholders.put("playtime_hours", String.valueOf(totalSeconds / 3_600L));
    }

    private long playtimeSeconds(OfflinePlayer player) {
        try {
            return Math.max(0L, player.getStatistic(playtimeStatistic) / 20L);
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    private Statistic findPlaytimeStatistic() {
        try {
            return Statistic.valueOf("PLAY_ONE_MINUTE");
        } catch (IllegalArgumentException exception) {
            return Statistic.valueOf("PLAY_ONE_TICK");
        }
    }
}
