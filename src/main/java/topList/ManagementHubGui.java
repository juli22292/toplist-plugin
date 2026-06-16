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

import java.util.List;

final class ManagementHubGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int SIZE = 54;

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

        if (slot == 19) {
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder) {
            holder.inventory = null;
        }
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

    private enum Screen {
        MAIN,
        SETTINGS
    }

    private static final class GuiHolder implements InventoryHolder {

        private final Screen screen;
        private Inventory inventory;

        private GuiHolder(Screen screen) {
            this.screen = screen;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
