package topList;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class MoneyLeaderboardProvider implements LeaderboardProvider {

    private final Economy economy;

    MoneyLeaderboardProvider(Economy economy) {
        this.economy = economy;
    }

    @Override
    public List<LeaderboardEntry> entries(LeaderboardSettings settings) {
        return PlayerCollector.players(settings.includeOfflinePlayers()).stream()
                .filter(player -> shouldInclude(player, settings))
                .map(player -> new LeaderboardEntry(player, PlayerCollector.displayName(player, settings.playerNameFallback()), safeBalance(player)))
                .filter(entry -> !settings.hideZeroValues() || entry.value() > 0.0D)
                .sorted(Comparator.comparingDouble(LeaderboardEntry::value).reversed())
                .limit(settings.maxEntries())
                .toList();
    }

    @Override
    public String formatValue(double value, LeaderboardSettings settings) {
        if (settings.useVaultFormat()) {
            return safeVaultFormat(value, settings.formatBalance(value));
        }
        return settings.formatBalance(value);
    }

    @Override
    public void addPlaceholders(Map<String, String> placeholders, LeaderboardEntry entry, String formattedValue, LeaderboardSettings settings) {
        placeholders.put("balance", String.valueOf(entry.value()));
        placeholders.put("amount", formattedValue);
        placeholders.put("betrag", formattedValue);
        placeholders.put("wert", formattedValue);
        placeholders.put("balance_formatted", formattedValue);
        placeholders.put("vault_formatted", safeVaultFormat(entry.value(), formattedValue));
        placeholders.put("economy", safeString(economy.getName()));
        placeholders.put("currency", safeString(economy.currencyNamePlural()));
        placeholders.put("currency_singular", safeString(economy.currencyNameSingular()));
        placeholders.put("currency_plural", safeString(economy.currencyNamePlural()));
    }

    private boolean shouldInclude(OfflinePlayer player, LeaderboardSettings settings) {
        if (!settings.requireEconomyAccount()) {
            return true;
        }

        try {
            return economy.hasAccount(player);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private double safeBalance(OfflinePlayer player) {
        try {
            return economy.getBalance(player);
        } catch (RuntimeException exception) {
            return 0.0D;
        }
    }

    private String safeVaultFormat(double balance, String fallback) {
        try {
            String formatted = economy.format(balance);
            return formatted == null ? fallback : formatted;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
