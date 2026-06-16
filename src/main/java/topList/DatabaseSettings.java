package topList;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

final class DatabaseSettings {

    private final boolean enabled;
    private final String jdbcUrl;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String parameters;
    private final Map<TopListType, DatabaseLeaderboardSettings> leaderboards;

    private DatabaseSettings(
            boolean enabled,
            String jdbcUrl,
            String host,
            int port,
            String database,
            String username,
            String password,
            String parameters,
            Map<TopListType, DatabaseLeaderboardSettings> leaderboards
    ) {
        this.enabled = enabled;
        this.jdbcUrl = jdbcUrl;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.parameters = parameters;
        this.leaderboards = leaderboards;
    }

    static DatabaseSettings load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "databaseconfig.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        Map<TopListType, DatabaseLeaderboardSettings> leaderboards = new EnumMap<>(TopListType.class);
        for (TopListType type : TopListType.values()) {
            String path = "leaderboards." + type.argument();
            leaderboards.put(type, new DatabaseLeaderboardSettings(
                    config.getBoolean(path + ".enabled", false),
                    config.getString(path + ".query", ""),
                    config.getString(path + ".name-column", "player_name"),
                    config.getString(path + ".uuid-column", "uuid"),
                    config.getString(path + ".value-column", "value")
            ));
        }

        return new DatabaseSettings(
                config.getBoolean("database.enabled", false),
                config.getString("database.jdbc-url", ""),
                config.getString("database.host", "127.0.0.1"),
                Math.max(1, config.getInt("database.port", 3306)),
                config.getString("database.database", "minecraft"),
                config.getString("database.username", "root"),
                config.getString("database.password", ""),
                config.getString("database.parameters", "useSSL=false&characterEncoding=utf8&serverTimezone=UTC"),
                leaderboards
        );
    }

    boolean enabled() {
        return enabled;
    }

    String url() {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            return jdbcUrl;
        }

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database;
        if (parameters != null && !parameters.isBlank()) {
            url += "?" + parameters;
        }
        return url;
    }

    String username() {
        return username;
    }

    String password() {
        return password;
    }

    DatabaseLeaderboardSettings leaderboard(TopListType type) {
        return leaderboards.get(type);
    }
}
