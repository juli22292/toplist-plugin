package topList;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class CollectibleSignService implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final NamespacedKey signKey;
    private final CollectibleSignProgressStore progressStore;

    private CollectibleSignSettings settings;

    CollectibleSignService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.signKey = new NamespacedKey(plugin, "ladvancement_sign");
        this.progressStore = new CollectibleSignProgressStore(plugin);
    }

    void load() {
        settings = CollectibleSignSettings.load(plugin);
    }

    void reload() {
        load();
    }

    CollectibleSignSettings settings() {
        return settings;
    }

    Collection<CollectibleSignTemplate> templates() {
        return List.copyOf(settings.signs().values());
    }

    CollectibleSignTemplate template(String name) {
        return settings.sign(name);
    }

    ItemStack itemFor(String name, int amount) {
        CollectibleSignTemplate template = template(name);
        if (template == null) {
            return null;
        }

        ItemStack item = new ItemStack(template.material(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(component(applyPlaceholders(settings.itemName(), template)));
        meta.lore(settings.itemLore().stream()
                .map(line -> component(applyPlaceholders(line, template)))
                .toList());
        meta.getPersistentDataContainer().set(signKey, PersistentDataType.STRING, template.name());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    Set<String> collected(Player player) {
        return progressStore.collected(player.getUniqueId());
    }

    boolean hasCollected(Player player, String signName) {
        return progressStore.has(player.getUniqueId(), signName);
    }

    int totalSigns() {
        return settings.signs().size();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        String signName = signName(event.getItemInHand());
        if (signName == null) {
            return;
        }

        CollectibleSignTemplate template = template(signName);
        if (template == null) {
            event.getPlayer().sendMessage(component(settings.message("template-not-found", Map.of("name", signName))));
            return;
        }

        if (!(event.getBlockPlaced().getState() instanceof Sign sign)) {
            return;
        }

        applySignData(sign, template);
        event.getPlayer().sendMessage(component(settings.message("placed", Map.of("name", template.displayName()))));
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_WOOD_PLACE, 0.6F, 1.2F);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }

        String signName = sign.getPersistentDataContainer().get(signKey, PersistentDataType.STRING);
        if (signName == null || signName.isBlank()) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        CollectibleSignTemplate template = template(signName);
        if (template == null) {
            player.sendMessage(component(settings.message("template-not-found", Map.of("name", signName))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55F, 0.75F);
            return;
        }

        if (!progressStore.add(player.getUniqueId(), template.name())) {
            player.sendMessage(component(settings.message("already-collected", Map.of("name", template.displayName()))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55F, 0.75F);
            return;
        }

        int collected = collected(player).size();
        int total = totalSigns();
        player.sendMessage(component(settings.message("collected", Map.of(
                "name", template.displayName(),
                "collected", String.valueOf(collected),
                "total", String.valueOf(total)
        ))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.65F, 1.35F);
        giveReward(player, template);
    }

    private void applySignData(Sign sign, CollectibleSignTemplate template) {
        sign.getPersistentDataContainer().set(signKey, PersistentDataType.STRING, template.name());
        sign.setWaxed(true);

        applyLines(sign.getSide(Side.FRONT), template.lines());
        applyLines(sign.getSide(Side.BACK), template.lines());
        sign.update(true, false);
    }

    private void applyLines(SignSide side, List<String> lines) {
        for (int index = 0; index < 4; index++) {
            String line = index < lines.size() ? lines.get(index) : "";
            side.line(index, component(line));
        }
    }

    private void giveReward(Player player, CollectibleSignTemplate template) {
        if (!settings.rewardEnabled()) {
            return;
        }

        String command = settings.rewardCommand()
                .replace("{spieler}", player.getName())
                .replace("{player}", player.getName())
                .replace("{amount}", template.rewardAmount());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        player.sendMessage(component(settings.message("reward", Map.of(
                "name", template.displayName(),
                "amount", template.rewardAmount()
        ))));
    }

    private String signName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        String signName = container.get(signKey, PersistentDataType.STRING);
        if (signName == null || signName.isBlank()) {
            return null;
        }
        return signName;
    }

    private String applyPlaceholders(String text, CollectibleSignTemplate template) {
        return text
                .replace("{id}", template.name())
                .replace("{name}", template.displayName());
    }

    private Component component(String text) {
        return LEGACY.deserialize(text == null ? "" : text).decoration(TextDecoration.ITALIC, false);
    }

    List<String> templateNames() {
        List<String> names = new ArrayList<>();
        for (CollectibleSignTemplate template : settings.signs().values()) {
            names.add(template.name());
        }
        return names;
    }

    String normalized(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
