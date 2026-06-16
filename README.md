# TopList

TopList ist ein Paper/FancyHolograms Plugin für Minecraft-Server.

## Funktionen

- Toplisten-Hologramme für Vault Economy, Spielzeit, Kills, gelaufene Blöcke und abgebaute Blöcke
- FancyHolograms Integration
- Vault Economy Support
- EssentialsX Spielzeit-Anzeige
- Datenbank-Toplisten für Netzwerk-Statistiken
- Management-GUIs für Toplisten, Tutorial-Hologramme, freie Hologramme und Sammel-Schilder
- LAdvancement Sammel-Schilder mit optionalen Rewards
- Update-Prüfung über `latest.txt`

## Abhängigkeiten

- Paper `1.21.11`
- Vault
- FancyHolograms

Optionale Integrationen:

- PlaceholderAPI
- LuckPerms
- EssentialsX
- MySqlPlayerBridge

## Update-Prüfung

Die Datei `latest.txt` im Repository enthält die neueste Plugin-Version.
Das Plugin liest die URL aus `updates.yml`:

```yaml
latest-url: "https://raw.githubusercontent.com/juli22292/toplist-plugin/main/latest.txt"
```

Wenn `latest.txt` eine höhere Version enthält als die installierte Plugin-Version, werden Console und berechtigte Admins informiert.

## Build

```bash
mvn clean package
```

Die fertige Jar liegt danach in `target/`.
