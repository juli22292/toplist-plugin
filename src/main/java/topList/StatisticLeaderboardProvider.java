package topList;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class StatisticLeaderboardProvider implements LeaderboardProvider {

    private static final List<Material> BLOCK_MATERIALS = Arrays.stream(Material.values())
            .filter(Material::isBlock)
            .filter(material -> !material.isAir())
            .toList();

    private final Statistic statistic;
    private final boolean centimetersToBlocks;
    private final boolean sumBlocksMined;
    private final TopListType type;

    private StatisticLeaderboardProvider(TopListType type, Statistic statistic, boolean centimetersToBlocks, boolean sumBlocksMined) {
        this.type = type;
        this.statistic = statistic;
        this.centimetersToBlocks = centimetersToBlocks;
        this.sumBlocksMined = sumBlocksMined;
    }

    static StatisticLeaderboardProvider kills() {
        return new StatisticLeaderboardProvider(TopListType.KILLS, Statistic.PLAYER_KILLS, false, false);
    }

    static StatisticLeaderboardProvider walkedBlocks() {
        return new StatisticLeaderboardProvider(TopListType.WALKED, Statistic.WALK_ONE_CM, true, false);
    }

    static StatisticLeaderboardProvider minedBlocks() {
        return new StatisticLeaderboardProvider(TopListType.MINED, Statistic.MINE_BLOCK, false, true);
    }

    @Override
    public List<LeaderboardEntry> entries(LeaderboardSettings settings) {
        return PlayerCollector.players(settings.includeOfflinePlayers()).stream()
                .map(player -> new LeaderboardEntry(player, PlayerCollector.displayName(player, settings.playerNameFallback()), value(player)))
                .filter(entry -> !settings.hideZeroValues() || entry.value() > 0.0D)
                .sorted(Comparator.comparingDouble(LeaderboardEntry::value).reversed())
                .limit(settings.maxEntries())
                .toList();
    }

    @Override
    public String formatValue(double value, LeaderboardSettings settings) {
        return settings.formatBalance(value);
    }

    @Override
    public void addPlaceholders(Map<String, String> placeholders, LeaderboardEntry entry, String formattedValue, LeaderboardSettings settings) {
        placeholders.put("wert", formattedValue);
        placeholders.put("amount", formattedValue);
        placeholders.put("anzahl", formattedValue);
        placeholders.put("stat", formattedValue);
        placeholders.put("stat_raw", String.valueOf((long) entry.value()));
        switch (type) {
            case KILLS -> placeholders.put("kills", formattedValue);
            case WALKED -> {
                placeholders.put("walked", formattedValue);
                placeholders.put("gelaufen", formattedValue);
                placeholders.put("blocks", formattedValue);
                placeholders.put("bloecke", formattedValue);
                placeholders.put("blöcke", formattedValue);
            }
            case MINED -> {
                placeholders.put("mined", formattedValue);
                placeholders.put("abgebaut", formattedValue);
                placeholders.put("blocks", formattedValue);
                placeholders.put("bloecke", formattedValue);
                placeholders.put("blöcke", formattedValue);
            }
            default -> {
            }
        }
    }

    private long value(OfflinePlayer player) {
        if (sumBlocksMined) {
            return minedBlocks(player);
        }

        try {
            int rawValue = player.getStatistic(statistic);
            if (centimetersToBlocks) {
                return Math.max(0L, rawValue / 100L);
            }
            return Math.max(0L, rawValue);
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    private long minedBlocks(OfflinePlayer player) {
        long total = 0L;
        for (Material material : BLOCK_MATERIALS) {
            try {
                total += Math.max(0, player.getStatistic(statistic, material));
            } catch (RuntimeException ignored) {
            }
        }
        return total;
    }
}
