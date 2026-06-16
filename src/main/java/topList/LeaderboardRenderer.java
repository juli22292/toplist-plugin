package topList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LeaderboardRenderer {

    private final LeaderboardSettings settings;
    private final LeaderboardProvider provider;
    private final PlaceholderResolver placeholderResolver;

    LeaderboardRenderer(LeaderboardSettings settings, LeaderboardProvider provider, PlaceholderResolver placeholderResolver) {
        this.settings = settings;
        this.provider = provider;
        this.placeholderResolver = placeholderResolver;
    }

    List<String> renderLines(List<LeaderboardEntry> entries, long secondsUntilUpdate) {
        List<String> rendered = new ArrayList<>();
        boolean insertedEntries = false;

        for (String line : settings.layoutLines()) {
            if ("{entries}".equalsIgnoreCase(line.trim())) {
                rendered.addAll(renderEntryLines(entries));
                insertedEntries = true;
            } else {
                rendered.add(replaceGlobalPlaceholders(line, secondsUntilUpdate));
            }
        }

        if (!insertedEntries) {
            rendered.addAll(renderEntryLines(entries));
        }

        if (settings.showUpdateTimer()) {
            rendered.add(replaceGlobalPlaceholders(settings.timerLine(), secondsUntilUpdate));
        }

        return rendered;
    }

    private List<String> renderEntryLines(List<LeaderboardEntry> entries) {
        List<String> lines = new ArrayList<>();
        if (entries.isEmpty() && !settings.noDataLine().isBlank()) {
            lines.add(replaceGlobalPlaceholders(settings.noDataLine(), 0L));
        }

        for (int index = 0; index < settings.maxEntries(); index++) {
            int rank = index + 1;
            if (index < entries.size()) {
                lines.add(renderEntryLine(rank, entries.get(index)));
            } else if (settings.showEmptyRanks()) {
                lines.add(renderEmptyLine(rank));
            }
        }

        return lines;
    }

    private String renderEntryLine(int rank, LeaderboardEntry entry) {
        String formattedValue = provider.formatValue(entry.value(), settings);
        String papiValue = formattedValue;
        if (!settings.placeholderApiValuePlaceholder().isBlank()) {
            papiValue = placeholderResolver.setPlaceholders(entry.player(), settings.placeholderApiValuePlaceholder());
        }

        Map<String, String> placeholders = basePlaceholders(rank);
        placeholders.put("player", entry.playerName());
        placeholders.put("spieler", entry.playerName());
        placeholders.put("uuid", entry.player().getUniqueId().toString());
        placeholders.put("value", String.valueOf(entry.value()));
        placeholders.put("wert_roh", String.valueOf(entry.value()));
        placeholders.put("value_formatted", formattedValue);
        placeholders.put("papi_value", papiValue);
        placeholders.put("papi_wert", papiValue);
        provider.addPlaceholders(placeholders, entry, formattedValue, settings);

        String line = replacePlaceholders(settings.entryLine(), placeholders);
        if (settings.parseEntryLinePlaceholders()) {
            line = placeholderResolver.setPlaceholders(entry.player(), line);
        }
        return line;
    }

    private String renderEmptyLine(int rank) {
        return replacePlaceholders(settings.emptyLine(), basePlaceholders(rank));
    }

    private Map<String, String> basePlaceholders(int rank) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("rank", String.valueOf(rank));
        placeholders.put("platz", String.valueOf(rank));
        placeholders.put("max", String.valueOf(settings.maxEntries()));
        placeholders.put("type", settings.type().argument());
        placeholders.put("typ", settings.type().displayName());
        return placeholders;
    }

    private String replaceGlobalPlaceholders(String line, long secondsUntilUpdate) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("max", String.valueOf(settings.maxEntries()));
        placeholders.put("type", settings.type().argument());
        placeholders.put("typ", settings.type().displayName());
        placeholders.put("seconds", String.valueOf(secondsUntilUpdate));
        placeholders.put("sekunden", String.valueOf(secondsUntilUpdate));
        placeholders.put("time", formatTimer(secondsUntilUpdate));
        placeholders.put("zeit", formatTimer(secondsUntilUpdate));
        return replacePlaceholders(line, placeholders);
    }

    private String replacePlaceholders(String line, Map<String, String> placeholders) {
        String result = line;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            result = result.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return result;
    }

    private String formatTimer(long secondsUntilUpdate) {
        long seconds = Math.max(0L, secondsUntilUpdate);
        long minutes = seconds / 60L;
        long restSeconds = seconds % 60L;
        if (minutes <= 0L) {
            return restSeconds + "s";
        }
        return minutes + "m " + restSeconds + "s";
    }
}
