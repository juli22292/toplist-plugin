package topList;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
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
import java.util.Map;

final class SignManagementGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int SIZE = 54;
    private static final int[] SIGN_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final CollectibleSignService service;
    private ManagementHubGui hubGui;

    SignManagementGui(CollectibleSignService service) {
        this.service = service;
    }

    void setHubGui(ManagementHubGui hubGui) {
        this.hubGui = hubGui;
    }

    void openMain(Player player) {
        openMain(player, 0);
    }

    private void openMain(Player player, int requestedPage) {
        List<CollectibleSignTemplate> templates = new ArrayList<>(service.templates());
        int pageCount = pageCount(templates.size(), SIGN_SLOTS.length);
        int page = clampPage(requestedPage, pageCount);

        GuiHolder holder = new GuiHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, component("&8Sammel-Schilder Management"));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(4, item(Material.OAK_SIGN, "&6&lSammel-Schilder", List.of(
                "&7Vorlagen: &f" + templates.size(),
                "&7Reward: " + (service.settings().rewardEnabled() ? "&aaktiv" : "&caus"),
                "&7Standardbetrag: &e" + service.settings().rewardAmount(),
                "",
                "&8Klick auf ein Schild: 1x geben",
                "&8Rechtsklick: 8x geben",
                "&8Shift-Klick: 64x geben"
        )));
        inventory.setItem(10, item(Material.CLOCK, "&eKonfigurationen neu laden", List.of(
                "&7Lädt die signconfig.yml neu.",
                "&8Klick: reload"
        )));
        inventory.setItem(12, item(service.settings().rewardEnabled() ? Material.EMERALD : Material.REDSTONE, "&aReward-System", List.of(
                "&7Status: " + (service.settings().rewardEnabled() ? "&aaktiv" : "&caus"),
                "&7Befehl: &f" + service.settings().rewardCommand(),
                "&7Betrag kommt pro Schild aus",
                "&f     reward-amount"
        )));
        inventory.setItem(14, item(Material.WRITABLE_BOOK, "&bVorlagen im Chat anzeigen", List.of(
                "&7Listet alle Sammel-Schilder",
                "&7mit ihrem Reward-Betrag.",
                "&8Klick: anzeigen"
        )));
        inventory.setItem(45, item(Material.COMPASS, "&7TopList Startseite", List.of("&8Klick: Startseite öffnen")));
        inventory.setItem(53, item(Material.BARRIER, "&cSchließen", List.of("&8Klick: Menü schließen")));

        int startIndex = page * SIGN_SLOTS.length;
        for (int index = 0; index < SIGN_SLOTS.length && startIndex + index < templates.size(); index++) {
            inventory.setItem(SIGN_SLOTS[index], signItem(templates.get(startIndex + index)));
        }

        addPagination(inventory, page, pageCount);
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
        if (slot == 10) {
            service.reload();
            GuiSounds.success(player);
            message(player, service.settings().message("reloaded"));
            openMain(player, holder.page);
            return;
        }
        if (slot == 14) {
            GuiSounds.click(player);
            listInChat(player);
            return;
        }
        if (slot == 48 && holder.page > 0) {
            GuiSounds.page(player);
            openMain(player, holder.page - 1);
            return;
        }
        if (slot == 50 && holder.page + 1 < pageCount(service.templates().size(), SIGN_SLOTS.length)) {
            GuiSounds.page(player);
            openMain(player, holder.page + 1);
            return;
        }

        CollectibleSignTemplate template = templateBySlot(service.templates(), slot, holder.page);
        if (template != null) {
            giveSign(player, template, amountFor(event.getClick()));
            openMain(player, holder.page);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder) {
            holder.inventory = null;
        }
    }

    private void giveSign(Player player, CollectibleSignTemplate template, int amount) {
        ItemStack item = service.itemFor(template.name(), amount);
        if (item == null) {
            GuiSounds.error(player);
            message(player, service.settings().message("template-not-found", Map.of("name", template.name())));
            return;
        }

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        GuiSounds.success(player);
        message(player, service.settings().message("received", Map.of(
                "name", template.displayName(),
                "amount", String.valueOf(amount)
        )));
    }

    private int amountFor(ClickType clickType) {
        if (clickType.isShiftClick()) {
            return 64;
        }
        if (clickType.isRightClick()) {
            return 8;
        }
        return 1;
    }

    private void listInChat(Player player) {
        message(player, service.settings().message("list-header"));
        for (CollectibleSignTemplate template : service.templates()) {
            message(player, service.settings().message("list-entry", Map.of(
                    "id", template.name(),
                    "name", template.displayName(),
                    "amount", template.rewardAmount()
            )));
        }
    }

    private CollectibleSignTemplate templateBySlot(Collection<CollectibleSignTemplate> templates, int slot, int page) {
        int index = slotIndex(slot);
        if (index < 0) {
            return null;
        }

        index += page * SIGN_SLOTS.length;
        int current = 0;
        for (CollectibleSignTemplate template : templates) {
            if (current == index) {
                return template;
            }
            current++;
        }
        return null;
    }

    private int slotIndex(int slot) {
        for (int index = 0; index < SIGN_SLOTS.length; index++) {
            if (SIGN_SLOTS[index] == slot) {
                return index;
            }
        }
        return -1;
    }

    private ItemStack signItem(CollectibleSignTemplate template) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Config-ID: &f" + template.name());
        lore.add("&7Reward-Betrag: &e" + template.rewardAmount());
        lore.add("&7Material: &f" + template.material().name());
        lore.add("");
        lore.addAll(template.guiLore());
        lore.add("");
        lore.add("&8Linksklick: 1x geben");
        lore.add("&8Rechtsklick: 8x geben");
        lore.add("&8Shift-Klick: 64x geben");
        return item(template.material(), template.displayName(), lore);
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
                "&7Weitere Schilder werden über",
                "&7die Pfeile angezeigt."
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
        return LEGACY.deserialize(text == null ? "" : text).decoration(TextDecoration.ITALIC, false);
    }

    private void message(Player player, String text) {
        player.sendMessage(component(text));
    }

    private static final class GuiHolder implements InventoryHolder {

        private final int page;
        private Inventory inventory;

        private GuiHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
