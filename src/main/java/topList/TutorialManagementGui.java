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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TutorialManagementGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int SIZE = 54;
    private static final int[] TEMPLATE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16
    };
    private static final int[] HOLOGRAM_SLOTS = {
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final TutorialHologramService service;
    private final String inventoryTitle;
    private final Material headerIcon;
    private final String headerTitle;
    private final List<String> headerLore;
    private final String configFileName;
    private final String areaName;
    private final String instanceName;
    private final String backLabel;
    private ManagementHubGui hubGui;

    TutorialManagementGui(TutorialHologramService service) {
        this(
                service,
                "&8Tutorial Management",
                Material.LECTERN,
                "&a&lTutorial Management",
                List.of("&7Verwalte statische Tutorial-Hologramme", "&7aus der &ftutorialconfig.yml&7."),
                "tutorialconfig.yml",
                "Tutorial",
                "Tutorial-Hologramm",
                "Tutorial-Menü"
        );
    }

    TutorialManagementGui(
            TutorialHologramService service,
            String inventoryTitle,
            Material headerIcon,
            String headerTitle,
            List<String> headerLore,
            String configFileName,
            String areaName,
            String instanceName,
            String backLabel
    ) {
        this.service = service;
        this.inventoryTitle = inventoryTitle;
        this.headerIcon = headerIcon;
        this.headerTitle = headerTitle;
        this.headerLore = List.copyOf(headerLore);
        this.configFileName = configFileName;
        this.areaName = areaName;
        this.instanceName = instanceName;
        this.backLabel = backLabel;
    }

    void setHubGui(ManagementHubGui hubGui) {
        this.hubGui = hubGui;
    }

    void openMain(Player player) {
        openMain(player, 0);
    }

    private void openMain(Player player, int requestedPage) {
        List<TutorialTemplate> templates = new ArrayList<>(service.templates());
        List<StoredHologram> holograms = new ArrayList<>(service.holograms());
        int pageCount = Math.max(
                pageCount(templates.size(), TEMPLATE_SLOTS.length),
                pageCount(holograms.size(), HOLOGRAM_SLOTS.length)
        );
        int page = clampPage(requestedPage, pageCount);

        GuiHolder holder = new GuiHolder(this, Screen.MAIN, null, page);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize(inventoryTitle));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(4, item(headerIcon, headerTitle, headerLore));
        inventory.setItem(45, item(Material.COMPASS, "&7TopList Startseite", List.of("&8Klick: Startseite öffnen")));
        inventory.setItem(46, item(Material.CLOCK, "&eKonfigurationen neu laden", List.of(
                "&7Lädt Vorlagen und Hologramme neu.",
                "&8Klick: reload all"
        )));
        inventory.setItem(47, doubleSidedItem(service.doubleSidedHolograms()));
        inventory.setItem(53, item(Material.BARRIER, "&cSchließen", List.of("&8Klick: Menü schließen")));

        int templateStart = page * TEMPLATE_SLOTS.length;
        for (int index = 0; index < TEMPLATE_SLOTS.length && templateStart + index < templates.size(); index++) {
            inventory.setItem(TEMPLATE_SLOTS[index], templateItem(templates.get(templateStart + index)));
        }

        int hologramStart = page * HOLOGRAM_SLOTS.length;
        for (int index = 0; index < HOLOGRAM_SLOTS.length && hologramStart + index < holograms.size(); index++) {
            inventory.setItem(HOLOGRAM_SLOTS[index], hologramItem(holograms.get(hologramStart + index)));
        }

        addPagination(inventory, page, pageCount);
        player.openInventory(inventory);
    }

    private void openTemplate(Player player, String name) {
        openTemplate(player, name, 0);
    }

    private void openTemplate(Player player, String name, int page) {
        TutorialTemplate template = service.template(name);
        if (template == null) {
            openMain(player, page);
            return;
        }

        GuiHolder holder = new GuiHolder(this, Screen.TEMPLATE, name, page);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8" + areaName + ": " + name));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(4, templateItem(template));
        inventory.setItem(10, item(Material.EMERALD_BLOCK, "&aAn dieser Position erstellen", List.of(
                "&7Erstellt eine weitere Kopie dieser Vorlage",
                "&7an deiner aktuellen Position.",
                "&7Name wird automatisch vergeben.",
                "",
                "&8Linksklick: Pitch 0",
                "&8Rechtsklick: aktueller Blick-Pitch"
        )));
        inventory.setItem(12, item(Material.ENDER_EYE, "&bWeitere Kopie spawnen", List.of(
                "&7Spawnt eine neue Instanz aus der Config.",
                "&7Nutze das Instanz-Menü zum Aktualisieren.",
                "",
                "&8Linksklick: Pitch 0",
                "&8Rechtsklick: aktueller Blick-Pitch"
        )));
        inventory.setItem(14, item(Material.CLOCK, "&eAlle Kopien neu laden", List.of(
                "&7Lädt alle platzierten Kopien dieser Vorlage neu.",
                "&8Klick: reload " + name
        )));
        inventory.setItem(16, item(Material.WRITABLE_BOOK, "&fVorschau im Chat", List.of(
                "&7Zeigt die Hologramm-Zeilen mit &-Farbcodes.",
                "&8Klick: anzeigen"
        )));
        inventory.setItem(45, item(Material.COMPASS, "&7Zurück", List.of("&8Klick: " + backLabel)));

        addPreview(inventory, template.lines(), 28);
        player.openInventory(inventory);
    }

    private void openHologram(Player player, String name) {
        openHologram(player, name, 0);
    }

    private void openHologram(Player player, String name, int page) {
        StoredHologram hologram = service.hologram(name);
        if (hologram == null) {
            openMain(player, page);
            return;
        }

        GuiHolder holder = new GuiHolder(this, Screen.HOLOGRAM, name, page);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize("&8" + instanceName + ": " + name));
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
        inventory.setItem(14, item(Material.ENDER_EYE, "&eSpawnen oder aktualisieren", List.of(
                "&7Setzt die aktuellen Zeilen aus der " + configFileName + ".",
                "&8Klick: spawn"
        )));
        inventory.setItem(16, item(Material.CLOCK, "&6Dieses Hologramm neu laden", List.of("&8Klick: reload " + name)));
        inventory.setItem(28, item(Material.WRITABLE_BOOK, "&fVorschau im Chat", List.of("&8Klick: anzeigen")));
        inventory.setItem(34, item(Material.TNT, "&cHologramm löschen", List.of(
                "&7Entfernt Hologramm und JSON-Datei.",
                "&8Klick: löschen"
        )));
        inventory.setItem(45, item(Material.COMPASS, "&7Zurück", List.of("&8Klick: " + backLabel)));
        player.openInventory(inventory);
    }

    private void openDeleteConfirm(Player player, String name) {
        openDeleteConfirm(player, name, 0);
    }

    private void openDeleteConfirm(Player player, String name, int page) {
        GuiHolder holder = new GuiHolder(this, Screen.CONFIRM_DELETE, name, page);
        Inventory inventory = Bukkit.createInventory(holder, 27, LEGACY.deserialize("&8Löschen bestätigen"));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(18, item(Material.COMPASS, "&7Zurück", List.of("&8Klick: zurück")));
        inventory.setItem(13, item(Material.NAME_TAG, "&e" + name, List.of("&7" + instanceName)));
        inventory.setItem(15, item(Material.RED_CONCRETE, "&cEndgültig löschen", List.of("&7Entfernt Hologramm und JSON-Datei.", "&8Klick: löschen")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder) || holder.owner != this) {
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
            case MAIN -> handleMainClick(player, slot, holder.page);
            case TEMPLATE -> handleTemplateClick(player, holder.name, slot, event.getClick(), holder.page);
            case HOLOGRAM -> handleHologramClick(player, holder.name, slot, event.getClick(), holder.page);
            case CONFIRM_DELETE -> handleDeleteConfirmClick(player, holder.name, slot, holder.page);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder && holder.owner == this) {
            holder.inventory = null;
        }
    }

    private void handleMainClick(Player player, int slot, int page) {
        int pageCount = Math.max(
                pageCount(service.templates().size(), TEMPLATE_SLOTS.length),
                pageCount(service.holograms().size(), HOLOGRAM_SLOTS.length)
        );
        if (slot == 53) {
            GuiSounds.close(player);
            player.closeInventory();
            return;
        }
        if (slot == 45 && hubGui != null) {
            GuiSounds.click(player);
            hubGui.open(player);
            return;
        }
        if (slot == 46) {
            service.reload();
            GuiSounds.success(player);
            message(player, service.settings().message("reloaded"));
            openMain(player, page);
            return;
        }
        if (slot == 47) {
            boolean enabled = !service.doubleSidedHolograms();
            if (service.setDoubleSidedHolograms(enabled)) {
                GuiSounds.success(player);
                message(player, enabled ? "&a" + areaName + "-Hologramme sind jetzt beidseitig sichtbar." : "&c" + areaName + "-Hologramme sind jetzt nur einseitig sichtbar.");
            } else {
                GuiSounds.error(player);
                message(player, "&cDie Einstellung konnte nicht in der " + configFileName + " gespeichert werden.");
            }
            openMain(player, page);
            return;
        }
        if (slot == 48 && page > 0) {
            GuiSounds.page(player);
            openMain(player, page - 1);
            return;
        }
        if (slot == 50 && page + 1 < pageCount) {
            GuiSounds.page(player);
            openMain(player, page + 1);
            return;
        }

        TutorialTemplate template = templateBySlot(service.templates(), slot, page);
        if (template != null) {
            GuiSounds.click(player);
            openTemplate(player, template.name(), page);
            return;
        }

        StoredHologram hologram = hologramBySlot(service.holograms(), slot, page);
        if (hologram != null) {
            GuiSounds.click(player);
            openHologram(player, hologram.name(), page);
        }
    }

    private void handleTemplateClick(Player player, String name, int slot, ClickType clickType, int page) {
        if (slot == 45) {
            GuiSounds.click(player);
            openMain(player, page);
            return;
        }
        if (slot == 10) {
            String instanceName = service.nextInstanceName(name);
            showResult(player, service.create(name, instanceName, player.getLocation().clone(), useCurrentPitch(clickType)), instanceName, "created");
            openTemplate(player, name, page);
            return;
        }
        if (slot == 12) {
            String instanceName = service.nextInstanceName(name);
            showResult(player, service.spawn(name, instanceName, player.getLocation().clone(), useCurrentPitch(clickType)), instanceName, "spawned");
            openTemplate(player, name, page);
            return;
        }
        if (slot == 14) {
            showResult(player, service.reload(name), name, "reloaded-one");
            openTemplate(player, name, page);
            return;
        }
        if (slot == 16) {
            GuiSounds.click(player);
            preview(player, name);
        }
    }

    private void handleHologramClick(Player player, String name, int slot, ClickType clickType, int page) {
        if (slot == 45) {
            GuiSounds.click(player);
            openMain(player, page);
            return;
        }
        if (slot == 10) {
            StoredHologram hologram = service.hologram(name);
            Location location = hologram == null ? null : hologram.toLocation();
            if (location == null) {
                GuiSounds.error(player);
                message(player, service.settings().message("not-found", Map.of("name", name)));
                openMain(player, page);
                return;
            }
            player.teleport(location);
            GuiSounds.success(player);
            message(player, service.settings().message("teleported-to-hologram", Map.of("name", name)));
            return;
        }
        if (slot == 12) {
            if (service.moveHere(name, player.getLocation().clone(), useCurrentPitch(clickType))) {
                GuiSounds.success(player);
                message(player, service.settings().message("hologram-teleported-here", Map.of("name", name)));
            } else {
                GuiSounds.error(player);
                message(player, service.settings().message("not-found", Map.of("name", name)));
            }
            openHologram(player, name, page);
            return;
        }
        if (slot == 14) {
            showResult(player, service.spawn(name, player.getLocation().clone()), name, "spawned");
            return;
        }
        if (slot == 16) {
            showResult(player, service.reload(name), name, "reloaded-one");
            return;
        }
        if (slot == 28) {
            GuiSounds.click(player);
            preview(player, name);
            return;
        }
        if (slot == 34 || clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP) {
            GuiSounds.click(player);
            openDeleteConfirm(player, name, page);
        }
    }

    private void handleDeleteConfirmClick(Player player, String name, int slot, int page) {
        if (slot == 18) {
            GuiSounds.click(player);
            openHologram(player, name, page);
            return;
        }
        if (slot == 15) {
            if (service.delete(name)) {
                GuiSounds.success(player);
                message(player, service.settings().message("deleted", Map.of("name", name)));
            } else {
                GuiSounds.error(player);
            }
            openMain(player, page);
        }
    }

    private void preview(Player player, String name) {
        message(player, service.settings().message("preview-header", Map.of("name", name)));
        for (String line : service.previewLines(name)) {
            message(player, line);
        }
    }

    private void showResult(Player player, TutorialActionResult result, String name, String successKey) {
        String key = switch (result) {
            case SUCCESS -> successKey;
            case INVALID_NAME -> "invalid-name";
            case TEMPLATE_NOT_FOUND -> "template-not-found";
            case ALREADY_EXISTS -> "already-exists";
            case NOT_FOUND -> "not-found";
        };
        if (result == TutorialActionResult.SUCCESS) {
            GuiSounds.success(player);
        } else {
            GuiSounds.error(player);
        }
        message(player, service.settings().message(key, Map.of("name", name)));
    }

    private TutorialTemplate templateBySlot(Collection<TutorialTemplate> templates, int slot, int page) {
        int index = slotIndex(TEMPLATE_SLOTS, slot);
        if (index < 0) {
            return null;
        }

        index += page * TEMPLATE_SLOTS.length;
        int current = 0;
        for (TutorialTemplate template : templates) {
            if (current == index) {
                return template;
            }
            current++;
        }
        return null;
    }

    private StoredHologram hologramBySlot(Collection<StoredHologram> holograms, int slot, int page) {
        int index = slotIndex(HOLOGRAM_SLOTS, slot);
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

    private int slotIndex(int[] slots, int slot) {
        for (int index = 0; index < slots.length; index++) {
            if (slots[index] == slot) {
                return index;
            }
        }
        return -1;
    }

    private ItemStack templateItem(TutorialTemplate template) {
        int placed = service.countForTemplate(template.name());
        return item(template.icon(), template.displayName(), List.of(
                "&7Config-Name: &f" + template.name(),
                "&7Zeilen: &f" + template.lines().size(),
                "&7Platziert: &f" + placed,
                "",
                "&8Klick: Aktionen öffnen"
        ));
    }

    private ItemStack hologramItem(StoredHologram hologram) {
        return item(Material.ARMOR_STAND, "&e" + hologram.name(), List.of(
                "&7Vorlage: &f" + hologram.templateName(),
                "&7Welt: &f" + hologram.world(),
                "&7X/Y/Z: &f" + coordinate(hologram.x()) + "&7, &f" + coordinate(hologram.y()) + "&7, &f" + coordinate(hologram.z()),
                "&7Yaw/Pitch: &f" + coordinate(hologram.yaw()) + "&7, &f" + coordinate(hologram.pitch()),
                "",
                "&8Klick: Aktionen öffnen"
        ));
    }

    private void addPreview(Inventory inventory, List<String> lines, int firstSlot) {
        int slot = firstSlot;
        for (String line : lines.stream().limit(9).toList()) {
            inventory.setItem(slot++, item(Material.PAPER, "&fZeile", List.of(line)));
        }
    }

    private ItemStack doubleSidedItem(boolean enabled) {
        return item(enabled ? Material.LIME_DYE : Material.GRAY_DYE, enabled ? "&aBeidseitig sichtbar: AN" : "&cBeidseitig sichtbar: AUS", List.of(
                "&7Erstellt intern eine zweite Rückseite",
                "&7mit gedrehter Yaw.",
                "",
                "&8Klick: umschalten"
        ));
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
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

    private boolean useCurrentPitch(ClickType clickType) {
        return clickType.isRightClick();
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
        TEMPLATE,
        HOLOGRAM,
        CONFIRM_DELETE
    }

    private static final class GuiHolder implements InventoryHolder {

        private final Screen screen;
        private final TutorialManagementGui owner;
        private final String name;
        private final int page;
        private Inventory inventory;

        private GuiHolder(TutorialManagementGui owner, Screen screen, String name, int page) {
            this.owner = owner;
            this.screen = screen;
            this.name = name;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
