package topList;

import java.text.DecimalFormat;
import java.util.List;

record LeaderboardSettings(
        TopListType type,
        String saveFolder,
        String fancyNamePrefix,
        int updateIntervalSeconds,
        boolean showUpdateTimer,
        int maxEntries,
        boolean includeOfflinePlayers,
        boolean requireEconomyAccount,
        boolean hideZeroValues,
        boolean showEmptyRanks,
        String playerNameFallback,
        DecimalFormat balanceFormat,
        boolean useVaultFormat,
        String playtimeValueFormat,
        boolean placeholderApiEnabled,
        String placeholderApiValuePlaceholder,
        boolean parseEntryLinePlaceholders,
        List<String> layoutLines,
        String entryLine,
        String emptyLine,
        String noDataLine,
        String timerLine
) {

    String formatBalance(double value) {
        return balanceFormat.format(value);
    }
}
