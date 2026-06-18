package topList;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import java.util.Set;

final class LAdvancementGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final CollectibleSignService service;

    LAdvancementGui(CollectibleSignService service) {
        this.service = service;
    }

    void open(Player player) {
        open(player, 0);
    }

    private void open(Player player, int requestedPage) {
        List<CollectibleSignTemplate> templates = new ArrayList<>(service.templates());
        int pageCount = pageCount(templates.size(), CONTENT_SLOTS.length);
        int page = clampPage(requestedPage, pageCount);
        Set<String> collected = service.collected(player);

        GuiHolder holder = new GuiHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, component(service.settings().guiTitle()));
        holder.inventory = inventory;

        fill(inventory);
        inventory.setItem(4, item(Material.NETHER_STAR, "&a&lLAdvancement", List.of(
                "&7Gesammelte Schilder: &a" + collected.size() + "&7/&f" + service.totalSigns(),
                "&7Klicke Sammel-Schilder in der Welt an,",
                "&7um sie hier freizuschalten."
        )));

        int startIndex = page * CONTENT_SLOTS.length;
        for (int index = 0; index < CONTENT_SLOTS.length && startIndex + index < templates.size(); index++) {
            CollectibleSignTemplate template = templates.get(startIndex + index);
            inventory.setItem(CONTENT_SLOTS[index], signItem(template, collected.contains(service.normalized(template.name()))));
        }

        addPagination(inventory, page, pageCount);
        inventory.setItem(53, item(Material.BARRIER, "&cSchließen", List.of("&8Klick: Menü schließen")));
        player.openInventory(inventory);
        GuiSounds.open(player);
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
        if (slot == 48 && holder.page > 0) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.55F, 1.15F);
            open(player, holder.page - 1);
            return;
        }
        if (slot == 50 && holder.page + 1 < pageCount(service.templates().size(), CONTENT_SLOTS.length)) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.55F, 1.15F);
            open(player, holder.page + 1);
            return;
        }
        if (slot == 53) {
            GuiSounds.close(player);
            player.closeInventory();
            return;
        }

        if (slot >= 0 && slot < event.getInventory().getSize()) {
            GuiSounds.click(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder) {
            holder.inventory = null;
        }
    }

    private ItemStack signItem(CollectibleSignTemplate template, boolean collected) {
        List<String> lore = new ArrayList<>();
        lore.add(collected ? "&aStatus: Gesammelt" : "&7Status: Noch nicht gesammelt");
        lore.add("");
        lore.addAll(template.guiLore());
        lore.add("");
        lore.add("&8Config-ID: &f" + template.name());

        Material material = collected ? template.material() : Material.GRAY_DYE;
        String name = collected ? "&a" + template.displayName() : "&8???";
        return item(material, name, lore);
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
