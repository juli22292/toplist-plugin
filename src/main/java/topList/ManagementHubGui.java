package topList;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ManagementHubGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int SIZE = 54;
    private static final int[] CREATED_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final TopListManager topListManager;
    private final TutorialHologramService tutorialHologramService;
    private final TutorialHologramService freeHologramService;
    private final TopListManagementGui topListManagementGui;
    private final TutorialManagementGui tutorialManagementGui;
    private final TutorialManagementGui freeHologramGui;
    private final CollectibleSignService signService;
    private final SignManagementGui signManagementGui;

    ManagementHubGui(
            TopListManager topListManager,
            TutorialHologramService tutorialHologramService,
            TutorialHologramService freeHologramService,
            TopListManagementGui topListManagementGui,
            TutorialManagementGui tutorialManagementGui,
            TutorialManagementGui freeHologramGui,
            CollectibleSignService signService,
            SignManagementGui signManagementGui
    ) {
        this.topListManager = topListManager;
        this.tutorialHologramService = tutorialHologramService;
        this.freeHologramService = freeHologramService;
        this.topListManagementGui = topListManagementGui;
        this.tutorialManagementGui = tutorialManagementGui;
        this.freeHologramGui = freeHologramGui;
        this.signService = signService;
        this.signManagementGui = signManagementGui;
    }

    void open(Player player) {
        open(player, false);
    }

    void open(Player player, boolean playOpenSound) {
        GuiHolder holder = new GuiHolder(Screen.MAIN);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8TopList Management"));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(4, item(Material.NETHER_STAR, "&a&lManagement", List.of(
                "&7Wähle aus, welchen Bereich du verwalten möchtest."
        )));
        inventory.setItem(13, item(Material.ARMOR_STAND, "&eErstellte Hologramme", List.of(
                "&7Zeigt alle platzierten Toplisten-,",
                "&7Tutorial- und freien Hologramme.",
                "&7Erstellt: &f" + createdEntries().size(),
                "",
                "&8Klick: Übersicht öffnen"
        )));
        inventory.setItem(19, item(Material.BEACON, "&aToplisten", List.of(
                "&7Money, Playtime, Kills, Gelaufen und Abgebaut.",
                "&8Klick: öffnen"
        )));
        inventory.setItem(21, item(Material.LECTERN, "&bTutorial-Hologramme", List.of(
                "&7Statische Hologramme aus der tutorialconfig.yml.",
                "&8Klick: öffnen"
        )));
        inventory.setItem(23, item(Material.END_CRYSTAL, "&dFreie Hologramme", List.of(
                "&7Normale Text-Hologramme aus der hologramconfig.yml.",
                "&8Klick: öffnen"
        )));
        inventory.setItem(25, item(Material.OAK_SIGN, "&6Sammel-Schilder", List.of(
                "&7LAdvancement-Schilder aus der signconfig.yml.",
                "&7Vorlagen: &f" + signService.templates().size(),
                "&8Klick: öffnen"
        )));
        inventory.setItem(31, item(Material.COMPARATOR, "&eEinstellungen", List.of(
                "&7Reload, Update-Queue und globale Optionen.",
                "&8Klick: öffnen"
        )));
        inventory.setItem(49, item(Material.BARRIER, "&cSchließen", List.of("&8Klick: Menü schließen")));
        player.openInventory(inventory);
        if (playOpenSound) {
            GuiSounds.open(player);
        }
    }

    private void openCreated(Player player) {
        openCreated(player, 0);
    }

    private void openCreated(Player player, int requestedPage) {
        List<CreatedEntry> entries = createdEntries();
        int pageCount = pageCount(entries.size(), CREATED_SLOTS.length);
        int page = clampPage(requestedPage, pageCount);

        GuiHolder holder = new GuiHolder(Screen.CREATED, page, null);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8Erstellte Hologramme"));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(4, item(Material.ARMOR_STAND, "&e&lErstellte Hologramme", List.of(
                "&7Alle gespeicherten Hologramme an einem Ort.",
                "&7Anzahl: &f" + entries.size(),
                "",
                "&8Klick auf einen Eintrag: löschen"
        )));

        if (entries.isEmpty()) {
            inventory.setItem(22, item(Material.RED_STAINED_GLASS_PANE, "&cKeine Hologramme vorhanden", List.of(
                    "&7Es wurde noch nichts erstellt."
            )));
        } else {
            int startIndex = page * CREATED_SLOTS.length;
            for (int index = 0; index < CREATED_SLOTS.length && startIndex + index < entries.size(); index++) {
                inventory.setItem(CREATED_SLOTS[index], createdItem(entries.get(startIndex + index)));
            }
        }

        addPagination(inventory, page, pageCount);
        inventory.setItem(45, item(Material.COMPASS, "&7TopList Startseite", List.of("&8Klick: zurück")));
        inventory.setItem(53, item(Material.BARRIER, "&cSchließen", List.of("&8Klick: Menü schließen")));
        player.openInventory(inventory);
    }

    private void openDeleteConfirm(Player player, CreatedEntry entry, int page) {
        GuiHolder holder = new GuiHolder(Screen.CONFIRM_DELETE, page, entry);
        Inventory inventory = Bukkit.createInventory(holder, 27, LEGACY.deserialize("&8Löschen bestätigen"));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(18, item(Material.COMPASS, "&7Zurück", List.of("&8Klick: zurück")));
        inventory.setItem(13, createdItem(entry));
        inventory.setItem(15, item(Material.RED_CONCRETE, "&cEndgültig löschen", List.of(
                "&7Entfernt Hologramm und JSON-Datei.",
                "&8Klick: löschen"
        )));
        player.openInventory(inventory);
    }

    void openSettings(Player player) {
        GuiHolder holder = new GuiHolder(Screen.SETTINGS);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8TopList Einstellungen"));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(4, item(Material.COMPARATOR, "&e&lEinstellungen", List.of(
                "&7Globale Verwaltung für TopList.",
                "&7Updates bleiben auf Toplisten begrenzt."
        )));
        inventory.setItem(10, item(Material.CLOCK, "&eKonfigurationen neu laden", List.of(
                "&7Lädt Configs, Datenbanken, Hologramme",
                "&7und Sammel-Schilder neu.",
                "&8Klick: reload all"
        )));
        inventory.setItem(12, item(Material.BEACON, "&aToplisten aktualisieren", List.of(
                "&7Aktualisiert nur die Kategorie Toplisten.",
                "&7Es werden nur erstellte Hologramme verarbeitet.",
                "&8Klick: Update-Queue starten"
        )));
        inventory.setItem(14, doubleSidedItem());
        inventory.setItem(16, item(signService.settings().rewardEnabled() ? Material.EMERALD : Material.REDSTONE, "&6Schild-Rewards", List.of(
                "&7Status: " + (signService.settings().rewardEnabled() ? "&aaktiv" : "&caus"),
                "&7Beträge stehen pro Schild in",
                "&fsignconfig.yml &7unter &freward-amount&7.",
                "&8Klick: Sammel-Schilder öffnen"
        )));
        inventory.setItem(45, item(Material.COMPASS, "&7TopList Startseite", List.of("&8Klick: zurück")));
        inventory.setItem(49, item(Material.BARRIER, "&cSchließen", List.of("&8Klick: Menü schließen")));

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
        if (holder.screen == Screen.SETTINGS) {
            handleSettingsClick(player, slot);
            return;
        }
        if (holder.screen == Screen.CREATED) {
            handleCreatedClick(player, slot, holder.page);
            return;
        }
        if (holder.screen == Screen.CONFIRM_DELETE) {
            handleDeleteConfirmClick(player, slot, holder.page, holder.entry);
            return;
        }

        if (slot == 13) {
            GuiSounds.click(player);
            openCreated(player);
        } else if (slot == 19) {
            GuiSounds.click(player);
            topListManagementGui.openMain(player);
        } else if (slot == 21) {
            GuiSounds.click(player);
            tutorialManagementGui.openMain(player);
        } else if (slot == 23) {
            GuiSounds.click(player);
            freeHologramGui.openMain(player);
        } else if (slot == 25) {
            GuiSounds.click(player);
            signManagementGui.openMain(player);
        } else if (slot == 31) {
            GuiSounds.click(player);
            openSettings(player);
        } else if (slot == 49) {
            GuiSounds.close(player);
            player.closeInventory();
        }
    }

    private void handleSettingsClick(Player player, int slot) {
        if (slot == 10) {
            topListManager.reload();
            tutorialHologramService.reload();
            freeHologramService.reload();
            signService.reload();
            GuiSounds.success(player);
            player.sendMessage(component("&aAlle Konfigurationen wurden neu geladen."));
            openSettings(player);
            return;
        }
        if (slot == 12) {
            if (topListManager.refreshCreatedToplists()) {
                GuiSounds.success(player);
                player.sendMessage(component("&aAlle erstellten Toplisten wurden in die Update-Queue gelegt."));
            } else {
                GuiSounds.error(player);
                player.sendMessage(component("&cEs gibt noch keine erstellten Toplisten-Hologramme."));
            }
            openSettings(player);
            return;
        }
        if (slot == 14) {
            boolean enabled = !allDoubleSided();
            topListManager.setDoubleSidedHolograms(enabled);
            boolean tutorialSaved = tutorialHologramService.setDoubleSidedHolograms(enabled);
            boolean freeSaved = freeHologramService.setDoubleSidedHolograms(enabled);
            if (tutorialSaved && freeSaved) {
                GuiSounds.success(player);
                player.sendMessage(component(enabled ? "&aAlle Hologramme sind jetzt beidseitig sichtbar." : "&cAlle Hologramme sind jetzt nur einseitig sichtbar."));
            } else {
                GuiSounds.error(player);
                player.sendMessage(component("&cMindestens eine Hologramm-Config konnte nicht gespeichert werden."));
            }
            openSettings(player);
            return;
        }
        if (slot == 16) {
            GuiSounds.click(player);
            signManagementGui.openMain(player);
            return;
        }
        if (slot == 45) {
            GuiSounds.click(player);
            open(player);
            return;
        }
        if (slot == 49) {
            GuiSounds.close(player);
            player.closeInventory();
        }
    }

    private void handleCreatedClick(Player player, int slot, int page) {
        List<CreatedEntry> entries = createdEntries();
        int pageCount = pageCount(entries.size(), CREATED_SLOTS.length);
        if (slot == 45) {
            GuiSounds.click(player);
            open(player);
            return;
        }
        if (slot == 48 && page > 0) {
            GuiSounds.page(player);
            openCreated(player, page - 1);
            return;
        }
        if (slot == 50 && page + 1 < pageCount) {
            GuiSounds.page(player);
            openCreated(player, page + 1);
            return;
        }
        if (slot == 53) {
            GuiSounds.close(player);
            player.closeInventory();
            return;
        }

        CreatedEntry entry = entryBySlot(entries, slot, page);
        if (entry != null) {
            GuiSounds.click(player);
            openDeleteConfirm(player, entry, page);
        }
    }

    private void handleDeleteConfirmClick(Player player, int slot, int page, CreatedEntry entry) {
        if (slot == 18) {
            GuiSounds.click(player);
            openCreated(player, page);
            return;
        }
        if (slot == 15 && entry != null) {
            if (delete(entry)) {
                GuiSounds.success(player);
                player.sendMessage(component("&aHologramm &e" + entry.name() + " &awurde gelöscht."));
            } else {
                GuiSounds.error(player);
                player.sendMessage(component("&cDieses Hologramm wurde nicht gefunden."));
            }
            openCreated(player, page);
        }
    }

    private ItemStack doubleSidedItem() {
        boolean allEnabled = allDoubleSided();
        boolean anyEnabled = topListManager.doubleSidedHolograms()
                || tutorialHologramService.doubleSidedHolograms()
                || freeHologramService.doubleSidedHolograms();
        Material material = allEnabled ? Material.LIME_DYE : anyEnabled ? Material.YELLOW_DYE : Material.GRAY_DYE;
        String status = allEnabled ? "&aAN" : anyEnabled ? "&eTEILWEISE" : "&cAUS";
        return item(material, "&dBeidseitige Hologramme: " + status, List.of(
                "&7Gilt für Toplisten, Tutorial",
                "&7und freie Hologramme zusammen.",
                "",
                "&8Klick: alle umschalten"
        ));
    }

    private boolean allDoubleSided() {
        return topListManager.doubleSidedHolograms()
                && tutorialHologramService.doubleSidedHolograms()
                && freeHologramService.doubleSidedHolograms();
    }

    private List<CreatedEntry> createdEntries() {
        List<CreatedEntry> entries = new ArrayList<>();
        for (TopListType type : TopListType.values()) {
            HologramService service = topListManager.service(type);
            for (StoredHologram hologram : service.holograms()) {
                entries.add(new CreatedEntry(CreatedKind.TOPLIST, type, hologram.name(), hologram));
            }
        }
        for (StoredHologram hologram : tutorialHologramService.holograms()) {
            entries.add(new CreatedEntry(CreatedKind.TUTORIAL, null, hologram.name(), hologram));
        }
        for (StoredHologram hologram : freeHologramService.holograms()) {
            entries.add(new CreatedEntry(CreatedKind.FREE, null, hologram.name(), hologram));
        }
        return entries;
    }

    private CreatedEntry entryBySlot(List<CreatedEntry> entries, int slot, int page) {
        int index = slotIndex(CREATED_SLOTS, slot);
        if (index < 0) {
            return null;
        }
        index += page * CREATED_SLOTS.length;
        if (index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    private int slotIndex(int[] slots, int slot) {
        for (int index = 0; index < slots.length; index++) {
            if (slots[index] == slot) {
                return index;
            }
        }
        return -1;
    }

    private boolean delete(CreatedEntry entry) {
        return switch (entry.kind()) {
            case TOPLIST -> topListManager.service(entry.type()).delete(entry.name());
            case TUTORIAL -> tutorialHologramService.delete(entry.name());
            case FREE -> freeHologramService.delete(entry.name());
        };
    }

    private ItemStack createdItem(CreatedEntry entry) {
        StoredHologram hologram = entry.hologram();
        List<String> lore = new ArrayList<>();
        lore.add("&7Bereich: &f" + entry.sourceName());
        lore.add("&7Welt: &f" + hologram.world());
        lore.add("&7X/Y/Z: &f" + coordinate(hologram.x()) + "&7, &f" + coordinate(hologram.y()) + "&7, &f" + coordinate(hologram.z()));
        lore.add("&7Yaw/Pitch: &f" + coordinate(hologram.yaw()) + "&7, &f" + coordinate(hologram.pitch()));
        if (entry.kind() != CreatedKind.TOPLIST) {
            lore.add("&7Vorlage: &f" + hologram.templateName());
        }
        lore.add("");
        lore.add("&8Klick: Löschbestätigung öffnen");
        return item(entry.icon(), "&e" + entry.name(), lore);
    }

    private void addPagination(Inventory inventory, int page, int pageCount) {
        boolean hasPrevious = page > 0;
        boolean hasNext = page + 1 < pageCount;
        inventory.setItem(48, item(
                hasPrevious ? Material.ARROW : Material.RED_STAINED_GLASS_PANE,
                hasPrevious ? "&eVorherige Seite" : "&cKeine vorherige Seite",
                hasPrevious ? List.of("&7Gehe zu Seite &f" + page + "&7/&f" + pageCount) : List.of()
        ));
        inventory.setItem(49, item(Material.MAP, "&eSeite &f" + (page + 1) + "&7/&f" + pageCount, List.of(
                "&7Weitere Einträge werden über",
                "&7die Pfeile daneben angezeigt."
        )));
        inventory.setItem(50, item(
                hasNext ? Material.ARROW : Material.RED_STAINED_GLASS_PANE,
                hasNext ? "&eNächste Seite" : "&cKeine nächste Seite",
                hasNext ? List.of("&7Gehe zu Seite &f" + (page + 2) + "&7/&f" + pageCount) : List.of()
        ));
    }

    private int pageCount(int totalEntries, int entriesPerPage) {
        return Math.max(1, (int) Math.ceil(totalEntries / (double) entriesPerPage));
    }

    private int clampPage(int page, int pageCount) {
        return Math.max(0, Math.min(page, pageCount - 1));
    }

    private String coordinate(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder) {
            holder.inventory = null;
        }
    }

    private void fill(Inventory inventory) {
        GuiLayout.fill(
                inventory,
                item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()),
                item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())
        );
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

    private enum Screen {
        MAIN,
        SETTINGS,
        CREATED,
        CONFIRM_DELETE
    }

    private enum CreatedKind {
        TOPLIST,
        TUTORIAL,
        FREE
    }

    private record CreatedEntry(CreatedKind kind, TopListType type, String name, StoredHologram hologram) {

        String sourceName() {
            return switch (kind) {
                case TOPLIST -> type.displayName() + "-Topliste";
                case TUTORIAL -> "Tutorial-Hologramm";
                case FREE -> "Freies Hologramm";
            };
        }

        Material icon() {
            if (kind != CreatedKind.TOPLIST) {
                return kind == CreatedKind.TUTORIAL ? Material.LECTERN : Material.END_CRYSTAL;
            }
            return switch (type) {
                case MONEY -> Material.EMERALD;
                case PLAYTIME -> Material.CLOCK;
                case KILLS -> Material.DIAMOND_SWORD;
                case WALKED -> Material.LEATHER_BOOTS;
                case MINED -> Material.DIAMOND_PICKAXE;
            };
        }
    }

    private static final class GuiHolder implements InventoryHolder {

        private final Screen screen;
        private final int page;
        private final CreatedEntry entry;
        private Inventory inventory;

        private GuiHolder(Screen screen) {
            this(screen, 0, null);
        }

        private GuiHolder(Screen screen, int page, CreatedEntry entry) {
            this.screen = screen;
            this.page = page;
            this.entry = entry;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
