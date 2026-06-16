package topList;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class DatabaseBackedLeaderboardProvider implements LeaderboardProvider {

    private final JavaPlugin plugin;
    private final TopListType type;
    private final LeaderboardProvider fallback;
    private final Supplier<DatabaseSettings> databaseSettingsSupplier;

    DatabaseBackedLeaderboardProvider(
            JavaPlugin plugin,
            TopListType type,
            LeaderboardProvider fallback,
            Supplier<DatabaseSettings> databaseSettingsSupplier
    ) {
        this.plugin = plugin;
        this.type = type;
        this.fallback = fallback;
        this.databaseSettingsSupplier = databaseSettingsSupplier;
    }

    @Override
    public List<LeaderboardEntry> entries(LeaderboardSettings settings) {
        return fallback.entries(settings);
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> entriesAsync(LeaderboardSettings settings) {
        DatabaseSettings databaseSettings = databaseSettingsSupplier.get();
        DatabaseLeaderboardSettings leaderboardSettings = databaseSettings == null ? null : databaseSettings.leaderboard(type);
        if (databaseSettings == null || !databaseSettings.enabled() || leaderboardSettings == null || !leaderboardSettings.enabled()) {
            return fallback.entriesAsync(settings);
        }

        if (leaderboardSettings.query() == null || leaderboardSettings.query().isBlank()) {
            plugin.getLogger().warning("Database leaderboard " + type.argument() + " is enabled, but no query is configured.");
            return fallback.entriesAsync(settings);
        }

        return CompletableFuture.supplyAsync(() -> loadFromDatabase(settings, databaseSettings, leaderboardSettings));
    }

    @Override
    public String formatValue(double value, LeaderboardSettings settings) {
        return fallback.formatValue(value, settings);
    }

    @Override
    public void addPlaceholders(Map<String, String> placeholders, LeaderboardEntry entry, String formattedValue, LeaderboardSettings settings) {
        fallback.addPlaceholders(placeholders, entry, formattedValue, settings);
    }

    private List<LeaderboardEntry> loadFromDatabase(
            LeaderboardSettings settings,
            DatabaseSettings databaseSettings,
            DatabaseLeaderboardSettings leaderboardSettings
    ) {
        List<LeaderboardEntry> entries = new ArrayList<>();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            plugin.getLogger().warning("MySQL driver is missing. Build the plugin with mysql-connector-j included.");
            return entries;
        }

        try (
                Connection connection = DriverManager.getConnection(databaseSettings.url(), databaseSettings.username(), databaseSettings.password());
                PreparedStatement statement = connection.prepareStatement(leaderboardSettings.query())
        ) {
            if (leaderboardSettings.query().contains("?")) {
                statement.setInt(1, settings.maxEntries());
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next() && entries.size() < settings.maxEntries()) {
                    double value = resultSet.getDouble(leaderboardSettings.valueColumn());
                    if (settings.hideZeroValues() && value <= 0.0D) {
                        continue;
                    }

                    String playerName = columnString(resultSet, leaderboardSettings.nameColumn());
                    String uuid = columnString(resultSet, leaderboardSettings.uuidColumn());
                    if (playerName.isBlank()) {
                        playerName = settings.playerNameFallback();
                    }
                    entries.add(LeaderboardEntry.databaseEntry(uuid, playerName, value));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load " + type.argument() + " leaderboard from database: " + exception.getMessage());
        }

        return entries;
    }

    private String columnString(ResultSet resultSet, String column) {
        if (column == null || column.isBlank()) {
            return "";
        }

        try {
            String value = resultSet.getString(column);
            return value == null ? "" : value;
        } catch (SQLException exception) {
            return "";
        }
    }
}
