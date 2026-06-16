package topList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

interface LeaderboardProvider {

    List<LeaderboardEntry> entries(LeaderboardSettings settings);

    default CompletableFuture<List<LeaderboardEntry>> entriesAsync(LeaderboardSettings settings) {
        return CompletableFuture.completedFuture(entries(settings));
    }

    String formatValue(double value, LeaderboardSettings settings);

    void addPlaceholders(Map<String, String> placeholders, LeaderboardEntry entry, String formattedValue, LeaderboardSettings settings);
}
