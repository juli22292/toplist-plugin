package topList;

import java.util.Locale;

enum TopListType {

    MONEY("money", "Geld", "moneyhologramme", "geld"),
    PLAYTIME("playtime", "Spielzeit", "playtimehologramme", "spielzeit"),
    KILLS("kills", "Kills", "killshologramme", "kill"),
    WALKED("walked", "Gelaufene Blöcke", "walkedhologramme", "travel", "traveled", "laufen"),
    MINED("mined", "Abgebaute Blöcke", "minedhologramme", "blocks", "bloecke", "blöcke", "abbauen");

    private final String argument;
    private final String displayName;
    private final String defaultFolder;
    private final String[] aliases;

    TopListType(String argument, String displayName, String defaultFolder, String... aliases) {
        this.argument = argument;
        this.displayName = displayName;
        this.defaultFolder = defaultFolder;
        this.aliases = aliases;
    }

    static TopListType fromArgument(String argument) {
        if (argument == null) {
            return null;
        }

        String normalized = argument.toLowerCase(Locale.ROOT);
        for (TopListType type : values()) {
            if (type.argument.equals(normalized)) {
                return type;
            }
            for (String alias : type.aliases) {
                if (alias.equals(normalized)) {
                    return type;
                }
            }
        }
        return null;
    }

    String argument() {
        return argument;
    }

    String displayName() {
        return displayName;
    }

    String configPath() {
        return "leaderboards." + argument;
    }

    String defaultFolder() {
        return defaultFolder;
    }

    String defaultFancyNamePrefix() {
        return "topliste_" + argument + "_";
    }
}
