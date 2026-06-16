package topList;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class LAdvancementCommand implements CommandExecutor {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final CollectibleSignService service;
    private final LAdvancementGui gui;

    LAdvancementCommand(CollectibleSignService service, LAdvancementGui gui) {
        this.service = service;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LEGACY.deserialize(service.settings().message("player-only")));
            return true;
        }
        if (!player.hasPermission("topliste.ladvancement")) {
            player.sendMessage(LEGACY.deserialize(service.settings().message("no-permission")));
            return true;
        }

        gui.open(player);
        return true;
    }
}
