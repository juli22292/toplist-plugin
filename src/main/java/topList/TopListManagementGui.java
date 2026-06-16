package topList;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TopListManagementGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int SIZE = 54;
    private static final Map<TopListType, Integer> TYPE_SLOTS = Map.of(
            TopListType.MONEY, 20,
            TopListType.PLAYTIME, 21,
            TopListType.KILLS, 22,
            TopListType.WALKED, 23,
            TopListType.MINED, 24
    );
    private static final int[] HOLOGRAM_SLOTS = {
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final JavaPlugin plugin;
    private final TopListManager manager;
    private final Map<Integer, TopListType> typesBySlot;
    private ManagementHubGui hubGui;

    TopListManagementGui(JavaPlugin plugin, TopListManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.typesBySlot = new HashMap<>();
        TYPE_SLOTS.forEach((type, slot) -> typesBySlot.put(slot, type));
    }

    void setHubGui(ManagementHubGui hubGui) {
        this.hubGui = hubGui;
    }

    void openMain(Player player) {
        GuiHolder holder = new GuiHolder(Screen.MAIN, null, null, 0);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8TopList Management"));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(4, item(Material.BEACON, "&a&lTopList Management", List.of(
                "&7Verwalte alle Toplisten-Hologramme",
                "&7direkt über diese Kontroll-GUI."
        )));

        for (TopListType type : TopListType.values()) {
            inventory.setItem(TYPE_SLOTS.get(type), typeItem(type));
        }

        inventory.setItem(39, item(Material.CLOCK, "&eToplisten aktualisieren", List.of(
                "&7Aktualisiert nur erstellte Toplisten.",
                "&7Leere Kategorien werden übersprungen.",
                "&8Klick: Update-Queue starten"
        )));
        inventory.setItem(41, doubleSidedItem(manager.doubleSidedHolograms()));
        inventory.setItem(45, item(Material.COMPASS, "&7TopList Startseite", List.of("&8Klick: Startseite öffnen")));
        inventory.setItem(49, item(Material.BARRIER, "&cSchließen", List.of("&8Klick: Menü schließen")));
        player.openInventory(inventory);
    }

    private void openType(Player player, TopListType type) {
        openType(player, type, 0);
    }

    private void openType(Player player, TopListType type, int requestedPage) {
        HologramService service = manager.service(type);
        List<StoredHologram> holograms = new ArrayList<>(service.holograms());
        int pageCount = pageCount(holograms.size(), HOLOGRAM_SLOTS.length);
        int page = clampPage(requestedPage, pageCount);

        GuiHolder holder = new GuiHolder(Screen.TYPE, type, null, page);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8TopList: " + type.displayName()));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(4, item(typeMaterial(type), "&a&l" + type.displayName(), List.of(
                "&7Typ: &f" + type.argument(),
                "&7Hologramme: &f" + service.holograms().size(),
                "&7Update: &f" + service.leaderboardSettings().updateIntervalSeconds() + "s"
        )));
        inventory.setItem(10, item(Material.EMERALD_BLOCK, "&aNeues Hologramm erstellen", List.of(
                "&7Erstellt eine weitere Kopie an deiner Position.",
                "&7Name wird automatisch vergeben.",
                "&7Beispiel: &f" + nextName(service),
                "",
                "&8Linksklick: Pitch 0",
                "&8Rechtsklick: aktueller Blick-Pitch"
        )));
        inventory.setItem(12, item(Material.CLOCK, "&eTopliste aktualisieren", List.of(
                "&7Aktualisiert diese Topliste über die Queue.",
                "&8Klick: ausführen"
        )));
        inventory.setItem(14, item(Material.WRITABLE_BOOK, "&bVorschau im Chat", List.of(
                "&7Zeigt die gerenderten Zeilen im Chat.",
                "&8Klick: anzeigen"
        )));
        inventory.setItem(45, item(Material.COMPASS, "&7Zurück", List.of("&8Klick: Toplisten-Menü")));

        int startIndex = page * HOLOGRAM_SLOTS.length;
        for (int index = 0; index < HOLOGRAM_SLOTS.length && startIndex + index < holograms.size(); index++) {
            StoredHologram hologram = holograms.get(startIndex + index);
            inventory.setItem(HOLOGRAM_SLOTS[index], hologramItem(hologram));
        }

        addPagination(inventory, page, pageCount);
        player.openInventory(inventory);
    }

    private void openHologram(Player player, TopListType type, String name) {
        openHologram(player, type, name, 0);
    }

    private void openHologram(Player player, TopListType type, String name, int page) {
        StoredHologram hologram = manager.service(type).hologram(name);
        if (hologram == null) {
            openType(player, type, page);
            return;
        }

        GuiHolder holder = new GuiHolder(Screen.HOLOGRAM, type, name, page);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8Hologramm: " + name));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(4, hologramItem(hologram));
        inventory.setItem(10, item(Material.ENDER_PEARL, "&aZu Hologramm teleportieren", List.of("&8Klick: tpo")));
        inventory.setItem(12, item(Material.COMPASS, "&bHologramm zu dir setzen", List.of(
                "&7Speichert deine aktuelle Position.",
                "",
                "&8Linksklick: Pitch 0",
                "&8Rechtsklick: aktueller Blick-Pitch"
        )));
        inventory.setItem(14, item(Material.CLOCK, "&eHologramm aktualisieren", List.of("&8Klick: test")));
        inventory.setItem(16, item(Material.TNT, "&cHologramm löschen", List.of("&7Öffnet eine Bestätigung.", "&8Klick: löschen")));
        inventory.setItem(45, item(Material.COMPASS, "&7Zurück", List.of("&8Klick: Typ-Menü")));
        player.openInventory(inventory);
    }

    private void openDeleteConfirm(Player player, TopListType type, String name) {
        openDeleteConfirm(player, type, name, 0);
    }

    private void openDeleteConfirm(Player player, TopListType type, String name, int page) {
        GuiHolder holder = new GuiHolder(Screen.CONFIRM_DELETE, type, name, page);
        Inventory inventory = Bukkit.createInventory(holder, 27, LEGACY.deserialize("&8Löschen bestätigen"));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(18, item(Material.COMPASS, "&7Zurück", List.of("&8Klick: zurück")));
        inventory.setItem(13, item(Material.NAME_TAG, "&e" + name, List.of("&7Typ: &f" + type.displayName())));
        inventory.setItem(15, item(Material.RED_CONCRETE, "&cEndgültig löschen", List.of("&7Entfernt Hologramm und JSON-Datei.", "&8Klick: löschen")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        switch (holder.screen) {
            case MAIN -> handleMainClick(player, slot);
            case TYPE -> handleTypeClick(player, holder.type, slot, event.getClick(), holder.page);
            case HOLOGRAM -> handleHologramClick(player, holder.type, holder.hologramName, slot, event.getClick(), holder.page);
            case CONFIRM_DELETE -> handleDeleteConfirmClick(player, holder.type, holder.hologramName, slot, holder.page);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder) {
            holder.inventory = null;
        }
    }

    private void handleMainClick(Player player, int slot) {
        if (slot == 49) {
            GuiSounds.close(player);
            player.closeInventory();
            return;
        }
        if (slot == 45 && hubGui != null) {
            GuiSounds.click(player);
            hubGui.open(player);
            return;
        }
        if (slot == 39) {
            if (manager.refreshCreatedToplists()) {
                GuiSounds.success(player);
                message(player, "&aAlle erstellten Toplisten wurden in die Update-Queue gelegt.");
            } else {
                GuiSounds.error(player);
                message(player, "&cEs gibt noch keine erstellten Toplisten-Hologramme.");
            }
            openMain(player);
            return;
        }
        if (slot == 41) {
            boolean enabled = !manager.doubleSidedHolograms();
            manager.setDoubleSidedHolograms(enabled);
            GuiSounds.success(player);
            message(player, enabled ? "&aToplisten-Hologramme sind jetzt beidseitig sichtbar." : "&cToplisten-Hologramme sind jetzt nur einseitig sichtbar.");
            openMain(player);
            return;
        }

        TopListType type = typesBySlot.get(slot);
        if (type != null) {
            GuiSounds.click(player);
            openType(player, type);
        }
    }

    private void handleTypeClick(Player player, TopListType type, int slot, ClickType clickType, int page) {
        HologramService service = manager.service(type);
        int pageCount = pageCount(service.holograms().size(), HOLOGRAM_SLOTS.length);
        if (slot == 45) {
            GuiSounds.click(player);
            openMain(player);
            return;
        }
        if (slot == 48 && page > 0) {
            GuiSounds.page(player);
            openType(player, type, page - 1);
            return;
        }
        if (slot == 50 && page + 1 < pageCount) {
            GuiSounds.page(player);
            openType(player, type, page + 1);
            return;
        }
        if (slot == 10) {
            String name = nextName(service);
            service.create(name, player.getLocation().clone(), useCurrentPitch(clickType));
            GuiSounds.success(player);
            message(player, "&a" + type.displayName() + "-Hologramm &e" + name + " &aerstellt.");
            openType(player, type, page);
            return;
        }
        if (slot == 12) {
            service.refreshAll();
            GuiSounds.success(player);
            message(player, "&a" + type.displayName() + "-Topliste wurde in die Update-Queue gelegt.");
            openType(player, type, page);
            return;
        }
        if (slot == 14) {
            GuiSounds.click(player);
            message(player, "&7Vorschau der &e" + type.displayName() + "&7-Topliste:");
            for (String line : service.previewLines()) {
                message(player, line);
            }
            return;
        }

        StoredHologram hologram = hologramBySlot(service.holograms(), slot, page);
        if (hologram != null) {
            GuiSounds.click(player);
            openHologram(player, type, hologram.name(), page);
        }
    }

    private void handleHologramClick(Player player, TopListType type, String name, int slot, ClickType clickType, int page) {
        HologramService service = manager.service(type);
        if (slot == 45) {
            GuiSounds.click(player);
            openType(player, type, page);
            return;
        }
        if (slot == 10) {
            StoredHologram hologram = service.hologram(name);
            Location location = hologram == null ? null : hologram.toLocation();
            if (location == null) {
                GuiSounds.error(player);
                message(player, "&cDieses Hologramm wurde nicht gefunden.");
                openType(player, type, page);
                return;
            }
            player.teleport(location);
            GuiSounds.success(player);
            message(player, "&aDu wurdest zu &e" + name + " &ateleportiert.");
            return;
        }
        if (slot == 12) {
            service.moveHere(name, player.getLocation().clone(), useCurrentPitch(clickType));
            GuiSounds.success(player);
            message(player, "&aHologramm &e" + name + " &awurde zu dir gesetzt.");
            openHologram(player, type, name, page);
            return;
        }
        if (slot == 14) {
            service.refresh(name);
            GuiSounds.success(player);
            message(player, "&aHologramm &e" + name + " &awurde aktualisiert.");
            return;
        }
        if (slot == 16 || clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP) {
            GuiSounds.click(player);
            openDeleteConfirm(player, type, name, page);
        }
    }

    private void handleDeleteConfirmClick(Player player, TopListType type, String name, int slot, int page) {
        if (slot == 18) {
            GuiSounds.click(player);
            openHologram(player, type, name, page);
            return;
        }
        if (slot == 15) {
            manager.service(type).delete(name);
            GuiSounds.success(player);
            message(player, "&cHologramm &e" + name + " &cwurde gelöscht.");
            openType(player, type, page);
        }
    }

    private StoredHologram hologramBySlot(Collection<StoredHologram> holograms, int slot, int page) {
        int index = -1;
        for (int i = 0; i < HOLOGRAM_SLOTS.length; i++) {
            if (HOLOGRAM_SLOTS[i] == slot) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return null;
        }

        index += page * HOLOGRAM_SLOTS.length;
        int current = 0;
        for (StoredHologram hologram : holograms) {
            if (current == index) {
                return hologram;
            }
            current++;
        }
        return null;
    }

    private String nextName(HologramService service) {
        String prefix = service.type().argument().toLowerCase(Locale.ROOT) + "_";
        int index = service.holograms().size() + 1;
        while (service.isKnown(prefix + index)) {
            index++;
        }
        return prefix + index;
    }

    private ItemStack typeItem(TopListType type) {
        HologramService service = manager.service(type);
        return item(typeMaterial(type), "&a&l" + type.displayName(), List.of(
                "&7Argument: &f" + type.argument(),
                "&7Hologramme: &f" + service.holograms().size(),
                "&7Intervall: &f" + service.leaderboardSettings().updateIntervalSeconds() + "s",
                "",
                "&8Klick: verwalten"
        ));
    }

    private ItemStack hologramItem(StoredHologram hologram) {
        return item(Material.ARMOR_STAND, "&e" + hologram.name(), List.of(
                "&7Welt: &f" + hologram.world(),
                "&7X/Y/Z: &f" + coordinate(hologram.x()) + "&7, &f" + coordinate(hologram.y()) + "&7, &f" + coordinate(hologram.z()),
                "&7Yaw/Pitch: &f" + coordinate(hologram.yaw()) + "&7, &f" + coordinate(hologram.pitch()),
                "",
                "&8Klick: Aktionen öffnen"
        ));
    }

    private Material typeMaterial(TopListType type) {
        return switch (type) {
            case MONEY -> Material.EMERALD;
            case PLAYTIME -> Material.CLOCK;
            case KILLS -> Material.DIAMOND_SWORD;
            case WALKED -> Material.LEATHER_BOOTS;
            case MINED -> Material.DIAMOND_PICKAXE;
        };
    }

    private ItemStack doubleSidedItem(boolean enabled) {
        return item(enabled ? Material.LIME_DYE : Material.GRAY_DYE, enabled ? "&aBeidseitig sichtbar: AN" : "&cBeidseitig sichtbar: AUS", List.of(
                "&7Erstellt intern eine zweite Rückseite",
                "&7mit gedrehter Yaw.",
                "",
                "&8Klick: umschalten"
        ));
    }

    private boolean useCurrentPitch(ClickType clickType) {
        return clickType.isRightClick();
    }

    private void addPagination(Inventory inventory, int page, int pageCount) {
        boolean hasPrevious = page > 0;
        boolean hasNext = page + 1 < pageCount;
        inventory.setItem(48, item(
                hasPrevious ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
                hasPrevious ? "&eVorherige Seite" : "&8Keine vorherige Seite",
                hasPrevious ? List.of("&7Gehe zu Seite &f" + page + "&7/&f" + pageCount) : List.of()
        ));
        inventory.setItem(49, item(Material.MAP, "&eSeite &f" + (page + 1) + "&7/&f" + pageCount, List.of(
                "&7Weitere Einträge werden über",
                "&7die Pfeile daneben angezeigt."
        )));
        inventory.setItem(50, item(
                hasNext ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
                hasNext ? "&eNächste Seite" : "&8Keine nächste Seite",
                hasNext ? List.of("&7Gehe zu Seite &f" + (page + 2) + "&7/&f" + pageCount) : List.of()
        ));
    }

    private int pageCount(int totalEntries, int entriesPerPage) {
        return Math.max(1, (int) Math.ceil(totalEntries / (double) entriesPerPage));
    }

    private int clampPage(int page, int pageCount) {
        return Math.max(0, Math.min(page, pageCount - 1));
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(component(name));
        meta.lore(lore.stream().map(this::component).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private Component component(String text) {
        return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    private void message(Player player, String text) {
        player.sendMessage(component(text));
    }

    private String coordinate(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private enum Screen {
        MAIN,
        TYPE,
        HOLOGRAM,
        CONFIRM_DELETE
    }

    private static final class GuiHolder implements InventoryHolder {

        private final Screen screen;
        private final TopListType type;
        private final String hologramName;
        private final int page;
        private Inventory inventory;

        private GuiHolder(Screen screen, TopListType type, String hologramName, int page) {
            this.screen = screen;
            this.type = type;
            this.hologramName = hologramName;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
